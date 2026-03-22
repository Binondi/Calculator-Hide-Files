package devs.org.calculator.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.activities.EditNotesActivity
import devs.org.calculator.activities.PreviewActivity
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileEntity
import devs.org.calculator.database.HiddenFileRepository
import devs.org.calculator.databinding.ListItemFileBinding
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FolderManager
import devs.org.calculator.utils.SecurityUtils
import devs.org.calculator.utils.SecurityUtils.getDecryptedPreviewFile
import devs.org.calculator.utils.SecurityUtils.getUriForPreviewFile
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
    private val showFileName: Boolean,
    private val onFolderLongClick: (Boolean) -> Unit,
) : ListAdapter<File, FileAdapter.FilesViewHolder>(FileDiffCallback()) {

    private var filesOperationCallback: WeakReference<FilesOperationCallback>? = null
    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

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

    inner class FilesViewHolder(private val binding: ListItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("FileEndsWithExt")
        fun bind(file: File) {
            val position = adapterPosition

            // Show a placeholder immediately so the cell isn't blank while loading
            showLoadingPlaceholder()

            lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    val currentFileData = withContext(Dispatchers.IO) {
                        hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    }

                    val currentFileType = currentFileData?.fileType
                        ?: FileManager(context, lifecycleOwner).getFileType(file)

                    val isCurrentFileEncrypted =
                        currentFileData?.isEncrypted ?: (file.extension == "enc")

                    setupClickListeners(file, currentFileType)

                    // setupDisplay is now a suspend fun — it moves IO work off the main thread
                    setupDisplay(file, currentFileType, isCurrentFileEncrypted, currentFileData)

                    binding.fileNameTextView.text =
                        if (isCurrentFileEncrypted) currentFileData?.fileName else file.name
                    binding.fileNameTextView.visibility =
                        if (showFileName) View.VISIBLE else View.GONE
                    binding.shade.visibility = if (showFileName) View.VISIBLE else View.GONE

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

        /** Shows a generic icon immediately while async work is in progress */
        private fun showLoadingPlaceholder() {
            binding.videoPlay.visibility = View.GONE
            binding.fileIconImageView.setPadding(25, 25, 25, 25)
            binding.fileIconImageView.setImageResource(R.drawable.encrypted)
        }

        /**
         * Suspend version of setupDisplay.
         * Heavy work (decryption) is dispatched to IO; only Glide calls happen on Main.
         */
        private suspend fun setupDisplay(
            file: File,
            type: FileManager.FileType,
            isCurrentFileEncrypted: Boolean,
            metadata: HiddenFileEntity?,
        ) {
            when (type) {
                FileManager.FileType.IMAGE -> {
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(0, 0, 0, 0)

                    if (isCurrentFileEncrypted) {
                        loadEncryptedThumbnail(metadata, isVideo = false)
                    } else {
                        Glide.with(context)
                            .load(file)
                            .centerCrop()
                            .into(binding.fileIconImageView)
                    }
                }

                FileManager.FileType.VIDEO -> {
                    binding.fileIconImageView.setPadding(0, 0, 0, 0)
                    binding.videoPlay.visibility = View.VISIBLE

                    if (isCurrentFileEncrypted) {
                        loadEncryptedThumbnail(metadata, isVideo = true)
                    } else {
                        Glide.with(context)
                            .load(file)
                            .centerCrop()
                            .into(binding.fileIconImageView)
                    }
                }

                FileManager.FileType.AUDIO -> {
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_audio)
                }

                FileManager.FileType.NOTE -> {
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_sticky_note)
                }

                FileManager.FileType.PDF -> {
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_pdf)
                }

                else -> {
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_document)
                }
            }
        }

        /**
         * Decrypts the file on IO, then loads the result into Glide on Main.
         * Safe to call from a coroutine already on Main.
         */
        private suspend fun loadEncryptedThumbnail(
            metadata: HiddenFileEntity?,
            isVideo: Boolean,
        ) {
            if (metadata == null) {
                // No DB record — nothing to decrypt, show fallback icon
                showEncryptedIcon()
                return
            }

            // Decrypt off the main thread
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

            // Back on Main — load into Glide (or show fallback)
            if (uri != null) {
                try {
                    Glide.with(context)
                        .load(uri)
                        .centerCrop()
                        // Videos: skip disk cache so stale frames don't linger
                        .diskCacheStrategy(
                            if (isVideo) DiskCacheStrategy.NONE else DiskCacheStrategy.ALL
                        )
                        .skipMemoryCache(isVideo)
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
                                if (file.extension == FileManager.ENCRYPTED_EXTENSION && hiddenFile == null) {
                                    showDecryptionTypeDialog(file)
                                } else {
                                    val tempFile =
                                        withContext(Dispatchers.IO) {
                                            val tmp = File(context.cacheDir, "preview_${file.name}")
                                            if (SecurityUtils.decryptFile(context, file, tmp)) tmp else null
                                        }

                                    if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
                                        val fileTypeString = when (fileType) {
                                            FileManager.FileType.IMAGE -> context.getString(R.string.image)
                                            FileManager.FileType.VIDEO -> context.getString(R.string.video)
                                            else -> "unknown"
                                        }
                                        val intent = Intent(context, PreviewActivity::class.java).apply {
                                            putExtra("type", fileTypeString)
                                            putExtra("folder", currentFolder.toString())
                                            putExtra("position", adapterPosition)
                                            putExtra("isEncrypted", true)
                                            putExtra("tempFile", tempFile.absolutePath)
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, "Failed to decrypt file for preview", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                openInPreview(fileType)
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "Error preparing file for preview", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                FileManager.FileType.DOCUMENT -> openDocumentFile(file)
                else -> openDocumentFile(file)
            }
        }

        private fun showDecryptionTypeDialog(file: File) {
            val options = arrayOf("Image", "Video", "Audio", "Note", "PDF")
            MaterialAlertDialogBuilder(context)
                .setTitle("Select File Type")
                .setMessage("Please select the type of file to decrypt")
                .setItems(options) { _, which ->
                    val selectedType = when (which) {
                        0 -> FileManager.FileType.IMAGE
                        1 -> FileManager.FileType.VIDEO
                        2 -> FileManager.FileType.AUDIO
                        3 -> FileManager.FileType.NOTE
                        4 -> FileManager.FileType.PDF
                        else -> FileManager.FileType.DOCUMENT
                    }
                    performDecryptionWithType(file, selectedType)
                }
                .setNegativeButton("Cancel", null)
                .show()
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
                putExtra("position", adapterPosition)
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
                    putExtra("position", adapterPosition)
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
                Toast.makeText(
                    context,
                    context.getString(R.string.no_suitable_app_found_to_open_this_document),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun setupClickListeners(file: File, fileType: FileManager.FileType) {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                if (isSelectionMode) toggleSelection(position) else openFile(file, fileType)
            }

            itemView.setOnLongClickListener {
                val position = adapterPosition
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
                                    putExtra("position", adapterPosition)
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
            selectedItems.forEach { position -> notifyItemChanged(position) }
            selectedItems.clear()
            notifySelectionModeChange()
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

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.encrypt_files))
            .setMessage(context.getString(R.string.encryption_disclaimer))
            .setPositiveButton(context.getString(R.string.encrypt)) { _, _ ->
                FileManager(context, lifecycleOwner).performEncryption(selectedFiles) {
                    updateItemsAfterEncryption(it)
                }
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    fun decryptSelectedFiles() {
        val selectedFiles = getSelectedItems()
        if (selectedFiles.isEmpty()) return

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.decrypt_files))
            .setMessage(context.getString(R.string.decryption_disclaimer))
            .setPositiveButton(context.getString(R.string.decrypt)) { _, _ ->
                FileManager(context, lifecycleOwner).performDecryption(selectedFiles) {
                    updateItemsAfterDecryption(it)
                }
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
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