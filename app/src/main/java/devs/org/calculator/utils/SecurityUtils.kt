package devs.org.calculator.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class SecurityUtils {
    companion object {
        private const val ALGORITHM = "AES"
        private const val HIDDEN_FOLDER = "Calculator_Data"
        
        fun validatePassword(input: String, storedHash: String): Boolean {
            return input.hashCode().toString() == storedHash
        }

        fun encryptFile(context: Context, sourceUri: Uri, password: String): File {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val secretKey = generateKey(password)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val hiddenDir = File(context.getExternalFilesDir(null), HIDDEN_FOLDER)
            if (!hiddenDir.exists()) hiddenDir.mkdirs()

            val encryptedFile = File(hiddenDir, "${System.currentTimeMillis()}_encrypted")
            val outputStream = FileOutputStream(encryptedFile)

            inputStream?.use { input ->
                val buffer = ByteArray(1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val encrypted = cipher.update(buffer, 0, read)
                    outputStream.write(encrypted)
                }
                val finalBlock = cipher.doFinal()
                outputStream.write(finalBlock)
            }
            outputStream.close()
            return encryptedFile
        }

        fun decryptFile(file: File, password: String): ByteArray {
            val secretKey = generateKey(password)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            val inputStream = FileInputStream(file)
            val bytes = inputStream.readBytes()
            inputStream.close()

            return cipher.doFinal(bytes)
        }

        private fun generateKey(password: String): SecretKey {
            val keyBytes = password.toByteArray().copyOf(16)
            return SecretKeySpec(keyBytes, ALGORITHM)
        }
    }
} 