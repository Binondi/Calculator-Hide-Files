package devs.org.calculator.utils

import android.content.Context
import android.net.Uri
import android.util.Log
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

object SecurityUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 256
    private const val TAG = "SecurityUtils"

    private fun getSecretKey(context: Context): SecretKey {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        val encodedKey = keyStore.getString("secret_key", null)

        return if (encodedKey != null) {
            try {
                val decodedKey = android.util.Base64.decode(encodedKey, android.util.Base64.DEFAULT)
                SecretKeySpec(decodedKey, ALGORITHM)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding stored key, generating new key", e)
                generateAndStoreNewKey(keyStore)
            }
        } else {
            Log.d(TAG, "No stored key found, generating new key")
            generateAndStoreNewKey(keyStore)
        }
    }

    private fun generateAndStoreNewKey(keyStore: SharedPreferences): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_SIZE, SecureRandom())
        val key = keyGenerator.generateKey()
        val encodedKey = android.util.Base64.encodeToString(key.encoded, android.util.Base64.DEFAULT)
        keyStore.edit().putString("secret_key", encodedKey).apply()
        return key
    }

    fun encryptFile(context: Context, inputFile: File, outputFile: File): Boolean {
        return try {
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist: ${inputFile.absolutePath}")
                return false
            }

            val secretKey = getSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

            FileInputStream(inputFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    // Write IV at the beginning of the file
                    output.write(iv)
                    CipherOutputStream(output, cipher).use { cipherOutput ->
                        input.copyTo(cipherOutput)
                    }
                }
            }

            // Verify the encrypted file exists and has content
            if (!outputFile.exists() || outputFile.length() == 0L) {
                Log.e(TAG, "Encrypted file is empty or does not exist: ${outputFile.absolutePath}")
                return false
            }

            // Verify we can read the IV from the encrypted file
            FileInputStream(outputFile).use { input ->
                val iv = ByteArray(16)
                val bytesRead = input.read(iv)
                if (bytesRead != 16) {
                    Log.e(TAG, "Failed to verify IV in encrypted file: expected 16 bytes but got $bytesRead")
                    return false
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting file: ${e.message}", e)
            // Clean up the output file if it exists
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
                Log.e(TAG, "Encrypted file does not exist: ${meta.filePath}")
                return null
            }

            // Create a unique temp file name using the original file name
            val tempDir = File(context.cacheDir, "preview_temp")
            if (!tempDir.exists()) tempDir.mkdirs()

            // Use the original extension from metadata
            val tempFile = File(tempDir, "preview_${System.currentTimeMillis()}_${meta.fileName}")
            
            // Clean up any existing temp files
            tempDir.listFiles()?.forEach { it.delete() }

            // Attempt to decrypt the file
            val success = decryptFile(context, encryptedFile, tempFile)
            
            if (success && tempFile.exists() && tempFile.length() > 0) {
                Log.d(TAG, "Successfully created preview file: ${tempFile.absolutePath}")
                return tempFile
            } else {
                Log.e(TAG, "Failed to create preview file or file is empty")
                if (tempFile.exists()) tempFile.delete()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating preview file: ${e.message}", e)
            return null
        }
    }


    fun getDecryptedFileUri(context: Context, encryptedFile: File): Uri? {
        return try {
            // Create temp file in cache dir with same extension
            val extension = getFileExtension(encryptedFile)
            val tempFile = File.createTempFile("decrypted_", extension, context.cacheDir)

            if (decryptFile(context, encryptedFile, tempFile)) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    tempFile
                )
                uri
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get decrypted file URI: ${e.message}", e)
            null
        }
    }

    fun getUriForPreviewFile(context: Context, file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider", // Must match AndroidManifest
                file
            )
        } catch (e: Exception) {
            Log.e("PreviewUtils", "Error getting URI", e)
            null
        }
    }



    fun decryptFile(context: Context, inputFile: File, outputFile: File): Boolean {
        return try {
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist: ${inputFile.absolutePath}")
                return false
            }

            if (inputFile.length() == 0L) {
                Log.e(TAG, "Input file is empty: ${inputFile.absolutePath}")
                return false
            }

            val secretKey = getSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)

            // First verify we can read the IV
            FileInputStream(inputFile).use { input ->
                val iv = ByteArray(16)
                val bytesRead = input.read(iv)
                if (bytesRead != 16) {
                    Log.e(TAG, "Failed to read IV: expected 16 bytes but got $bytesRead")
                    return false
                }
                
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

                // Create a new input stream for the actual decryption
                FileInputStream(inputFile).use { decInput ->
                    // Skip the IV
                    decInput.skip(16)
                    
                    FileOutputStream(outputFile).use { output ->
                        CipherInputStream(decInput, cipher).use { cipherInput ->
                            cipherInput.copyTo(output)
                        }
                    }
                }
            }

            // Verify the decrypted file exists and has content
            if (!outputFile.exists() || outputFile.length() == 0L) {
                Log.e(TAG, "Decrypted file is empty or does not exist: ${outputFile.absolutePath}")
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting file: ${e.message}", e)
            // Clean up the output file if it exists
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
} 