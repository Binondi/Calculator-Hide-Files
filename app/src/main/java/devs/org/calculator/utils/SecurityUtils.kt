package devs.org.calculator.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.Settings.Secure
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileEntity
import devs.org.calculator.database.HiddenFileRepository
import devs.org.calculator.databinding.BottomSheetEncryptionInfoBinding
import devs.org.calculator.databinding.BottomSheetSetKeyBinding
import devs.org.calculator.databinding.MigrationProgressDialogBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {

    const val ENCRYPTED_EXTENSION = ".enc"
    const val DEFAULT_KEY = "encryption_key_default"

    // Magic bytes for the new AES-GCM self-contained format
    private val MAGIC = byteArrayOf('E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte(), '3'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1

    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val BUFFER_SIZE = 64 * 1024

    // Legacy parameters
    private const val LEGACY_ALGORITHM = "AES"
    private const val LEGACY_TRANSFORMATION = "AES/CBC/PKCS5Padding"

    data class EncryptedFileMetadata(
        val originalFileName: String,
        val originalExtension: String,
        val fileType: FileManager.FileType
    )

    // ==========================================
    // Key Derivation
    // ==========================================

    fun hasCustomKey(context: Context): Boolean {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        val customKey = keyStore.getString("custom_key", null)
        return !customKey.isNullOrBlank()
    }

    fun isUsingCustomKey(context: Context): Boolean = hasCustomKey(context)

    fun getCustomKey(context: Context): String? {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        val customKey = keyStore.getString("custom_key", null)
        return if (!customKey.isNullOrBlank()) customKey else null
    }

    fun getMasterKeyString(context: Context): String {
        val customKey = getCustomKey(context)
        if (!customKey.isNullOrEmpty()) {
            return customKey
        }

        @SuppressLint("HardwareIds")
        val androidId = Secure.getString(
            context.contentResolver,
            Secure.ANDROID_ID
        ) ?: "fallback_id"

        return "${context.packageName}:$androidId:$DEFAULT_KEY"
    }

    private fun derivePbkdf2Key(context: Context, salt: ByteArray): SecretKey {
        val password = getMasterKeyString(context)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    @SuppressLint("HardwareIds")
    private fun deriveLegacyKey(context: Context): SecretKey {
        val keyStore = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        if (keyStore.getBoolean("use_custom_key", false)) {
            val customKey = keyStore.getString("custom_key", null)
            if (!customKey.isNullOrEmpty()) {
                return try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val keyBytes = digest.digest(customKey.toByteArray(Charsets.UTF_8))
                    SecretKeySpec(keyBytes, LEGACY_ALGORITHM)
                } catch (_: Exception) {
                    deriveLegacyDeviceKey(context)
                }
            }
        }
        return deriveLegacyDeviceKey(context)
    }

    @SuppressLint("HardwareIds")
    private fun deriveLegacyDeviceKey(context: Context): SecretKey {
        val androidId = Secure.getString(
            context.contentResolver,
            Secure.ANDROID_ID
        ) ?: "fallback_id"

        val rawMaterial = "${context.packageName}:$androidId:$DEFAULT_KEY"
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(rawMaterial.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, LEGACY_ALGORITHM)
    }

    // ==========================================
    // Format Detection
    // ==========================================

    fun isNewFormat(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                val read = input.read(magic)
                read == 4 && magic.contentEquals(MAGIC)
            }
        } catch (_: Exception) {
            false
        }
    }

    // ==========================================
    // Metadata Header Inspection (Fast, ~1ms)
    // ==========================================

    fun readMetadataFromEncryptedFile(context: Context, file: File): EncryptedFileMetadata? {
        if (!file.exists() || file.length() < (4 + 1 + SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + 4)) {
            return null
        }
        return try {
            FileInputStream(file).use { fis ->
                val dis = DataInputStream(fis)

                val magic = ByteArray(4)
                dis.readFully(magic)
                if (!magic.contentEquals(MAGIC)) return null

                val version = dis.readByte()
                if (version != FORMAT_VERSION) return null

                val salt = ByteArray(SALT_LENGTH_BYTES)
                dis.readFully(salt)

                val metaIv = ByteArray(GCM_IV_LENGTH_BYTES)
                dis.readFully(metaIv)

                val metaLen = dis.readInt()
                if (metaLen <= 0 || metaLen > 64 * 1024) return null

                val encMeta = ByteArray(metaLen)
                dis.readFully(encMeta)

                val secretKey = derivePbkdf2Key(context, salt)
                val metaCipher = Cipher.getInstance("AES/GCM/NoPadding")
                metaCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, metaIv))
                val plainMetaBytes = metaCipher.doFinal(encMeta)

                val json = JSONObject(String(plainMetaBytes, Charsets.UTF_8))
                val name = json.optString("name", file.name.removeSuffix(ENCRYPTED_EXTENSION))
                val ext = json.optString("ext", "")
                val typeStr = json.optString("type", "DOCUMENT")
                val fileType = try {
                    FileManager.FileType.valueOf(typeStr)
                } catch (_: Exception) {
                    FileManager.FileType.DOCUMENT
                }

                EncryptedFileMetadata(name, ext, fileType)
            }
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Failed to read metadata from ${file.name}: ${e.message}")
            null
        }
    }

    // ==========================================
    // Encryption (New Format: AES-256-GCM + Header)
    // ==========================================

    fun encryptFile(
        context: Context,
        inputFile: File,
        outputFile: File,
        metadata: EncryptedFileMetadata? = null
    ): Boolean {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            return false
        }

        if (!hasCustomKey(context)) {
            Log.e("SecurityUtils", "Cannot encrypt file: No encryption key is set.")
            return false
        }

        val parentDir = outputFile.parentFile ?: inputFile.parentFile ?: context.cacheDir
        val tempOutputFile = File(parentDir, "${outputFile.name}.tmp_${System.currentTimeMillis()}")

        return try {
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
            val metaIv = ByteArray(GCM_IV_LENGTH_BYTES).also { random.nextBytes(it) }
            val payloadIv = ByteArray(GCM_IV_LENGTH_BYTES).also { random.nextBytes(it) }

            val secretKey = derivePbkdf2Key(context, salt)

            // 1. Prepare and encrypt metadata
            val origName = metadata?.originalFileName ?: inputFile.name
            val origExt = metadata?.originalExtension ?: getFileExtension(inputFile)
            val fileType = metadata?.fileType ?: detectFileTypeFromBytes(inputFile).second

            val metaJson = JSONObject().apply {
                put("name", origName)
                put("ext", origExt)
                put("type", fileType.name)
            }.toString().toByteArray(Charsets.UTF_8)

            val metaCipher = Cipher.getInstance("AES/GCM/NoPadding")
            metaCipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, metaIv))
            val encMeta = metaCipher.doFinal(metaJson)

            // 2. Stream-encrypt file payload
            val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
            payloadCipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, payloadIv))

            FileOutputStream(tempOutputFile).use { fos ->
                val dos = DataOutputStream(fos)

                // Write header
                dos.write(MAGIC)
                dos.writeByte(FORMAT_VERSION.toInt())
                dos.write(salt)
                dos.write(metaIv)
                dos.writeInt(encMeta.size)
                dos.write(encMeta)
                dos.write(payloadIv)
                dos.flush()

                // Encrypt payload
                FileInputStream(inputFile).use { fis ->
                    val inBuffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (fis.read(inBuffer).also { bytesRead = it } != -1) {
                        val outBuffer = payloadCipher.update(inBuffer, 0, bytesRead)
                        if (outBuffer != null && outBuffer.isNotEmpty()) {
                            fos.write(outBuffer)
                        }
                    }
                }
                val finalBytes = payloadCipher.doFinal()
                if (finalBytes != null && finalBytes.isNotEmpty()) {
                    fos.write(finalBytes)
                }
                fos.flush()
            }

            if (!tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                if (tempOutputFile.exists()) tempOutputFile.delete()
                return false
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }
            val renamed = tempOutputFile.renameTo(outputFile)
            if (!renamed) {
                tempOutputFile.copyTo(outputFile, overwrite = true)
                tempOutputFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Encryption failed: ${e.message}")
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            false
        }
    }

    // ==========================================
    // Decryption (Auto-Detects ENC3 vs Legacy CBC)
    // ==========================================

    fun decryptFile(context: Context, inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists() || inputFile.length() < 16) {
            return false
        }

        return if (isNewFormat(inputFile)) {
            decryptNewFormat(context, inputFile, outputFile)
        } else {
            decryptLegacyFile(context, inputFile, outputFile)
        }
    }

    private fun decryptNewFormat(context: Context, inputFile: File, outputFile: File): Boolean {
        val parentDir = outputFile.parentFile ?: inputFile.parentFile ?: context.cacheDir
        val tempOutputFile = File(parentDir, "${outputFile.name}.tmp_${System.currentTimeMillis()}")

        return try {
            FileInputStream(inputFile).use { fis ->
                val dis = DataInputStream(fis)

                val magic = ByteArray(4)
                dis.readFully(magic)
                if (!magic.contentEquals(MAGIC)) return false

                val version = dis.readByte()
                if (version != FORMAT_VERSION) return false

                val salt = ByteArray(SALT_LENGTH_BYTES)
                dis.readFully(salt)

                val metaIv = ByteArray(GCM_IV_LENGTH_BYTES)
                dis.readFully(metaIv)

                val metaLen = dis.readInt()
                if (metaLen <= 0 || metaLen > 64 * 1024) return false

                // Skip or read metadata bytes
                val encMeta = ByteArray(metaLen)
                dis.readFully(encMeta)

                val payloadIv = ByteArray(GCM_IV_LENGTH_BYTES)
                dis.readFully(payloadIv)

                val secretKey = derivePbkdf2Key(context, salt)
                val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
                payloadCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, payloadIv))

                FileOutputStream(tempOutputFile).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val outBuffer = payloadCipher.update(buffer, 0, bytesRead)
                        if (outBuffer != null && outBuffer.isNotEmpty()) {
                            fos.write(outBuffer)
                        }
                    }
                    val finalBytes = payloadCipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                    fos.flush()
                }
            }

            if (!tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                if (tempOutputFile.exists()) tempOutputFile.delete()
                return false
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }
            val renamed = tempOutputFile.renameTo(outputFile)
            if (!renamed) {
                tempOutputFile.copyTo(outputFile, overwrite = true)
                tempOutputFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e("SecurityUtils", "New format decryption failed: ${e.message}")
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            false
        }
    }

    private fun decryptLegacyFile(context: Context, inputFile: File, outputFile: File): Boolean {
        val parentDir = outputFile.parentFile ?: inputFile.parentFile ?: context.cacheDir
        val tempOutputFile = File(parentDir, "${outputFile.name}.tmp_${System.currentTimeMillis()}")

        return try {
            val secretKey = deriveLegacyKey(context)
            val cipher = Cipher.getInstance(LEGACY_TRANSFORMATION)

            FileInputStream(inputFile).use { input ->
                val iv = ByteArray(16)
                val bytesRead = input.read(iv)
                if (bytesRead != 16) {
                    return false
                }
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

                FileOutputStream(tempOutputFile).use { output ->
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

            if (!tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                if (tempOutputFile.exists()) tempOutputFile.delete()
                return false
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }
            val renamed = tempOutputFile.renameTo(outputFile)
            if (!renamed) {
                tempOutputFile.copyTo(outputFile, overwrite = true)
                tempOutputFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Legacy decryption failed: ${e.message}")
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            false
        }
    }

    // ==========================================
    // Previews (Safe, Cached, Atomic)
    // ==========================================

    fun getDecryptedPreviewFile(context: Context, meta: HiddenFileEntity): File? {
        try {
            val encryptedFile = File(meta.filePath)
            if (!encryptedFile.exists()) {
                Log.e("SecurityUtils", "Encrypted file does not exist: ${meta.filePath}")
                return null
            }

            val previewDir = File(context.cacheDir, "previews")
            if (!previewDir.exists()) {
                previewDir.mkdirs()
            }

            val fileNameHash = (meta.filePath + "_" + encryptedFile.length() + "_" + encryptedFile.lastModified()).hashCode().toString()
            val cachedPreview = File(previewDir, "pre_${fileNameHash}_${meta.fileName}")

            if (cachedPreview.exists() && cachedPreview.length() > 0) {
                return cachedPreview
            }

            val tempPreview = File(previewDir, "pre_${fileNameHash}_${meta.fileName}.tmp")
            val success = decryptFile(context, encryptedFile, tempPreview)

            return if (success && tempPreview.exists() && tempPreview.length() > 0) {
                tempPreview.renameTo(cachedPreview)
                cachedPreview
            } else {
                if (tempPreview.exists()) tempPreview.delete()
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
                null
            } else {
                FileProvider.getUriForFile(
                    context,
                    "devs.org.calculator.fileprovider",
                    file
                )
            }
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Error getting URI for preview file: ${e.message}")
            null
        }
    }

    // ==========================================
    // Legacy Migration & File Type Auto-Detection
    // ==========================================

    fun detectFileTypeFromBytes(file: File): Pair<String, FileManager.FileType> {
        if (!file.exists() || file.length() < 4) return Pair(".bin", FileManager.FileType.DOCUMENT)
        return try {
            val header = ByteArray(12)
            FileInputStream(file).use { it.read(header) }

            // JPEG: FF D8 FF
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                Pair(".jpg", FileManager.FileType.IMAGE)
            }
            // PNG: 89 50 4E 47
            else if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
                Pair(".png", FileManager.FileType.IMAGE)
            }
            // GIF: GIF8
            else if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) {
                Pair(".gif", FileManager.FileType.IMAGE)
            }
            // MP4: ....ftyp
            else if (header.size >= 8 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() && header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()) {
                Pair(".mp4", FileManager.FileType.VIDEO)
            }
            // MP3: ID3 or FF FB
            else if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                Pair(".mp3", FileManager.FileType.AUDIO)
            }
            // PDF: %PDF
            else if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
                Pair(".pdf", FileManager.FileType.PDF)
            }
            else {
                Pair(".bin", FileManager.FileType.DOCUMENT)
            }
        } catch (_: Exception) {
            Pair(".bin", FileManager.FileType.DOCUMENT)
        }
    }

    fun findLegacyFiles(directory: File): List<File> {
        val result = mutableListOf<File>()
        if (!directory.exists()) return result

        directory.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(ENCRYPTED_EXTENSION) && !isNewFormat(file)) {
                result.add(file)
            }
        }
        return result
    }

    fun migrateFileToNewFormat(
        context: Context,
        legacyFile: File,
        metadata: HiddenFileEntity?
    ): Boolean {
        if (!legacyFile.exists() || isNewFormat(legacyFile)) {
            return true // Already migrated or doesn't exist
        }

        val tempDecrypted = File(legacyFile.parentFile ?: context.cacheDir, "migrate_dec_${System.currentTimeMillis()}.tmp")
        val tempEncrypted = File(legacyFile.parentFile ?: context.cacheDir, "migrate_enc_${System.currentTimeMillis()}.tmp")

        return try {
            // 1. Decrypt legacy file
            val decrypted = decryptLegacyFile(context, legacyFile, tempDecrypted)
            if (!decrypted || !tempDecrypted.exists() || tempDecrypted.length() == 0L) {
                if (tempDecrypted.exists()) tempDecrypted.delete()
                return false
            }

            // 2. Determine metadata
            val origName: String
            val origExt: String
            val fileType: FileManager.FileType

            if (metadata != null) {
                origName = metadata.fileName
                origExt = metadata.originalExtension
                fileType = metadata.fileType
            } else {
                val detected = detectFileTypeFromBytes(tempDecrypted)
                origExt = detected.first
                fileType = detected.second
                val base = legacyFile.name.removeSuffix(ENCRYPTED_EXTENSION)
                origName = if (base.endsWith(origExt, ignoreCase = true)) base else "$base$origExt"
            }

            val metaObj = EncryptedFileMetadata(origName, origExt, fileType)

            // 3. Encrypt with new format
            val encrypted = encryptFile(context, tempDecrypted, tempEncrypted, metaObj)
            if (!encrypted || !tempEncrypted.exists() || tempEncrypted.length() == 0L) {
                if (tempDecrypted.exists()) tempDecrypted.delete()
                if (tempEncrypted.exists()) tempEncrypted.delete()
                return false
            }

            // 4. Atomically replace legacy file
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
            val renamed = tempEncrypted.renameTo(legacyFile)
            if (!renamed) {
                tempEncrypted.copyTo(legacyFile, overwrite = true)
                tempEncrypted.delete()
            }

            // 5. Cleanup
            if (tempDecrypted.exists()) {
                tempDecrypted.delete()
            }
            true
        } catch (e: Exception) {
            Log.e("SecurityUtils", "Failed to migrate ${legacyFile.name}: ${e.message}")
            if (tempDecrypted.exists()) tempDecrypted.delete()
            if (tempEncrypted.exists()) tempEncrypted.delete()
            false
        }
    }

    // ==========================================
    // File Utility Helpers
    // ==========================================

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

    // ==========================================
    // Custom Key Management
    // ==========================================

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
        val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("encryption", false).apply()
    }

    // ==========================================
    // Encryption Info & Set Key Bottom Sheets
    // ==========================================

    fun showSetKeyBottomSheet(
        activity: Activity,
        title: String? = null,
        subtitle: String? = null,
        onKeySet: (String) -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (!hasCustomKey(activity)) {
            // First time setting key: show educational info sheet first!
            showEncryptionInfoBottomSheet(
                activity = activity,
                onProceed = {
                    showKeyInputDialog(activity, title, subtitle, onKeySet, onDismissed)
                },
                onDismissed = onDismissed
            )
        } else {
            // Key already configured: open change key dialog directly
            showKeyInputDialog(activity, title, subtitle, onKeySet, onDismissed)
        }
    }

    fun showEncryptionInfoBottomSheet(
        activity: Activity,
        onProceed: () -> Unit,
        onDismissed: () -> Unit = {}
    ) {
        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.expandFully()
        val binding = BottomSheetEncryptionInfoBinding.inflate(activity.layoutInflater)
        bottomSheet.setContentView(binding.root)

        var proceeded = false

        binding.btnProceedSetKey.setOnClickListener {
            proceeded = true
            bottomSheet.dismiss()
            onProceed()
        }

        binding.btnCloseInfo.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.setOnDismissListener {
            if (!proceeded) {
                onDismissed()
            }
        }

        bottomSheet.show()
    }

    private fun showKeyInputDialog(
        activity: Activity,
        title: String? = null,
        subtitle: String? = null,
        onKeySet: (String) -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.expandFully()
        val binding = BottomSheetSetKeyBinding.inflate(activity.layoutInflater)
        bottomSheet.setContentView(binding.root)

        val isKeyAlreadySet = hasCustomKey(activity)

        if (title != null) {
            binding.tvKeyTitle.text = title
        } else if (isKeyAlreadySet) {
            binding.tvKeyTitle.text = activity.getString(R.string.change_encryption_key)
        } else {
            binding.tvKeyTitle.text = activity.getString(R.string.set_custom_encryption_key)
        }

        if (subtitle != null) {
            binding.tvKeySubtitle.text = subtitle
        }

        if (isKeyAlreadySet) {
            binding.btnDeleteKey.visibility = View.VISIBLE
            binding.btnDeleteKey.setOnClickListener {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.delete_key))
                    .setMessage(activity.getString(R.string.delete_key_confirmation))
                    .setPositiveButton(activity.getString(R.string.delete_key)) { _, _ ->
                        clearCustomKey(activity)
                        Toast.makeText(activity, activity.getString(R.string.custom_encryption_key_cleared), Toast.LENGTH_SHORT).show()
                        bottomSheet.dismiss()
                        onDismissed()
                    }
                    .setNegativeButton(activity.getString(R.string.cancel), null)
                    .show()
            }
        } else {
            binding.btnDeleteKey.visibility = View.GONE
        }

        var keyWasSaved = false

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etKeyInput.text?.toString()?.trim() ?: ""
            val confirm = binding.etConfirmKeyInput.text?.toString()?.trim() ?: ""

            binding.tilKeyInput.error = null
            binding.tilConfirmKeyInput.error = null

            if (key.isEmpty()) {
                binding.tilKeyInput.error = activity.getString(R.string.key_cannot_be_empty)
                return@setOnClickListener
            }

            if (key != confirm) {
                binding.tilConfirmKeyInput.error = activity.getString(R.string.keys_do_not_match)
                return@setOnClickListener
            }

            if (setCustomKey(activity, key)) {
                keyWasSaved = true
                Toast.makeText(activity, activity.getString(R.string.custom_key_set_successfully), Toast.LENGTH_SHORT).show()
                bottomSheet.dismiss()
                onKeySet(key)
            } else {
                Toast.makeText(activity, activity.getString(R.string.failed_to_set_custom_key), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancelKey.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.setOnDismissListener {
            if (!keyWasSaved) {
                onDismissed()
            }
        }

        bottomSheet.show()
    }

    // ==========================================
    // Migration Bottom Sheet & Execution
    // ==========================================

    fun showMigrationBottomSheet(
        activity: Activity,
        legacyFiles: List<File>,
        onMigrationComplete: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed || legacyFiles.isEmpty()) return

        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.expandFully()
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_migration, null)
        bottomSheet.setContentView(view)

        view.findViewById<View>(R.id.btnMigrateNow).setOnClickListener {
            bottomSheet.dismiss()
            if (!hasCustomKey(activity)) {
                showSetKeyBottomSheet(
                    activity = activity,
                    title = activity.getString(R.string.encryption_key_required_title),
                    subtitle = activity.getString(R.string.encryption_key_required_message),
                    onKeySet = {
                        startMigration(activity, legacyFiles, onMigrationComplete)
                    }
                )
            } else {
                startMigration(activity, legacyFiles, onMigrationComplete)
            }
        }

        view.findViewById<View>(R.id.btnLater).setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    fun startMigration(
        activity: Activity,
        legacyFiles: List<File>,
        onMigrationComplete: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogBinding = MigrationProgressDialogBinding.inflate(activity.layoutInflater)
        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.title.text = activity.getString(R.string.migrating_files)
        dialogBinding.subtitle.text = activity.getString(R.string.migrating_progress, 0, legacyFiles.size)
        dialogBinding.progressBar.isIndeterminate = false
        dialogBinding.progressBar.max = legacyFiles.size
        dialogBinding.progressBar.progress = 0
        progressDialog.show()

        val hiddenFileRepository = HiddenFileRepository(AppDatabase.getDatabase(activity).hiddenFileDao())

        CoroutineScope(Dispatchers.IO).launch {
            var successCount = 0
            var failCount = 0

            legacyFiles.forEachIndexed { index, file ->
                val meta = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                val success = migrateFileToNewFormat(activity, file, meta)
                if (success) {
                    successCount++
                } else {
                    failCount++
                }

                withContext(Dispatchers.Main) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        dialogBinding.subtitle.text = activity.getString(R.string.migrating_progress, index + 1, legacyFiles.size)
                        dialogBinding.progressBar.progress = index + 1
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    try {
                        progressDialog.dismiss()
                    } catch (_: Exception) {}
                    val message = if (failCount == 0) {
                        activity.getString(R.string.migration_completed)
                    } else {
                        activity.getString(R.string.migration_partial_result, successCount, failCount)
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
                onMigrationComplete()
            }
        }
    }
}
