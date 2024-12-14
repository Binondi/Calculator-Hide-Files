package devs.org.calculator.utils

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.adapters.FileAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManager(private val context: Context, private val lifecycleOwner: LifecycleOwner) {
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

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
            dir.mkdirs()
            // Create .nomedia file to hide from media scanners
            File(dir, ".nomedia").createNewFile()
        }
        return dir
    }



    fun getFilesInHiddenDir(type: FileType): List<File> {
        val hiddenDir = getHiddenDirectory()
        val typeDir = File(hiddenDir, type.dirName)
        return if (typeDir.exists()) {
            typeDir.listFiles()?.filterNotNull()?.filter { it.name != ".nomedia" } ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun copyFileToHiddenDir(uri: Uri, type: FileType): File? {
        return try {
            val contentResolver = context.contentResolver

            // Get the target directory
            val targetDir = File(Environment.getExternalStorageDirectory(), "$HIDDEN_DIR/${type.dirName}")
            targetDir.mkdirs()
            File(targetDir, ".nomedia").createNewFile()

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
                    val deleted = documentFile.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@withContext
                }

                // If DocumentFile approach fails, try content resolver
                try {
                    context.contentResolver.delete(photoUri, null, null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
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
                        "Error deleting file: ${e.message}",
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
    class FileManager(){
        fun getContentUri(context: Context, file: File): Uri? {
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
            return null
        }
    }




    enum class FileType(val dirName: String) {
        IMAGE(IMAGES_DIR),
        VIDEO(VIDEOS_DIR),
        AUDIO(AUDIO_DIR),
        DOCUMENT(DOCS_DIR)
    }
}