package devs.org.calculator.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import devs.org.calculator.utils.FileManager
import java.io.File

class VideoGalleryActivity : BaseGalleryActivity() {
    override val fileType = FileManager.FileType.VIDEO
    private lateinit var pickVideoLauncher: ActivityResultLauncher<Intent>
    private var selectedUri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFabButton()

        pickVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    selectedUri = uri
                    try {
                        val file = fileManager.copyFileToHiddenDir(selectedUri!!, fileType)
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
                    Toast.makeText(this, "No video selected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupFabButton() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            pickVideoLauncher.launch(intent)
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