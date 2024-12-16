package devs.org.calculator.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.activities.PreviewActivity
import devs.org.calculator.callbacks.DialogActionsCallback
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File

class FileAdapter(
    private val fileType: FileManager.FileType,
    var context: Context,
    private var lifecycleOwner: LifecycleOwner
) :
    ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false
    private var fileName = "Unknown File"
    private var fileTypes = when (fileType) {

        FileManager.FileType.IMAGE -> {
            "IMAGE"
        }

        FileManager.FileType.VIDEO -> {
            "VIDEO"
        }

        FileManager.FileType.AUDIO -> {
            "AUDIO"
        }

        else -> "DOCUMENT"

    }

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.imageView)

        fun bind(file: File) {

            when (fileType) {
                FileManager.FileType.IMAGE -> {
                    Glide.with(imageView)
                        .load(file)
                        .centerCrop()
                        .into(imageView)
                }

                FileManager.FileType.VIDEO -> {
                    Glide.with(imageView)
                        .asBitmap()
                        .load(file)
                        .centerCrop()
                        .into(imageView)
                }

                else -> {
                    val resourceId = when (fileType) {
                        FileManager.FileType.AUDIO -> R.drawable.ic_audio
                        FileManager.FileType.DOCUMENT -> R.drawable.ic_document
                        else -> R.drawable.ic_file
                    }
                    imageView.setImageResource(resourceId)
                }
            }
            itemView.setOnClickListener {


                when(fileType){
                    FileManager.FileType.AUDIO -> {
                        // Create an intent to play audio using available audio players
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(FileManager.FileManager().getContentUriImage(context, file, fileType), "audio/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No audio player found!", Toast.LENGTH_SHORT).show()
                        }
                    }

                    FileManager.FileType.DOCUMENT -> {
                        // Create an intent to open the document using available viewers or file managers
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(FileManager.FileManager().getContentUriImage(context, file, fileType), "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No suitable app found to open this document!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        val intent = Intent(context, PreviewActivity::class.java).apply {
                            putExtra("type", fileTypes)
                            putExtra("position", position)
                        }
                        context.startActivity(intent)
                    }
                }


            }
            itemView.setOnLongClickListener {

                val fileUri = FileManager.FileManager().getContentUriImage(context, file, fileType)
                if (fileUri == null) {
                    Toast.makeText(context, "Unable to access file: $file", Toast.LENGTH_SHORT)
                        .show()

                    return@setOnLongClickListener true

                }
                fileName = FileManager.FileName(context).getFileNameFromUri(fileUri)?.toString()
                    ?: "Unknown File"

                DialogUtil(context).showMaterialDialogWithNaturalButton(
                    "$fileTypes DETAILS",
                    "File Name: $fileName\n\nFile Path: $file\n\nYou can permanently delete or unhide this file.",
                    "Delete Permanently",
                    "Unhide",
                    "Cancel",
                    object : DialogActionsCallback {
                        override fun onPositiveButtonClicked() {
                            lifecycleOwner.lifecycleScope.launch {
                                FileManager(context, lifecycleOwner).deletePhotoFromExternalStorage(
                                    fileUri
                                )
                            }
                            val currentList = currentList.toMutableList()
                            currentList.remove(file)
                            submitList(currentList)
                        }

                        override fun onNegativeButtonClicked() {
                            FileManager(context, lifecycleOwner).copyFileToNormalDir(fileUri)
                            val currentList = currentList.toMutableList()
                            currentList.remove(file)
                            submitList(currentList)
                        }

                        override fun onNaturalButtonClicked() {

                        }
                    }
                )

                return@setOnLongClickListener true
            }


        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }


}
