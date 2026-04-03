package devs.org.calculator.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import devs.org.calculator.CalculatorApp
import devs.org.calculator.R
import devs.org.calculator.adapters.FileAdapter
import devs.org.calculator.adapters.FolderSelectionAdapter
import devs.org.calculator.callbacks.FileProcessCallback
import devs.org.calculator.database.HiddenFileEntity
import devs.org.calculator.databinding.ActivityViewFolderBinding
import devs.org.calculator.databinding.EncryptionProgressDialogBinding
import devs.org.calculator.databinding.ProccessingDialogBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.ENCRYPTED_EXTENSION
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import devs.org.calculator.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ViewFolderActivity : BaseActivity() {

    private var isFabOpen = false
    private lateinit var fabOpen: Animation
    private lateinit var fabClose: Animation
    private lateinit var binding: ActivityViewFolderBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var fileManager: FileManager
    private lateinit var folderManager: FolderManager
    private lateinit var dialogUtil: DialogUtil
    private var fileAdapter: FileAdapter? = null
    private var currentFolder: File? = null
    private val hiddenDir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private var customDialog: androidx.appcompat.app.AlertDialog? = null

    private var dialogShowTime: Long = 0
    private val MINIMUM_DIALOG_DURATION = 1200L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
        folderManager = FolderManager()
        dialogUtil = DialogUtil(this)

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
        dialogView.title.text = getString(R.string.hiding_count_files, count)
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
                refreshCurrentFolder()
            }, remainingTime)
        } else {
            customDialog?.dismiss()
            customDialog = null
            refreshCurrentFolder()
            updateFilesToAdapter()
        }
    }

    private fun updateFilesToAdapter() {
        val files = folderManager.getFilesInFolder(currentFolder!!)
        fileAdapter?.submitList(files)
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
                                copiedFiles.forEach { file ->
                                    val fileType = fileManager.getFileType(file)
                                    var finalFile = file
                                    val extension = ".${file.extension}"
                                    val isEncrypted = prefs.getBoolean("encryption",false)

                                    if (isEncrypted) {
                                        val encryptedFile = SecurityUtils.changeFileExtension(file, ENCRYPTED_EXTENSION)
                                        if (SecurityUtils.encryptFile(this@ViewFolderActivity, file, encryptedFile)) {
                                            finalFile = encryptedFile
                                            file.delete()
                                        }
                                    }

                                    lifecycleScope.launch {
                                        fileAdapter?.hiddenFileRepository?.insertHiddenFile(
                                            HiddenFileEntity(
                                                filePath = finalFile.absolutePath,
                                                fileName = file.name,
                                                fileType = fileType,
                                                originalExtension = extension,
                                                isEncrypted = isEncrypted,
                                                encryptedFileName = finalFile.name
                                            )
                                        )
                                    }
                                }

                                mainHandler.postDelayed({
                                    dismissCustomDialog()
                                    val files = folderManager.getFilesInFolder(targetFolder)
                                    if (files.isNotEmpty()) {
                                        binding.swipeLayout.visibility = View.VISIBLE
                                        binding.noItems.visibility = View.GONE
                                        if (fileAdapter == null) {
                                            showFileList(files, targetFolder)
                                        } else {
                                            fileAdapter?.submitList(files.toMutableList())
                                        }
                                    }
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
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentFolder()
    }

    fun openFolder(folder: File) {
        if (!folder.exists()) {
            folder.mkdirs()
            File(folder, ".nomedia").createNewFile()
        }

        val files = folderManager.getFilesInFolder(folder)
        binding.toolBar.title = folder.name

        if (files.isNotEmpty()) {
            showFileList(files, folder)
        } else {
            showEmptyState()
        }
        binding.swipeLayout.isRefreshing = false
    }

    private fun showEmptyState() {
        binding.noItems.visibility = View.VISIBLE
        binding.swipeLayout.visibility = View.GONE
    }

    private fun showFileList(files: List<File>, folder: File) {
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        fileAdapter?.cleanup()

        fileAdapter = FileAdapter(this, this, folder, prefs.getBoolean("showFileName", true),
            onFolderLongClick = { isSelected ->
                handleFileSelectionModeChange(isSelected)
            }).apply {
            setFilesOperationCallback(object : FileAdapter.FilesOperationCallback {
                override fun onFileDeleted(file: File) {
                    refreshCurrentFolder()
                    updateFilesToAdapter()
                }

                override fun onFileRenamed(oldFile: File, newFile: File) {
                    refreshCurrentFolder()
                    updateFilesToAdapter()
                }

                override fun onRefreshNeeded() {
                    refreshCurrentFolder()
                    updateFilesToAdapter()
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
        binding.swipeLayout.visibility = View.VISIBLE
        binding.noItems.visibility = View.GONE
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when(menuItem.itemId){
                R.id.options ->{
                    fileAdapter?.let { adapter ->
                        showFileOptionsMenu(adapter.getSelectedItems())
                    }
                    true
                }
                else -> false
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

        lifecycleScope.launch {
            var hasEncryptedFiles = false
            var hasDecryptedFiles = false
            var hasEncFilesWithoutMetadata = false

            for (file in selectedFiles) {
                val hiddenFile = fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)

                if (file.name.endsWith(ENCRYPTED_EXTENSION)) {
                    if (hiddenFile?.isEncrypted == true) {
                        hasEncryptedFiles = true
                    } else {
                        hasEncFilesWithoutMetadata = true
                    }
                } else {
                    hasDecryptedFiles = true
                }
            }

            val options = mutableListOf(
                getString(R.string.un_hide),
                getString(R.string.delete),
                getString(R.string.copy_to_another_folder),
                getString(R.string.move_to_another_folder)
            )

            if (hasDecryptedFiles) {
                options.add(getString(R.string.encrypt_file))
            }
            if (hasEncryptedFiles || hasEncFilesWithoutMetadata) {
                options.add(getString(R.string.decrypt_file))
            }


            MaterialAlertDialogBuilder(this@ViewFolderActivity)
                .setTitle(getString(R.string.file_options))
                .setItems(options.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> unhideSelectedFiles(selectedFiles)
                        1 -> deleteSelectedFiles(selectedFiles)
                        2 -> copyToAnotherFolder(selectedFiles)
                        3 -> moveToAnotherFolder(selectedFiles)
                        else -> {
                            val option = options[which]
                            when (option) {
                                getString(R.string.encrypt_file) -> fileAdapter?.encryptSelectedFiles()
                                getString(R.string.decrypt_file) -> {
                                    lifecycleScope.launch {
                                        val filesWithoutMetadata = selectedFiles.filter { file ->
                                            file.name.endsWith(ENCRYPTED_EXTENSION) &&
                                                    fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)?.isEncrypted != true
                                        }

                                        if (filesWithoutMetadata.isNotEmpty()) {
                                            showDecryptionTypeDialog(filesWithoutMetadata)
                                        } else {
                                            fileAdapter?.decryptSelectedFiles()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .show()
        }
    }

    private fun showDecryptionTypeDialog(selectedFiles: List<File>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_file_type_selection, null)
        val imageCheckbox = dialogView.findViewById<CheckBox>(R.id.checkboxImage)
        val videoCheckbox = dialogView.findViewById<CheckBox>(R.id.checkboxVideo)
        val audioCheckbox = dialogView.findViewById<CheckBox>(R.id.checkboxAudio)
        val otherCheckbox = dialogView.findViewById<CheckBox>(R.id.checkboxOther)
        val otherLayer = dialogView.findViewById<LinearLayout>(R.id.otherLayer)
        val edtExtension = dialogView.findViewById<TextInputEditText>(R.id.editExtension)

        val checkboxes = listOf(imageCheckbox, videoCheckbox, audioCheckbox, otherCheckbox)

        val mutualExclusiveListener = CompoundButton.OnCheckedChangeListener { button, isChecked ->
            if (isChecked) {
                checkboxes.filter { it != button }.forEach { it.isChecked = false }
            }
            otherLayer.visibility = if (otherCheckbox.isChecked) View.VISIBLE else View.GONE
        }

        checkboxes.forEach { it.setOnCheckedChangeListener(mutualExclusiveListener) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_file_type))
            .setMessage(getString(R.string.please_select_the_type_of_file_to_decrypt))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.decrypt), null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val updatePositiveButton = {
                positiveButton.isEnabled = checkboxes.any { it.isChecked }
            }
            checkboxes.forEach {
                it.setOnCheckedChangeListener { button, isChecked ->
                    mutualExclusiveListener.onCheckedChanged(button, isChecked)
                    updatePositiveButton()
                }
            }

            updatePositiveButton()
        }

        dialog.setOnDismissListener {
            checkboxes.forEach { it.setOnCheckedChangeListener(null) }
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (otherCheckbox.isChecked) {
                val extension = edtExtension.text.toString().trim()
                if (extension.isEmpty()) {
                    edtExtension.error = getString(R.string.please_enter_a_file_extension)
                    return@setOnClickListener
                }
                val regex = Regex("""\.\w+""")
                if (!regex.matches(extension)) {
                    edtExtension.error = getString(R.string.invalid_extension_format)
                    return@setOnClickListener
                }
            }

            val selectedType = when {
                imageCheckbox.isChecked -> FileManager.FileType.IMAGE
                videoCheckbox.isChecked -> FileManager.FileType.VIDEO
                audioCheckbox.isChecked -> FileManager.FileType.AUDIO
                otherCheckbox.isChecked -> FileManager.FileType.DOCUMENT
                else -> return@setOnClickListener
            }

            fileManager.performDecryption(
                selectedFiles,
                forcedType = selectedType,
                forcedExtension = if (otherCheckbox.isChecked) edtExtension.text.toString().trim() else null
            ) {
                refreshCurrentFolder()
                fileAdapter?.exitSelectionMode()
            }
            dialog.dismiss()
        }

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

                override fun onNegativeButtonClicked() {}

                override fun onNaturalButtonClicked() {}
            }
        )
    }



    private fun deleteSelectedFiles(selectedFiles: List<File>) {
        dialogUtil.showMaterialDialog(
            getString(R.string.delete_file),
            getString(R.string.are_you_sure_to_delete_selected_files_permanently),
            getString(R.string.delete),
            getString(R.string.cancel),
            object : DialogUtil.DialogCallback {
                override fun onPositiveButtonClicked() {
                    performFileDeletion(selectedFiles)
                }

                override fun onNegativeButtonClicked() {}

                override fun onNaturalButtonClicked() {}
            }
        )
    }



    private fun refreshCurrentFolder() {
        currentFolder?.let { folder ->
            lifecycleScope.launch {
                try {
                    val files = folderManager.getFilesInFolder(folder)
                    mainHandler.post {
                        if (files.isNotEmpty()) {
                            binding.swipeLayout.visibility = View.VISIBLE
                            binding.noItems.visibility = View.GONE

                            val currentFiles = fileAdapter?.currentList ?: emptyList()
                            val hasChanges = files.size != currentFiles.size ||
                                    files.any { newFile ->
                                        currentFiles.none { it.absolutePath == newFile.absolutePath }
                                    }

                            if (hasChanges) {
                                fileAdapter?.submitList(files.toMutableList())
                            }

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
                } catch (_: Exception) {
                    mainHandler.post {
                        showEmptyState()
                    }
                }
            }
        }
    }
    private fun setupClickListeners() {
        binding.fabExpend.setOnClickListener {
            if (isFabOpen) closeFabs()
            else openFabs()
        }
        binding.toolBar.setNavigationOnClickListener {
            finish()
        }
        binding.swipeLayout.setOnRefreshListener {
            openFolder(currentFolder!!)
        }

        binding.addImage.setOnClickListener { openFilePicker("image/*") }
        binding.addVideo.setOnClickListener { openFilePicker("video/*") }
        binding.addAudio.setOnClickListener { openFilePicker("audio/*") }
        binding.addDocument.setOnClickListener { openFilePicker("*/*") }
    }

    private fun setupAnimations() {
        fabOpen = AnimationUtils.loadAnimation(this, R.anim.fab_open)
        fabClose = AnimationUtils.loadAnimation(this, R.anim.fab_close)
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
        (application as CalculatorApp).isWaitingForResult = true
        pickImageLauncher.launch(intent)
    }

    private fun openFabs() {
        if (!isFabOpen) {
            isFabOpen = true

            binding.addImage.visibility = View.VISIBLE
            binding.addVideo.visibility = View.VISIBLE
            binding.addAudio.visibility = View.VISIBLE
            binding.addDocument.visibility = View.VISIBLE

            binding.addImage.startAnimation(fabOpen)
            binding.addVideo.startAnimation(fabOpen)
            binding.addAudio.startAnimation(fabOpen)
            binding.addDocument.startAnimation(fabOpen)
            binding.fabExpend.animate()
                .rotation(45f)
                .setDuration(200)
                .start()
        }
    }

    private fun closeFabs() {
        if (isFabOpen) {
            isFabOpen = false

            binding.addImage.startAnimation(fabClose)
            binding.addVideo.startAnimation(fabClose)
            binding.addAudio.startAnimation(fabClose)
            binding.addDocument.startAnimation(fabClose)
            binding.fabExpend.animate()
                .rotation(0f)
                .setDuration(200)
                .start()

            mainHandler.postDelayed({
                if (!isFabOpen) {
                    binding.addImage.visibility = View.GONE
                    binding.addVideo.visibility = View.GONE
                    binding.addAudio.visibility = View.GONE
                    binding.addDocument.visibility = View.GONE
                }
            }, 200)
        }
    }

    private fun showFileViewIcons() {
        binding.toolBar.menu.findItem(R.id.options)?.isVisible = false
        binding.fabExpend.visibility = View.VISIBLE
        binding.addImage.visibility = View.GONE
        binding.addVideo.visibility = View.GONE
        binding.addAudio.visibility = View.GONE
        binding.addDocument.visibility = View.GONE
        isFabOpen = false
        binding.fabExpend.setImageResource(R.drawable.ic_add)
        binding.fabExpend.rotation = 0f
    }

    private fun showFileSelectionIcons() {
        binding.toolBar.menu.findItem(R.id.options)?.isVisible = true
        binding.fabExpend.visibility = View.GONE
        binding.addImage.visibility = View.GONE
        binding.addVideo.visibility = View.GONE
        binding.addAudio.visibility = View.GONE
        binding.addDocument.visibility = View.GONE
        isFabOpen = false
    }

    private fun performFileUnhiding(selectedFiles: List<File>) {
        lifecycleScope.launch {
            var allUnhidden = true
            val unhiddenFiles = mutableListOf<File>()

            // Filter encrypted and plain files
            val encryptedFiles = selectedFiles.filter { file ->
                val hiddenFile = fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)
                hiddenFile?.isEncrypted == true
            }

            val plainFiles = selectedFiles.filter { it !in encryptedFiles }

            // Switch to Main thread to show dialog immediately
            withContext(Dispatchers.Main) {
                if (encryptedFiles.isNotEmpty()) {
                    showEncryptionDialog(R.string.decrypting_files)
                } else if (plainFiles.isNotEmpty()) {
                    showCustomDialog(plainFiles.size)
                }
            }

            // Perform heavy work on IO thread
            withContext(Dispatchers.IO) {
                selectedFiles.forEach { file ->
                    try {
                        val hiddenFile = fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)

                        if (hiddenFile?.isEncrypted == true) {
                            val originalExtension = hiddenFile.originalExtension
                            val decryptedFile = SecurityUtils.changeFileExtension(file, originalExtension)

                            if (SecurityUtils.decryptFile(this@ViewFolderActivity, file, decryptedFile)) {
                                if (decryptedFile.exists() && decryptedFile.length() > 0) {
                                    val fileUri = FileManager.FileManager().getContentUriImage(this@ViewFolderActivity, decryptedFile)
                                    if (fileUri != null) {
                                        val result = fileManager.copyFileToNormalDir(fileUri)
                                        if (result != null) {
                                            hiddenFile.let {
                                                fileAdapter?.hiddenFileRepository?.deleteHiddenFile(it)
                                            }
                                            file.delete()
                                            decryptedFile.delete()
                                            unhiddenFiles.add(file)
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
                            val fileUri = FileManager.FileManager().getContentUriImage(this@ViewFolderActivity, file)
                            if (fileUri != null) {
                                val result = fileManager.copyFileToNormalDir(fileUri)
                                if (result != null) {
                                    hiddenFile?.let {
                                        fileAdapter?.hiddenFileRepository?.deleteHiddenFile(it)
                                    }
                                    file.delete()
                                    unhiddenFiles.add(file)
                                } else {
                                    allUnhidden = false
                                }
                            } else {
                                allUnhidden = false
                            }
                        }
                    } catch (e: Exception) {
                        allUnhidden = false
                        e.printStackTrace()
                    }
                }
            }

            // Back to Main thread for UI updates
            withContext(Dispatchers.Main) {
                dismissProcessingDialog()
                val message = if (allUnhidden) {
                    getString(R.string.files_unhidden_successfully)
                } else {
                    getString(R.string.some_files_could_not_be_unhidden)
                }

                Toast.makeText(this@ViewFolderActivity, message, Toast.LENGTH_SHORT).show()
                refreshCurrentFolder()
                fileAdapter?.exitSelectionMode()
            }
        }
    }

    private fun showEncryptionDialog(titleResId: Int) {
        val binding = EncryptionProgressDialogBinding.inflate(LayoutInflater.from(this))
        binding.title.text = getString(titleResId)
        customDialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(false)
            .create()
        customDialog?.show()
        dialogShowTime = System.currentTimeMillis()
    }

    private fun dismissProcessingDialog() {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - dialogShowTime

        if (elapsedTime < MINIMUM_DIALOG_DURATION) {
            val remainingTime = MINIMUM_DIALOG_DURATION - elapsedTime
            mainHandler.postDelayed({
                customDialog?.dismiss()
                customDialog = null
            }, remainingTime)
        } else {
            customDialog?.dismiss()
            customDialog = null
        }
    }

    private fun performFileDeletion(selectedFiles: List<File>) {
        lifecycleScope.launch {
            var allDeleted = true
            val deletedFiles = mutableListOf<File>()
            selectedFiles.forEach { file ->
                try {
                    val hiddenFile = fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)
                    hiddenFile?.let {
                        fileAdapter?.hiddenFileRepository?.deleteHiddenFile(it)
                    }
                    if (file.delete()) {
                        deletedFiles.add(file)
                    } else {
                        allDeleted = false
                    }
                } catch (_: Exception) {
                    allDeleted = false
                }
            }

            mainHandler.post {
                val message = if (allDeleted) {
                    getString(R.string.files_deleted_successfully)
                } else {
                    getString(R.string.some_items_could_not_be_deleted)
                }

                Toast.makeText(this@ViewFolderActivity, message, Toast.LENGTH_SHORT).show()
                refreshCurrentFolder()
                fileAdapter?.exitSelectionMode()
            }
        }
    }

    private fun copyToAnotherFolder(selectedFiles: List<File>) {
        showFolderSelectionDialog { destinationFolder ->
            copyFilesToFolder(selectedFiles, destinationFolder)
        }
    }

    private fun copyFilesToFolder(selectedFiles: List<File>, destinationFolder: File) {
        lifecycleScope.launch {
            var allCopied = true
            val copiedFiles = mutableListOf<File>()
            selectedFiles.forEach { file ->
                try {
                    val newFile = File(destinationFolder, file.name)
                    file.copyTo(newFile, overwrite = true)

                    val hiddenFile = fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)
                    hiddenFile?.let {
                        fileAdapter?.hiddenFileRepository?.insertHiddenFile(
                            HiddenFileEntity(
                                filePath = newFile.absolutePath,
                                fileName = it.fileName,
                                fileType = it.fileType,
                                originalExtension = it.originalExtension,
                                isEncrypted = it.isEncrypted,
                                encryptedFileName = it.encryptedFileName
                            )
                        )
                    }
                    copiedFiles.add(file)
                } catch (e: Exception) {
                    allCopied = false
                    e.printStackTrace()
                }
            }

            mainHandler.post {
                val message = if (allCopied) getString(R.string.files_copied_successfully) else getString(R.string.some_files_could_not_be_copied)
                Toast.makeText(this@ViewFolderActivity, message, Toast.LENGTH_SHORT).show()
                refreshCurrentFolder()
                fileAdapter?.exitSelectionMode()
            }
        }
    }

    private fun moveFilesToFolder(selectedFiles: List<File>, destinationFolder: File) {
        lifecycleScope.launch {
            var allMoved = true
            val movedFiles = mutableListOf<File>()
            selectedFiles.forEach { file ->
                try {
                    val newFile = File(destinationFolder, file.name)
                    file.copyTo(newFile, overwrite = true)

                    val hiddenFile = fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)
                    hiddenFile?.let {
                        fileAdapter?.hiddenFileRepository?.updateEncryptionStatus(
                            filePath = file.absolutePath,
                            newFilePath = newFile.absolutePath,
                            encryptedFileName = it.encryptedFileName,
                            isEncrypted = it.isEncrypted
                        )
                    }

                    if (file.delete()) {
                        movedFiles.add(file)
                    } else {
                        allMoved = false
                    }
                } catch (e: Exception) {
                    allMoved = false
                    e.printStackTrace()
                }
            }

            mainHandler.post {
                val message = if (allMoved) getString(R.string.files_moved_successfully) else getString(R.string.some_files_could_not_be_moved)
                Toast.makeText(this@ViewFolderActivity, message, Toast.LENGTH_SHORT).show()
                refreshCurrentFolder()
                fileAdapter?.exitSelectionMode()
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showFolderSelectionDialog(onFolderSelected: (File) -> Unit) {
        val folders = folderManager.getFoldersInDirectory(hiddenDir)
            .filter { it != currentFolder }

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
