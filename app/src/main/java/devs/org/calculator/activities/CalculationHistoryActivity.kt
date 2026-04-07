package devs.org.calculator.activities

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.adapters.HistoryAdapter
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.databinding.ActivityCalculationHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CalculationHistoryActivity : BaseCalculatorActivity() {
    private lateinit var binding: ActivityCalculationHistoryBinding
    private val historyDao by lazy { AppDatabase.getDatabase(this).calculationHistoryDao() }
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalculationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        observeHistory()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.history_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.clear_history) {
                showClearHistoryDialog()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { history ->
            // for now i am not using it
        }
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CalculationHistoryActivity)
            adapter = historyAdapter
        }
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            historyDao.getAllHistory().collectLatest { history ->
                if (history.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.historyRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.historyRecyclerView.visibility = View.VISIBLE
                    historyAdapter.submitList(history)
                }
            }
        }
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_history)
            .setMessage(R.string.are_you_sure_you_want_to_clear_history)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    historyDao.clearHistory()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
