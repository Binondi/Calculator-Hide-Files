package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivityNotesBinding
import devs.org.calculator.databinding.ItemNoteBinding
import devs.org.calculator.utils.FileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NotesActivity : BaseActivity() {
    private lateinit var binding: ActivityNotesBinding
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var notesDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val fileManager = FileManager(this, this)
        notesDir = File(fileManager.getHiddenDirectory(), FileManager.NOTES_DIR)
        if (!notesDir.exists()) notesDir.mkdirs()

        setupToolbar()
        setupRecyclerView()

        binding.addNoteFab.setOnClickListener {
            startActivity(Intent(this, EditNotesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotes()
    }

    private fun setupToolbar() {
        binding.back.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onNoteClick = { file ->
                val intent = Intent(this, EditNotesActivity::class.java)
                intent.putExtra("note_path", file.absolutePath)
                startActivity(intent)
            },
            onNoteLongClick = { showDeleteConfirmDialog(it) }
        )
        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotesActivity)
            adapter = notesAdapter
        }
    }

    private fun refreshNotes() {
        val notes = notesDir.listFiles()?.filter { it.extension == "txt" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (notes.isEmpty()) {
            binding.emptyNotesState.visibility = View.VISIBLE
            binding.notesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyNotesState.visibility = View.GONE
            binding.notesRecyclerView.visibility = View.VISIBLE
            notesAdapter.submitList(notes)
        }
    }

    private fun showDeleteConfirmDialog(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_note))
            .setMessage(getString(R.string.are_you_sure_you_want_to_delete_this_secret_note))
            .setPositiveButton(R.string.delete) { _, _ ->
                file.delete()
                refreshNotes()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    inner class NotesAdapter(
        private val onNoteClick: (File) -> Unit,
        private val onNoteLongClick: (File) -> Unit
    ) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

        private var notes = listOf<File>()

        fun submitList(newList: List<File>) {
            notes = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return NoteViewHolder(binding)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            holder.bind(notes[position])
        }

        override fun getItemCount() = notes.size

        inner class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(file: File) {
                binding.noteTitle.text = file.nameWithoutExtension
                binding.noteContent.text = try { file.readText() } catch (_: Exception) { "" }
                binding.noteDate.text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

                binding.root.setOnClickListener { onNoteClick(file) }
                binding.root.setOnLongClickListener {
                    onNoteLongClick(file)
                    true
                }
            }
        }
    }
}
