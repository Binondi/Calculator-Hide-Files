package devs.org.calculator.utils

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.callbacks.FileProcessCallback
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileEntity
import devs.org.calculator.database.HiddenFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManager(private val context: Context, private val lifecycleOwner: LifecycleOwner) {
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    val intent = Intent()
    val hiddenFileRepository: HiddenFileRepository by lazy {
        HiddenFileRepository(AppDatabase.getDatabase(context).hiddenFileDao())
    }

    companion object {
        const val HIDDEN_DIR = ".CalculatorHide"
        const val IMAGES_DIR = "images"
        const val VIDEOS_DIR = "videos"
        const val AUDIO_DIR = "audio"
        const val DOCS_DIR = "documents"
        const val ENCRYPTED_EXTENSION = ".enc"
    }


    fun getHiddenDirectory(): File {
        val dir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created) {
                throw RuntimeException("Failed to create hidden directory: ${dir.absolutePath}")
            }
            val nomediaFile = File(dir, ".nomedia")
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile()
            }
        }
        return dir
    }

    fun getFilesInHiddenDirFromFolder(type: FileType, folder: String): List<File> {
        val typeDir = File(folder)
        if (!typeDir.exists()) {
            typeDir.mkdirs()
            File(typeDir, ".nomedia").createNewFile()
        }
        return typeDir.listFiles()?.filterNotNull()?.filter { it.name != ".nomedia" } ?: emptyList()
    }

    private fun copyFileToHiddenDir(uri: Uri, folderName: File, currentDir: File? = null): File? {
        return try {
            val contentResolver = context.contentResolver

            // Get the target directory (i am using the current opened folder as target folder)
            val targetDir = folderName
            
            // Ensure target directory exists and has .nomedia file
            if (!targetDir.exists()) {
                targetDir.mkdirs()
                File(targetDir, ".nomedia").createNewFile()
            }

            // Create target file
            val mimeType = contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType) ?: ""
            val fileName = "${System.currentTimeMillis()}.${extension}"
            var targetFile = File(targetDir, fileName)

            // Copy file using DocumentFile
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Verify copy success
            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw Exception("File copy failed")
            }

            // Media scan the new file to hide it
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(targetDir)
            context.sendBroadcast(mediaScanIntent)
            lifecycleOwner.lifecycleScope.launch {
                deletePhotoFromExternalStorage(uri)
            }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun copyFileToNormalDir(uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver

            // Get the target directory
            val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            targetDir.mkdirs()

            // Create target file
            val mimeType = contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType) ?: ""
            val fileName = "${System.currentTimeMillis()}.${extension}"
            val targetFile = File(targetDir, fileName)

            // Copy file using DocumentFile
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Verify copy success
            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw Exception("File copy failed")
            }

            // Media scan the new file to hide it
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(targetDir)
            context.sendBroadcast(mediaScanIntent)
            lifecycleOwner.lifecycleScope.launch {
                deletePhotoFromExternalStorage(uri)
            }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun deletePhotoFromExternalStorage(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // First try to delete using DocumentFile
                val documentFile = DocumentFile.fromSingleUri(context, photoUri)
                if (documentFile?.exists() == true && documentFile.canWrite()) {
                    documentFile.delete()
                    withContext(Dispatchers.Main) {
//                            Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                // If DocumentFile approach fails, try content resolver
                try {
                    context.contentResolver.delete(photoUri, null, null)
                    withContext(Dispatchers.Main) {
//                            Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: SecurityException) {
                    // Handle security exception for Android 10 and above
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val intentSender = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                MediaStore.createDeleteRequest(context.contentResolver, listOf(photoUri)).intentSender
                            }
                            else -> {
                                val recoverableSecurityException = e as? RecoverableSecurityException
                                recoverableSecurityException?.userAction?.actionIntent?.intentSender
                            }
                        }
                        intentSender?.let { sender ->
                            intentSenderLauncher.launch(
                                IntentSenderRequest.Builder(sender).build()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error hiding/un-hiding file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }



    class FileName(private val context: Context) {
        fun getFileNameFromUri(uri: Uri): String? {
            val contentResolver = context.contentResolver
            var fileName: String? = null

            if (uri.scheme == "content") {
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = it.getString(nameIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                fileName = File(uri.path ?: "").name
            }

            return fileName
        }

    }
    class FileManager{
        fun getContentUriImage(context: Context, file: File): Uri? {

            // Query MediaStore for the file
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)
            val queryUri = MediaStore.Files.getContentUri("external")

            context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return Uri.withAppendedPath(queryUri, id.toString())
                }
            }

            // If the file is not found in MediaStore, fallback to FileProvider for hidden files
            return if (file.exists()) {
                FileProvider.getUriForFile(context, "devs.org.calculator.fileprovider", file)
            } else {
                null
            }
        }

    }


    fun askPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    activity.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(activity, "Unable to open settings. Please grant permission manually.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // For Android 10 and below
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE),
                6767
            )
        }
    }

    suspend fun processMultipleFiles(
        uriList: List<Uri>,
        targetFolder: File,
        callback: FileProcessCallback,
        currentDir: File? = null
    ) {
        withContext(Dispatchers.IO) {
            val copiedFiles = mutableListOf<File>()
            for (uri in uriList) {
                try {
                    val file = copyFileToHiddenDir(uri, targetFolder, currentDir)
                    file?.let { copiedFiles.add(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            delay(500)
            
            withContext(Dispatchers.Main) {
                if (copiedFiles.isNotEmpty()) {
                    callback.onFilesProcessedSuccessfully(copiedFiles)
                } else {
                    callback.onFileProcessFailed()
                }
            }
        }
    }

    fun getFileType(file: File): FileType {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> FileType.IMAGE
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp" -> FileType.VIDEO
            "mp3", "wav", "flac", "aac", "ogg", "m4a" -> FileType.AUDIO
            else -> FileType.DOCUMENT
        }
    }


    enum class FileType(val dirName: String) {
        IMAGE(IMAGES_DIR),
        VIDEO(VIDEOS_DIR),
        AUDIO(AUDIO_DIR),
        DOCUMENT(DOCS_DIR),
        ALL("all")
    }

    fun performEncryption(selectedFiles: List<File>,onEncryptionEnded :(MutableMap<File, File>)-> Unit) {
        lifecycleOwner.lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            val encryptedFiles = mutableMapOf<File, File>()

            for (file in selectedFiles) {
                try {
                    val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    if (hiddenFile?.isEncrypted == true) continue
                    val originalExtension = ".${file.extension.lowercase()}"
                    val fileType = FileManager(context,lifecycleOwner).getFileType(file)
                    val encryptedFile = SecurityUtils.changeFileExtension(file, devs.org.calculator.utils.FileManager.ENCRYPTED_EXTENSION)
                    if (SecurityUtils.encryptFile(context, file, encryptedFile)) {
                        if (encryptedFile.exists()) {
                            if (hiddenFile == null){
                                hiddenFileRepository.insertHiddenFile(
                                    HiddenFileEntity(
                                        filePath = encryptedFile.absolutePath,
                                        isEncrypted = true,
                                        encryptedFileName = encryptedFile.name,
                                        fileType = fileType,
                                        fileName = file.name,
                                        originalExtension = originalExtension
                                    )
                                )
                            }else{
                                hiddenFile.let {
                                    hiddenFileRepository.updateEncryptionStatus(
                                        filePath = hiddenFile.filePath,
                                        newFilePath = encryptedFile.absolutePath,
                                        encryptedFileName = encryptedFile.name,
                                        isEncrypted = true
                                    )
                                }
                            }
                            if (file.delete()) {
                                encryptedFiles[file] = encryptedFile
                                successCount++
                            } else {
                                failCount++
                            }
                        } else {
                            failCount++
                        }
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }

            Handler(Looper.getMainLooper()).post{
                when {
                    successCount > 0 && failCount == 0 -> {
                        Toast.makeText(context, "Files encrypted successfully", Toast.LENGTH_SHORT).show()
                        onEncryptionEnded(encryptedFiles)
                    }
                    successCount > 0 && failCount > 0 -> {
                        Toast.makeText(context, "Some files could not be encrypted", Toast.LENGTH_SHORT).show()
                        onEncryptionEnded(encryptedFiles)
                    }
                    else -> {
                        Toast.makeText(context, "Failed to encrypt files", Toast.LENGTH_SHORT).show()
                        onEncryptionEnded(encryptedFiles)
                    }
                }
            }
        }
    }

    fun performDecryption(selectedFiles: List<File>,onDecryptionEnded :(MutableMap<File, File>) -> Unit) {
        lifecycleOwner.lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            val decryptedFiles = mutableMapOf<File, File>()

            for (file in selectedFiles) {
                try {
                    val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    if (hiddenFile?.isEncrypted == true) {
                        val originalExtension = hiddenFile.originalExtension
                        val decryptedFile = SecurityUtils.changeFileExtension(file, originalExtension)
                        if (SecurityUtils.decryptFile(context, file, decryptedFile)) {
                            if (decryptedFile.exists() && decryptedFile.length() > 0) {
                                hiddenFile.let {
                                    hiddenFileRepository.updateEncryptionStatus(
                                        filePath = file.absolutePath,
                                        newFilePath = decryptedFile.absolutePath,
                                        encryptedFileName = decryptedFile.name,
                                        isEncrypted = false
                                    )
                                }
                                if (file.delete()) {
                                    decryptedFiles[file] = decryptedFile
                                    successCount++
                                } else {
                                    decryptedFile.delete()
                                    failCount++
                                }
                            } else {
                                decryptedFile.delete()
                                failCount++
                            }
                        } else {
                            if (decryptedFile.exists()) {
                                decryptedFile.delete()
                            }
                            failCount++
                        }
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }

            Handler(Looper.getMainLooper()).post{
                when {
                    successCount > 0 && failCount == 0 -> {
                        Toast.makeText(context, "Files decrypted successfully", Toast.LENGTH_SHORT).show()
                        onDecryptionEnded(decryptedFiles)
                    }
                    successCount > 0 && failCount > 0 -> {
                        Toast.makeText(context, "Some files could not be decrypted", Toast.LENGTH_SHORT).show()
                        onDecryptionEnded(decryptedFiles)
                    }
                    else -> {
                        Toast.makeText(context, "Failed to decrypt files", Toast.LENGTH_SHORT).show()
                        onDecryptionEnded(decryptedFiles)
                    }
                }
            }
        }
    }


}