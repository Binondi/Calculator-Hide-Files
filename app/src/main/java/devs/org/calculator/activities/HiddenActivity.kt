package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.adapters.FolderAdapter
import devs.org.calculator.adapters.ListFolderAdapter
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileRepository
import devs.org.calculator.databinding.ActivityHiddenBinding
import devs.org.calculator.databinding.MigrationProgressDialogBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import devs.org.calculator.utils.PrefsUtil
import devs.org.calculator.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HiddenActivity : BaseActivity() {

    private lateinit var binding: ActivityHiddenBinding
    private lateinit var fileManager: FileManager
    private lateinit var folderManager: FolderManager
    private lateinit var dialogUtil: DialogUtil
    private var folderAdapter: FolderAdapter? = null
    private var listFolderAdapter: ListFolderAdapter? = null
    private val hiddenDir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasCheckedMigration = false
    private var hasCheckedDonate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)
        folderManager = FolderManager()
        dialogUtil = DialogUtil(this)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupClickListeners()
        setupBackPressedHandler()

        fileManager.askPermission(this)

        refreshCurrentView()
    }

    override fun onResume() {
        super.onResume()
        checkAndPromptMigration()
    }

    private fun checkAndPromptMigration() {
        if (hasCheckedMigration) return
        hasCheckedMigration = true

        lifecycleScope.launch(Dispatchers.IO) {
            val legacyFiles = SecurityUtils.findLegacyFiles(hiddenDir)
            if (legacyFiles.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    showMigrationBottomSheet(legacyFiles)
                }
            } else {
                withContext(Dispatchers.Main) {
                    checkAndPromptPrompts()
                }
            }
        }
    }

    private fun showMigrationBottomSheet(legacyFiles: List<File>) {
        SecurityUtils.showMigrationBottomSheet(this, legacyFiles) {
            refreshCurrentView()
            checkAndPromptPrompts()
        }
    }

    private fun checkAndPromptPrompts() {
        val prefs = PrefsUtil(this)
        if (!prefs.hasSeenShakeAnnouncement()) {
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    DialogUtil.showShakeFeatureAnnouncement(this) {
                        checkAndPromptDonate()
                    }
                }
            }, 400)
        } else {
            checkAndPromptDonate()
        }
    }

    private fun checkAndPromptDonate() {
        if (hasCheckedDonate) return
        hasCheckedDonate = true

        val prefs = PrefsUtil(this)
        if (prefs.shouldShowDonateOnVaultOpen()) {
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    dialogUtil.showDonateBottomSheet()
                }
            }, 500)
        }
    }

    private fun setupToolbar() {
        binding.toolBar.setNavigationOnClickListener {
            handleBackPress()
        }
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.edit -> {
                    editSelectedFolder()
                    true
                }
                R.id.delete -> {
                    deleteSelectedItems()
                    true
                }
                R.id.list -> {
                    toggleOrientation()
                    true
                }
                else -> false
            }
        }
        updateToolbarMenu()
    }

    private fun toggleOrientation() {
        val currentIsList = PrefsUtil(this).getBoolean("isList", false)
        val newIsList = !currentIsList
        PrefsUtil(this).setBoolean("isList", newIsList)
        refreshCurrentView()
    }

    private fun setupClickListeners() {
        binding.addNotes.setOnClickListener {
            startActivity(Intent(this, NotesActivity::class.java))
        }

        binding.addFolder.setOnClickListener {
            createNewFolder()
        }

        binding.deleteSelected.setOnClickListener {
            deleteSelectedItems()
        }
    }

    private fun refreshCurrentView() {
        val isList = PrefsUtil(this).getBoolean("isList", false)
        val listMenuItem = binding.toolBar.menu.findItem(R.id.list)
        if (isList) {
            listMenuItem?.setIcon(R.drawable.ic_grid)
            listMenuItem?.setTitle(R.string.grid)
            listFoldersInHiddenDirectoryListStyle()
        } else {
            listMenuItem?.setIcon(R.drawable.ic_list)
            listMenuItem?.setTitle(R.string.list)
            listFoldersInHiddenDirectory()
        }
        updateToolbarMenu()
    }

    private fun listFoldersInHiddenDirectory() {
        try {
            if (!hiddenDir.exists()) {
                fileManager.getHiddenDirectory()
            }

            if (hiddenDir.exists() && hiddenDir.isDirectory) {
                val folders = folderManager.getFoldersInDirectory(hiddenDir)
                if (folders.isNotEmpty()) {
                    showFolderList(folders)
                } else {
                    showEmptyState()
                }
            } else {
                showEmptyState()
            }
        } catch (_: Exception) {
            showEmptyState()
        }
    }

    private fun listFoldersInHiddenDirectoryListStyle() {
        try {
            if (!hiddenDir.exists()) {
                fileManager.getHiddenDirectory()
            }

            if (hiddenDir.exists() && hiddenDir.isDirectory) {
                val folders = folderManager.getFoldersInDirectory(hiddenDir)
                if (folders.isNotEmpty()) {
                    showFolderListStyle(folders)
                } else {
                    showEmptyState()
                }
            } else {
                showEmptyState()
            }
        } catch (_: Exception) {
            showEmptyState()
        }
    }

    private fun showFolderList(folders: List<File>) {
        binding.noItems.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        listFolderAdapter = null

        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        folderAdapter = FolderAdapter(
            onFolderClick = { clickedFolder ->
                startActivity(Intent(this, ViewFolderActivity::class.java).putExtra("folder", clickedFolder.toString()))
            },
            onFolderLongClick = {
                // Sync UI handled by onSelectionModeChanged
            },
            onSelectionModeChanged = { isSelectionMode ->
                handleFolderSelectionModeChange(isSelectionMode)
            },
            onSelectionCountChanged = { _ ->
                updateToolbarMenu()
            }
        )
        binding.recyclerView.adapter = folderAdapter
        folderAdapter?.submitList(folders)
        handleFolderSelectionModeChange(folderAdapter?.isInSelectionMode() == true)
    }

    private fun showFolderListStyle(folders: List<File>) {
        binding.noItems.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        folderAdapter = null

        binding.recyclerView.layoutManager = GridLayoutManager(this, 1)
        listFolderAdapter = ListFolderAdapter(
            onFolderClick = { clickedFolder ->
                startActivity(Intent(this, ViewFolderActivity::class.java).putExtra("folder", clickedFolder.toString()))
            },
            onFolderLongClick = {
                // Sync UI handled by onSelectionModeChanged
            },
            onSelectionModeChanged = { isSelectionMode ->
                handleFolderSelectionModeChange(isSelectionMode)
            },
            onSelectionCountChanged = { _ ->
                updateToolbarMenu()
            }
        )
        binding.recyclerView.adapter = listFolderAdapter
        listFolderAdapter?.submitList(folders)
        handleFolderSelectionModeChange(listFolderAdapter?.isInSelectionMode() == true)
    }

    private fun updateToolbarMenu() {
        val menu = binding.toolBar.menu
        val selectedCount = when {
            folderAdapter != null -> folderAdapter?.getSelectedItems()?.size ?: 0
            listFolderAdapter != null -> listFolderAdapter?.getSelectedItems()?.size ?: 0
            else -> 0
        }
        val isSelectionMode = when {
            folderAdapter != null -> folderAdapter?.isInSelectionMode() ?: false
            listFolderAdapter != null -> listFolderAdapter?.isInSelectionMode() ?: false
            else -> false
        }

        menu.findItem(R.id.edit)?.isVisible = selectedCount == 1
        menu.findItem(R.id.delete)?.isVisible = selectedCount > 0
        menu.findItem(R.id.list)?.isVisible = !isSelectionMode
        menu.findItem(R.id.settings)?.isVisible = !isSelectionMode
        
        binding.deleteSelected.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        binding.addFolder.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        binding.addNotes.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
    }

    private fun showEmptyState() {
        binding.noItems.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun handleFolderSelectionModeChange(isSelectionMode: Boolean) {
        updateToolbarMenu()
    }

    private fun createNewFolder() {
        dialogUtil.createInputDialog(
            title = getString(R.string.enter_folder_name_to_create),
            hint = getString(R.string.enter_folder_name_to_create),
            positiveButtonText = getString(R.string.create),
            callback = object : DialogUtil.InputDialogCallback {
                override fun onPositiveButtonClicked(input: String) {
                    val newName = input.trim()
                    if (newName.isNotEmpty()) {
                        try {
                            folderManager.createFolder(hiddenDir, newName)
                            refreshCurrentView()
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@HiddenActivity,
                                getString(R.string.failed_to_create_folder),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }

    private fun deleteSelectedItems() {
        val selectedFolders = when {
            folderAdapter != null -> folderAdapter?.getSelectedItems() ?: emptyList()
            listFolderAdapter != null -> listFolderAdapter?.getSelectedItems() ?: emptyList()
            else -> emptyList()
        }

        if (selectedFolders.isNotEmpty()) {
            dialogUtil.showMaterialDialog(
                getString(R.string.delete_items),
                "${getString(R.string.are_you_sure_you_want_to_delete_selected_items)}\n ${getString(R.string.folder_will_be_deleted_permanently)}",
                getString(R.string.delete),
                getString(R.string.cancel),
                object : DialogUtil.DialogCallback {
                    override fun onPositiveButtonClicked() {
                        performFolderDeletion(selectedFolders)
                    }
                    override fun onNegativeButtonClicked() {}
                    override fun onNaturalButtonClicked() {}
                }
            )
        }
    }

    private fun performFolderDeletion(selectedFolders: List<File>) {
        var allDeleted = true
        selectedFolders.forEach { folder ->
            if (!folderManager.deleteFolder(folder)) {
                allDeleted = false
            }
        }

        val message = if (allDeleted) {
            getString(R.string.folder_deleted_successfully)
        } else {
            getString(R.string.some_items_could_not_be_deleted)
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        folderAdapter?.clearSelection()
        listFolderAdapter?.clearSelection()
        refreshCurrentView()
    }

    private fun editSelectedFolder() {
        val selectedFolders = when {
            folderAdapter != null -> folderAdapter?.getSelectedItems() ?: emptyList()
            listFolderAdapter != null -> listFolderAdapter?.getSelectedItems() ?: emptyList()
            else -> emptyList()
        }

        if (selectedFolders.size != 1) {
            Toast.makeText(this, getString(R.string.please_select_exactly_one_folder_to_edit), Toast.LENGTH_SHORT).show()
            return
        }

        showEditFolderDialog(selectedFolders[0])
    }

    private fun showEditFolderDialog(folder: File) {
        dialogUtil.createInputDialog(
            title = getString(R.string.rename_folder),
            hint = getString(R.string.rename_folder),
            initialText = folder.name,
            positiveButtonText = getString(R.string.rename),
            callback = object : DialogUtil.InputDialogCallback {
                override fun onPositiveButtonClicked(input: String) {
                    val newName = input.trim()
                    if (newName.isNotEmpty() && newName != folder.name) {
                        if (isValidFolderName(newName)) {
                            renameFolder(folder, newName)
                        } else {
                            Toast.makeText(this@HiddenActivity, getString(R.string.invalid_folder_name), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    private fun isValidFolderName(folderName: String): Boolean {
        val forbiddenChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return folderName.isNotBlank() &&
                folderName.none { it in forbiddenChars } &&
                !folderName.startsWith(".") &&
                folderName.length <= 255
    }

    private fun renameFolder(oldFolder: File, newName: String) {
        val parentDir = oldFolder.parentFile
        if (parentDir != null) {
            val newFolder = File(parentDir, newName)
            if (newFolder.exists()) {
                Toast.makeText(this, getString(R.string.folder_with_this_name_already_exists), Toast.LENGTH_SHORT).show()
                return
            }

            if (oldFolder.renameTo(newFolder)) {
                folderAdapter?.clearSelection()
                listFolderAdapter?.clearSelection()
                refreshCurrentView()
            } else {
                Toast.makeText(this, getString(R.string.failed_to_rename_folder), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (folderAdapter?.onBackPressed() == true || listFolderAdapter?.onBackPressed() == true) {
            return
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
