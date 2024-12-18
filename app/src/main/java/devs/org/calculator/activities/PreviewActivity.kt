package devs.org.calculator.activities

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import devs.org.calculator.adapters.ImagePreviewAdapter
import devs.org.calculator.callbacks.DialogActionsCallback
import devs.org.calculator.databinding.ActivityPreviewBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File
import devs.org.calculator.R

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var currentPosition: Int = 0
    private lateinit var files: List<File>
    private lateinit var type: String
    private lateinit var filetype: FileManager.FileType
    private lateinit var adapter: ImagePreviewAdapter
    private lateinit var fileManager: FileManager
    private val dialogUtil = DialogUtil(this)

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

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                
            }
        })

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
        adapter.images = files
        binding.viewPager.adapter = adapter

        binding.viewPager.setCurrentItem(currentPosition, false)

        val fileUri = Uri.fromFile(files[currentPosition])
        val fileName = FileManager.FileName(this).getFileNameFromUri(fileUri).toString()
    }


    override fun onPause() {
        super.onPause()
        if (filetype == FileManager.FileType.AUDIO) {
            (binding.viewPager.adapter as? ImagePreviewAdapter)?.currentMediaPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (filetype == FileManager.FileType.AUDIO) {
            (binding.viewPager.adapter as? ImagePreviewAdapter)?.let { adapter ->
                adapter.currentMediaPlayer?.release()
                adapter.currentMediaPlayer = null
            }
        }
    }


    private fun clickListeners() {
        binding.delete.setOnClickListener {
            val fileUri = FileManager.FileManager().getContentUriImage(this, files[binding.viewPager.currentItem], filetype)
            if (fileUri != null) {
                dialogUtil.showMaterialDialog(
                    "Delete File",
                    "Are you sure to Delete this file permanently?",
                    "Delete Permanently",
                    "Cancel",
                    object : DialogActionsCallback{
                        override fun onPositiveButtonClicked() {
                            lifecycleScope.launch {
                                FileManager(this@PreviewActivity, this@PreviewActivity).deletePhotoFromExternalStorage(fileUri)
                                removeFileFromList(binding.viewPager.currentItem)
                            }
                        }

                        override fun onNegativeButtonClicked() {

                        }

                        override fun onNaturalButtonClicked() {

                        }

                    }
                )
            }
        }

        binding.unHide.setOnClickListener {
            val fileUri = FileManager.FileManager().getContentUriImage(this, files[binding.viewPager.currentItem], filetype)
            if (fileUri != null) {
                dialogUtil.showMaterialDialog(
                    "Unhide File",
                    "Are you sure you want to Unhide this file?",
                    "Unhide",
                    "Cancel",
                    object : DialogActionsCallback{
                        override fun onPositiveButtonClicked() {
                            lifecycleScope.launch {
                                FileManager(this@PreviewActivity, this@PreviewActivity).copyFileToNormalDir(fileUri)
                                removeFileFromList(binding.viewPager.currentItem)
                            }
                        }

                        override fun onNegativeButtonClicked() {

                        }

                        override fun onNaturalButtonClicked() {

                        }

                    }
                )
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
