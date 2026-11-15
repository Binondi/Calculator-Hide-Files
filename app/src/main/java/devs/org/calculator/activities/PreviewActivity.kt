package devs.org.calculator.activities

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import devs.org.calculator.R
import devs.org.calculator.adapters.ImagePreviewAdapter
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileRepository
import devs.org.calculator.databinding.ActivityPreviewBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.SecurityUtils
import kotlinx.coroutines.launch
import java.io.File

class PreviewActivity : BaseActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var currentPosition: Int = 0
    private var files: MutableList<File> = mutableListOf()
    private lateinit var type: String
    private lateinit var folder: String
    private lateinit var filetype: FileManager.FileType
    private lateinit var adapter: ImagePreviewAdapter
    private lateinit var fileManager: FileManager
    private val dialogUtil = DialogUtil(this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hiddenFileRepository: HiddenFileRepository by lazy {
        HiddenFileRepository(AppDatabase.getDatabase(this).hiddenFileDao())
    }
    private var areControlsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBarContainer.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                (4 * resources.displayMetrics.density).toInt()
            )
            val baseMargin = (24 * resources.displayMetrics.density).toInt()
            val lp = binding.bottomBarCard.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.bottomMargin = baseMargin + systemBars.bottom
                lp.leftMargin = (20 * resources.displayMetrics.density).toInt() + systemBars.left
                lp.rightMargin = (20 * resources.displayMetrics.density).toInt() + systemBars.right
                binding.bottomBarCard.layoutParams = lp
            }
            insets
        }
        fileManager = FileManager(this, this)

        currentPosition = intent.getIntExtra("position", 0)
        type = intent.getStringExtra("type") ?: "IMAGE"
        folder = intent.getStringExtra("folder") ?: ""

        setupFileType()
        loadFiles()
        setupImagePreview()
        setupClickListeners()
        setupPageChangeCallback()
        setupBackPressed()
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!areControlsVisible) {
                    setControlsVisible(true, animate = true)
                } else {
                    finish()
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        adapter.releaseAllResources()
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.releaseAllResources()
    }

    private fun setupFileType() {
        when (type) {
            "IMAGE" -> {
                filetype = FileManager.FileType.IMAGE
                binding.toolBar.title = getString(R.string.preview_images)
            }

            "VIDEO" -> {
                filetype = FileManager.FileType.VIDEO
                binding.toolBar.title = getString(R.string.preview_videos)
            }

            "AUDIO" -> {
                filetype = FileManager.FileType.AUDIO
                binding.toolBar.title = getString(R.string.preview_audios)
            }

            else -> {
                filetype = FileManager.FileType.DOCUMENT
                binding.toolBar.title = getString(R.string.preview_documents)
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

        adapter = ImagePreviewAdapter(
            context = this,
            lifecycleOwner = this,
            onToggleControls = { toggleControls() },
            onFullscreenClick = { toggleOrientation() }
        )
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
                if (currentPosition != position) {
                    adapter.onItemScrolledAway(currentPosition)
                }
                currentPosition = position
                updateFileInfo()
            }
        })
    }

    private fun updateFileInfo() {
        if (files.isNotEmpty() && currentPosition < files.size) {
            val fileUri = Uri.fromFile(files[currentPosition])
            val fileName = FileManager.FileName(this).getFileNameFromUri(fileUri) ?: "Unknown"
            binding.toolBar.subtitle = "${currentPosition + 1} / ${files.size}"
        }
    }

    private fun setupClickListeners() {
        binding.toolBar.setNavigationOnClickListener {
            finish()
        }

        binding.delete.setOnClickListener {
            handleDeleteFile()
        }

        binding.unHide.setOnClickListener {
            performFileUnHiding()
        }
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
                                val hiddenFile =
                                    hiddenFileRepository.getHiddenFileByPath(currentFile.absolutePath)
                                hiddenFile?.let {
                                    hiddenFileRepository.deleteHiddenFile(it)
                                }

                                fileManager.deletePhotoFromExternalStorage(fileUri)
                                removeFileFromList(currentPosition)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    override fun onNegativeButtonClicked() {}

                    override fun onNaturalButtonClicked() {}
                }
            )
        }
    }

    private fun performFileUnHiding() {
        if (files.isEmpty() || currentPosition >= files.size) return
        val file = files[currentPosition]

        dialogUtil.showMaterialDialog(
            getString(R.string.un_hide_file),
            getString(R.string.are_you_sure_you_want_to_un_hide_this_file),
            getString(R.string.un_hide),
            getString(R.string.cancel),
            object : DialogUtil.DialogCallback {
                override fun onPositiveButtonClicked() {
                    lifecycleScope.launch {
                        var allUnhidden = true
                        try {
                            val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                            if (hiddenFile != null){
                                if (hiddenFile.isEncrypted) {
                                    val originalExtension = hiddenFile.originalExtension
                                    val decryptedFile = SecurityUtils.changeFileExtension(file, originalExtension)

                                    if (SecurityUtils.decryptFile(this@PreviewActivity, file, decryptedFile)) {
                                        if (decryptedFile.exists() && decryptedFile.length() > 0) {
                                            val fileUri = FileManager.FileManager()
                                                .getContentUriImage(this@PreviewActivity, decryptedFile)
                                            if (fileUri != null) {
                                                val result = fileManager.copyFileToNormalDir(fileUri)
                                                if (result != null) {
                                                    hiddenFile.let {
                                                        hiddenFileRepository.deleteHiddenFile(it)
                                                    }
                                                    file.delete()
                                                    decryptedFile.delete()
                                                    removeFileFromList(currentPosition)
                                                } else {
                                                    decryptedFile.delete()
                                                    allUnhidden = false
                                                }
                                            } else {
                                                decryptedFile.delete()
                                                allUnhidden = false
                                            }
                                        } else {
                                            decryptedFile.delete()
                                            allUnhidden = false
                                        }
                                    } else {
                                        if (decryptedFile.exists()) {
                                            decryptedFile.delete()
                                        }
                                        allUnhidden = false
                                    }
                                } else {
                                    val fileUri = FileManager.FileManager().getContentUriImage(this@PreviewActivity, file)
                                    if (fileUri != null) {
                                        val result = fileManager.copyFileToNormalDir(fileUri)
                                        if (result != null) {
                                            hiddenFile.let {
                                                hiddenFileRepository.deleteHiddenFile(it)
                                            }
                                            file.delete()
                                            removeFileFromList(currentPosition)
                                        } else {
                                            allUnhidden = false
                                        }
                                    } else {
                                        allUnhidden = false
                                    }
                                }
                            }else{
                                val fileUri = FileManager.FileManager().getContentUriImage(this@PreviewActivity, file)
                                if (fileUri != null) {
                                    val result = fileManager.copyFileToNormalDir(fileUri)
                                    if (result != null) {
                                        file.delete()
                                        removeFileFromList(currentPosition)
                                    } else {
                                        allUnhidden = false
                                    }
                                } else {
                                    allUnhidden = false
                                }
                            }

                        } catch (_: Exception) {

                            allUnhidden = false
                        }

                        mainHandler.post {
                            val message = if (allUnhidden) {
                                getString(R.string.file_unhidden_successfully)
                            } else {
                                getString(R.string.something_went_wrong)
                            }

                            Toast.makeText(this@PreviewActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onNegativeButtonClicked() {}

                override fun onNaturalButtonClicked() {}
            }
        )


    }

    private fun removeFileFromList(position: Int) {
        if (position < 0 || position >= files.size) return
        adapter.releaseAllResources()
        files.removeAt(position)

        if (files.isEmpty()) {
            adapter.submitFiles(emptyList()) {
                finish()
            }
            return
        }

        val targetPosition = if (position >= files.size) {
            files.size - 1
        } else {
            position
        }

        adapter.submitFiles(ArrayList(files)) {
            currentPosition = targetPosition
            binding.viewPager.setCurrentItem(targetPosition, true)
            updateFileInfo()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun toggleControls() {
        setControlsVisible(!areControlsVisible, animate = true)
    }

    private fun setControlsVisible(visible: Boolean, animate: Boolean) {
        areControlsVisible = visible

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

            if (animate) {
                binding.topBarContainer.visibility = View.VISIBLE
                binding.topBarContainer.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .setListener(null)
                    .start()

                binding.bottomBarCard.visibility = View.VISIBLE
                binding.bottomBarCard.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .setListener(null)
                    .start()
            } else {
                binding.topBarContainer.visibility = View.VISIBLE
                binding.topBarContainer.translationY = 0f
                binding.topBarContainer.alpha = 1f

                binding.bottomBarCard.visibility = View.VISIBLE
                binding.bottomBarCard.translationY = 0f
                binding.bottomBarCard.alpha = 1f
            }
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            if (animate) {
                val topDistance = if (binding.topBarContainer.height > 0) {
                    -binding.topBarContainer.height.toFloat()
                } else {
                    -300f
                }
                binding.topBarContainer.animate()
                    .translationY(topDistance)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        if (!areControlsVisible) {
                            binding.topBarContainer.visibility = View.GONE
                        }
                    }
                    .start()

                val bottomDistance = if (binding.bottomBarCard.height > 0) {
                    binding.bottomBarCard.height.toFloat() + 200f
                } else {
                    300f
                }
                binding.bottomBarCard.animate()
                    .translationY(bottomDistance)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        if (!areControlsVisible) {
                            binding.bottomBarCard.visibility = View.GONE
                        }
                    }
                    .start()
            } else {
                binding.topBarContainer.visibility = View.GONE
                binding.bottomBarCard.visibility = View.GONE
            }
        }
    }

    private fun toggleOrientation() {
        val currentOrientation = resources.configuration.orientation
        requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}
