package devs.org.calculator.activities

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageGalleryActivity : BaseGalleryActivity() {
    override val fileType = FileManager.FileType.IMAGE

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var selectedImageUri: Uri? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private suspend fun deletePhotoFromExternalStorage(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // First try to delete using DocumentFile
                val documentFile = DocumentFile.fromSingleUri(this@ImageGalleryActivity, photoUri)
                if (documentFile?.exists() == true && documentFile.canWrite()) {
                    val deleted = documentFile.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            Toast.makeText(this@ImageGalleryActivity, "File deleted successfully", Toast.LENGTH_SHORT).show()
                            selectedImageUri = null
                        } else {
                            Toast.makeText(this@ImageGalleryActivity, "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@withContext
                }

                // If DocumentFile approach fails, try content resolver
                try {
                    contentResolver.delete(photoUri, null, null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageGalleryActivity, "File deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val intentSender = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                MediaStore.createDeleteRequest(contentResolver, listOf(photoUri)).intentSender
                            }
                            else -> {
                                val recoverableSecurityException = e as? RecoverableSecurityException
                                recoverableSecurityException?.userAction?.actionIntent?.intentSender
                            }
                        }
                        intentSender?.let { sender ->
                            withContext(Dispatchers.Main) {
                                intentSenderLauncher.launch(
                                    IntentSenderRequest.Builder(sender).build()
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ImageGalleryActivity,
                        "Error deleting file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupIntentSenderLauncher()
        setupFabButton()

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){
            if (it.resultCode == RESULT_OK){
                Toast.makeText(this, "Photo Deleted Successfully", Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this, "Failed to delete photo", Toast.LENGTH_SHORT).show()
            }
        }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    selectedImageUri = uri
                    try {
                        val file = fileManager.copyFileToHiddenDir(selectedImageUri!!, fileType)
                        if (file != null && file.exists()) {
                            Toast.makeText(this, "File hidden successfully", Toast.LENGTH_SHORT).show()
                            loadFiles()
                        } else {
                            Toast.makeText(this, "Failed to hide file", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                }
            }
        }
        askPermissiom()
    }

    private fun setupIntentSenderLauncher() {
        intentSenderLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                loadFiles()
            }
        }
    }

    private fun askPermissiom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if (!Environment.isExternalStorageManager()){
                val intent = Intent().setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
        else {
            Toast.makeText(this, "Android Version Lower", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFabButton() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            pickImageLauncher.launch(intent)
        }
    }

    override fun openPreview(file: File) {
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra(PreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
            putExtra(PreviewActivity.EXTRA_FILE_TYPE, fileType.name)
        }
        startActivity(intent)
    }
}