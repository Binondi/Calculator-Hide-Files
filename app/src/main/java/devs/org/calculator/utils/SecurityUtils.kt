package devs.org.calculator.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.content.SharedPreferences
import androidx.core.content.FileProvider
import devs.org.calculator.database.HiddenFileEntity
import androidx.core.content.edit
import android.util.Log

object SecurityUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 256
    val ENCRYPTED_EXTENSION = ".enc"

    private fun getSecretKey(context: Context): SecretKey {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        val useCustomKey = keyStore.getBoolean("use_custom_key", false)
        
        if (useCustomKey) {
            val customKey = keyStore.getString("custom_key", null)
            if (customKey != null) {
                try {
                    val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
                    val keyBytes = messageDigest.digest(customKey.toByteArray())
                    return SecretKeySpec(keyBytes, ALGORITHM)
                } catch (_: Exception) {
                }
            }
        }

        val encodedKey = keyStore.getString("secret_key", null)
        return if (encodedKey != null) {
            try {
                val decodedKey = android.util.Base64.decode(encodedKey, android.util.Base64.DEFAULT)
                SecretKeySpec(decodedKey, ALGORITHM)
            } catch (_: Exception) {
                generateAndStoreNewKey(keyStore)
            }
        } else {
            generateAndStoreNewKey(keyStore)
        }
    }

    private fun generateAndStoreNewKey(keyStore: SharedPreferences): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_SIZE, SecureRandom())
        val key = keyGenerator.generateKey()
        val encodedKey = android.util.Base64.encodeToString(key.encoded, android.util.Base64.DEFAULT)
        keyStore.edit { putString("secret_key", encodedKey) }
        return key
    }

    fun encryptFile(context: Context, inputFile: File, outputFile: File): Boolean {
        return try {
            if (!inputFile.exists()) {
                return false
            }

            val secretKey = getSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

            FileInputStream(inputFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    output.write(iv)
                    CipherOutputStream(output, cipher).use { cipherOutput ->
                        input.copyTo(cipherOutput)
                    }
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return false
            }

            FileInputStream(outputFile).use { input ->
                val iv = ByteArray(16)
                val bytesRead = input.read(iv)
                if (bytesRead != 16) {

                    return false
                }
            }

            true
        } catch (_: Exception) {

            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }

    fun getDecryptedPreviewFile(context: Context, meta: HiddenFileEntity): File? {
        try {
            val encryptedFile = File(meta.filePath)
            if (!encryptedFile.exists()) {
                Log.e("SecurityUtils", "Encrypted file does not exist: ${meta.filePath}")
                return null
            }

            val tempDir = File(context.cacheDir, "preview_temp")
            if (!tempDir.exists()) {
                if (!tempDir.mkdirs()) {
                    Log.e("SecurityUtils", "Failed to create temp directory")
                    return null
                }
            }

            // Clean up old preview files
            tempDir.listFiles()?.forEach { 
                if (it.lastModified() < System.currentTimeMillis() - 5 * 60 * 1000) { // 5 minutes
                    it.delete()
                }
            }

            val tempFile = File(tempDir, "preview_${System.currentTimeMillis()}_${meta.fileName}")
            
            val success = decryptFile(context, encryptedFile, tempFile)
            
            if (success && tempFile.exists() && tempFile.length() > 0) {
                return tempFile
            } else {
                Log.e("SecurityUtils", "Failed to decrypt preview file: ${meta.filePath}")
                if (tempFile.exists()) tempFile.delete()
                return null
            }
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Error in getDecryptedPreviewFile: ${e.message}")
            return null
        }
    }

    fun getUriForPreviewFile(context: Context, file: File): Uri? {
        return try {
            if (!file.exists() || file.length() == 0L) {
                Log.e("SecurityUtils", "Preview file does not exist or is empty: ${file.absolutePath}")
                return null
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Error getting URI for preview file: ${e.message}")
            null
        }
    }

    fun decryptFile(context: Context, inputFile: File, outputFile: File): Boolean {
        return try {
            if (!inputFile.exists()) {
                return false
            }

            if (inputFile.length() == 0L) {
                return false
            }

            val secretKey = getSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            FileInputStream(inputFile).use { input ->
                val iv = ByteArray(16)
                val bytesRead = input.read(iv)
                if (bytesRead != 16) {
                    return false
                }
                
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

                FileInputStream(inputFile).use { decInput ->
                    decInput.skip(16)
                    
                    FileOutputStream(outputFile).use { output ->
                        CipherInputStream(decInput, cipher).use { cipherInput ->
                            cipherInput.copyTo(output)
                        }
                    }
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return false
            }

            true
        } catch (_: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }

    fun getFileExtension(file: File): String {
        val name = file.name
        val lastDotIndex = name.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            name.substring(lastDotIndex)
        } else {
            ""
        }
    }

    fun changeFileExtension(file: File, newExtension: String): File {
        val name = file.name
        val lastDotIndex = name.lastIndexOf('.')
        val newName = if (lastDotIndex > 0) {
            name.substring(0, lastDotIndex) + newExtension
        } else {
            name + newExtension
        }
        return File(file.parent, newName)
    }

    fun setCustomKey(context: Context, key: String): Boolean {
        return try {
            val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
            keyStore.edit {
                putString("custom_key", key)
                putBoolean("use_custom_key", true)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCustomKey(context: Context) {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        keyStore.edit {
            remove("custom_key")
            putBoolean("use_custom_key", false)
        }
    }

    fun isUsingCustomKey(context: Context): Boolean {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        return keyStore.getBoolean("use_custom_key", false)
    }
} 