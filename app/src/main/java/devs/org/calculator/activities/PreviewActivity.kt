package devs.org.calculator.activities

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import devs.org.calculator.R
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
    private lateinit var folder: String
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
        folder = intent.getStringExtra("folder").toString()

        setupFileType()
        files = fileManager.getFilesInHiddenDirFromFolder(filetype, folder = folder)

        setupImagePreview()
        clickListeners()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                
            }
        })

        binding.back.setOnClickListener {
            finish()
        }

    }

    override fun onResume() {
        super.onResume()
        setupFlagSecure()
    }

    private fun setupFlagSecure() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        if (prefs.getBoolean("screenshot_restriction", true)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    private fun setupFileType() {
        when (type) {
            "IMAGE" -> {
                filetype = FileManager.FileType.IMAGE
                binding.title.text = getString(R.string.preview_images)
            }
            "VIDEO" -> {
                filetype = FileManager.FileType.VIDEO
                binding.title.text = getString(R.string.preview_videos)
            }
            "AUDIO" -> {
                filetype = FileManager.FileType.AUDIO
                binding.title.text = getString(R.string.preview_audios)
            }
            else -> {
                filetype = FileManager.FileType.DOCUMENT
                binding.title.text = getString(R.string.preview_documents)
            }
        }
    }

    private fun setupImagePreview() {
        adapter = ImagePreviewAdapter(this, this)
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
            val fileUri = FileManager.FileManager().getContentUriImage(this, files[binding.viewPager.currentItem])
            if (fileUri != null) {
                dialogUtil.showMaterialDialog(
                    getString(R.string.delete_file),
                    getString(R.string.are_you_sure_to_delete_this_file_permanently),
                    getString(R.string.delete_permanently),
                    getString(R.string.cancel),
                    object : DialogUtil.DialogCallback {
                        override fun onPositiveButtonClicked() {
                            lifecycleScope.launch {
                                FileManager(this@PreviewActivity, this@PreviewActivity).deletePhotoFromExternalStorage(fileUri)
                                removeFileFromList(binding.viewPager.currentItem)
                            }
                        }

                        override fun onNegativeButtonClicked() {
                            // Handle negative button click
                        }

                        override fun onNaturalButtonClicked() {
                            // Handle neutral button click
                        }
                    }
                )
            }
        }

        binding.unHide.setOnClickListener {
            val fileUri = FileManager.FileManager().getContentUriImage(this, files[binding.viewPager.currentItem])
            if (fileUri != null) {
                dialogUtil.showMaterialDialog(
                    getString(R.string.un_hide_file),
                    getString(R.string.are_you_sure_you_want_to_un_hide_this_file),
                    getString(R.string.un_hide),
                    getString(R.string.cancel),
                    object : DialogUtil.DialogCallback {
                        override fun onPositiveButtonClicked() {
                            lifecycleScope.launch {
                                FileManager(this@PreviewActivity, this@PreviewActivity).copyFileToNormalDir(fileUri)
                                removeFileFromList(binding.viewPager.currentItem)
                            }
                        }

                        override fun onNegativeButtonClicked() {
                            // Handle negative button click
                        }

                        override fun onNaturalButtonClicked() {
                            // Handle neutral button click
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
        if (updatedFiles.isEmpty()) finish()

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


}
