package devs.org.calculator.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import devs.org.calculator.R
import devs.org.calculator.adapters.FileAdapter
import devs.org.calculator.adapters.FolderSelectionAdapter
import devs.org.calculator.callbacks.FileProcessCallback
import devs.org.calculator.databinding.ActivityViewFolderBinding
import devs.org.calculator.databinding.ProccessingDialogBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import kotlinx.coroutines.launch
import java.io.File

class ViewFolderActivity : AppCompatActivity() {

    private var isFabOpen = false
    private lateinit var fabOpen: Animation
    private lateinit var fabClose: Animation
    private lateinit var rotateOpen: Animation
    private lateinit var rotateClose: Animation
    private lateinit var binding: ActivityViewFolderBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var fileManager: FileManager
    private lateinit var folderManager: FolderManager
    private lateinit var dialogUtil: DialogUtil
    private var fileAdapter: FileAdapter? = null
    private var currentFolder: File? = null
    private val hiddenDir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var prefs: SharedPreferences

    private var customDialog: androidx.appcompat.app.AlertDialog? = null

    private var dialogShowTime: Long = 0
    private val MINIMUM_DIALOG_DURATION = 1200L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAnimations()
        initialize()
        setupClickListeners()
        closeFabs()
        val folder = intent.getStringExtra("folder").toString()
        currentFolder = File(folder)
        if (currentFolder != null){
            openFolder(currentFolder!!)
        }else {
            showEmptyState()
        }

        setupActivityResultLaunchers()
    }

    private fun initialize() {
        fileManager = FileManager(this, this)
        folderManager = FolderManager(this)
        dialogUtil = DialogUtil(this)
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
    }

    private fun setupActivityResultLaunchers() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                handleFilePickerResult(result.data)
            }
        }
    }

    private fun handleFilePickerResult(data: Intent?) {
        val clipData = data?.clipData
        val uriList = mutableListOf<Uri>()

        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                uriList.add(uri)
            }
        } else {
            data?.data?.let { uriList.add(it) }
        }

        if (uriList.isNotEmpty()) {
            processSelectedFiles(uriList)
        } else {
            Toast.makeText(this, getString(R.string.no_files_selected), Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showCustomDialog(count: Int) {
        val dialogView = ProccessingDialogBinding.inflate(layoutInflater)
        customDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView.root)
            .setCancelable(false)
            .create()
        dialogView.title.text = "Hiding $count files"
        customDialog?.show()
        dialogShowTime = System.currentTimeMillis()
    }
    private fun dismissCustomDialog() {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - dialogShowTime

        if (elapsedTime < MINIMUM_DIALOG_DURATION) {
            val remainingTime = MINIMUM_DIALOG_DURATION - elapsedTime
            mainHandler.postDelayed({
                customDialog?.dismiss()
                customDialog = null
                updateFilesToAdapter()
            }, remainingTime)
        } else {
            customDialog?.dismiss()
            customDialog = null
            updateFilesToAdapter()
        }
    }

    private fun updateFilesToAdapter() {
        openFolder(currentFolder!!)
    }


    private fun processSelectedFiles(uriList: List<Uri>) {
        val targetFolder = currentFolder ?: hiddenDir
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
            File(targetFolder, ".nomedia").createNewFile()
        }

        showCustomDialog(uriList.size)
        lifecycleScope.launch {
            try {
                fileManager.processMultipleFiles(uriList, targetFolder,
                    object : FileProcessCallback {
                        override fun onFilesProcessedSuccessfully(copiedFiles: List<File>) {
                            mainHandler.post {
                                mainHandler.postDelayed({
                                    dismissCustomDialog()
                                }, 1000)
                            }
                        }

                        override fun onFileProcessFailed() {
                            mainHandler.post {
                                Toast.makeText(
                                    this@ViewFolderActivity,
                                    getString(R.string.failed_to_hide_files),
                                    Toast.LENGTH_SHORT
                                ).show()
                                dismissCustomDialog()
                            }
                        }
                    })
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(
                        this@ViewFolderActivity,
                        getString(R.string.there_was_a_problem_in_the_folder),
                        Toast.LENGTH_SHORT
                    ).show()
                    dismissCustomDialog()
                }
            }
        }
    }

    private fun refreshCurrentView() {
        if (currentFolder != null) {
            refreshCurrentFolder()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentFolder()
    }

    private fun openFolder(folder: File) {
        // Ensure folder exists and has .nomedia file
        if (!folder.exists()) {
            folder.mkdirs()
            File(folder, ".nomedia").createNewFile()
        }

        val files = folderManager.getFilesInFolder(folder)
        binding.folderName.text = folder.name

        if (files.isNotEmpty()) {
            showFileList(files, folder)
        } else {
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        binding.noItems.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showFileList(files: List<File>, folder: File) {
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)

        // Clean up previous adapter
        fileAdapter?.cleanup()

        fileAdapter = FileAdapter(this, this, folder, prefs.getBoolean("showFileName", true),
            onFolderLongClick = { isSelected ->
                handleFileSelectionModeChange(isSelected)
            }).apply {
            setFileOperationCallback(object : FileAdapter.FileOperationCallback {
                override fun onFileDeleted(file: File) {
                    refreshCurrentFolder()
                }

                override fun onFileRenamed(oldFile: File, newFile: File) {
                    refreshCurrentFolder()
                }

                override fun onRefreshNeeded() {
                    refreshCurrentFolder()
                }

                override fun onSelectionModeChanged(isSelectionMode: Boolean, selectedCount: Int) {
                    handleFileSelectionModeChange(isSelectionMode)
                }

                override fun onSelectionCountChanged(selectedCount: Int) {
                    updateSelectionCountDisplay(selectedCount)
                }
            })

            submitList(files)
        }

        binding.recyclerView.adapter = fileAdapter
        binding.recyclerView.visibility = View.VISIBLE
        binding.noItems.visibility = View.GONE

        binding.menuButton.setOnClickListener {
            fileAdapter?.let { adapter ->
                showFileOptionsMenu(adapter.getSelectedItems())
            }
        }
        showFileViewIcons()
    }


    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        handleBackPress()
    }

    private fun handleBackPress() {
        if (fileAdapter?.onBackPressed() == true) {
            return
        }

        super.onBackPressed()
    }

    private fun handleFileSelectionModeChange(isSelectionMode: Boolean) {
        if (isSelectionMode) {
            showFileSelectionIcons()
        } else {
            showFileViewIcons()
        }
    }

    private fun updateSelectionCountDisplay(selectedCount: Int) {
        if (selectedCount > 0) {
            showFileSelectionIcons()
        } else {
            showFileViewIcons()
        }
    }

    private fun showFileOptionsMenu(selectedFiles: List<File>) {
        if (selectedFiles.isEmpty()) return

        val options = arrayOf(
            getString(R.string.un_hide),
            getString(R.string.delete),
            getString(R.string.copy_to_another_folder),
            getString(R.string.move_to_another_folder)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.file_options))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> unhideSelectedFiles(selectedFiles)
                    1 -> deleteSelectedFiles(selectedFiles)
                    2 -> copyToAnotherFolder(selectedFiles)
                    3 -> moveToAnotherFolder(selectedFiles)
                }
            }
            .show()
    }

    private fun moveToAnotherFolder(selectedFiles: List<File>) {
        showFolderSelectionDialog { destinationFolder ->
            moveFilesToFolder(selectedFiles, destinationFolder)
        }
    }


    private fun unhideSelectedFiles(selectedFiles: List<File>) {
        dialogUtil.showMaterialDialog(
            getString(R.string.un_hide_files),
            getString(R.string.are_you_sure_you_want_to_un_hide_selected_files),
            getString(R.string.un_hide),
            getString(R.string.cancel),
            object : DialogUtil.DialogCallback {
                override fun onPositiveButtonClicked() {
                    performFileUnhiding(selectedFiles)
                }

                override fun onNegativeButtonClicked() {
                    // Do nothing
                }

                override fun onNaturalButtonClicked() {
                    // Do nothing
                }
            }
        )
    }



    private fun deleteSelectedFiles(selectedFiles: List<File>) {
        dialogUtil.showMaterialDialog(
            getString(R.string.delete_items),
            getString(R.string.are_you_sure_you_want_to_delete_selected_items),
            getString(R.string.delete),
            getString(R.string.cancel),
            object : DialogUtil.DialogCallback {
                override fun onPositiveButtonClicked() {
                    performFileDeletion(selectedFiles)
                }

                override fun onNegativeButtonClicked() {
                    // Do nothing
                }

                override fun onNaturalButtonClicked() {
                    // Do nothing
                }
            }
        )
    }



    private fun refreshCurrentFolder() {
        currentFolder?.let { folder ->
            val files = folderManager.getFilesInFolder(folder)
            if (files.isNotEmpty()) {
                binding.recyclerView.visibility = View.VISIBLE
                binding.noItems.visibility = View.GONE
                fileAdapter?.submitList(files.toMutableList())
                fileAdapter?.let { adapter ->
                    if (adapter.isInSelectionMode()) {
                        showFileSelectionIcons()
                    } else {
                        showFileViewIcons()
                    }
                }
            } else {
                showEmptyState()
            }
        }
    }
    private fun setupClickListeners() {
        binding.fabExpend.setOnClickListener {
            if (isFabOpen) closeFabs()
            else openFabs()
        }
        binding.back.setOnClickListener {
            finish()
        }

        binding.addImage.setOnClickListener { openFilePicker("image/*") }
        binding.addVideo.setOnClickListener { openFilePicker("video/*") }
        binding.addAudio.setOnClickListener { openFilePicker("audio/*") }
        binding.addDocument.setOnClickListener { openFilePicker("*/*") }
    }

    private fun setupAnimations() {
        fabOpen = AnimationUtils.loadAnimation(this, R.anim.fab_open)
        fabClose = AnimationUtils.loadAnimation(this, R.anim.fab_close)
        rotateOpen = AnimationUtils.loadAnimation(this, R.anim.rotate_open)
        rotateClose = AnimationUtils.loadAnimation(this, R.anim.rotate_close)
    }

    private fun openFilePicker(mimeType: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        closeFabs()
        pickImageLauncher.launch(intent)
    }

    private fun openFabs() {
        if (!isFabOpen) {
            binding.addImage.startAnimation(fabOpen)
            binding.addVideo.startAnimation(fabOpen)
            binding.addAudio.startAnimation(fabOpen)
            binding.addDocument.startAnimation(fabOpen)
            binding.fabExpend.startAnimation(rotateOpen)

            binding.addImage.visibility = View.VISIBLE
            binding.addVideo.visibility = View.VISIBLE
            binding.addAudio.visibility = View.VISIBLE
            binding.addDocument.visibility = View.VISIBLE

            isFabOpen = true
            mainHandler.postDelayed({
                binding.fabExpend.setImageResource(R.drawable.wrong)
            }, 200)
        }
    }

    private fun closeFabs() {
        if (isFabOpen) {
            binding.addImage.startAnimation(fabClose)
            binding.addVideo.startAnimation(fabClose)
            binding.addAudio.startAnimation(fabClose)
            binding.addDocument.startAnimation(fabClose)
            binding.fabExpend.startAnimation(rotateClose)

            binding.addImage.visibility = View.INVISIBLE
            binding.addVideo.visibility = View.INVISIBLE
            binding.addAudio.visibility = View.INVISIBLE
            binding.addDocument.visibility = View.INVISIBLE

            isFabOpen = false
            binding.fabExpend.setImageResource(R.drawable.ic_add)
        }
    }

    private fun showFileViewIcons() {
        binding.menuButton.visibility = View.GONE
        binding.fabExpend.visibility = View.VISIBLE
        binding.addImage.visibility = View.INVISIBLE
        binding.addVideo.visibility = View.INVISIBLE
        binding.addAudio.visibility = View.INVISIBLE
        binding.addDocument.visibility = View.INVISIBLE
        isFabOpen = false
        binding.fabExpend.setImageResource(R.drawable.ic_add)
    }

    private fun showFileSelectionIcons() {
        binding.menuButton.visibility = View.VISIBLE
        binding.fabExpend.visibility = View.GONE
        binding.addImage.visibility = View.INVISIBLE
        binding.addVideo.visibility = View.INVISIBLE
        binding.addAudio.visibility = View.INVISIBLE
        binding.addDocument.visibility = View.INVISIBLE
        isFabOpen = false
    }

    private fun performFileUnhiding(selectedFiles: List<File>) {
        lifecycleScope.launch {
            var allUnhidden = true
            selectedFiles.forEach { file ->
                try {
                    val fileUri = FileManager.FileManager().getContentUriImage(this@ViewFolderActivity, file)

                    if (fileUri != null) {
                        val result = fileManager.copyFileToNormalDir(fileUri)
                        if (result == null) {
                            allUnhidden = false
                        }
                    } else {
                        allUnhidden = false
                    }

                } catch (e: Exception) {
                    allUnhidden = false
                }
            }

            mainHandler.post {
                val message = if (allUnhidden) {
                    getString(R.string.files_unhidden_successfully)
                } else {
                    getString(R.string.some_files_could_not_be_unhidden)
                }

                Toast.makeText(this@ViewFolderActivity, message, Toast.LENGTH_SHORT).show()

                // Fixed: Ensure proper order of operations
                fileAdapter?.exitSelectionMode()
                refreshCurrentFolder()
            }
        }
    }

    private fun performFileDeletion(selectedFiles: List<File>) {
        var allDeleted = true
        selectedFiles.forEach { file ->
            if (!file.delete()) {
                allDeleted = false
            }
        }

        val message = if (allDeleted) {
            getString(R.string.files_deleted_successfully)
        } else {
            getString(R.string.some_items_could_not_be_deleted)
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Fixed: Ensure proper order of operations
        fileAdapter?.exitSelectionMode()
        refreshCurrentFolder()
    }

    private fun copyToAnotherFolder(selectedFiles: List<File>) {
        showFolderSelectionDialog { destinationFolder ->
            copyFilesToFolder(selectedFiles, destinationFolder)
        }
    }

    private fun copyFilesToFolder(selectedFiles: List<File>, destinationFolder: File) {
        var allCopied = true
        selectedFiles.forEach { file ->
            try {
                val newFile = File(destinationFolder, file.name)
                file.copyTo(newFile, overwrite = true)
            } catch (e: Exception) {
                allCopied = false
            }
        }

        val message = if (allCopied) "Files copied successfully" else "Some files could not be copied"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Fixed: Ensure proper order of operations
        fileAdapter?.exitSelectionMode()
        refreshCurrentFolder()
    }

    private fun moveFilesToFolder(selectedFiles: List<File>, destinationFolder: File) {
        var allMoved = true
        selectedFiles.forEach { file ->
            try {
                val newFile = File(destinationFolder, file.name)
                file.copyTo(newFile, overwrite = true)
                file.delete()
            } catch (e: Exception) {
                allMoved = false
            }
        }

        val message = if (allMoved) "Files moved successfully" else "Some files could not be moved"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Fixed: Ensure proper order of operations
        fileAdapter?.exitSelectionMode()
        refreshCurrentFolder()
    }

    private fun showFolderSelectionDialog(onFolderSelected: (File) -> Unit) {
        val folders = folderManager.getFoldersInDirectory(hiddenDir)
            .filter { it != currentFolder } // Exclude current folder

        if (folders.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_folders_available), Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_folder_selection, null)
        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.folderRecyclerView)
        
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = FolderSelectionAdapter(folders) { selectedFolder ->
            bottomSheetDialog.dismiss()
            onFolderSelected(selectedFolder)
        }

        bottomSheetDialog.show()
    }
}