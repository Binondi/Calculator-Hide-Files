package devs.org.calculator.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import devs.org.calculator.R
import devs.org.calculator.utils.FileManager
import java.io.File

class FileAdapter(private val fileType: FileManager.FileType) :
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
            itemView.setOnClickListener { }
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
