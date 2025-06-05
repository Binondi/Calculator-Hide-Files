package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.adapters.FolderAdapter
import devs.org.calculator.adapters.ListFolderAdapter
import devs.org.calculator.databinding.ActivityHiddenBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import devs.org.calculator.utils.PrefsUtil
import java.io.File

class HiddenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHiddenBinding
    private lateinit var fileManager: FileManager
    private lateinit var folderManager: FolderManager
    private lateinit var dialogUtil: DialogUtil
    private var currentFolder: File? = null
    private var folderAdapter: FolderAdapter? = null
    private var listFolderAdapter: ListFolderAdapter? = null
    private val hiddenDir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)
    private val prefs:PrefsUtil by lazy { PrefsUtil(this) }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)
        folderManager = FolderManager()
        dialogUtil = DialogUtil(this)


        setupInitialUIState()
        setupClickListeners()
        setupBackPressedHandler()

        fileManager.askPermission(this)

        refreshCurrentView()
    }

    private fun setupInitialUIState() {

        binding.addFolder.visibility = View.VISIBLE
        binding.settings.visibility = View.VISIBLE
        binding.folderOrientation.visibility = View.VISIBLE
        binding.deleteSelected.visibility = View.GONE
        binding.delete.visibility = View.GONE
        binding.menuButton.visibility = View.GONE
    }

    private fun setupClickListeners() {


        binding.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }


        binding.back.setOnClickListener {
            handleBackPress()
        }

        binding.addFolder.setOnClickListener {
            createNewFolder()
        }

        binding.deleteSelected.setOnClickListener {
            deleteSelectedItems()
        }

        binding.delete.setOnClickListener {
            deleteSelectedItems()
        }

        binding.edit.setOnClickListener {
            editSelectedFolder()
        }

        binding.folderOrientation.setOnClickListener {
            val currentIsList = PrefsUtil(this).getBoolean("isList", false)
            val newIsList = !currentIsList

            if (newIsList) {
                showListUI()
                PrefsUtil(this).setBoolean("isList", true)
                binding.folderOrientation.setIconResource(R.drawable.ic_grid)
            } else {
                showGridUI()
                PrefsUtil(this).setBoolean("isList", false)
                binding.folderOrientation.setIconResource(R.drawable.ic_list)
            }
        }
    }

    private fun showGridUI() {
        listFoldersInHiddenDirectory()
    }

    private fun showListUI() {
        listFoldersInHiddenDirectoryListStyle()
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
        } catch (e: Exception) {

            showEmptyState()
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }


    private fun createNewFolder() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null)
        val inputEditText = dialogView.findViewById<EditText>(R.id.editText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Enter Folder Name To Create")
            .setView(dialogView)
            .setPositiveButton("Create") { dialog, _ ->
                val newName = inputEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    try {
                        folderManager.createFolder(hiddenDir, newName)
                        refreshCurrentView()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@HiddenActivity,
                            "Failed to create folder",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        setupFlagSecure()
    }

    private fun setupFlagSecure() {
        if (prefs.getBoolean("screenshot_restriction", true)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
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
        } catch (e: Exception) {
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
                startActivity(Intent(this,ViewFolderActivity::class.java).putExtra("folder",clickedFolder.toString()))
            },
            onFolderLongClick = {
                enterFolderSelectionMode()
            },
            onSelectionModeChanged = { isSelectionMode ->
                handleFolderSelectionModeChange(isSelectionMode)
            },
            onSelectionCountChanged = { _ ->
                updateEditButtonVisibility()
            }
        )
        binding.recyclerView.adapter = folderAdapter
        folderAdapter?.submitList(folders)

        if (folderAdapter?.isInSelectionMode() != true) {
            showFolderViewIcons()
        }
    }
    private fun showFolderListStyle(folders: List<File>) {
        binding.noItems.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        folderAdapter = null

        binding.recyclerView.layoutManager = GridLayoutManager(this, 1)
        listFolderAdapter = ListFolderAdapter(
            onFolderClick = { clickedFolder ->
                startActivity(Intent(this,ViewFolderActivity::class.java).putExtra("folder",clickedFolder.toString()))
            },
            onFolderLongClick = {
                enterFolderSelectionMode()
            },
            onSelectionModeChanged = { isSelectionMode ->
                handleFolderSelectionModeChange(isSelectionMode)
            },
            onSelectionCountChanged = { _ ->
                updateEditButtonVisibility()
            }
        )
        binding.recyclerView.adapter = listFolderAdapter
        listFolderAdapter?.submitList(folders)

        if (listFolderAdapter?.isInSelectionMode() != true) {
            showFolderViewIcons()
        }
    }

    private fun updateEditButtonVisibility() {
        val selectedCount = when {
            folderAdapter != null -> folderAdapter?.getSelectedItems()?.size ?: 0
            listFolderAdapter != null -> listFolderAdapter?.getSelectedItems()?.size ?: 0
            else -> 0
        }
        binding.edit.visibility = if (selectedCount == 1) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        binding.noItems.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun enterFolderSelectionMode() {
        showFolderSelectionIcons()
    }


    private fun refreshCurrentView() {
        val isList = PrefsUtil(this).getBoolean("isList", false)
        if (isList) {
            binding.folderOrientation.setIconResource(R.drawable.ic_grid)
            listFoldersInHiddenDirectoryListStyle()
        } else {
            binding.folderOrientation.setIconResource(R.drawable.ic_list)
            listFoldersInHiddenDirectory()
        }
    }


    private fun deleteSelectedItems() {
        deleteSelectedFolders()
    }

    private fun deleteSelectedFolders() {
        val selectedFolders = when {
            folderAdapter != null -> folderAdapter?.getSelectedItems() ?: emptyList()
            listFolderAdapter != null -> listFolderAdapter?.getSelectedItems() ?: emptyList()
            else -> emptyList()
        }

        if (selectedFolders.isNotEmpty()) {
            dialogUtil.showMaterialDialog(
                getString(R.string.delete_items),
               "${getString(R.string.are_you_sure_you_want_to_delete_selected_items)}\n ${getString(R.string.folder_will_be_deleted_permanently)}" ,
                getString(R.string.delete),
                getString(R.string.cancel),
                object : DialogUtil.DialogCallback {
                    override fun onPositiveButtonClicked() {
                        performFolderDeletion(selectedFolders)
                    }

                    override fun onNegativeButtonClicked() {

                    }

                    override fun onNaturalButtonClicked() {

                    }
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

        exitFolderSelectionMode()

        refreshCurrentView()
    }

    private fun handleBackPress() {

        if (folderAdapter?.onBackPressed() == true || listFolderAdapter?.onBackPressed() == true) {
            return
        }

        if (currentFolder != null) {
            navigateBackToFolders()
        } else {
            finish()
        }
    }

    private fun navigateBackToFolders() {
        currentFolder = null

        refreshCurrentView()

        binding.folderName.text = getString(R.string.hidden_space)

        showFolderViewIcons()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun showFolderViewIcons() {
        binding.folderOrientation.visibility = View.VISIBLE
        binding.settings.visibility = View.VISIBLE
        binding.delete.visibility = View.GONE
        binding.deleteSelected.visibility = View.GONE
        binding.menuButton.visibility = View.GONE
        binding.addFolder.visibility = View.VISIBLE
        binding.edit.visibility = View.GONE
        if (currentFolder == null) {

            binding.addFolder.visibility = View.VISIBLE
        }
    }
    private fun showFolderSelectionIcons() {
        binding.folderOrientation.visibility = View.GONE
        binding.settings.visibility = View.GONE
        binding.delete.visibility = View.VISIBLE
        binding.deleteSelected.visibility = View.VISIBLE
        binding.menuButton.visibility = View.GONE
        binding.addFolder.visibility = View.GONE
        updateEditButtonVisibility()
    }
    private fun exitFolderSelectionMode() {
        showFolderViewIcons()
    }

    private fun handleFolderSelectionModeChange(isSelectionMode: Boolean) {
        if (!isSelectionMode) {
            exitFolderSelectionMode()
        } else {
            enterFolderSelectionMode()
        }
        updateEditButtonVisibility()
    }

    private fun editSelectedFolder() {
        val selectedFolders = when {
            folderAdapter != null -> folderAdapter?.getSelectedItems() ?: emptyList()
            listFolderAdapter != null -> listFolderAdapter?.getSelectedItems() ?: emptyList()
            else -> emptyList()
        }

        if (selectedFolders.size != 1) {
            Toast.makeText(this, "Please select exactly one folder to edit", Toast.LENGTH_SHORT).show()
            return
        }

        val folder = selectedFolders[0]
        showEditFolderDialog(folder)
    }

    private fun showEditFolderDialog(folder: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null)
        val inputEditText = dialogView.findViewById<EditText>(R.id.editText)
        inputEditText.setText(folder.name)
        inputEditText.selectAll()

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Folder")
            .setView(dialogView)
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = inputEditText.text.toString().trim()
                if (newName.isNotEmpty() && newName != folder.name) {
                    if (isValidFolderName(newName)) {
                        renameFolder(folder, newName)
                    } else {
                        Toast.makeText(this, "Invalid folder name", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
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
                Toast.makeText(this, "Folder with this name already exists", Toast.LENGTH_SHORT).show()
                return
            }

            if (oldFolder.renameTo(newFolder)) {
                folderAdapter?.clearSelection()
                listFolderAdapter?.clearSelection()
                exitFolderSelectionMode()

                refreshCurrentView()
            } else {
                Toast.makeText(this, "Failed to rename folder", Toast.LENGTH_SHORT).show()
            }
        }
    }
}