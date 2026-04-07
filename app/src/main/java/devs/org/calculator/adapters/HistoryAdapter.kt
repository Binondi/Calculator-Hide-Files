package devs.org.calculator.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import devs.org.calculator.database.CalculationHistory
import devs.org.calculator.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(private val onHistoryClick: (CalculationHistory) -> Unit) :
    ListAdapter<CalculationHistory, HistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(history: CalculationHistory) {
            binding.expressionText.text = history.expression
            binding.resultText.text = history.result
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            binding.dateText.text = sdf.format(Date(history.timestamp))
            binding.root.setOnClickListener { onHistoryClick(history) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CalculationHistory>() {
        override fun areItemsTheSame(oldItem: CalculationHistory, newItem: CalculationHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CalculationHistory, newItem: CalculationHistory): Boolean {
            return oldItem == newItem
        }
    }
}
