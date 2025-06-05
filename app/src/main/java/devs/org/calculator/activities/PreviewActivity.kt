package devs.org.calculator.activities

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.DynamicColors
import devs.org.calculator.R
import devs.org.calculator.adapters.ImagePreviewAdapter
import devs.org.calculator.databinding.ActivityPreviewBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.PrefsUtil
import kotlinx.coroutines.launch
import java.io.File
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileRepository
import android.util.Log

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var currentPosition: Int = 0
    private var files: MutableList<File> = mutableListOf()
    private lateinit var type: String
    private lateinit var folder: String
    private lateinit var filetype: FileManager.FileType
    private lateinit var adapter: ImagePreviewAdapter
    private lateinit var fileManager: FileManager
    private val dialogUtil = DialogUtil(this)
    private val prefs: PrefsUtil by lazy { PrefsUtil(this) }
    private val hiddenFileRepository: HiddenFileRepository by lazy {
        HiddenFileRepository(AppDatabase.getDatabase(this).hiddenFileDao())
    }

    companion object {
        private const val TAG = "PreviewActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)

        currentPosition = intent.getIntExtra("position", 0)
        type = intent.getStringExtra("type") ?: "IMAGE"
        folder = intent.getStringExtra("folder") ?: ""

        setupFileType()
        loadFiles()
        setupImagePreview()
        setupClickListeners()
        setupPageChangeCallback()
    }

    override fun onResume() {
        super.onResume()
        setupFlagSecure()
    }

    override fun onPause() {
        super.onPause()
        adapter.releaseAllResources()
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.releaseAllResources()
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

    private fun loadFiles() {
        try {
            val filesList = fileManager.getFilesInHiddenDirFromFolder(filetype, folder = folder)
            files = filesList.toMutableList()

            if (currentPosition >= files.size) {
                currentPosition = 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            files = mutableListOf()
        }
    }

    private fun setupImagePreview() {
        if (files.isEmpty()) {
            finish()
            return
        }

        adapter = ImagePreviewAdapter(this, this)
        adapter.images = files
        binding.viewPager.adapter = adapter
        if (currentPosition < files.size) {
            binding.viewPager.setCurrentItem(currentPosition, false)
        }

        updateFileInfo()
    }

    private fun setupPageChangeCallback() {
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                updateFileInfo()
            }
        })
    }

    private fun updateFileInfo() {
        if (files.isNotEmpty() && currentPosition < files.size) {
            val fileUri = Uri.fromFile(files[currentPosition])
            val fileName = FileManager.FileName(this).getFileNameFromUri(fileUri) ?: "Unknown"
            //For Now File Name not Needed, i am keeping it for later use
        }
    }

    private fun setupClickListeners() {
        binding.back.setOnClickListener {
            finish()
        }

        binding.delete.setOnClickListener {
            handleDeleteFile()
        }

        binding.unHide.setOnClickListener {
            handleUnhideFile()
        }
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                adapter.onItemScrolledAway(currentPosition)
                currentPosition = position
            }
        })
    }

    private fun handleDeleteFile() {
        if (files.isEmpty() || currentPosition >= files.size) return

        val currentFile = files[currentPosition]
        val fileUri = FileManager.FileManager().getContentUriImage(this, currentFile)

        if (fileUri != null) {
            dialogUtil.showMaterialDialog(
                getString(R.string.delete_file),
                getString(R.string.are_you_sure_to_delete_this_file_permanently),
                getString(R.string.delete_permanently),
                getString(R.string.cancel),
                object : DialogUtil.DialogCallback {
                    override fun onPositiveButtonClicked() {
                        lifecycleScope.launch {
                            try {
                                // First delete from database
                                val hiddenFile = hiddenFileRepository.getHiddenFileByPath(currentFile.absolutePath)
                                hiddenFile?.let {
                                    hiddenFileRepository.deleteHiddenFile(it)
                                    Log.d(TAG, "Deleted file metadata from database: ${it.filePath}")
                                }

                                // Then delete the actual file
                                fileManager.deletePhotoFromExternalStorage(fileUri)
                                removeFileFromList(currentPosition)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting file: ${e.message}", e)
                            }
                        }
                    }

                    override fun onNegativeButtonClicked() {}

                    override fun onNaturalButtonClicked() {}
                }
            )
        }
    }

    private fun handleUnhideFile() {
        if (files.isEmpty() || currentPosition >= files.size) return

        val currentFile = files[currentPosition]
        val fileUri = FileManager.FileManager().getContentUriImage(this, currentFile)

        if (fileUri != null) {
            dialogUtil.showMaterialDialog(
                getString(R.string.un_hide_file),
                getString(R.string.are_you_sure_you_want_to_un_hide_this_file),
                getString(R.string.un_hide),
                getString(R.string.cancel),
                object : DialogUtil.DialogCallback {
                    override fun onPositiveButtonClicked() {
                        lifecycleScope.launch {
                            try {
                                // First copy the file to normal directory
                                val result = fileManager.copyFileToNormalDir(fileUri)
                                if (result != null) {
                                    val hiddenFile = hiddenFileRepository.getHiddenFileByPath(currentFile.absolutePath)
                                    hiddenFile?.let {
                                        hiddenFileRepository.deleteHiddenFile(it)
                                        Log.d(TAG, "Deleted file metadata from database: ${it.filePath}")
                                    }

                                    removeFileFromList(currentPosition)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error unhiding file: ${e.message}", e)
                            }
                        }
                    }

                    override fun onNegativeButtonClicked() {}

                    override fun onNaturalButtonClicked() {}
                }
            )
        }
    }

    private fun removeFileFromList(position: Int) {
        if (position < 0 || position >= files.size) return
        adapter.releaseAllResources()
        files.removeAt(position)
        adapter.images = files
        if (files.isEmpty()) {
            finish()
            return
        }
        currentPosition = if (position >= files.size) {
            files.size - 1
        } else {
            position
        }

        binding.viewPager.setCurrentItem(currentPosition, false)
        updateFileInfo()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}