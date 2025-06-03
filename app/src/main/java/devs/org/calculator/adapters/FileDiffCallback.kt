package devs.org.calculator.adapters

import androidx.recyclerview.widget.DiffUtil
import java.io.File

class FileDiffCallback : DiffUtil.ItemCallback<File>() {

    override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem.absolutePath == newItem.absolutePath
    }

    override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem.name == newItem.name &&
                oldItem.length() == newItem.length() &&
                oldItem.lastModified() == newItem.lastModified() &&
                oldItem.canRead() == newItem.canRead() &&
                oldItem.canWrite() == newItem.canWrite() &&
                oldItem.exists() == newItem.exists()
    }

    override fun getChangePayload(oldItem: File, newItem: File): Any? {
        val changes = mutableListOf<String>()

        if (oldItem.name != newItem.name) {
            changes.add("NAME_CHANGED")
        }

        if (oldItem.length() != newItem.length()) {
            changes.add("SIZE_CHANGED")
        }

        if (oldItem.lastModified() != newItem.lastModified()) {
            changes.add("MODIFIED_DATE_CHANGED")
        }

        if (oldItem.exists() != newItem.exists()) {
            changes.add("EXISTENCE_CHANGED")
        }

        return changes.ifEmpty { null }
    }
}