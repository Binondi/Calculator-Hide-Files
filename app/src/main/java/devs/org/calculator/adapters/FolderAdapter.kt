package devs.org.calculator.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import devs.org.calculator.R
import java.io.File

class FolderAdapter(
    private val onFolderClick: (File) -> Unit,
    private val onFolderLongClick: (File) -> Unit
) : ListAdapter<File, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderNameTextView: TextView = itemView.findViewById(R.id.folderName)

        fun bind(folder: File, onFolderClick: (File) -> Unit, onFolderLongClick: (File) -> Unit) {
            folderNameTextView.text = folder.name

            itemView.setOnClickListener { onFolderClick(folder) }
            itemView.setOnLongClickListener {
                onFolderLongClick(folder)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.bind(folder, onFolderClick, onFolderLongClick)
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.name == newItem.name &&
                    oldItem.lastModified() == newItem.lastModified() &&
                    oldItem.length() == newItem.length()
        }
    }
}