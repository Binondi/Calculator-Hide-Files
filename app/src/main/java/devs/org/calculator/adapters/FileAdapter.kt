package devs.org.calculator.adapters

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.activities.PreviewActivity
import devs.org.calculator.utils.FileManager
import java.io.File

class FileAdapter(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val currentFolder: File
) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false

    // Callback interface for handling file operations
    interface FileOperationCallback {
        fun onFileDeleted(file: File)
        fun onFileRenamed(oldFile: File, newFile: File)
        fun onRefreshNeeded()
    }

    var fileOperationCallback: FileOperationCallback? = null

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.fileIconImageView)
        val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
        val playIcon: ImageView = view.findViewById(R.id.videoPlay)

        fun bind(file: File) {
            val fileType = FileManager(context, lifecycleOwner).getFileType(file)
            setupFileDisplay(file, fileType)
            setupClickListeners(file, fileType)

            // Handle selection state
            itemView.isSelected = selectedItems.contains(adapterPosition)
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
                }
            }
        }

        private fun setupFileDisplay(file: File, fileType: FileManager.FileType) {
            when (fileType) {
                FileManager.FileType.IMAGE -> {
                    loadImageThumbnail(file)
                    fileNameTextView.visibility = View.GONE
                    playIcon.visibility = View.GONE
                }
                FileManager.FileType.VIDEO -> {
                    loadVideoThumbnail(file)
                    fileNameTextView.visibility = View.GONE
                    playIcon.visibility = View.VISIBLE
                }
                else -> {
                    loadFileIcon(fileType)
                    fileNameTextView.visibility = View.VISIBLE
                    playIcon.visibility = View.GONE
                }
            }
            fileNameTextView.text = file.name
        }

        private fun loadImageThumbnail(file: File) {
            Glide.with(imageView)
                .load(file)
                .thumbnail(0.1f)
                .centerCrop()
                .override(300, 300)
                .placeholder(R.drawable.ic_file)
                .error(R.drawable.ic_file)
                .into(imageView)
        }

        private fun loadVideoThumbnail(file: File) {
            Glide.with(imageView)
                .asBitmap()
                .load(file)
                .thumbnail(0.1f)
                .centerCrop()
                .override(300, 300)
                .placeholder(R.drawable.ic_file)
                .error(R.drawable.ic_file)
                .into(imageView)
        }

        private fun loadFileIcon(fileType: FileManager.FileType) {
            val resourceId = when (fileType) {
                FileManager.FileType.AUDIO -> R.drawable.ic_audio
                FileManager.FileType.DOCUMENT -> R.drawable.ic_document
                else -> R.drawable.ic_file
            }
            imageView.setImageResource(resourceId)
        }

        private fun setupClickListeners(file: File, fileType: FileManager.FileType) {
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(adapterPosition)
                    return@setOnClickListener
                }
                openFile(file, fileType)
            }

            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    showFileOptionsDialog(file)
                    true
                } else {
                    false
                }
            }
        }

        private fun openFile(file: File, fileType: FileManager.FileType) {
            when (fileType) {
                FileManager.FileType.AUDIO -> openAudioFile(file)
                FileManager.FileType.IMAGE, FileManager.FileType.VIDEO -> openInPreview(fileType)
                FileManager.FileType.DOCUMENT -> openDocumentFile(file)
                else -> openDocumentFile(file)
            }
        }

        private fun openAudioFile(file: File) {
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
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.no_audio_player_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun openDocumentFile(file: File) {
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
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
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
            val options = arrayOf(
                context.getString(R.string.un_hide),
                context.getString(R.string.rename),
                context.getString(R.string.delete),
                context.getString(R.string.share)
            )

            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.file_options))
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> unHideFile(file)
                        1 -> renameFile(file)
                        2 -> deleteFile(file)
                        3 -> shareFile(file)
                    }
                    dialog.dismiss()
                }
                .create()
                .show()
        }

        private fun unHideFile(file: File) {
            FileManager(context, lifecycleOwner).unHideFile(
                file = file,
                onSuccess = {
                    fileOperationCallback?.onFileDeleted(file)

                },
                onError = { errorMessage ->

                    Toast.makeText(context, "Failed to unhide: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            )
        }

        private fun deleteFile(file: File) {
            if (file.delete()) {
                fileOperationCallback?.onFileDeleted(file)
                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
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
                        val parentDir = file.parentFile
                        if (parentDir != null) {
                            val newFile = File(parentDir, newName)
                            if (file.renameTo(newFile)) {
                                fileOperationCallback?.onFileRenamed(file, newFile)
                                Toast.makeText(context, "File renamed", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to rename file", Toast.LENGTH_SHORT).show()
                            }
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

        private fun shareFile(file: File) {
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
        }

        private fun toggleSelection(position: Int) {
            if (selectedItems.contains(position)) {
                selectedItems.remove(position)
            } else {
                selectedItems.add(position)
            }

            if (selectedItems.isEmpty()) {
                isSelectionMode = false
            }

            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        holder.bind(file)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val file = getItem(position)
            holder.bind(file, payloads)
        }
    }

    // Public methods for external control
    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<File> {
        return selectedItems.mapNotNull { position ->
            if (position < itemCount) getItem(position) else null
        }
    }
}