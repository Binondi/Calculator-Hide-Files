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
import androidx.activity.OnBackPressedCallback
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
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import devs.org.calculator.databinding.BottomSheetConfirmationBinding
import android.provider.MediaStore
import androidx.core.content.FileProvider
import devs.org.calculator.CalculatorApp
import devs.org.calculator.R
import devs.org.calculator.adapters.FileAdapter
import devs.org.calculator.adapters.FolderSelectionAdapter
import devs.org.calculator.callbacks.FileProcessCallback
import devs.org.calculator.database.HiddenFileEntity
import devs.org.calculator.databinding.ActivityViewFolderBinding
import devs.org.calculator.utils.expandFully
import devs.org.calculator.databinding.BottomSheetFileOptionsBinding
import devs.org.calculator.databinding.BottomSheetFileTypeBinding
import devs.org.calculator.databinding.EncryptionProgressDialogBinding
import devs.org.calculator.databinding.ProccessingDialogBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.ENCRYPTED_EXTENSION
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import devs.org.calculator.utils.PrefsUtil
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
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private var tempPhotoFile: File? = null

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
        setupBackPressed()
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
        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                handleCameraResult()
            }
        }
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    showCameraSettingsBottomSheet()
                } else {
                    Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
                }
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


    private fun processSelectedFiles(uriList: List<Uri>, isFromCamera: Boolean = false) {
        val wantsEncryption = prefs.getBoolean("encryption", false)
        if (wantsEncryption && !SecurityUtils.hasCustomKey(this)) {
            SecurityUtils.showSetKeyBottomSheet(
                activity = this,
                title = getString(R.string.encryption_key_required_title),
                subtitle = getString(R.string.encryption_key_required_message),
                onKeySet = {
                    determineHideActionAndProcess(uriList, isFromCamera)
                },
                onDismissed = {
                    Toast.makeText(this, getString(R.string.encryption_key_required_title), Toast.LENGTH_SHORT).show()
                }
            )
            return
        }
        determineHideActionAndProcess(uriList, isFromCamera)
    }

    private fun determineHideActionAndProcess(uriList: List<Uri>, isFromCamera: Boolean) {
        if (isFromCamera) {
            executeProcessSelectedFiles(uriList, deleteOriginal = true)
            return
        }

        val prefsUtil = PrefsUtil(this)
        when (prefsUtil.getHideFileAction()) {
            PrefsUtil.HIDE_ACTION_MOVE -> {
                executeProcessSelectedFiles(uriList, deleteOriginal = true)
            }
            PrefsUtil.HIDE_ACTION_COPY -> {
                executeProcessSelectedFiles(uriList, deleteOriginal = false)
            }
            else -> {
                DialogUtil.showHideFileActionBottomSheet(this,
                    onActionSelected = { deleteOriginal ->
                        executeProcessSelectedFiles(uriList, deleteOriginal = deleteOriginal)
                    }
                )
            }
        }
    }

    private fun executeProcessSelectedFiles(uriList: List<Uri>, deleteOriginal: Boolean = true) {
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
                                    val isEncrypted = prefs.getBoolean("encryption", false) && SecurityUtils.hasCustomKey(this@ViewFolderActivity)

                                    if (isEncrypted) {
                                        val encryptedFile = SecurityUtils.changeFileExtension(file, ENCRYPTED_EXTENSION)
                                        val meta = SecurityUtils.EncryptedFileMetadata(
                                            originalFileName = file.name,
                                            originalExtension = extension,
                                            fileType = fileType
                                        )
                                        if (SecurityUtils.encryptFile(this@ViewFolderActivity, file, encryptedFile, meta)) {
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
                    },
                    deleteOriginal = deleteOriginal)
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
        updateSetKeyBanner(files)

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
        binding.toolBar.menu.findItem(R.id.select_all)?.isVisible = false
        binding.toolBar.menu.findItem(R.id.options)?.isVisible = false
        binding.toolBar.menu.findItem(R.id.view_settings)?.isVisible = false
    }

    private fun showViewSettingsDialog() {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.expandFully()
        val view = layoutInflater.inflate(R.layout.bottom_sheet_view_settings, null)
        bottomSheetDialog.setContentView(view)

        val cornerRadiusSlider = view.findViewById<com.google.android.material.slider.Slider>(R.id.cornerRadiusSlider)
        val spanCountToggleGroup = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.spanCountToggleGroup)
        val showFileNameSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.showFileNameSwitch)

        val currentCornerRadius = prefs.getInt("cornerRadius", 10)
        val currentSpanCount = prefs.getInt("spanCount", 3)
        val currentShowFileName = prefs.getBoolean("showFileName", true)

        cornerRadiusSlider.value = currentCornerRadius.toFloat()
        showFileNameSwitch.isChecked = currentShowFileName

        when (currentSpanCount) {
            2 -> spanCountToggleGroup.check(R.id.span2)
            3 -> spanCountToggleGroup.check(R.id.span3)
            4 -> spanCountToggleGroup.check(R.id.span4)
        }

        cornerRadiusSlider.addOnChangeListener { _, value, _ ->
            prefs.setInt("cornerRadius", value.toInt())
            fileAdapter?.updateViewSettings(showFileNameSwitch.isChecked, value.toInt())
        }

        spanCountToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newSpanCount = when (checkedId) {
                    R.id.span2 -> 2
                    R.id.span3 -> 3
                    R.id.span4 -> 4
                    else -> 3
                }
                prefs.setInt("spanCount", newSpanCount)
                (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = newSpanCount
            }
        }

        showFileNameSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("showFileName", isChecked)
            fileAdapter?.updateViewSettings(isChecked, cornerRadiusSlider.value.toInt())
        }

        bottomSheetDialog.show()
    }

    private fun showFileList(files: List<File>, folder: File) {
        val spanCount = prefs.getInt("spanCount", 3)
        val cornerRadius = prefs.getInt("cornerRadius", 10)
        binding.recyclerView.layoutManager = GridLayoutManager(this, spanCount)
        fileAdapter?.cleanup()

        fileAdapter = FileAdapter(this, this, folder, prefs.getBoolean("showFileName", true), cornerRadius,
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
                R.id.select_all -> {
                    fileAdapter?.toggleSelectAll()
                    true
                }
                R.id.view_settings -> {
                    showViewSettingsDialog()
                    true
                }
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

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (fileAdapter?.onBackPressed() == true) {
            return
        }

        finish()
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

            val bottomSheet = BottomSheetDialog(this@ViewFolderActivity)
            bottomSheet.expandFully()
            val sheetBinding = BottomSheetFileOptionsBinding.inflate(layoutInflater)
            bottomSheet.setContentView(sheetBinding.root)

            sheetBinding.tvFileOptionsCount.text = getString(R.string.hiding_count_files, selectedFiles.size).replace(getString(R.string.hiding_files), "${selectedFiles.size} items")

            val isAllSelected = fileAdapter?.isAllSelected() == true
            sheetBinding.tvSelectAll.text = if (isAllSelected) getString(R.string.deselect_all) else getString(R.string.select_all)
            sheetBinding.ivSelectAll.setImageResource(if (isAllSelected) R.drawable.ic_deselect_all else R.drawable.ic_select_all)

            sheetBinding.rowSelectAll.setOnClickListener {
                bottomSheet.dismiss()
                fileAdapter?.toggleSelectAll()
            }

            sheetBinding.rowUnhide.setOnClickListener {
                bottomSheet.dismiss()
                unhideSelectedFiles(selectedFiles)
            }

            sheetBinding.rowDelete.setOnClickListener {
                bottomSheet.dismiss()
                deleteSelectedFiles(selectedFiles)
            }

            sheetBinding.rowCopy.setOnClickListener {
                bottomSheet.dismiss()
                copyToAnotherFolder(selectedFiles)
            }

            sheetBinding.rowMove.setOnClickListener {
                bottomSheet.dismiss()
                moveToAnotherFolder(selectedFiles)
            }

            if (hasDecryptedFiles) {
                sheetBinding.rowEncrypt.visibility = View.VISIBLE
                sheetBinding.rowEncrypt.setOnClickListener {
                    bottomSheet.dismiss()
                    fileAdapter?.encryptSelectedFiles()
                }
            } else {
                sheetBinding.rowEncrypt.visibility = View.GONE
            }

            if (hasEncryptedFiles || hasEncFilesWithoutMetadata) {
                sheetBinding.rowDecrypt.visibility = View.VISIBLE
                sheetBinding.rowDecrypt.setOnClickListener {
                    bottomSheet.dismiss()
                    lifecycleScope.launch {
                        val legacyFilesWithoutMeta = selectedFiles.filter { file ->
                            file.name.endsWith(ENCRYPTED_EXTENSION) &&
                            !SecurityUtils.isNewFormat(file) &&
                            fileAdapter?.hiddenFileRepository?.getHiddenFileByPath(file.absolutePath) == null
                        }

                        if (legacyFilesWithoutMeta.isNotEmpty()) {
                            showDecryptionTypeDialog(selectedFiles)
                        } else {
                            fileAdapter?.decryptSelectedFiles()
                        }
                    }
                }
            } else {
                sheetBinding.rowDecrypt.visibility = View.GONE
            }

            bottomSheet.show()
        }
    }

    fun showDecryptionTypeDialog(selectedFiles: List<File>) {
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.expandFully()
        val sheetBinding = BottomSheetFileTypeBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        val checkboxes = listOf(
            sheetBinding.checkboxImage,
            sheetBinding.checkboxVideo,
            sheetBinding.checkboxAudio,
            sheetBinding.checkboxOther
        )

        val mutualExclusiveListener = CompoundButton.OnCheckedChangeListener { button, isChecked ->
            if (isChecked) {
                checkboxes.filter { it != button }.forEach { it.isChecked = false }
            }
            sheetBinding.otherLayer.visibility = if (sheetBinding.checkboxOther.isChecked) View.VISIBLE else View.GONE
            sheetBinding.btnConfirmDecrypt.isEnabled = checkboxes.any { it.isChecked }
        }

        checkboxes.forEach { it.setOnCheckedChangeListener(mutualExclusiveListener) }

        sheetBinding.btnConfirmDecrypt.setOnClickListener {
            if (sheetBinding.checkboxOther.isChecked) {
                val extension = sheetBinding.editExtension.text?.toString()?.trim() ?: ""
                if (extension.isEmpty()) {
                    sheetBinding.editExtension.error = getString(R.string.please_enter_a_file_extension)
                    return@setOnClickListener
                }
                val regex = Regex("""\.\w+""")
                if (!regex.matches(extension)) {
                    sheetBinding.editExtension.error = getString(R.string.invalid_extension_format)
                    return@setOnClickListener
                }
            }

            val selectedType = when {
                sheetBinding.checkboxImage.isChecked -> FileManager.FileType.IMAGE
                sheetBinding.checkboxVideo.isChecked -> FileManager.FileType.VIDEO
                sheetBinding.checkboxAudio.isChecked -> FileManager.FileType.AUDIO
                sheetBinding.checkboxOther.isChecked -> FileManager.FileType.DOCUMENT
                else -> return@setOnClickListener
            }

            bottomSheet.dismiss()
            fileManager.performDecryption(
                selectedFiles,
                forcedType = selectedType,
                forcedExtension = if (sheetBinding.checkboxOther.isChecked) sheetBinding.editExtension.text?.toString()?.trim() else null
            ) {
                refreshCurrentFolder()
                fileAdapter?.exitSelectionMode()
            }
        }

        sheetBinding.btnCancelDecrypt.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
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
                        updateSetKeyBanner(files)
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

    private fun updateSetKeyBanner(files: List<File>) {
        val hasLockedFiles = files.any {
            it.name.endsWith(ENCRYPTED_EXTENSION) &&
                SecurityUtils.isNewFormat(it) &&
                SecurityUtils.readMetadataFromEncryptedFile(this, it) == null
        }
        val shouldShowBanner = hasLockedFiles && !SecurityUtils.hasCustomKey(this)

        if (shouldShowBanner) {
            binding.cardSetKeyBanner.visibility = View.VISIBLE
            binding.cardSetKeyBanner.setOnClickListener {
                SecurityUtils.showSetKeyBottomSheet(
                    activity = this,
                    title = getString(R.string.encryption_key_required_title),
                    subtitle = getString(R.string.encryption_key_required_message),
                    onKeySet = {
                        refreshCurrentFolder()
                    }
                )
            }
        } else {
            binding.cardSetKeyBanner.visibility = View.GONE
        }
    }
    private fun setupClickListeners() {
        binding.fabExpend.setOnClickListener {
            if (isFabOpen) closeFabs()
            else openFabs()
        }
        binding.toolBar.setNavigationOnClickListener {
            handleBackPress()
        }
        binding.swipeLayout.setOnRefreshListener {
            openFolder(currentFolder!!)
        }

        binding.takePhoto.setOnClickListener { checkCameraPermissionAndOpen() }
        binding.addImage.setOnClickListener { openFilePicker("image/*") }
        binding.addVideo.setOnClickListener { openFilePicker("video/*") }
        binding.addAudio.setOnClickListener { openFilePicker("audio/*") }
        binding.addDocument.setOnClickListener { openFilePicker("*/*") }
    }

    private fun checkCameraPermissionAndOpen() {
        closeFabs()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            showCameraPermissionBottomSheet()
        }
    }

    private fun showCameraPermissionBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.expandFully()
        val sheetBinding = BottomSheetConfirmationBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        sheetBinding.dialogIcon.setImageResource(R.drawable.ic_camera)
        sheetBinding.tvDialogTitle.text = getString(R.string.camera_permission_required)
        sheetBinding.tvDialogMessage.text = getString(R.string.camera_permission_desc)
        sheetBinding.btnPositive.text = getString(R.string.grant_permission)
        sheetBinding.btnNegative.text = getString(R.string.cancel)

        sheetBinding.btnPositive.setOnClickListener {
            bottomSheet.dismiss()
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        sheetBinding.btnNegative.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showCameraSettingsBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.expandFully()
        val sheetBinding = BottomSheetConfirmationBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        sheetBinding.dialogIcon.setImageResource(R.drawable.ic_settings)
        sheetBinding.tvDialogTitle.text = getString(R.string.camera_permission_required)
        sheetBinding.tvDialogMessage.text = getString(R.string.camera_permission_settings_desc)
        sheetBinding.btnPositive.text = getString(R.string.settings)
        sheetBinding.btnNegative.text = getString(R.string.cancel)

        sheetBinding.btnPositive.setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }

        sheetBinding.btnNegative.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
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

    private fun openCamera() {
        val photoFile = try {
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("TEMP_PHOTO_", ".jpg", storageDir).apply {
                tempPhotoFile = this
            }
        } catch (e: Exception) {
            null
        }

        photoFile?.also {
            try {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "devs.org.calculator.fileprovider",
                    it
                )
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                closeFabs()
                (application as CalculatorApp).isWaitingForResult = true
                takePhotoLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                (application as CalculatorApp).isWaitingForResult = false
                Toast.makeText(this, getString(R.string.unable_to_open_camera), Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, getString(R.string.unable_to_create_temp_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCameraResult() {
        tempPhotoFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "devs.org.calculator.fileprovider",
                    file
                )
                processSelectedFiles(listOf(uri), isFromCamera = true)
            }
        }
    }

    private fun openFabs() {
        if (!isFabOpen) {
            isFabOpen = true

            binding.takePhoto.visibility = View.VISIBLE
            binding.addImage.visibility = View.VISIBLE
            binding.addVideo.visibility = View.VISIBLE
            binding.addAudio.visibility = View.VISIBLE
            binding.addDocument.visibility = View.VISIBLE

            binding.takePhoto.startAnimation(fabOpen)
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

            binding.takePhoto.startAnimation(fabClose)
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
                    binding.takePhoto.visibility = View.GONE
                    binding.addImage.visibility = View.GONE
                    binding.addVideo.visibility = View.GONE
                    binding.addAudio.visibility = View.GONE
                    binding.addDocument.visibility = View.GONE
                }
            }, 200)
        }
    }

    private fun showFileViewIcons() {
        binding.toolBar.title = currentFolder?.name ?: getString(R.string.hidden_space)
        binding.toolBar.setNavigationIcon(R.drawable.ic_back)
        val hasFiles = (fileAdapter?.itemCount ?: 0) > 0
        binding.toolBar.menu.findItem(R.id.select_all)?.apply {
            isVisible = hasFiles
            setIcon(R.drawable.ic_select_all)
            setTitle(R.string.select_all)
        }
        binding.toolBar.menu.findItem(R.id.options)?.isVisible = false
        binding.toolBar.menu.findItem(R.id.view_settings)?.isVisible = true
        binding.fabExpend.visibility = View.VISIBLE
        binding.takePhoto.visibility = View.GONE
        binding.addImage.visibility = View.GONE
        binding.addVideo.visibility = View.GONE
        binding.addAudio.visibility = View.GONE
        binding.addDocument.visibility = View.GONE
        isFabOpen = false
        binding.fabExpend.setImageResource(R.drawable.ic_add)
        binding.fabExpend.rotation = 0f
    }

    private fun showFileSelectionIcons() {
        val selectedCount = fileAdapter?.getSelectedItemCount() ?: 0
        binding.toolBar.title = getString(R.string.files_selected, selectedCount)
        binding.toolBar.setNavigationIcon(R.drawable.ic_close)
        val isAll = fileAdapter?.isAllSelected() == true
        binding.toolBar.menu.findItem(R.id.select_all)?.apply {
            isVisible = true
            if (isAll) {
                setIcon(R.drawable.ic_deselect_all)
                setTitle(R.string.deselect_all)
            } else {
                setIcon(R.drawable.ic_select_all)
                setTitle(R.string.select_all)
            }
        }
        binding.toolBar.menu.findItem(R.id.options)?.isVisible = true
        binding.toolBar.menu.findItem(R.id.view_settings)?.isVisible = false
        binding.fabExpend.visibility = View.GONE
        binding.takePhoto.visibility = View.GONE
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
                        val headerMeta = if (file.name.endsWith(ENCRYPTED_EXTENSION)) {
                            SecurityUtils.readMetadataFromEncryptedFile(this@ViewFolderActivity, file)
                        } else null
                        val isEncrypted = hiddenFile?.isEncrypted == true || file.name.endsWith(ENCRYPTED_EXTENSION)

                        if (isEncrypted) {
                            val originalExtension = hiddenFile?.originalExtension ?: headerMeta?.originalExtension ?: ".jpg"
                            val originalName = headerMeta?.originalFileName
                            val decryptedFile = if (originalName != null && originalName.isNotEmpty()) {
                                File(file.parentFile, originalName)
                            } else {
                                SecurityUtils.changeFileExtension(file, originalExtension)
                            }

                            if (SecurityUtils.decryptFile(this@ViewFolderActivity, file, decryptedFile)) {
                                if (decryptedFile.exists() && decryptedFile.length() > 0) {
                                    val fileUri = FileManager.FileManager().getContentUriImage(this@ViewFolderActivity, decryptedFile)
                                    if (fileUri != null) {
                                        val result = fileManager.copyFileToNormalDir(fileUri)
                                        if (result != null) {
                                            hiddenFile?.let {
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
        bottomSheetDialog.expandFully()
        bottomSheetDialog.setContentView(bottomSheetView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = FolderSelectionAdapter(folders) { selectedFolder ->
            bottomSheetDialog.dismiss()
            onFolderSelected(selectedFolder)
        }

        bottomSheetDialog.show()
    }
}
