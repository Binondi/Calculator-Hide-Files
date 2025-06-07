package devs.org.calculator.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.launch
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


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): FilesViewHolder {
        val binding =
            ListItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilesViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: FilesViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))

    }


    inner class FilesViewHolder(private val binding: ListItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("FileEndsWithExt")
        fun bind(file: File) {
            val position = adapterPosition
            lifecycleOwner.lifecycleScope.launch {
                try {
                    hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    val currentFileData =
                        hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    val currentFileType = currentFileData?.fileType ?: FileManager(
                        context,
                        lifecycleOwner
                    ).getFileType(file)


                    val isCurrentFileEncrypted = currentFileData?.isEncrypted ?: file.endsWith(
                        SecurityUtils.ENCRYPTED_EXTENSION
                    )

                    setupClickListeners(file, currentFileType)
                    setupDisplay(
                        file,
                        currentFileType,
                        isCurrentFileEncrypted,
                        currentFileData
                    )
                    binding.fileNameTextView.text = if (isCurrentFileEncrypted) currentFileData?.fileName else file.name
                    binding.fileNameTextView.visibility =
                        if (showFileName) View.VISIBLE else View.GONE
                    binding.shade.visibility = if (showFileName) View.VISIBLE else View.GONE

                    if (position != RecyclerView.NO_POSITION) {
                        val isSelected = selectedItems.contains(position)
                        updateSelectionUI(isSelected)
                    }
                    binding.encrypted.visibility =
                        if (isCurrentFileEncrypted) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    Log.e("FileAdapter", "Error in bind: ${e.message}")
                }
            }
        }

        private fun openFile(file: File, fileType: FileManager.FileType) {
            if (!file.exists()) {
                Toast.makeText(context,
                    context.getString(R.string.file_no_longer_exists), Toast.LENGTH_SHORT).show()
                return
            }

            when (fileType) {
                FileManager.FileType.AUDIO -> openAudioFile(file)
                FileManager.FileType.IMAGE, FileManager.FileType.VIDEO -> {
                    lifecycleOwner.lifecycleScope.launch {
                        try {
                            val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                            if (hiddenFile?.isEncrypted == true || file.extension == FileManager.ENCRYPTED_EXTENSION) {
                                if (file.extension == FileManager.ENCRYPTED_EXTENSION && hiddenFile == null) {
                                    showDecryptionTypeDialog(file)
                                } else {
                                    val tempFile = File(context.cacheDir, "preview_${file.name}")

                                    if (SecurityUtils.decryptFile(context, file, tempFile)) {
                                        if (tempFile.exists() && tempFile.length() > 0) {
                                            mainHandler.post {
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
                                            }
                                        } else {
                                            mainHandler.post {
                                                Toast.makeText(context, "Failed to prepare file for preview", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        mainHandler.post {
                                            Toast.makeText(context, "Failed to decrypt file for preview", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                openInPreview(fileType)
                            }
                        } catch (_: Exception) {
                            mainHandler.post {
                                Toast.makeText(context, "Error preparing file for preview", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                FileManager.FileType.DOCUMENT -> openDocumentFile(file)
                else -> openDocumentFile(file)
            }
        }

        private fun showDecryptionTypeDialog(file: File) {
            val options = arrayOf("Image", "Video", "Audio")
            MaterialAlertDialogBuilder(context)
                .setTitle("Select File Type")
                .setMessage("Please select the type of file to decrypt")
                .setItems(options) { _, which ->
                    val selectedType = when (which) {
                        0 -> FileManager.FileType.IMAGE
                        1 -> FileManager.FileType.VIDEO
                        2 -> FileManager.FileType.AUDIO
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
            val fileType = FileManager(context,lifecycleOwner).getFileType(file)
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
                Toast.makeText(
                    context,
                    context.getString(R.string.no_audio_player_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun openDocumentFile(file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    putExtra("folder", currentFolder.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (_: Exception) {

                Toast.makeText(
                    context,
                    context.getString(R.string.no_suitable_app_found_to_open_this_document),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun setupDisplay(
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
                        try {
                            val decryptedFile = getDecryptedPreviewFile(context, metadata!!)
                            if (decryptedFile != null && decryptedFile.exists() && decryptedFile.length() > 0) {
                                val uri = getUriForPreviewFile(context, decryptedFile)
                                if (uri != null) {
                                    Glide.with(context)
                                        .load(uri)
                                        .centerCrop()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .skipMemoryCache(false)
                                        .error(R.drawable.encrypted)
                                        .into(binding.fileIconImageView)
                                } else {
                                    showEncryptedIcon()
                                }
                            } else {
                                showEncryptedIcon()
                            }
                        } catch (e: Exception) {
                            Log.e("FileAdapter", "Error displaying encrypted image: ${e.message}")
                            showEncryptedIcon()
                        }
                    } else {
                        Glide.with(context)
                            .load(file)
                            .into(binding.fileIconImageView)
                    }
                }

                FileManager.FileType.VIDEO -> {
                    binding.fileIconImageView.setPadding(0, 0, 0, 0)
                    binding.videoPlay.visibility = View.VISIBLE
                    if (isCurrentFileEncrypted) {
                        try {
                            val decryptedFile = getDecryptedPreviewFile(context, metadata!!)
                            if (decryptedFile != null && decryptedFile.exists() && decryptedFile.length() > 0) {
                                val uri = getUriForPreviewFile(context, decryptedFile)
                                if (uri != null) {
                                    Glide.with(context)
                                        .load(uri)
                                        .centerCrop()
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .skipMemoryCache(true)
                                        .error(R.drawable.encrypted)
                                        .into(binding.fileIconImageView)
                                    binding.fileIconImageView.setPadding(0, 0, 0, 0)
                                } else {
                                    showEncryptedIcon()
                                }
                            } else {
                                showEncryptedIcon()
                            }
                        } catch (e: Exception) {
                            Log.e("FileAdapter", "Error displaying encrypted video: ${e.message}")
                            showEncryptedIcon()
                        }
                    } else {
                        Glide.with(context)
                            .load(file)
                            .into(binding.fileIconImageView)
                    }
                }

                FileManager.FileType.AUDIO -> {
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_audio)

                }

                else -> {
                    binding.videoPlay.visibility = View.GONE
                    binding.fileIconImageView.setPadding(25, 25, 25, 25)
                    binding.fileIconImageView.setImageResource(R.drawable.ic_document)
                }
            }
        }

        private fun showEncryptedIcon() {
            binding.fileIconImageView.setImageResource(R.drawable.encrypted)
        }


        private fun updateSelectionUI(isSelected: Boolean) {
            binding.selectedLayer.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.selected.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        private fun setupClickListeners(file: File, fileType: FileManager.FileType) {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                if (isSelectionMode) {
                    toggleSelection(position)
                } else {
                    openFile(file,fileType)
                }
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
                if (selectedItems.isEmpty()) {
                    exitSelectionMode()
                }
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
                        else -> ".txt"
                    }

                    val decryptedFile = SecurityUtils.changeFileExtension(file, extension)
                    if (SecurityUtils.decryptFile(context, file, decryptedFile)) {
                        if (decryptedFile.exists() && decryptedFile.length() > 0) {
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
                                else -> openDocumentFile(decryptedFile)
                            }

                            file.delete()
                        } else {
                            decryptedFile.delete()
                            mainHandler.post {
                                Toast.makeText(context, "Failed to decrypt file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        if (decryptedFile.exists()) {
                            decryptedFile.delete()
                        }
                        mainHandler.post {
                            Toast.makeText(context, "Failed to decrypt file", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    mainHandler.post {
                        Toast.makeText(context, "Error decrypting file", Toast.LENGTH_SHORT).show()
                    }
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
            selectedItems.forEach { position ->
                notifyItemChanged(position)
            }
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
            if (!fileExecutor.isShutdown) {
                fileExecutor.shutdown()
            }
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
                FileManager(context, lifecycleOwner).performEncryption(
                    selectedFiles
                ) {
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
        val updatedList = currentList.map { file ->
            encryptedFiles[file] ?: file
        }.toMutableList()
        selectedItems.clear()
        exitSelectionMode()
        submitList(updatedList)
        mainHandler.postDelayed({

            filesOperationCallback?.get()?.onRefreshNeeded()
        },20)
    }

    fun updateItemsAfterDecryption(decryptedFiles: Map<File, File>) {
        val currentList = FolderManager().getFilesInFolder(currentFolder)
        val updatedList = currentList.map { file ->
            decryptedFiles[file] ?: file
        }.toMutableList()
        selectedItems.clear()
        exitSelectionMode()
        submitList(updatedList)
        mainHandler.postDelayed({

            filesOperationCallback?.get()?.onRefreshNeeded()
        },20)
    }
}