package devs.org.calculator.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import devs.org.calculator.R
import devs.org.calculator.activities.EditNotesActivity
import devs.org.calculator.activities.PreviewActivity
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileEntity
import devs.org.calculator.database.HiddenFileRepository
import devs.org.calculator.databinding.BottomSheetFileTypeBinding
import devs.org.calculator.databinding.ListItemFileBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FolderManager
import devs.org.calculator.utils.SecurityUtils
import devs.org.calculator.utils.SecurityUtils.getDecryptedPreviewFile
import devs.org.calculator.utils.SecurityUtils.getUriForPreviewFile
import devs.org.calculator.utils.expandFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class FileAdapter(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val currentFolder: File,
    private var showFileName: Boolean,
    private var cornerRadius: Int = 10,
    private val onFolderLongClick: (Boolean) -> Unit,
) : ListAdapter<File, FileAdapter.FilesViewHolder>(FileDiffCallback()) {

    private var filesOperationCallback: WeakReference<FilesOperationCallback>? = null
    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun updateViewSettings(showFileName: Boolean, cornerRadius: Int) {
        this.showFileName = showFileName
        this.cornerRadius = cornerRadius
        notifyItemRangeChanged(0, itemCount, "VIEW_SETTINGS_CHANGED")
    }

    interface FilesOperationCallback {
        fun onFileDeleted(file: File)
        fun onFileRenamed(oldFile: File, newFile: File)
        fun onRefreshNeeded()
        fun onSelectionModeChanged(isSelectionMode: Boolean, selectedCount: Int)
        fun onSelectionCountChanged(selectedCount: Int)
    }

    fun setFilesOperationCallback(callback: FilesOperationCallback?) {
        filesOperationCallback = callback?.let { WeakReference(it) }
    }

    val hiddenFileRepository: HiddenFileRepository by lazy {
        HiddenFileRepository(AppDatabase.getDatabase(context).hiddenFileDao())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilesViewHolder {
        val binding = ListItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: FilesViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                if (payload == "VIEW_SETTINGS_CHANGED") {
                    holder.updateViewSettingsOnly()
                }
            }
        }
    }

    inner class FilesViewHolder(private val binding: ListItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentFilePath: String? = null

        fun updateViewSettingsOnly() {
            binding.cardView.radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                cornerRadius.toFloat(),
                context.resources.displayMetrics
            )
            binding.fileNameTextView.visibility = if (showFileName) View.VISIBLE else View.GONE
            binding.shade.visibility = if (showFileName) View.VISIBLE else View.GONE
        }

        @SuppressLint("FileEndsWithExt")
        fun bind(file: File) {
            val position = bindingAdapterPosition

            updateViewSettingsOnly()
            
            if (currentFilePath == file.absolutePath) {
                updateSelectionUI(selectedItems.contains(position))
                return
            }
            currentFilePath = file.absolutePath

            // Reset state
            binding.fileIconImageView.setImageDrawable(null)
            binding.videoPlay.visibility = View.GONE
            binding.loadingProgress.visibility = View.VISIBLE
            
            lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    var currentFileData = withContext(Dispatchers.IO) {
                        hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    }

                    if (currentFileData == null && file.name.endsWith(FileManager.ENCRYPTED_EXTENSION)) {
                        val headerMeta = withContext(Dispatchers.IO) {
                            SecurityUtils.readMetadataFromEncryptedFile(context, file)
                        }
                        if (headerMeta != null) {
                            val newEntity = HiddenFileEntity(
                                filePath = file.absolutePath,
                                fileName = headerMeta.originalFileName,
                                encryptedFileName = file.name,
                                fileType = headerMeta.fileType,
                                originalExtension = headerMeta.originalExtension,
                                isEncrypted = true
                            )
                            withContext(Dispatchers.IO) {
                                hiddenFileRepository.insertHiddenFile(newEntity)
                            }
                            currentFileData = newEntity
                        }
                    }

                    val isEncryptedFileWithoutKey = if (file.name.endsWith(FileManager.ENCRYPTED_EXTENSION)) {
                        if (SecurityUtils.isNewFormat(file)) {
                            SecurityUtils.readMetadataFromEncryptedFile(context, file) == null
                        } else {
                            false
                        }
                    } else {
                        false
                    }

                    val currentFileType = currentFileData?.fileType
                        ?: FileManager(context, lifecycleOwner).getFileType(file)

                    val isCurrentFileEncrypted =
                        currentFileData?.isEncrypted ?: (file.extension == "enc")

                    setupClickListeners(file, currentFileType, isEncryptedFileWithoutKey)
                    setupDisplay(file, currentFileType, isCurrentFileEncrypted, currentFileData, isEncryptedFileWithoutKey)

                    binding.fileNameTextView.text = if (isEncryptedFileWithoutKey) {
                        context.getString(R.string.set_key_to_view_file)
                    } else if (isCurrentFileEncrypted) {
                        currentFileData?.fileName ?: file.name
                    } else {
                        file.name
                    }
                    binding.fileNameTextView.visibility =
                        if (showFileName || isEncryptedFileWithoutKey) View.VISIBLE else View.GONE
                    binding.shade.visibility = if (showFileName || isEncryptedFileWithoutKey) View.VISIBLE else View.GONE

                    if (position != RecyclerView.NO_POSITION) {
                        updateSelectionUI(selectedItems.contains(position))
                    }
                    binding.encrypted.visibility =
                        if (isCurrentFileEncrypted) View.VISIBLE else View.GONE

                } catch (e: Exception) {
                    Log.e("FileAdapter", "Error in bind: ${e.message}")
                }
            }
        }
        
        private suspend fun setupDisplay(
            file: File,
            type: FileManager.FileType,
            isCurrentFileEncrypted: Boolean,
            metadata: HiddenFileEntity?,
            isEncryptedFileWithoutKey: Boolean = false,
        ) {
            if (isEncryptedFileWithoutKey) {
                binding.loadingProgress.visibility = View.GONE
                binding.videoPlay.visibility = View.GONE
                binding.fileIconImageView.setPadding(28, 28, 28, 28)
                binding.fileIconImageView.setImageResource(R.drawable.encrypted)
                return
            }

            when (type) {
                FileManager.FileType.IMAGE, FileManager.FileType.VIDEO -> {
                    binding.videoPlay.visibility = if (type == FileManager.FileType.VIDEO) View.VISIBLE else View.GONE
                    binding.fileIconImageView.setPadding(0, 0, 0, 0)

                    if (isCurrentFileEncrypted) {
                        loadEncryptedThumbnail(metadata, isVideo = (type == FileManager.FileType.VIDEO))
                    } else {
                        binding.loadingProgress.visibility = View.GONE
                        Glide.with(context)
                            .load(file)
                            .centerCrop()
                            .into(binding.fileIconImageView)
                    }
                }

                FileManager.FileType.AUDIO -> {
                    binding.loadingProgress.visibility = View.GONE
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_audio)
                }

                FileManager.FileType.NOTE -> {
                    binding.loadingProgress.visibility = View.GONE
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_sticky_note)
                }

                FileManager.FileType.PDF -> {
                    binding.loadingProgress.visibility = View.GONE
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_pdf)
                }

                else -> {
                    binding.loadingProgress.visibility = View.GONE
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_document)
                }
            }
        }
        
        private suspend fun loadEncryptedThumbnail(
            metadata: HiddenFileEntity?,
            isVideo: Boolean,
        ) {
            if (metadata == null) {
                showEncryptedIcon()
                return
            }

            // Show loading indicator while decrypting preview
            binding.loadingProgress.visibility = View.VISIBLE
            binding.fileIconImageView.setImageDrawable(null)

            val uri: Uri? = withContext(Dispatchers.IO) {
                try {
                    val decryptedFile = getDecryptedPreviewFile(context, metadata)
                    if (decryptedFile != null && decryptedFile.exists() && decryptedFile.length() > 0) {
                        getUriForPreviewFile(context, decryptedFile)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("FileAdapter", "Error decrypting preview: ${e.message}")
                    null
                }
            }

            binding.loadingProgress.visibility = View.GONE

            if (uri != null) {
                try {
                    Glide.with(context)
                        .load(uri)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .error(R.drawable.encrypted)
                        .into(binding.fileIconImageView)
                } catch (e: Exception) {
                    Log.e("FileAdapter", "Glide error for encrypted file: ${e.message}")
                    showEncryptedIcon()
                }
            } else {
                showEncryptedIcon()
            }
        }

        private fun showEncryptedIcon() {
            binding.loadingProgress.visibility = View.GONE
            binding.fileIconImageView.setPadding(25, 25, 25, 25)
            binding.fileIconImageView.setImageResource(R.drawable.encrypted)
        }

        private fun updateSelectionUI(isSelected: Boolean) {
            binding.selectedLayer.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.selected.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        private fun openFile(file: File, fileType: FileManager.FileType) {
            if (!file.exists()) {
                Toast.makeText(context, context.getString(R.string.file_no_longer_exists), Toast.LENGTH_SHORT).show()
                return
            }

            when (fileType) {
                FileManager.FileType.AUDIO -> openAudioFile(file)
                FileManager.FileType.NOTE -> openNoteFile(file)
                FileManager.FileType.PDF -> openDocumentFile(file)
                FileManager.FileType.IMAGE, FileManager.FileType.VIDEO -> {
                    lifecycleOwner.lifecycleScope.launch {
                        try {
                            val hiddenFile =
                                hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                            if (hiddenFile?.isEncrypted == true || file.extension == FileManager.ENCRYPTED_EXTENSION) {
                                if (file.extension == FileManager.ENCRYPTED_EXTENSION && !SecurityUtils.isNewFormat(file)) {
                                    val activity = (context as? Activity) ?: (lifecycleOwner as? Activity)
                                    if (activity != null) {
                                        SecurityUtils.showMigrationBottomSheet(activity, listOf(file)) {
                                            notifyDataSetChanged()
                                        }
                                    }
                                } else {
                                    // Use the cached preview if available
                                    val tempFile = withContext(Dispatchers.IO) {
                                        if (hiddenFile != null) {
                                            SecurityUtils.getDecryptedPreviewFile(context, hiddenFile)
                                        } else {
                                            val tmp = File(context.cacheDir, "preview_${file.name}")
                                            if (SecurityUtils.decryptFile(context, file, tmp)) tmp else null
                                        }
                                    }

                                    if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
                                        val fileTypeString = if (fileType == FileManager.FileType.IMAGE) context.getString(R.string.image) else context.getString(R.string.video)
                                        val intent = Intent(context, PreviewActivity::class.java).apply {
                                            putExtra("type", fileTypeString)
                                            putExtra("folder", currentFolder.toString())
                                            putExtra("position", bindingAdapterPosition)
                                            putExtra("isEncrypted", true)
                                            putExtra("tempFile", tempFile.absolutePath)
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context,
                                            context.getString(R.string.failed_to_decrypt_file_for_preview), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                openInPreview(fileType)
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context,
                                context.getString(R.string.error_preparing_file_for_preview), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                FileManager.FileType.DOCUMENT -> openDocumentFile(file)
                else -> openDocumentFile(file)
            }
        }

        private fun showDecryptionTypeDialog(file: File) {
            val bottomSheet = BottomSheetDialog(context)
            bottomSheet.expandFully()
            val sheetBinding = BottomSheetFileTypeBinding.inflate(LayoutInflater.from(context))
            bottomSheet.setContentView(sheetBinding.root)

            val checkboxes = listOf(
                sheetBinding.checkboxImage,
                sheetBinding.checkboxVideo,
                sheetBinding.checkboxAudio,
                sheetBinding.checkboxOther
            )

            val mutualExclusiveListener = android.widget.CompoundButton.OnCheckedChangeListener { button, isChecked ->
                if (isChecked) {
                    checkboxes.filter { it != button }.forEach { it.isChecked = false }
                }
                sheetBinding.otherLayer.visibility = if (sheetBinding.checkboxOther.isChecked) View.VISIBLE else View.GONE
                sheetBinding.btnConfirmDecrypt.isEnabled = checkboxes.any { it.isChecked }
            }

            checkboxes.forEach { it.setOnCheckedChangeListener(mutualExclusiveListener) }

            sheetBinding.btnConfirmDecrypt.setOnClickListener {
                val selectedType = when {
                    sheetBinding.checkboxImage.isChecked -> FileManager.FileType.IMAGE
                    sheetBinding.checkboxVideo.isChecked -> FileManager.FileType.VIDEO
                    sheetBinding.checkboxAudio.isChecked -> FileManager.FileType.AUDIO
                    sheetBinding.checkboxOther.isChecked -> FileManager.FileType.DOCUMENT
                    else -> return@setOnClickListener
                }
                bottomSheet.dismiss()
                performDecryptionWithType(file, selectedType)
            }

            sheetBinding.btnCancelDecrypt.setOnClickListener {
                bottomSheet.dismiss()
            }

            bottomSheet.show()
        }

        private fun openInPreview(fileType: FileManager.FileType) {
            val fileTypeString = when (fileType) {
                FileManager.FileType.IMAGE -> context.getString(R.string.image)
                FileManager.FileType.VIDEO -> context.getString(R.string.video)
                else -> "unknown"
            }
            val intent = Intent(context, PreviewActivity::class.java).apply {
                putExtra("type", fileTypeString)
                putExtra("folder", currentFolder.toString())
                putExtra("position", bindingAdapterPosition)
            }
            context.startActivity(intent)
        }

        private fun openAudioFile(file: File) {
            val fileType = FileManager(context, lifecycleOwner).getFileType(file)
            try {
                val fileTypeString = when (fileType) {
                    FileManager.FileType.IMAGE -> context.getString(R.string.image)
                    FileManager.FileType.VIDEO -> context.getString(R.string.video)
                    else -> "unknown"
                }
                val intent = Intent(context, PreviewActivity::class.java).apply {
                    putExtra("type", fileTypeString)
                    putExtra("folder", currentFolder.toString())
                    putExtra("position", bindingAdapterPosition)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.no_audio_player_found), Toast.LENGTH_SHORT).show()
            }
        }

        private fun openNoteFile(file: File) {
            val intent = Intent(context, EditNotesActivity::class.java).apply {
                putExtra("note_path", file.absolutePath)
            }
            context.startActivity(intent)
        }

        private fun openDocumentFile(file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "devs.org.calculator.fileprovider",
                    file
                )
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    putExtra("folder", currentFolder.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    context.getString(R.string.no_suitable_app_found_to_open_this_document),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun setupClickListeners(
            file: File,
            fileType: FileManager.FileType,
            isEncryptedWithoutKey: Boolean = false,
        ) {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                if (isSelectionMode) {
                    toggleSelection(position)
                } else if (isEncryptedWithoutKey) {
                    val activity = (context as? Activity) ?: (lifecycleOwner as? Activity)
                    if (activity != null) {
                        SecurityUtils.showSetKeyBottomSheet(
                            activity = activity,
                            title = context.getString(R.string.encryption_key_required_title),
                            subtitle = context.getString(R.string.encryption_key_required_message),
                            onKeySet = {
                                notifyDataSetChanged()
                            }
                        )
                    }
                } else {
                    openFile(file, fileType)
                }
            }

            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                if (!isSelectionMode) {
                    enterSelectionMode()
                    toggleSelection(position)
                }
                true
            }
        }

        private fun toggleSelection(position: Int) {
            if (selectedItems.contains(position)) {
                selectedItems.remove(position)
                if (selectedItems.isEmpty()) exitSelectionMode()
            } else {
                selectedItems.add(position)
            }
            onSelectionCountChanged(selectedItems.size)
            notifyItemChanged(position)
        }

        private fun performDecryptionWithType(file: File, fileType: FileManager.FileType) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val extension = when (fileType) {
                        FileManager.FileType.IMAGE -> ".jpg"
                        FileManager.FileType.VIDEO -> ".mp4"
                        FileManager.FileType.AUDIO -> ".mp3"
                        FileManager.FileType.NOTE -> ".txt"
                        FileManager.FileType.PDF -> ".pdf"
                        else -> ".txt"
                    }

                    val decryptedFile = SecurityUtils.changeFileExtension(file, extension)
                    val success = withContext(Dispatchers.IO) {
                        SecurityUtils.decryptFile(context, file, decryptedFile)
                    }

                    if (success && decryptedFile.exists() && decryptedFile.length() > 0) {
                        hiddenFileRepository.insertHiddenFile(
                            HiddenFileEntity(
                                filePath = decryptedFile.absolutePath,
                                fileName = decryptedFile.name,
                                encryptedFileName = file.name,
                                fileType = fileType,
                                originalExtension = extension,
                                isEncrypted = false
                            )
                        )
                        when (fileType) {
                            FileManager.FileType.IMAGE, FileManager.FileType.VIDEO -> {
                                val intent = Intent(context, PreviewActivity::class.java).apply {
                                    putExtra("type", if (fileType == FileManager.FileType.IMAGE) "image" else "video")
                                    putExtra("folder", currentFolder.toString())
                                    putExtra("position", bindingAdapterPosition)
                                    putExtra("isEncrypted", false)
                                    putExtra("file", decryptedFile.absolutePath)
                                }
                                context.startActivity(intent)
                            }
                            FileManager.FileType.AUDIO -> openAudioFile(decryptedFile)
                            FileManager.FileType.NOTE -> openNoteFile(decryptedFile)
                            FileManager.FileType.PDF -> openDocumentFile(decryptedFile)
                            else -> openDocumentFile(decryptedFile)
                        }
                        file.delete()
                    } else {
                        decryptedFile.delete()
                        Toast.makeText(context, "Failed to decrypt file", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, "Error decrypting file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun isInSelectionMode(): Boolean = isSelectionMode

    fun onBackPressed(): Boolean {
        return if (isSelectionMode) {
            exitSelectionMode()
            true
        } else {
            false
        }
    }

    private fun onSelectionCountChanged(count: Int) {
        filesOperationCallback?.get()?.onSelectionCountChanged(count)
    }

    fun enterSelectionMode() {
        if (!isSelectionMode) {
            isSelectionMode = true
            notifySelectionModeChange()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun exitSelectionMode() {
        if (isSelectionMode) {
            isSelectionMode = false
            val hadMany = selectedItems.size > 20
            val oldPositions = selectedItems.toSet()
            selectedItems.clear()
            if (hadMany) {
                notifyDataSetChanged()
            } else {
                oldPositions.forEach { position -> notifyItemChanged(position) }
            }
            notifySelectionModeChange()
            onSelectionCountChanged(0)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll() {
        if (itemCount == 0) return
        isSelectionMode = true
        selectedItems.clear()
        for (i in 0 until itemCount) {
            selectedItems.add(i)
        }
        notifyDataSetChanged()
        notifySelectionModeChange()
        onSelectionCountChanged(selectedItems.size)
    }

    fun deselectAll() {
        exitSelectionMode()
    }

    fun isAllSelected(): Boolean {
        return itemCount > 0 && selectedItems.size == itemCount
    }

    fun getSelectedItemCount(): Int = selectedItems.size

    fun toggleSelectAll() {
        if (isAllSelected()) {
            deselectAll()
        } else {
            selectAll()
        }
    }

    private fun notifySelectionModeChange() {
        filesOperationCallback?.get()?.onSelectionModeChanged(isSelectionMode, selectedItems.size)
        onFolderLongClick(isSelectionMode)
    }

    fun cleanup() {
        try {
            if (!fileExecutor.isShutdown) fileExecutor.shutdown()
        } catch (_: Exception) {
        }
        filesOperationCallback?.clear()
        filesOperationCallback = null
    }

    fun getSelectedItems(): List<File> {
        return selectedItems.mapNotNull { position ->
            if (position < itemCount) getItem(position) else null
        }
    }

    fun encryptSelectedFiles() {
        val selectedFiles = getSelectedItems()
        if (selectedFiles.isEmpty()) return

        if (!SecurityUtils.hasCustomKey(context)) {
            val activity = (context as? Activity) ?: (lifecycleOwner as? Activity)
            if (activity != null) {
                SecurityUtils.showSetKeyBottomSheet(
                    activity = activity,
                    title = context.getString(R.string.encryption_key_required_title),
                    subtitle = context.getString(R.string.encryption_key_required_message),
                    onKeySet = {
                        encryptSelectedFiles()
                    }
                )
                return
            }
        }

        DialogUtil(context).showMaterialDialog(
            title = context.getString(R.string.encrypt_files),
            message = context.getString(R.string.encryption_disclaimer),
            positiveButtonText = context.getString(R.string.encrypt),
            negativeButtonText = context.getString(R.string.cancel),
            callback = object : DialogUtil.DialogCallback {
                override fun onPositiveButtonClicked() {
                    FileManager(context, lifecycleOwner).performEncryption(selectedFiles) {
                        updateItemsAfterEncryption(it)
                    }
                }
                override fun onNegativeButtonClicked() {}
            }
        )
    }

    fun decryptSelectedFiles() {
        val selectedFiles = getSelectedItems()
        if (selectedFiles.isEmpty()) return

        val legacyFiles = selectedFiles.filter { it.name.endsWith(FileManager.ENCRYPTED_EXTENSION) && !SecurityUtils.isNewFormat(it) }
        val activity = (context as? Activity) ?: (lifecycleOwner as? Activity)

        if (legacyFiles.isNotEmpty() && activity != null) {
            SecurityUtils.showMigrationBottomSheet(activity, legacyFiles) {
                notifyDataSetChanged()
                exitSelectionMode()
            }
            return
        }

        val hasLockedEnc3Files = selectedFiles.any {
            SecurityUtils.isNewFormat(it) && SecurityUtils.readMetadataFromEncryptedFile(context, it) == null
        }
        if (hasLockedEnc3Files && !SecurityUtils.hasCustomKey(context)) {
            val activity = (context as? Activity) ?: (lifecycleOwner as? Activity)
            if (activity != null) {
                SecurityUtils.showSetKeyBottomSheet(
                    activity = activity,
                    title = context.getString(R.string.encryption_key_required_title),
                    subtitle = context.getString(R.string.encryption_key_required_message),
                    onKeySet = {
                        decryptSelectedFiles()
                    }
                )
                return
            }
        }

        DialogUtil(context).showMaterialDialog(
            title = context.getString(R.string.decrypt_files),
            message = context.getString(R.string.decryption_disclaimer),
            positiveButtonText = context.getString(R.string.decrypt),
            negativeButtonText = context.getString(R.string.cancel),
            callback = object : DialogUtil.DialogCallback {
                override fun onPositiveButtonClicked() {
                    FileManager(context, lifecycleOwner).performDecryption(selectedFiles) {
                        updateItemsAfterDecryption(it)
                    }
                }
                override fun onNegativeButtonClicked() {}
            }
        )
    }

    fun updateItemsAfterEncryption(encryptedFiles: Map<File, File>) {
        val currentList = FolderManager().getFilesInFolder(currentFolder)
        val updatedList = currentList.map { file -> encryptedFiles[file] ?: file }.toMutableList()
        selectedItems.clear()
        exitSelectionMode()
        submitList(updatedList)
        mainHandler.postDelayed({ filesOperationCallback?.get()?.onRefreshNeeded() }, 20)
    }

    fun updateItemsAfterDecryption(decryptedFiles: Map<File, File>) {
        val currentList = FolderManager().getFilesInFolder(currentFolder)
        val updatedList = currentList.map { file -> decryptedFiles[file] ?: file }.toMutableList()
        selectedItems.clear()
        exitSelectionMode()
        submitList(updatedList)
        mainHandler.postDelayed({ filesOperationCallback?.get()?.onRefreshNeeded() }, 20)
    }
}