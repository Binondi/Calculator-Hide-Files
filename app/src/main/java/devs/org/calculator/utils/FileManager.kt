package devs.org.calculator.utils

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.callbacks.FileProcessCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.Manifest
import androidx.core.content.FileProvider
import devs.org.calculator.R

class FileManager(private val context: Context, private val lifecycleOwner: LifecycleOwner) {
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    val intent = Intent()

    companion object {
        const val HIDDEN_DIR = ".CalculatorHide"
        const val IMAGES_DIR = "images"
        const val VIDEOS_DIR = "videos"
        const val AUDIO_DIR = "audio"
        const val DOCS_DIR = "documents"
    }

    fun getHiddenDirectory(): File {
        val dir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created) {
                throw RuntimeException("Failed to create hidden directory: ${dir.absolutePath}")
            }
            // Create .nomedia file to hide from media scanners
            val nomediaFile = File(dir, ".nomedia")
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile()
            }
        }
        return dir
    }

    fun getFilesInHiddenDir(type: FileType): List<File> {
        val hiddenDir = getHiddenDirectory()
        val typeDir = File(hiddenDir, type.dirName)
        if (!typeDir.exists()) {
            typeDir.mkdirs()
            File(typeDir, ".nomedia").createNewFile()
        }
        return typeDir.listFiles()?.filterNotNull()?.filter { it.name != ".nomedia" } ?: emptyList()
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

    fun unHideFile(file: File, onSuccess: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create target directory (Downloads)
                val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                targetDir.mkdirs()

                // Create target file with same name or timestamp
                val targetFile = File(targetDir, file.name)

                // If file with same name exists, add timestamp
                val finalTargetFile = if (targetFile.exists()) {
                    val nameWithoutExt = file.nameWithoutExtension
                    val extension = file.extension
                    File(targetDir, "${nameWithoutExt}_${System.currentTimeMillis()}.${extension}")
                } else {
                    targetFile
                }

                // Copy file content
                file.copyTo(finalTargetFile, overwrite = false)

                // Verify copy success
                if (finalTargetFile.exists() && finalTargetFile.length() > 0) {
                    // Delete original hidden file
                    if (file.delete()) {
                        // Trigger media scan for the new file
                        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        mediaScanIntent.data = Uri.fromFile(finalTargetFile)
                        context.sendBroadcast(mediaScanIntent)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.file_unhidden_successfully), Toast.LENGTH_SHORT).show()
                            onSuccess?.invoke() // Call success callback
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "File copied but failed to remove from hidden folder", Toast.LENGTH_SHORT).show()
                            onError?.invoke("Failed to remove from hidden folder")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to copy file", Toast.LENGTH_SHORT).show()
                        onError?.invoke("Failed to copy file")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error unhiding file: ${e.message}", Toast.LENGTH_LONG).show()
                    onError?.invoke(e.message ?: "Unknown error")
                }
                e.printStackTrace()
            }
        }
    }
    
    suspend fun deletePhotoFromExternalStorage(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // First try to delete using DocumentFile
                val documentFile = DocumentFile.fromSingleUri(context, photoUri)
                if (documentFile?.exists() == true && documentFile.canWrite()) {
                    val deleted = documentFile.delete()
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
}