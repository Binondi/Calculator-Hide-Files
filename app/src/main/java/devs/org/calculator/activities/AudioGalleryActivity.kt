package devs.org.calculator.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.R
import devs.org.calculator.callbacks.FileProcessCallback
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File

class AudioGalleryActivity : BaseGalleryActivity(), FileProcessCallback {
    override val fileType = FileManager.FileType.AUDIO
    private lateinit var pickAudioLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFabButton()

        pickAudioLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val clipData = result.data?.clipData
                    val uriList = mutableListOf<Uri>()

                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            uriList.add(uri)
                        }
                    } else {
                        result.data?.data?.let { uriList.add(it) }
                    }

                    if (uriList.isNotEmpty()) {
                        lifecycleScope.launch {
                            FileManager(
                                this@AudioGalleryActivity,
                                this@AudioGalleryActivity
                            ).processMultipleFiles(uriList, fileType, this@AudioGalleryActivity)
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.no_files_selected), Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    override fun onFilesProcessedSuccessfully(copiedFiles: List<File>) {
        Toast.makeText(
            this@AudioGalleryActivity,
            "${copiedFiles.size} ${getString(R.string.audio_hidded_successfully)} ",
            Toast.LENGTH_SHORT
        ).show()
        loadFiles()
    }

    override fun onFileProcessFailed() {
        Toast.makeText(this@AudioGalleryActivity, "Failed to hide Audios", Toast.LENGTH_SHORT)
            .show()
    }

    private fun setupFabButton() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            pickAudioLauncher.launch(intent)
        }
    }

    override fun openPreview() {
        // Not implemented audio preview
    }


}