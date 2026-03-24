package devs.org.calculator.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

class EditNotesActivity : BaseActivity() {
    private lateinit var binding: ActivityEditNotesBinding
    private var noteFile: File? = null
    private lateinit var notesDir: File
    private val hiddenFileRepository: HiddenFileRepository by lazy {
        HiddenFileRepository(AppDatabase.getDatabase(this).hiddenFileDao())
    }

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
            loadNote()
        }

        binding.back.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { saveNote() }
    }

    private fun loadNote() {
        noteFile?.let {
            val title = it.nameWithoutExtension
            val content = try { it.readText() } catch (_: Exception) { "" }
            binding.noteTitle.setText(title)
            binding.noteContent.setText(content)
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
                        Toast.makeText(this@EditNotesActivity,
                            getString(R.string.a_note_with_this_title_already_exists_in_this_folder),
                            Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@EditNotesActivity,
                        getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditNotesActivity,
                        getString(R.string.failed_to_save_note), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
