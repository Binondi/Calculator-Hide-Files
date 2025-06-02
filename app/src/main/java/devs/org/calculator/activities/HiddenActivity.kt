package devs.org.calculator.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.adapters.FileAdapter
import devs.org.calculator.adapters.FolderAdapter
import devs.org.calculator.callbacks.FileProcessCallback
import devs.org.calculator.databinding.ActivityHiddenBinding
import devs.org.calculator.databinding.ProccessingDialogBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import kotlinx.coroutines.launch
import java.io.File

class HiddenActivity : AppCompatActivity() {

    private var isFabOpen = false
    private lateinit var fabOpen: Animation
    private lateinit var fabClose: Animation
    private lateinit var rotateOpen: Animation
    private lateinit var rotateClose: Animation

    private lateinit var binding: ActivityHiddenBinding
    private val fileManager = FileManager(this, this)
    private val folderManager = FolderManager(this)
    private val dialogUtil = DialogUtil(this)
    private var customDialog: androidx.appcompat.app.AlertDialog? = null
    private val STORAGE_PERMISSION_CODE = 101
    private var currentFolder: File? = null
    private var folderAdapter: FolderAdapter? = null
    val hiddenDir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)

    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private var dialogShowTime: Long = 0
    private val MINIMUM_DIALOG_DURATION = 1700L

    private fun showCustomDialog(i: Int) {
        val dialogView = ProccessingDialogBinding.inflate(layoutInflater)
        customDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView.root)
            .setCancelable(false)
            .create()
        dialogView.title.text = "Hiding $i files"
        customDialog?.show()
        dialogShowTime = System.currentTimeMillis()

    }

    private fun dismissCustomDialog() {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - dialogShowTime

        if (elapsedTime < MINIMUM_DIALOG_DURATION) {
            val remainingTime = MINIMUM_DIALOG_DURATION - elapsedTime
            Handler(Looper.getMainLooper()).postDelayed({
                customDialog?.dismiss()
                customDialog = null
            }, remainingTime)
        } else {
            customDialog?.dismiss()
            customDialog = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //initialized animations for fabs
        fabOpen = AnimationUtils.loadAnimation(this, R.anim.fab_open)
        fabClose = AnimationUtils.loadAnimation(this, R.anim.fab_close)
        rotateOpen = AnimationUtils.loadAnimation(this, R.anim.rotate_open)
        rotateClose = AnimationUtils.loadAnimation(this, R.anim.rotate_close)

        binding.fabExpend.visibility = View.GONE
        binding.addImage.visibility = View.GONE
        binding.addVideo.visibility = View.GONE
        binding.addAudio.visibility = View.GONE
        binding.addDocument.visibility = View.GONE
        binding.addFolder.visibility = View.VISIBLE
        binding.deleteSelected.visibility = View.GONE

        binding.fabExpend.setOnClickListener {
            if (isFabOpen) {
                closeFabs()

            } else {
                openFabs()

            }
        }

        binding.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.addImage.setOnClickListener { openFilePicker("image/*") }
        binding.addVideo.setOnClickListener { openFilePicker("video/*") }
        binding.addAudio.setOnClickListener { openFilePicker("audio/*") }
        binding.back.setOnClickListener {
            if (currentFolder != null) {
                pressBack()
            } else {
                super.onBackPressed()
            }
        }
        binding.addDocument.setOnClickListener { openFilePicker("*/*") }
        binding.addFolder.setOnClickListener {
            dialogUtil.createInputDialog(
                title = "Enter Folder Name To Create",
                hint = "",
                callback = object : DialogUtil.InputDialogCallback {
                    override fun onPositiveButtonClicked(input: String) {
                        fileManager.askPermission(this@HiddenActivity)
                        folderManager.createFolder( hiddenDir,input )
                        listFoldersInHiddenDirectory()
                    }
                }
            )
        }

        fileManager.askPermission(this)
        listFoldersInHiddenDirectory()

        setupDeleteButton()

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                    showCustomDialog(uriList.size)
                    lifecycleScope.launch {
                        if (currentFolder != null){
                            FileManager(this@HiddenActivity, this@HiddenActivity)
                                .processMultipleFiles(uriList, currentFolder!!,
                                    object : FileProcessCallback {
                                        override fun onFilesProcessedSuccessfully(copiedFiles: List<File>) {
                                            Toast.makeText(this@HiddenActivity,  "${copiedFiles.size} ${getString(R.string.documents_hidden_successfully)}", Toast.LENGTH_SHORT).show()
                                            openFolder(currentFolder!!)
                                            dismissCustomDialog()
                                        }

                                        override fun onFileProcessFailed() {
                                            Toast.makeText(this@HiddenActivity,
                                                getString(R.string.failed_to_hide_files), Toast.LENGTH_SHORT).show()
                                            dismissCustomDialog()
                                        }

                                    })
                        }else{
                            Toast.makeText(
                                this@HiddenActivity,
                                getString(R.string.there_was_a_problem_in_the_folder),
                                Toast.LENGTH_SHORT
                            ).show()
                            dismissCustomDialog()
                        }

                    }
                } else {
                    Toast.makeText(this, getString(R.string.no_files_selected), Toast.LENGTH_SHORT).show()
                }
            }
        }
        askPermissiom()
    }

    private fun askPermissiom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if (!Environment.isExternalStorageManager()){
                val intent = Intent().setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
        else {
            checkAndRequestStoragePermission()
        }
    }

    private fun checkAndRequestStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_CODE
            )
        }
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
        pickImageLauncher.launch(intent)
    }

    private fun listFoldersInHiddenDirectory() {
        if (hiddenDir.exists() && hiddenDir.isDirectory) {
            val folders = folderManager.getFoldersInDirectory(hiddenDir)

            if (folders.isNotEmpty()) {
                binding.noItems.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE

                // Initialize adapter only once
                if (folderAdapter == null) {
                    binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
                    folderAdapter = FolderAdapter(
                        onFolderClick = { clickedFolder ->
                            openFolder(clickedFolder)
                        },
                        onFolderLongClick = { folder ->
                            // Enter selection mode
                            binding.fabExpend.visibility = View.GONE
                            binding.addFolder.visibility = View.GONE
                            binding.deleteSelected.visibility = View.VISIBLE
                        },
                        onSelectionModeChanged = { isSelectionMode ->
                            if (!isSelectionMode) {
                                binding.deleteSelected.visibility = View.GONE
                                binding.addFolder.visibility = View.VISIBLE
                            }
                        }
                    )
                    binding.recyclerView.adapter = folderAdapter
                }

                // Submit new list to adapter - DiffUtil will handle the comparison
                folderAdapter?.submitList(folders)
            } else {
                binding.noItems.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
        } else if (!hiddenDir.exists()) {
            fileManager.getHiddenDirectory()
        } else {
            Log.e("HiddenActivity", "Hidden directory is not a directory: ${hiddenDir.absolutePath}")
        }
    }

    private fun openFolder(folder: File) {
        Log.d("HiddenActivity", "Opening folder: ${folder.name}")
        currentFolder = folder
        binding.addFolder.visibility = View.GONE
        binding.fabExpend.visibility = View.VISIBLE

        // Read files in the clicked folder and update RecyclerView
        val files = folderManager.getFilesInFolder(folder)
        Log.d("HiddenActivity", "Found ${files.size} files in ${folder.name}")
        binding.folderName.text = folder.name

        if (files.isNotEmpty()) {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 3)

            val fileAdapter = FileAdapter(this, this, folder).apply {
                fileOperationCallback = object : FileAdapter.FileOperationCallback {
                    override fun onFileDeleted(file: File) {
                        // Refresh the file list
                        refreshCurrentFolder()
                    }

                    override fun onFileRenamed(oldFile: File, newFile: File) {
                        // Refresh the file list
                        refreshCurrentFolder()
                    }

                    override fun onRefreshNeeded() {
                        // Refresh the file list
                        refreshCurrentFolder()
                    }

                    override fun onSelectionModeChanged(
                        isSelectionMode: Boolean,
                        selectedCount: Int
                    ) {

                    }

                    override fun onSelectionCountChanged(selectedCount: Int) {

                        
                    }
                }

                submitList(files)
            }

            binding.recyclerView.adapter = fileAdapter
            binding.recyclerView.visibility = View.VISIBLE
            binding.noItems.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.noItems.visibility = View.VISIBLE
        }
    }

    private fun refreshCurrentFolder() {
        currentFolder?.let { folder ->
            val files = folderManager.getFilesInFolder(folder)
            (binding.recyclerView.adapter as? FileAdapter)?.submitList(files)

            if (files.isEmpty()) {
                binding.recyclerView.visibility = View.GONE
                binding.noItems.visibility = View.VISIBLE
            } else {
                binding.recyclerView.visibility = View.VISIBLE
                binding.noItems.visibility = View.GONE
            }
        }
    }

    private fun openFabs() {
        binding.addImage.startAnimation(fabOpen)
        binding.addVideo.startAnimation(fabOpen)
        binding.addAudio.startAnimation(fabOpen)
        binding.addDocument.startAnimation(fabOpen)
        binding.addFolder.startAnimation(fabOpen)
        binding.fabExpend.startAnimation(rotateOpen)

        binding.addImage.visibility = View.VISIBLE
        binding.addVideo.visibility = View.VISIBLE
        binding.addAudio.visibility = View.VISIBLE
        binding.addDocument.visibility = View.VISIBLE
        binding.addFolder.visibility = View.VISIBLE

        isFabOpen = true
        Handler(Looper.getMainLooper()).postDelayed({
            binding.fabExpend.setImageResource(R.drawable.wrong)
        },200)
    }

    private fun closeFabs() {
        binding.addImage.startAnimation(fabClose)
        binding.addVideo.startAnimation(fabClose)
        binding.addAudio.startAnimation(fabClose)
        binding.addDocument.startAnimation(fabClose)
        binding.addFolder.startAnimation(fabClose)
        binding.fabExpend.startAnimation(rotateClose)

        binding.addImage.visibility = View.INVISIBLE
        binding.addVideo.visibility = View.INVISIBLE
        binding.addAudio.visibility = View.INVISIBLE
        binding.addDocument.visibility = View.INVISIBLE
        binding.addFolder.visibility = View.INVISIBLE

        isFabOpen = false
        binding.fabExpend.setImageResource(R.drawable.ic_add)
    }


    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use { it ->
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                it.moveToFirst()
                name = it.getString(nameIndex)
            }
        }
        return name
    }


    private fun setupDeleteButton() {
        binding.deleteSelected.setOnClickListener {
            val selectedFolders = folderAdapter?.getSelectedItems() ?: emptyList()
            if (selectedFolders.isNotEmpty()) {
                dialogUtil.showMaterialDialog(
                    getString(R.string.delete_items),
                    getString(R.string.are_you_sure_you_want_to_delete_selected_items),
                    getString(R.string.delete),
                    getString(R.string.cancel),
                    object : DialogUtil.DialogCallback {
                        override fun onPositiveButtonClicked() {
                            var allDeleted = true
                            selectedFolders.forEach { folder ->
                                if (!folderManager.deleteFolder(folder)) {
                                    allDeleted = false
                                }
                            }
                            if (allDeleted) {
                                Toast.makeText(this@HiddenActivity, getString(R.string.folder_deleted_successfully), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@HiddenActivity, getString(R.string.some_items_could_not_be_deleted), Toast.LENGTH_SHORT).show()
                            }
                            folderAdapter?.clearSelection()
                            binding.deleteSelected.visibility = View.GONE
                            binding.addFolder.visibility = View.VISIBLE
                            listFoldersInHiddenDirectory()
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
        }
    }

    private fun pressBack(){
        currentFolder = null
        if (isFabOpen) {
            closeFabs()
        }
        if (folderAdapter != null) {
            binding.recyclerView.adapter = folderAdapter
        }
        binding.folderName.text = getString(R.string.hidden_space)
        listFoldersInHiddenDirectory()
        binding.fabExpend.visibility = View.GONE
        binding.addImage.visibility = View.GONE
        binding.addVideo.visibility = View.GONE
        binding.addAudio.visibility = View.GONE
        binding.addDocument.visibility = View.GONE
        binding.addFolder.visibility = View.VISIBLE
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (currentFolder != null) {
            pressBack()
        } else {
            super.onBackPressed()
        }
    }
}