package devs.org.calculator.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.activities.BaseGalleryActivity
import devs.org.calculator.activities.PreviewActivity
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File
import kotlin.collections.remove

class FileAdapter(private val fileType: FileManager.FileType, var context: Context, private var lifecycleOwner: LifecycleOwner) :
    ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false

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

                var fileTypes = when(fileType){

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
                val intent = Intent(context, PreviewActivity::class.java).apply {
                    putExtra("type", fileTypes)
                    putExtra("position", position)
                }
                context.startActivity(intent)

            }
            itemView.setOnLongClickListener{

                val fileUri = FileManager.FileManager().getContentUri(context, file)
                val filesName = FileManager.FileName(context).getFileNameFromUri(fileUri!!).toString()

                MaterialAlertDialogBuilder(context)
                    .setTitle("Details")
                    .setMessage("File Name: $filesName\n\nYou can delete or Unhide this file.")
                    .setPositiveButton("Delete") { dialog, _ ->
                        // Handle positive button click
                        lifecycleOwner.lifecycleScope.launch{
                            FileManager(context, context as LifecycleOwner).deletePhotoFromExternalStorage(fileUri)
                        }

                        val currentList = currentList.toMutableList()
                        currentList.remove(file)
                        submitList(currentList)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Unhide") { dialog, _ ->
                        // Handle negative button click
                        FileManager(context, context as LifecycleOwner).copyFileToNormalDir(fileUri)
                        val currentList = currentList.toMutableList()
                        currentList.remove(file)
                        submitList(currentList)
                        dialog.dismiss()
                        dialog.dismiss()
                    }
                    .show()

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
            // Compare based on file path or another unique identifier
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            // Compare the content of files if needed
            return oldItem == newItem
        }
    }
}
