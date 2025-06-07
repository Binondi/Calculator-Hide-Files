package devs.org.calculator.activities

import android.annotation.SuppressLint
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
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.adapters.FolderSelectionAdapter
import devs.org.calculator.callbacks.FileProcessCallback
import devs.org.calculator.database.HiddenFileEntity
import devs.org.calculator.databinding.ActivityViewFolderBinding
import devs.org.calculator.databinding.ProccessingDialogBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.ENCRYPTED_EXTENSION
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import devs.org.calculator.utils.PrefsUtil
import devs.org.calculator.utils.SecurityUtils
import kotlinx.coroutines.launch
import java.io.File
import android.widget.CheckBox
import android.widget.CompoundButton
import android.app.AlertDialog
import android.view.WindowManager
import devs.org.calculator.adapters.FileAdapter

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

    private var customDialog: androidx.appcompat.app.AlertDialog? = null

    private var dialogShowTime: Long = 0
    private val MINIMUM_DIALOG_DURATION = 1200L
    private val prefs:PrefsUtil by lazy { PrefsUtil(this) }


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
        setupFlagSecure()
    }

    private fun setupFlagSecure() {
        if (prefs.getBoolean("screenshot_restriction", true)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    fun openFolder(folder: File) {
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
        val checkboxes = listOf(imageCheckbox, videoCheckbox, audioCheckbox)
        checkboxes.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkboxes.filter { it != checkbox }.forEach { it.isChecked = false }
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_file_type))
            .setMessage(getString(R.string.please_select_the_type_of_file_to_decrypt))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.decrypt), null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false
            val checkboxListener = CompoundButton.OnCheckedChangeListener { _, _ ->
                positiveButton.isEnabled = checkboxes.any { it.isChecked }
            }
            checkboxes.forEach { it.setOnCheckedChangeListener(checkboxListener) }
        }

        dialog.setOnDismissListener {
            checkboxes.forEach { it.setOnCheckedChangeListener(null) }
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val selectedType = when {
                imageCheckbox.isChecked -> FileManager.FileType.IMAGE
                videoCheckbox.isChecked -> FileManager.FileType.VIDEO
                audioCheckbox.isChecked -> FileManager.FileType.AUDIO
                else -> return@setOnClickListener
            }
            
            lifecycleScope.launch {
                performDecryptionWithType(selectedFiles, selectedType)
            }
            dialog.dismiss()
        }
    }

    private fun performDecryptionWithType(selectedFiles: List<File>, fileType: FileManager.FileType) {
        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            val decryptedFiles = mutableMapOf<File, File>()

            for (file in selectedFiles) {
                try {
                    val hiddenFile = fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath)

                    if (hiddenFile?.isEncrypted == true) {
                        val originalExtension = hiddenFile.originalExtension
                        val decryptedFile = SecurityUtils.changeFileExtension(file, originalExtension)

                        if (SecurityUtils.decryptFile(this@ViewFolderActivity, file, decryptedFile)) {
                            if (decryptedFile.exists() && decryptedFile.length() > 0) {
                                hiddenFile.let {
                                    fileAdapter?.hiddenFileRepository?.updateEncryptionStatus(
                                        filePath = file.absolutePath,
                                        newFilePath = decryptedFile.absolutePath,
                                        encryptedFileName = decryptedFile.name,
                                        isEncrypted = false
                                    )
                                }
                                if (file.delete()) {
                                    decryptedFiles[file] = decryptedFile
                                    successCount++
                                } else {
                                    decryptedFile.delete()
                                    failCount++
                                }
                            } else {
                                decryptedFile.delete()
                                failCount++
                            }
                        } else {
                            if (decryptedFile.exists()) {
                                decryptedFile.delete()
                            }
                            failCount++
                        }
                    } else if (file.name.endsWith(ENCRYPTED_EXTENSION) && hiddenFile == null) {
                        val extension = when (fileType) {
                            FileManager.FileType.IMAGE -> ".jpg"
                            FileManager.FileType.VIDEO -> ".mp4"
                            FileManager.FileType.AUDIO -> ".mp3"
                            else -> ".txt"
                        }
                        
                        val decryptedFile = SecurityUtils.changeFileExtension(file, extension)

                        if (SecurityUtils.decryptFile(this@ViewFolderActivity, file, decryptedFile)) {
                            if (decryptedFile.exists() && decryptedFile.length() > 0) {
                                fileAdapter?.hiddenFileRepository?.insertHiddenFile(
                                    HiddenFileEntity(
                                        filePath = decryptedFile.absolutePath,
                                        fileName = decryptedFile.name,
                                        encryptedFileName = file.name,
                                        fileType = fileType,
                                        originalExtension = extension,
                                        isEncrypted = false
                                    )
                                )
                                if (file.delete()) {
                                    decryptedFiles[file] = decryptedFile
                                    successCount++
                                } else {
                                    decryptedFile.delete()
                                    failCount++
                                }
                            } else {
                                decryptedFile.delete()
                                failCount++
                            }
                        } else {
                            if (decryptedFile.exists()) {
                                decryptedFile.delete()
                            }
                            failCount++
                        }
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                    e.printStackTrace()
                }
            }

            mainHandler.post {
                when {
                    successCount > 0 && failCount == 0 -> {
                        Toast.makeText(this@ViewFolderActivity, "Decrypted $successCount file(s)", Toast.LENGTH_SHORT).show()
                    }
                    successCount > 0 && failCount > 0 -> {
                        Toast.makeText(this@ViewFolderActivity, "Decrypted $successCount file(s), failed to decrypt $failCount", Toast.LENGTH_LONG).show()
                    }
                    failCount > 0 -> {
                        Toast.makeText(this@ViewFolderActivity, "Failed to decrypt $failCount file(s)", Toast.LENGTH_SHORT).show()
                    }
                }
                if (successCount > 0) {
                    refreshCurrentFolder()
                    fileAdapter?.exitSelectionMode()
                }
            }
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
        binding.back.setOnClickListener {
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
            val unhiddenFiles = mutableListOf<File>()
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

            mainHandler.post {
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