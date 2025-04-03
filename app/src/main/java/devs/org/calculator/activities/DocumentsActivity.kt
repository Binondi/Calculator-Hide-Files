package devs.org.calculator.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.R
import devs.org.calculator.utils.FileManager
import devs.org.calculator.callbacks.FileProcessCallback
import kotlinx.coroutines.launch
import java.io.File

class DocumentsActivity : BaseGalleryActivity(), FileProcessCallback {
    override val fileType = FileManager.FileType.DOCUMENT
    private lateinit var pickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFabButton()

        pickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val clipData = result.data?.clipData
                val uriList = mutableListOf<Uri>()

                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        uriList.add(uri)
                    }
                } else {
                    result.data?.data?.let { uriList.add(it) } // Single file selected
                }

                if (uriList.isNotEmpty()) {
                    lifecycleScope.launch {
                        FileManager(this@DocumentsActivity, this@DocumentsActivity).processMultipleFiles(uriList, fileType,this@DocumentsActivity )
                    }
                } else {
                    Toast.makeText(this, getString(R.string.no_files_selected), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onFilesProcessedSuccessfully(copiedFiles: List<File>) {
        Toast.makeText(this@DocumentsActivity,copiedFiles.size.toString() +
            getString(R.string.documents_hidden_successfully ), Toast.LENGTH_SHORT).show()
        loadFiles()
    }

    override fun onFileProcessFailed() {
        Toast.makeText(this@DocumentsActivity,
            getString(R.string.failed_to_hide_documents), Toast.LENGTH_SHORT).show()
    }

    private fun setupFabButton() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            pickLauncher.launch(intent)
        }
    }

    override fun openPreview() {
        //Not implemented document preview
    }
}