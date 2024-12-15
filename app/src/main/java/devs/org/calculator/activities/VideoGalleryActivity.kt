package devs.org.calculator.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.utils.FileManager
import devs.org.calculator.callbacks.FileProcessCallback
import kotlinx.coroutines.launch
import java.io.File

class VideoGalleryActivity : BaseGalleryActivity(), FileProcessCallback {
    override val fileType = FileManager.FileType.VIDEO
    private lateinit var pickLauncher: ActivityResultLauncher<Intent>
    private var selectedUri: Uri? = null


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
                        FileManager(this@VideoGalleryActivity, this@VideoGalleryActivity)
                            .processMultipleFiles(uriList, fileType,this@VideoGalleryActivity )
                    }
                } else {
                    Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onFilesProcessedSuccessfully(copiedFiles: List<File>) {
        Toast.makeText(this@VideoGalleryActivity, "${copiedFiles.size} Videos hidden successfully", Toast.LENGTH_SHORT).show()
        loadFiles()
    }

    override fun onFileProcessFailed() {
        Toast.makeText(this@VideoGalleryActivity, "Failed to hide videos", Toast.LENGTH_SHORT).show()
    }

    private fun setupFabButton() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "video/*"
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
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("type", fileType)
        }
        startActivity(intent)
    }
}