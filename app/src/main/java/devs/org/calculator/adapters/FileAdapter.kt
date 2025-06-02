package devs.org.calculator.adapters

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
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.activities.PreviewActivity
import devs.org.calculator.utils.FileManager
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

    // Use WeakReference to prevent memory leaks
    private var fileOperationCallback: WeakReference<FileOperationCallback>? = null

    // Background executor for file operations
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "FileAdapter"
    }

    // Callback interface for handling file operations and selection changes
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

        fun bind(file: File) {
            val fileType = FileManager(context, lifecycleOwner).getFileType(file)
            setupFileDisplay(file, fileType)
            setupClickListeners(file, fileType)
            fileNameTextView.visibility = if (showFileName) View.VISIBLE else View.GONE

            // Update selection state
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val isSelected = selectedItems.contains(position)
                updateSelectionUI(isSelected)
            }
        }

        fun bind(file: File, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                bind(file)
                return
            }

            // Handle partial updates based on payload
            val changes = payloads.firstOrNull() as? List<String>
            changes?.forEach { change ->
                when (change) {
                    "NAME_CHANGED" -> {
                        fileNameTextView.text = file.name
                    }
                    "SIZE_CHANGED", "MODIFIED_DATE_CHANGED" -> {
                        // Could update file info if displayed
                    }
                    "SELECTION_CHANGED" -> {
                        val position = adapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            val isSelected = selectedItems.contains(position)
                            updateSelectionUI(isSelected)
                            // Notify activity about selection change
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

        private fun setupFileDisplay(file: File, fileType: FileManager.FileType) {
            fileNameTextView.text = file.name

            when (fileType) {
                FileManager.FileType.IMAGE -> {
                    playIcon.visibility = View.GONE
                    Glide.with(context)
                        .load(file)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .error(R.drawable.ic_document)
                        .placeholder(R.drawable.ic_document)
                        .into(imageView)
                }
                FileManager.FileType.VIDEO -> {
                    playIcon.visibility = View.VISIBLE
                    Glide.with(context)
                        .load(file)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .error(R.drawable.ic_document)
                        .placeholder(R.drawable.ic_document)
                        .into(imageView)
                }
                FileManager.FileType.AUDIO -> {
                    playIcon.visibility = View.GONE
                    imageView.setImageResource(R.drawable.ic_audio)
                }
                else -> {
                    playIcon.visibility = View.GONE
                    imageView.setImageResource(R.drawable.ic_document)
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
                FileManager.FileType.IMAGE, FileManager.FileType.VIDEO -> openInPreview(fileType)
                FileManager.FileType.DOCUMENT -> openDocumentFile(file)
                else -> openDocumentFile(file)
            }
        }

        private fun openAudioFile(file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "audio/*")
                    putExtra("folder", currentFolder.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

        private fun showFileOptionsDialog(file: File) {
            val options = if (isSelectionMode) {
                arrayOf(
                    context.getString(R.string.un_hide),
                    context.getString(R.string.delete),
                    context.getString(R.string.copy_to_another_folder),
                    context.getString(R.string.move_to_another_folder)
                )
            } else {
                arrayOf(
                    context.getString(R.string.un_hide),
                    context.getString(R.string.select_multiple),
                    context.getString(R.string.rename),
                    context.getString(R.string.delete),
                    context.getString(R.string.share)
                )
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.file_options))
                .setItems(options) { dialog, which ->
                    if (isSelectionMode) {
                        when (which) {
                            0 -> unHideFile(file)
                            1 -> deleteFile(file)
                            2 -> copyToAnotherFolder(file)
                            3 -> moveToAnotherFolder(file)
                        }
                    } else {
                        when (which) {
                            0 -> unHideFile(file)
                            1 -> enableSelectMultipleFiles()
                            2 -> renameFile(file)
                            3 -> deleteFile(file)
                            4 -> shareFile(file)
                        }
                    }
                    dialog.dismiss()
                }
                .create()
                .show()
        }

        private fun enableSelectMultipleFiles() {
            val position = adapterPosition
            if (position == RecyclerView.NO_POSITION) return

            enterSelectionMode()
            selectedItems.add(position)
            notifyItemChanged(position, listOf("SELECTION_CHANGED"))
        }

        private fun unHideFile(file: File) {
            FileManager(context, lifecycleOwner).unHideFile(
                file = file,
                onSuccess = {
                    fileOperationCallback?.get()?.onFileDeleted(file)
                },
                onError = { errorMessage ->
                    Toast.makeText(context, "Failed to unhide: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            )
        }

        private fun deleteFile(file: File) {
            // Show confirmation dialog first
            MaterialAlertDialogBuilder(context)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete ${file.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteFileAsync(file)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun deleteFileAsync(file: File) {
            fileExecutor.execute {
                val success = try {
                    file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete file: ${e.message}")
                    false
                }

                mainHandler.post {
                    if (success) {
                        fileOperationCallback?.get()?.onFileDeleted(file)
                        Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        private fun renameFile(file: File) {
            val inputEditText = EditText(context).apply {
                setText(file.name)
                selectAll()
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.rename_file))
                .setView(inputEditText)
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

        private fun shareFile(file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.share_file))
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share file: ${e.message}")
                Toast.makeText(context, "Failed to share file", Toast.LENGTH_SHORT).show()
            }
        }

        private fun copyToAnotherFolder(file: File) {
            // This will be handled by the activity
            fileOperationCallback?.get()?.onRefreshNeeded()
        }

        private fun moveToAnotherFolder(file: File) {
            // This will be handled by the activity
            fileOperationCallback?.get()?.onRefreshNeeded()
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
            super.onBindViewHolder(holder, position, payloads)
        } else {
            if (position < itemCount) {
                val file = getItem(position)
                holder.bind(file, payloads)
            }
        }
    }

    /**
     * Enter selection mode and notify callback immediately
     */
    fun enterSelectionMode() {
        if (!isSelectionMode) {
            isSelectionMode = true
            notifySelectionModeChange()
        }
    }

    /**
     * Exit selection mode and clear all selections
     */
    fun exitSelectionMode() {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedItems.clear()
            notifySelectionModeChange()
            notifyDataSetChanged()
        }
    }

    /**
     * Clear selection without exiting selection mode
     */
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

    /**
     * Select all items
     */
    fun selectAll() {
        if (!isSelectionMode) {
            enterSelectionMode()
        }

        val previouslySelected = selectedItems.toSet()
        selectedItems.clear()

        // Add all positions to selection
        for (i in 0 until itemCount) {
            selectedItems.add(i)
        }

        // Notify callback about selection change
        fileOperationCallback?.get()?.onSelectionCountChanged(selectedItems.size)

        // Update UI for changed items efficiently
        updateSelectionItems(selectedItems.toSet(), previouslySelected)
    }

    /**
     * Efficiently update selection UI for changed items only
     */
    private fun updateSelectionItems(newSelections: Set<Int>, oldSelections: Set<Int>) {
        val changedItems = (oldSelections - newSelections) + (newSelections - oldSelections)
        changedItems.forEach { position ->
            if (position < itemCount) {
                notifyItemChanged(position, listOf("SELECTION_CHANGED"))
            }
        }
    }

    /**
     * Centralized method to notify selection mode changes
     */
    private fun notifySelectionModeChange() {
        fileOperationCallback?.get()?.onSelectionModeChanged(isSelectionMode, selectedItems.size)
        onFolderLongClick(isSelectionMode)
    }

    /**
     * Get selected files
     */
    fun getSelectedItems(): List<File> {
        return selectedItems.mapNotNull { position ->
            if (position < itemCount) getItem(position) else null
        }
    }

    /**
     * Get selected file count
     */
    fun getSelectedCount(): Int = selectedItems.size

    /**
     * Check if in selection mode
     */
    fun isInSelectionMode(): Boolean = isSelectionMode

    /**
     * Delete selected files with proper error handling and background processing
     */
    fun deleteSelectedFiles() {
        val selectedFiles = getSelectedItems()
        if (selectedFiles.isEmpty()) return

        // Show confirmation dialog
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete Files")
            .setMessage("Are you sure you want to delete ${selectedFiles.size} file(s)?")
            .setPositiveButton("Delete") { _, _ ->
                deleteFilesAsync(selectedFiles)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFilesAsync(selectedFiles: List<File>) {
        fileExecutor.execute {
            var deletedCount = 0
            var failedCount = 0
            val failedFiles = mutableListOf<String>()

            selectedFiles.forEach { file ->
                try {
                    if (file.delete()) {
                        deletedCount++
                        mainHandler.post {
                            fileOperationCallback?.get()?.onFileDeleted(file)
                        }
                    } else {
                        failedCount++
                        failedFiles.add(file.name)
                    }
                } catch (e: Exception) {
                    failedCount++
                    failedFiles.add(file.name)
                    Log.e(TAG, "Failed to delete ${file.name}: ${e.message}")
                }
            }

            mainHandler.post {
                // Exit selection mode first
                exitSelectionMode()

                // Show detailed result message
                when {
                    deletedCount > 0 && failedCount == 0 -> {
                        Toast.makeText(context, "Deleted $deletedCount file(s)", Toast.LENGTH_SHORT).show()
                    }
                    deletedCount > 0 && failedCount > 0 -> {
                        Toast.makeText(context,
                            "Deleted $deletedCount file(s), failed to delete $failedCount",
                            Toast.LENGTH_LONG).show()
                    }
                    failedCount > 0 -> {
                        Toast.makeText(context,
                            "Failed to delete $failedCount file(s)",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Share selected files
     */
    fun shareSelectedFiles() {
        val selectedFiles = getSelectedItems()
        if (selectedFiles.isEmpty()) return

        try {
            if (selectedFiles.size == 1) {
                // Share single file
                val file = selectedFiles.first()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.share_file))
                )
            } else {
                // Share multiple files
                val uris = selectedFiles.mapNotNull { file ->
                    try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get URI for file ${file.name}: ${e.message}")
                        null
                    }
                }

                if (uris.isNotEmpty()) {
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share ${selectedFiles.size} files")
                    )
                } else {
                    Toast.makeText(context, "No files could be shared", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share files: ${e.message}")
            Toast.makeText(context, "Failed to share files", Toast.LENGTH_SHORT).show()
        }

        exitSelectionMode()
    }

    /**
     * Handle back press - exit selection mode if active
     * @return true if selection mode was active and has been exited, false otherwise
     */
    fun onBackPressed(): Boolean {
        return if (isSelectionMode) {
            exitSelectionMode()
            true
        } else {
            false
        }
    }

    /**
     * Force refresh of all selection states
     * Call this if you notice selection UI issues
     */
    fun refreshSelectionStates() {
        if (isSelectionMode) {
            selectedItems.forEach { position ->
                if (position < itemCount) {
                    notifyItemChanged(position, listOf("SELECTION_CHANGED"))
                }
            }
            // Ensure callback is notified
            notifySelectionModeChange()
        }
    }

    /**
     * Clean up resources to prevent memory leaks
     */
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

    /**
     * Call this from the activity's onDestroy()
     */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        cleanup()
    }

    private fun onSelectionCountChanged(count: Int) {
        fileOperationCallback?.get()?.onSelectionCountChanged(count)
    }
}