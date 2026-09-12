package devs.org.calculator.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.R
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileRepository
import devs.org.calculator.databinding.ActivityEditNotesBinding
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import kotlin.math.abs

class EditNotesActivity : BaseActivity() {
    private lateinit var binding: ActivityEditNotesBinding
    private var noteFile: File? = null
    private lateinit var notesDir: File
    private val hiddenFileRepository: HiddenFileRepository by lazy {
        HiddenFileRepository(AppDatabase.getDatabase(this).hiddenFileDao())
    }

    private data class NoteHistoryState(
        val title: String,
        val content: String,
        val focusedId: Int = 0,
        val titleSelection: Int = 0,
        val contentSelection: Int = 0
    )

    private val undoStack = ArrayDeque<NoteHistoryState>()
    private val redoStack = ArrayDeque<NoteHistoryState>()
    private var isApplyingState = false
    private var lastEditTime = 0L
    private var lastFocusedId = 0
    private var lastWasDelete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditNotesBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val fileManager = FileManager(this, this)
        notesDir = File(fileManager.getHiddenDirectory(), FileManager.NOTES_DIR)
        if (!notesDir.exists()) notesDir.mkdirs()

        val filePath = intent.getStringExtra("note_path")
        if (filePath != null) {
            noteFile = File(filePath)
            noteFile?.parentFile?.let {
                notesDir = it
            }
            binding.toolBar.title = getString(R.string.update_note)
            loadNote()
        } else {
            binding.toolBar.title = getString(R.string.edit_note)
            initHistory("", "")
        }

        setupToolbar()
        setupTextWatchers()
    }

    private fun setupToolbar() {
        binding.toolBar.setNavigationOnClickListener {
            finish()
        }
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_undo -> {
                    undo()
                    true
                }
                R.id.action_redo -> {
                    redo()
                    true
                }
                R.id.save -> {
                    saveNote()
                    true
                }
                else -> false
            }
        }
        updateUndoRedoMenuState()
    }

    private fun loadNote() {
        noteFile?.let {
            val title = it.nameWithoutExtension
            val content = try { it.readText() } catch (_: Exception) { "" }
            isApplyingState = true
            binding.noteTitle.setText(title)
            binding.noteContent.setText(content)
            isApplyingState = false
            initHistory(title, content)
        }
    }

    private fun initHistory(initialTitle: String, initialContent: String) {
        undoStack.clear()
        redoStack.clear()
        undoStack.addLast(NoteHistoryState(initialTitle, initialContent))
        lastEditTime = 0L
        updateUndoRedoMenuState()
    }

    private fun setupTextWatchers() {
        binding.noteTitle.doAfterTextChanged {
            onTextChanged()
        }
        binding.noteContent.doAfterTextChanged {
            onTextChanged()
        }
    }

    private fun onTextChanged() {
        if (isApplyingState) return

        val curTitle = binding.noteTitle.text?.toString() ?: ""
        val curContent = binding.noteContent.text?.toString() ?: ""
        val top = undoStack.lastOrNull()

        if (top != null && top.title == curTitle && top.content == curContent) {
            return
        }

        redoStack.clear()

        val now = System.currentTimeMillis()
        val focusedId = currentFocus?.id ?: 0
        val titleSel = binding.noteTitle.selectionStart.coerceAtLeast(0)
        val contentSel = binding.noteContent.selectionStart.coerceAtLeast(0)
        val newState = NoteHistoryState(curTitle, curContent, focusedId, titleSel, contentSel)

        val isBatchExpired = (now - lastEditTime > 750L)
        val isDelete = (curTitle.length < (top?.title?.length ?: 0) || curContent.length < (top?.content?.length ?: 0))
        val fieldChanged = (focusedId != lastFocusedId && lastFocusedId != 0)
        val isPasteOrCut = (abs(curTitle.length - (top?.title?.length ?: 0)) > 1 || abs(curContent.length - (top?.content?.length ?: 0)) > 1)
        val isNewLineOrSpace = (curTitle.endsWith(" ") || curTitle.endsWith("\n") || curContent.endsWith(" ") || curContent.endsWith("\n"))

        if (undoStack.size <= 1 || isBatchExpired || isPasteOrCut || fieldChanged || (isDelete != lastWasDelete) || isNewLineOrSpace) {
            undoStack.addLast(newState)
            if (undoStack.size > 150) {
                undoStack.removeFirst()
            }
        } else {
            undoStack.removeLast()
            undoStack.addLast(newState)
        }

        lastEditTime = now
        lastFocusedId = focusedId
        lastWasDelete = isDelete
        updateUndoRedoMenuState()
    }

    private fun undo() {
        if (undoStack.size <= 1) return
        val currentState = undoStack.removeLast()
        redoStack.addLast(currentState)
        val targetState = undoStack.last()
        applyState(targetState)
        lastEditTime = 0L
        updateUndoRedoMenuState()
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val targetState = redoStack.removeLast()
        undoStack.addLast(targetState)
        applyState(targetState)
        lastEditTime = 0L
        updateUndoRedoMenuState()
    }

    private fun applyState(state: NoteHistoryState) {
        isApplyingState = true
        try {
            if (binding.noteTitle.text?.toString() != state.title) {
                binding.noteTitle.setText(state.title)
            }
            if (binding.noteContent.text?.toString() != state.content) {
                binding.noteContent.setText(state.content)
            }

            if (state.focusedId == binding.noteTitle.id) {
                binding.noteTitle.requestFocus()
                val sel = state.titleSelection.coerceIn(0, binding.noteTitle.text?.length ?: 0)
                binding.noteTitle.setSelection(sel)
            } else if (state.focusedId == binding.noteContent.id) {
                binding.noteContent.requestFocus()
                val sel = state.contentSelection.coerceIn(0, binding.noteContent.text?.length ?: 0)
                binding.noteContent.setSelection(sel)
            }
        } finally {
            isApplyingState = false
        }
    }

    private fun updateUndoRedoMenuState() {
        val canUndo = undoStack.size > 1
        val canRedo = redoStack.isNotEmpty()

        val undoItem = binding.toolBar.menu.findItem(R.id.action_undo)
        val redoItem = binding.toolBar.menu.findItem(R.id.action_redo)

        undoItem?.let {
            it.isEnabled = canUndo
            it.icon?.mutate()?.alpha = if (canUndo) 255 else 90
        }
        redoItem?.let {
            it.isEnabled = canRedo
            it.icon?.mutate()?.alpha = if (canRedo) 255 else 90
        }
    }

    private fun saveNote() {
        val title = binding.noteTitle.text.toString().trim()
        val content = binding.noteContent.text.toString()

        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.title_cannot_be_empty), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val newFile = File(notesDir, "$title.txt")
                val oldPath = noteFile?.absolutePath
                val isRename = noteFile != null && oldPath != newFile.absolutePath

                if (isRename && newFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@EditNotesActivity,
                            getString(R.string.a_note_with_this_title_already_exists_in_this_folder),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    if (isRename) {
                        val hiddenFile = hiddenFileRepository.getHiddenFileByPath(oldPath!!)
                        if (hiddenFile != null) {
                            hiddenFileRepository.updateEncryptionStatus(
                                filePath = oldPath,
                                newFilePath = newFile.absolutePath,
                                encryptedFileName = newFile.name,
                                isEncrypted = hiddenFile.isEncrypted
                            )
                        }
                        File(oldPath).delete()
                    }
                    newFile.writeText(content)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditNotesActivity,
                        getString(R.string.note_saved), Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@EditNotesActivity,
                        getString(R.string.failed_to_save_note), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

