package devs.org.calculator.activities

import android.app.RecoverableSecurityException
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
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageGalleryActivity : BaseGalleryActivity() {
    override val fileType = FileManager.FileType.IMAGE

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var selectedImageUri: Uri? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

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

    override fun openPreview() {
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("type", fileType)
        }
        startActivity(intent)
    }
}