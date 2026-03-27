package devs.org.calculator.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Settings.Secure
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.core.content.FileProvider
import devs.org.calculator.database.HiddenFileEntity
import android.util.Log

object SecurityUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val BUFFER_SIZE = 64 * 1024
    val ENCRYPTED_EXTENSION = ".enc"
    val DEFAULT_KEY = "encryption_key_default"

    @SuppressLint("HardwareIds")
    private fun deriveDeviceKey(context: Context): SecretKey {
        val androidId = Secure.getString(
            context.contentResolver,
            Secure.ANDROID_ID
        ) ?: "fallback_id"

        val rawMaterial = "${context.packageName}:$androidId:$DEFAULT_KEY"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(rawMaterial.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    private fun getSecretKey(context: Context): SecretKey {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        val useCustomKey = keyStore.getBoolean("use_custom_key", false)

        if (useCustomKey) {
            val customKey = keyStore.getString("custom_key", null)
            if (customKey != null) {
                return try {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val keyBytes = digest.digest(customKey.toByteArray(Charsets.UTF_8))
                    SecretKeySpec(keyBytes, ALGORITHM)
                } catch (_: Exception) {
                    deriveDeviceKey(context)
                }
            }
        }
        return deriveDeviceKey(context)
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
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            cipherOutput.write(buffer, 0, bytesRead)
                        }
                        cipherOutput.flush()
                    }
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return false
            }
            true
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Encryption failed: ${e.message}")
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

            val previewDir = File(context.filesDir, "previews")
            if (!previewDir.exists()) {
                previewDir.mkdirs()
            }

            val fileNameHash = meta.filePath.hashCode().toString()
            val cachedPreview = File(previewDir, "pre_${fileNameHash}_${meta.fileName}")
            
            if (cachedPreview.exists() && cachedPreview.length() > 0) {
                return cachedPreview
            }

            val success = decryptFile(context, encryptedFile, cachedPreview)

            return if (success && cachedPreview.exists() && cachedPreview.length() > 0) {
                cachedPreview
            } else {
                Log.e("SecurityUtils", "Failed to decrypt preview file: ${meta.filePath}")
                if (cachedPreview.exists()) cachedPreview.delete()
                null
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
                "devs.org.calculator.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Error getting URI for preview file: ${e.message}")
            null
        }
    }

    fun decryptFile(context: Context, inputFile: File, outputFile: File): Boolean {
        return try {
            if (!inputFile.exists() || inputFile.length() < 16) {
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

                FileOutputStream(outputFile).use { output ->
                    CipherInputStream(input, cipher).use { cipherInput ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesReadInner: Int
                        while (cipherInput.read(buffer).also { bytesReadInner = it } != -1) {
                            output.write(buffer, 0, bytesReadInner)
                        }
                        output.flush()
                    }
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return false
            }

            true
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Decryption failed: ${e.message}")
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
            keyStore.edit().apply {
                putString("custom_key", key)
                putBoolean("use_custom_key", true)
                apply()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCustomKey(context: Context) {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        keyStore.edit().apply {
            remove("custom_key")
            putBoolean("use_custom_key", false)
            apply()
        }
    }

    fun isUsingCustomKey(context: Context): Boolean {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        return keyStore.getBoolean("use_custom_key", false)
    }
}