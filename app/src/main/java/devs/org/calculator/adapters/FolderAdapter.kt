package devs.org.calculator.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import devs.org.calculator.R
import java.io.File

class FolderAdapter(
    private val onFolderClick: (File) -> Unit,
    private val onFolderLongClick: (File) -> Unit,
    private val onSelectionModeChanged: (Boolean) -> Unit
) : ListAdapter<File, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderNameTextView: TextView = itemView.findViewById(R.id.folderName)
        val selectedView: ImageView = itemView.findViewById(R.id.selected)
        val selectedLayer: View = itemView.findViewById(R.id.selectedLayer)

        fun bind(folder: File, onFolderClick: (File) -> Unit, onFolderLongClick: (File) -> Unit, isSelected: Boolean) {
            folderNameTextView.text = folder.name

            selectedView.visibility = if (isSelected) View.VISIBLE else View.GONE
            selectedLayer.visibility = if (isSelected) View.VISIBLE else View.GONE

            itemView.setOnClickListener { 
                if (isSelectionMode) {
                    toggleSelection(adapterPosition)
                } else {
                    onFolderClick(folder)
                }
            }
            
            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    onSelectionModeChanged(true)
                    onFolderLongClick(folder)
                    toggleSelection(adapterPosition)
                }
                true
            }
        }

        private fun toggleSelection(position: Int) {
            if (selectedItems.contains(position)) {
                selectedItems.remove(position)
                if (selectedItems.isEmpty()) {
                    isSelectionMode = false
                    onSelectionModeChanged(false)
                }
            } else {
                selectedItems.add(position)
            }
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.bind(folder, onFolderClick, onFolderLongClick, selectedItems.contains(position))
    }

    fun getSelectedItems(): List<File> {
        return selectedItems.mapNotNull { position ->
            if (position < itemCount) getItem(position) else null
        }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onSelectionModeChanged(false)
        notifyDataSetChanged()
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.name == newItem.name
        }
    }
}