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
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
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
import devs.org.calculator.utils.FileManager
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
    private val onFolderLongClick: (Boolean) -> Unit
) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false

    private var fileOperationCallback: WeakReference<FileOperationCallback>? = null

    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    val hiddenFileRepository: HiddenFileRepository by lazy {
        HiddenFileRepository(AppDatabase.getDatabase(context).hiddenFileDao())
    }

    companion object {
        private const val TAG = "FileAdapter"
    }

    interface FileOperationCallback {
        fun onFileDeleted(file: File)
        fun onFileRenamed(oldFile: File, newFile: File)
        fun onRefreshNeeded()
        fun onSelectionModeChanged(isSelectionMode: Boolean, selectedCount: Int)
        fun onSelectionCountChanged(selectedCount: Int)
    }

    fun setFileOperationCallback(callback: FileOperationCallback?) {
        fileOperationCallback = callback?.let { WeakReference(it) }
    }

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.fileIconImageView)
        val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
        val playIcon: ImageView = view.findViewById(R.id.videoPlay)
        val selectedLayer: View = view.findViewById(R.id.selectedLayer)
        val selected: ImageView = view.findViewById(R.id.selected)
        val encryptedIcon: ImageView = view.findViewById(R.id.encrypted)

        fun bind(file: File) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    val fileType = if (hiddenFile?.fileType != null) hiddenFile.fileType
                    else {
                        FileManager(context, lifecycleOwner).getFileType(file)
                    }
                    
                    setupFileDisplay(file, fileType, hiddenFile?.isEncrypted == true,hiddenFile)
                    setupClickListeners(file, fileType)
                    fileNameTextView.visibility = if (showFileName) View.VISIBLE else View.GONE

                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val isSelected = selectedItems.contains(position)
                        updateSelectionUI(isSelected)
                    }
                    encryptedIcon.visibility = if (hiddenFile?.isEncrypted == true) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    Log.e(TAG, "Error binding file: ${e.message}")
                }
            }
        }

        fun bind(file: File, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                bind(file)
                return
            }

            val changes = payloads.firstOrNull() as? List<String>
            changes?.forEach { change ->
                when (change) {
                    "NAME_CHANGED" -> {
                        fileNameTextView.text = file.name
                    }
                    "SIZE_CHANGED", "MODIFIED_DATE_CHANGED" -> {

                    }
                    "SELECTION_CHANGED" -> {
                        val position = adapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            val isSelected = selectedItems.contains(position)
                            updateSelectionUI(isSelected)
                            notifySelectionModeChange()
                        }
                    }
                }
            }
        }

        private fun updateSelectionUI(isSelected: Boolean) {
            selectedLayer.visibility = if (isSelected) View.VISIBLE else View.GONE
            selected.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        private fun setupFileDisplay(file: File, fileType: FileManager.FileType, isEncrypted: Boolean, metadata: HiddenFileEntity?) {
            fileNameTextView.text = metadata?.fileName ?: file.name

            when (fileType) {
                FileManager.FileType.IMAGE -> {
                    playIcon.visibility = View.GONE
                    if (isEncrypted) {
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
                                        .into(imageView)
                                    imageView.setPadding(0, 0, 0, 0)
                                } else {
                                    showEncryptedIcon()
                                }
                            } else {
                                showEncryptedIcon()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading encrypted image preview: ${e.message}")
                            showEncryptedIcon()
                        }
                    } else {
                        Glide.with(context)
                            .load(file)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .error(R.drawable.ic_image)
                            .into(imageView)
                        imageView.setPadding(0, 0, 0, 0)
                    }
                }
                FileManager.FileType.VIDEO -> {
                    playIcon.visibility = View.VISIBLE
                    if (isEncrypted) {
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
                                        .into(imageView)
                                    imageView.setPadding(0, 0, 0, 0)
                                } else {
                                    showEncryptedIcon()
                                }
                            } else {
                                showEncryptedIcon()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading encrypted video preview: ${e.message}")
                            showEncryptedIcon()
                        }
                    } else {
                        Glide.with(context)
                            .load(file)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .error(R.drawable.ic_video)
                            .into(imageView)
                    }
                }
                FileManager.FileType.AUDIO -> {
                    playIcon.visibility = View.GONE
                    if (isEncrypted) {
                        imageView.setImageResource(R.drawable.encrypted)
                    } else {
                        imageView.setImageResource(R.drawable.ic_audio)
                    }
                    imageView.setPadding(50, 50, 50, 50)
                }
                else -> {
                    playIcon.visibility = View.GONE
                    if (isEncrypted) {
                        imageView.setImageResource(R.drawable.encrypted)
                    } else {
                        imageView.setImageResource(R.drawable.ic_document)
                    }
                    imageView.setPadding(50, 50, 50, 50)
                }
            }
        }

        private fun setupClickListeners(file: File, fileType: FileManager.FileType) {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                if (isSelectionMode) {
                    toggleSelection(position)
                } else {
                    openFile(file, fileType)
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

        private fun openFile(file: File, fileType: FileManager.FileType) {
            if (!file.exists()) {
                Toast.makeText(context, "File no longer exists", Toast.LENGTH_SHORT).show()
                return
            }

            when (fileType) {
                FileManager.FileType.AUDIO -> openAudioFile(file)
                FileManager.FileType.IMAGE, FileManager.FileType.VIDEO -> {
                    lifecycleOwner.lifecycleScope.launch {
                        try {
                            val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                            if (hiddenFile?.isEncrypted == true) {
                                val tempFile = File(context.cacheDir, "preview_${file.name}")
                                Log.d(TAG, "Attempting to decrypt file for preview: ${file.absolutePath}")
                                
                                if (SecurityUtils.decryptFile(context, file, tempFile)) {
                                    Log.d(TAG, "Successfully decrypted file for preview: ${tempFile.absolutePath}")
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
                                        Log.e(TAG, "Decrypted preview file is empty or doesn't exist")
                                        mainHandler.post {
                                            Toast.makeText(context, "Failed to prepare file for preview", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Failed to decrypt file for preview")
                                    mainHandler.post {
                                        Toast.makeText(context, "Failed to decrypt file for preview", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                openInPreview(fileType)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error preparing file for preview: ${e.message}", e)
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open audio file: ${e.message}")
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open document file: ${e.message}")
                Toast.makeText(
                    context,
                    context.getString(R.string.no_suitable_app_found_to_open_this_document),
                    Toast.LENGTH_SHORT
                ).show()
            }
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

        @SuppressLint("MissingInflatedId")
        private fun renameFile(file: File) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
            val inputEditText = dialogView.findViewById<EditText>(R.id.editText)
            inputEditText.setText(file.name)
            inputEditText.selectAll()

            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.rename_file))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.rename)) { dialog, _ ->
                    val newName = inputEditText.text.toString().trim()
                    if (newName.isNotEmpty() && newName != file.name) {
                        if (isValidFileName(newName)) {
                            renameFileAsync(file, newName)
                        } else {
                            Toast.makeText(context, "Invalid file name", Toast.LENGTH_SHORT).show()
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
                    dialog.cancel()
                }
                .create()
                .show()
        }

        private fun isValidFileName(fileName: String): Boolean {
            val forbiddenChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
            return fileName.isNotBlank() &&
                    fileName.none { it in forbiddenChars } &&
                    !fileName.startsWith(".") &&
                    fileName.length <= 255
        }

        private fun renameFileAsync(file: File, newName: String) {
            fileExecutor.execute {
                val parentDir = file.parentFile
                if (parentDir != null) {
                    val newFile = File(parentDir, newName)
                    if (newFile.exists()) {
                        mainHandler.post {
                            Toast.makeText(context, "File with this name already exists", Toast.LENGTH_SHORT).show()
                        }
                        return@execute
                    }

                    val success = try {
                        file.renameTo(newFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to rename file: ${e.message}")
                        false
                    }

                    mainHandler.post {
                        if (success) {
                            fileOperationCallback?.get()?.onFileRenamed(file, newFile)
                            Toast.makeText(context, "File renamed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to rename file", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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

        private fun showEncryptedIcon() {
            imageView.setImageResource(R.drawable.encrypted)
            imageView.setPadding(50, 50, 50, 50)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        if (position < itemCount) {
            val file = getItem(position)
            holder.bind(file)
        }
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            if (position < itemCount) {
                val file = getItem(position)
                holder.bind(file, payloads)
            }
        }
    }

    override fun submitList(list: List<File>?) {
        val currentList = currentList.toMutableList()
        if (list == null) {
            currentList.clear()
            super.submitList(null)
        } else {
            val newList = list.filter { it.name != ".nomedia" }.toMutableList()
            super.submitList(newList)
        }
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
            selectedItems.clear()
            notifySelectionModeChange()
            notifyDataSetChanged()
        }
    }

    fun clearSelection() {
        if (selectedItems.isNotEmpty()) {
            val previouslySelected = selectedItems.toSet()
            selectedItems.clear()
            fileOperationCallback?.get()?.onSelectionCountChanged(0)
            previouslySelected.forEach { position ->
                if (position < itemCount) {
                    notifyItemChanged(position, listOf("SELECTION_CHANGED"))
                }
            }
        }
    }

    fun selectAll() {
        if (!isSelectionMode) {
            enterSelectionMode()
        }
        val previouslySelected = selectedItems.toSet()
        selectedItems.clear()
        for (i in 0 until itemCount) {
            selectedItems.add(i)
        }
        fileOperationCallback?.get()?.onSelectionCountChanged(selectedItems.size)
        updateSelectionItems(selectedItems.toSet(), previouslySelected)
    }

    private fun updateSelectionItems(newSelections: Set<Int>, oldSelections: Set<Int>) {
        val changedItems = (oldSelections - newSelections) + (newSelections - oldSelections)
        changedItems.forEach { position ->
            if (position < itemCount) {
                notifyItemChanged(position, listOf("SELECTION_CHANGED"))
            }
        }
    }

    private fun notifySelectionModeChange() {
        fileOperationCallback?.get()?.onSelectionModeChanged(isSelectionMode, selectedItems.size)
        onFolderLongClick(isSelectionMode)
    }

    fun getSelectedItems(): List<File> {
        return selectedItems.mapNotNull { position ->
            if (position < itemCount) getItem(position) else null
        }
    }


    fun getSelectedCount(): Int = selectedItems.size


    fun isInSelectionMode(): Boolean = isSelectionMode


    fun onBackPressed(): Boolean {
        return if (isSelectionMode) {
            exitSelectionMode()
            true
        } else {
            false
        }
    }

    fun cleanup() {
        try {
            if (!fileExecutor.isShutdown) {
                fileExecutor.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down executor: ${e.message}")
        }

        fileOperationCallback?.clear()
        fileOperationCallback = null
    }
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        cleanup()
    }

    private fun onSelectionCountChanged(count: Int) {
        fileOperationCallback?.get()?.onSelectionCountChanged(count)
    }

    fun encryptSelectedFiles() {
        val selectedFiles = getSelectedItems()
        if (selectedFiles.isEmpty()) return

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.encrypt_files))
            .setMessage(context.getString(R.string.encryption_disclaimer))
            .setPositiveButton(context.getString(R.string.encrypt)) { _, _ ->
                performEncryption(selectedFiles)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun performEncryption(selectedFiles: List<File>) {
        lifecycleOwner.lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            val updatedFiles = mutableListOf<File>()

            for (file in selectedFiles) {
                try {
                    val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    if (hiddenFile?.isEncrypted == true) continue
                    val originalExtension = ".${file.extension.lowercase()}"
                    val fileType = FileManager(context,lifecycleOwner).getFileType(file)
                    val encryptedFile = SecurityUtils.changeFileExtension(file, FileManager.ENCRYPTED_EXTENSION)
                    if (SecurityUtils.encryptFile(context, file, encryptedFile)) {
                        if (encryptedFile.exists()) {
                            if (hiddenFile == null){
                                hiddenFileRepository.insertHiddenFile(
                                    HiddenFileEntity(
                                        filePath = encryptedFile.absolutePath,
                                        isEncrypted = true,
                                        encryptedFileName = encryptedFile.name,
                                        fileType = fileType,
                                        fileName = file.name,
                                        originalExtension = originalExtension
                                    )
                                )
                            }else{
                                hiddenFile.let {
                                    hiddenFileRepository.updateEncryptionStatus(
                                        filePath = hiddenFile.filePath,
                                        newFilePath = encryptedFile.absolutePath,
                                        encryptedFileName = encryptedFile.name,
                                        isEncrypted = true
                                    )
                                }
                            }
                            if (file.delete()) {
                                updatedFiles.add(encryptedFile)
                                successCount++
                            } else {
                                failCount++
                            }
                        } else {
                            failCount++
                        }
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encrypting file: ${e.message}")
                    failCount++
                }
            }

            mainHandler.post {
                exitSelectionMode()
                when {
                    successCount > 0 && failCount == 0 -> {
                        Toast.makeText(context, "Encrypted $successCount file(s)", Toast.LENGTH_SHORT).show()
                    }
                    successCount > 0 && failCount > 0 -> {
                        Toast.makeText(context, "Encrypted $successCount file(s), failed to encrypt $failCount", Toast.LENGTH_LONG).show()
                    }
                    failCount > 0 -> {
                        Toast.makeText(context, "Failed to encrypt $failCount file(s)", Toast.LENGTH_SHORT).show()
                    }
                }
                val currentFiles = currentFolder.listFiles()?.toList() ?: emptyList()
                submitList(currentFiles)
                fileOperationCallback?.get()?.onRefreshNeeded()
            }
        }
    }

    fun decryptSelectedFiles() {
        val selectedFiles = getSelectedItems()
        if (selectedFiles.isEmpty()) return

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.decrypt_files))
            .setMessage(context.getString(R.string.decryption_disclaimer))
            .setPositiveButton(context.getString(R.string.decrypt)) { _, _ ->
                performDecryption(selectedFiles)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun performDecryption(selectedFiles: List<File>) {
        lifecycleOwner.lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            val updatedFiles = mutableListOf<File>()

            for (file in selectedFiles) {
                try {
                    val hiddenFile = hiddenFileRepository.getHiddenFileByPath(file.absolutePath)
                    if (hiddenFile?.isEncrypted != true) continue
                    val originalExtension = hiddenFile.originalExtension
                    val decryptedFile = SecurityUtils.changeFileExtension(file, originalExtension)
                    if (SecurityUtils.decryptFile(context, file, decryptedFile)) {
                        if (decryptedFile.exists() && decryptedFile.length() > 0) {
                            hiddenFile.let {
                                hiddenFileRepository.updateEncryptionStatus(
                                    filePath = file.absolutePath,
                                    newFilePath = decryptedFile.absolutePath,
                                    encryptedFileName = decryptedFile.name,
                                    isEncrypted = false
                                )
                            }
                            if (file.delete()) {
                                updatedFiles.add(decryptedFile)
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
                } catch (e: Exception) {
                    Log.e(TAG, "Error decrypting file: ${e.message}")
                    failCount++
                }
            }

            mainHandler.post {
                exitSelectionMode()
                when {
                    successCount > 0 && failCount == 0 -> {
                        Toast.makeText(context, "Decrypted $successCount file(s)", Toast.LENGTH_SHORT).show()
                    }
                    successCount > 0 && failCount > 0 -> {
                        Toast.makeText(context, "Decrypted $successCount file(s), failed to decrypt $failCount", Toast.LENGTH_LONG).show()
                    }
                    failCount > 0 -> {
                        Toast.makeText(context, "Failed to decrypt $failCount file(s)", Toast.LENGTH_SHORT).show()
                    }
                }
                val currentFiles = currentFolder.listFiles()?.toList() ?: emptyList()
                submitList(currentFiles)
                fileOperationCallback?.get()?.onRefreshNeeded()
            }
        }
    }
}