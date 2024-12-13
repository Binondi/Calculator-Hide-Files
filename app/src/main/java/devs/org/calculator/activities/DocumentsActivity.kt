package devs.org.calculator.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import devs.org.calculator.utils.FileManager
import java.io.File

class DocumentsActivity : BaseGalleryActivity() {
    override val fileType = FileManager.FileType.DOCUMENT
    private lateinit var pickDocumentLauncher: ActivityResultLauncher<Intent>
    private var selectedUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFabButton()

        pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                    Toast.makeText(this, "No document selected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupFabButton() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            pickDocumentLauncher.launch(intent)
        }
    }

    override fun openPreview(file: File) {
        // Implement document preview
    }
}