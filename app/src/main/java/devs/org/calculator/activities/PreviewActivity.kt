package devs.org.calculator.activities

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.adapters.ImagePreviewAdapter
import devs.org.calculator.databinding.ActivityPreviewBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var currentPosition: Int = 0
    private lateinit var files: List<File>
    private lateinit var type: String
    private lateinit var filetype: FileManager.FileType
    private lateinit var adapter: ImagePreviewAdapter
    private lateinit var fileManager: FileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)

        currentPosition = intent.getIntExtra("position", 0)
        type = intent.getStringExtra("type").toString()

        setupFileType()
        files = fileManager.getFilesInHiddenDir(filetype)

        setupImagePreview()
        clickListeners()
    }

    private fun setupFileType() {
        when (type) {
            "IMAGE" -> {
                filetype = FileManager.FileType.IMAGE
                binding.title.text = "Preview Images"
            }
            "VIDEO" -> {
                filetype = FileManager.FileType.VIDEO
                binding.title.text = "Preview Videos"
            }
            "AUDIO" -> {
                filetype = FileManager.FileType.AUDIO
                binding.title.text = "Preview Audios"
            }
            else -> {
                filetype = FileManager.FileType.DOCUMENT
                binding.title.text = "Preview Documents"
            }
        }
    }

    private fun setupImagePreview() {
        adapter = ImagePreviewAdapter(this, filetype)
        adapter.images = files // Set initial data
        binding.viewPager.adapter = adapter

        binding.viewPager.setCurrentItem(currentPosition, false)

        val fileUri = Uri.fromFile(files[currentPosition])
        val fileName = FileManager.FileName(this).getFileNameFromUri(fileUri).toString()
    }

    private fun clickListeners() {
        binding.delete.setOnClickListener {
            val fileUri = FileManager.FileManager().getContentUriImage(this, files[binding.viewPager.currentItem], filetype)
            if (fileUri != null) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete File")
                    .setMessage("Are you sure you want to Delete this file?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        lifecycleScope.launch {
                            FileManager(this@PreviewActivity, this@PreviewActivity).deletePhotoFromExternalStorage(fileUri)
                            removeFileFromList(binding.viewPager.currentItem)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        binding.unHide.setOnClickListener {
            val fileUri = FileManager.FileManager().getContentUriImage(this, files[binding.viewPager.currentItem], filetype)
            if (fileUri != null) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Unhide File")
                    .setMessage("Are you sure you want to Unhide this file?")
                    .setPositiveButton("Unhide") { dialog, _ ->
                        lifecycleScope.launch {
                            FileManager(this@PreviewActivity, this@PreviewActivity).copyFileToNormalDir(fileUri)
                            removeFileFromList(binding.viewPager.currentItem)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun removeFileFromList(position: Int) {
        val updatedFiles = files.toMutableList().apply { removeAt(position) }
        files = updatedFiles
        adapter.images = updatedFiles // Update adapter with the new list

        // Update the ViewPager's position
        if (!updatedFiles.isNotEmpty()) finish()

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
