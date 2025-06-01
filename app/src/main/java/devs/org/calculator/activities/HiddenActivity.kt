package devs.org.calculator.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import devs.org.calculator.R
import devs.org.calculator.adapters.FileAdapter
import devs.org.calculator.adapters.FolderAdapter
import devs.org.calculator.callbacks.DialogActionsCallback
import devs.org.calculator.databinding.ActivityHiddenBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.FileManager.Companion.HIDDEN_DIR
import devs.org.calculator.utils.FolderManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class HiddenActivity : AppCompatActivity() {

    private var isFabOpen = false
    private lateinit var fabOpen: Animation
    private lateinit var fabClose: Animation
    private lateinit var rotateOpen: Animation
    private lateinit var rotateClose: Animation

    private lateinit var binding: ActivityHiddenBinding
    private val fileManager = FileManager(this, this)
    private val folderManager = FolderManager(this)
    private val dialogUtil = DialogUtil(this)

    private val STORAGE_PERMISSION_CODE = 101
    private val PICK_FILE_REQUEST_CODE = 102
    private var currentFolder: File? = null
    private var folderAdapter: FolderAdapter? = null
    val hiddenDir = File(Environment.getExternalStorageDirectory(), HIDDEN_DIR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHiddenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //initialized animations for fabs
        fabOpen = AnimationUtils.loadAnimation(this, R.anim.fab_open)
        fabClose = AnimationUtils.loadAnimation(this, R.anim.fab_close)
        rotateOpen = AnimationUtils.loadAnimation(this, R.anim.rotate_open)
        rotateClose = AnimationUtils.loadAnimation(this, R.anim.rotate_close)

        binding.fabExpend.visibility = View.GONE
        binding.addImage.visibility = View.GONE
        binding.addVideo.visibility = View.GONE
        binding.addAudio.visibility = View.GONE
        binding.addDocument.visibility = View.GONE
        binding.addFolder.visibility = View.VISIBLE

        binding.fabExpend.setOnClickListener {
            if (isFabOpen) {
                closeFabs()

            } else {
                openFabs()

            }
        }

        binding.addImage.setOnClickListener { openFilePicker("image/*") }
        binding.addVideo.setOnClickListener { openFilePicker("video/*") }
        binding.addAudio.setOnClickListener { openFilePicker("audio/*") }
        binding.addDocument.setOnClickListener { openFilePicker("*/*") }
        binding.addFolder.setOnClickListener {
            dialogUtil.createInputDialog(
                title = "Enter Folder Name To Create",
                hint = "",
                callback = object : DialogUtil.InputDialogCallback {
                    override fun onPositiveButtonClicked(input: String) {
                        fileManager.askPermission(this@HiddenActivity)
                        folderManager.createFolder( hiddenDir,input )
                        listFoldersInHiddenDirectory()
                    }
                }
            )
        }

        fileManager.askPermission(this)
        listFoldersInHiddenDirectory()
    }

    private fun openFilePicker(mimeType: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("HiddenActivity", "READ/WRITE_EXTERNAL_STORAGE permission granted via onRequestPermissionsResult")
                listFoldersInHiddenDirectory()
            } else {
                Log.d("HiddenActivity", "READ/WRITE_EXTERNAL_STORAGE permission denied via onRequestPermissionsResult")
                // Handle denied case, maybe show a message or disable functionality
            }
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    listFoldersInHiddenDirectory()
                } else {
                    // Handle denied case
                }
            }
        } else if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                Log.d("HiddenActivity", "Selected file URI: $uri")
                copyFileToHiddenDirectory(uri)
            }
        }
    }

    private fun listFoldersInHiddenDirectory() {
        if (hiddenDir.exists() && hiddenDir.isDirectory) {
            val folders = folderManager.getFoldersInDirectory(hiddenDir)

            if (folders.isNotEmpty()) {
                binding.noItems.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE

                // Initialize adapter only once
                if (folderAdapter == null) {
                    binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
                    folderAdapter = FolderAdapter(
                        onFolderClick = { clickedFolder ->
                            openFolder(clickedFolder)
                        },
                        onFolderLongClick = { folder ->
                            // go to selection mode
                        }
                    )
                    binding.recyclerView.adapter = folderAdapter
                }

                // Submit new list to adapter - DiffUtil will handle the comparison
                folderAdapter?.submitList(folders)
            } else {
                binding.noItems.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
        } else if (!hiddenDir.exists()) {
            fileManager.getHiddenDirectory()
        } else {
            Log.e("HiddenActivity", "Hidden directory is not a directory: ${hiddenDir.absolutePath}")
        }
    }

    private fun openFolder(folder: File) {
        Log.d("HiddenActivity", "Opening folder: ${folder.name}")
        currentFolder = folder
        binding.addFolder.visibility = View.GONE
        binding.fabExpend.visibility = View.VISIBLE

        // Read files in the clicked folder and update RecyclerView
        val files = folderManager.getFilesInFolder(folder)
        Log.d("HiddenActivity", "Found ${files.size} files in ${folder.name}")

        if (files.isNotEmpty()) {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 3)

            val fileAdapter = FileAdapter(this, this, folder).apply {
                // Set up the callback for file operations
                fileOperationCallback = object : FileAdapter.FileOperationCallback {
                    override fun onFileDeleted(file: File) {
                        // Refresh the file list
                        refreshCurrentFolder()
                    }

                    override fun onFileRenamed(oldFile: File, newFile: File) {
                        // Refresh the file list
                        refreshCurrentFolder()
                    }

                    override fun onRefreshNeeded() {
                        // Refresh the file list
                        refreshCurrentFolder()
                    }
                }

                submitList(files)
            }

            binding.recyclerView.adapter = fileAdapter
            binding.recyclerView.visibility = View.VISIBLE
            binding.noItems.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.noItems.visibility = View.VISIBLE
        }
    }

    private fun refreshCurrentFolder() {
        currentFolder?.let { folder ->
            val files = folderManager.getFilesInFolder(folder)
            (binding.recyclerView.adapter as? FileAdapter)?.submitList(files)

            if (files.isEmpty()) {
                binding.recyclerView.visibility = View.GONE
                binding.noItems.visibility = View.VISIBLE
            } else {
                binding.recyclerView.visibility = View.VISIBLE
                binding.noItems.visibility = View.GONE
            }
        }
    }

    private fun openFabs() {
        binding.addImage.startAnimation(fabOpen)
        binding.addVideo.startAnimation(fabOpen)
        binding.addAudio.startAnimation(fabOpen)
        binding.addDocument.startAnimation(fabOpen)
        binding.addFolder.startAnimation(fabOpen)
        binding.fabExpend.startAnimation(rotateOpen)

        binding.addImage.visibility = View.VISIBLE
        binding.addVideo.visibility = View.VISIBLE
        binding.addAudio.visibility = View.VISIBLE
        binding.addDocument.visibility = View.VISIBLE
        binding.addFolder.visibility = View.VISIBLE // Keep this visible if in folder list, but should be GONE when showing files

        isFabOpen = true
        Handler(Looper.getMainLooper()).postDelayed({
            binding.fabExpend.setImageResource(R.drawable.wrong)
        },200)
    }

    private fun closeFabs() {
        binding.addImage.startAnimation(fabClose)
        binding.addVideo.startAnimation(fabClose)
        binding.addAudio.startAnimation(fabClose)
        binding.addDocument.startAnimation(fabClose)
        binding.addFolder.startAnimation(fabClose)
        binding.fabExpend.startAnimation(rotateClose)

        binding.addImage.visibility = View.INVISIBLE
        binding.addVideo.visibility = View.INVISIBLE
        binding.addAudio.visibility = View.INVISIBLE
        binding.addDocument.visibility = View.INVISIBLE
        binding.addFolder.visibility = View.INVISIBLE

        isFabOpen = false
        binding.fabExpend.setImageResource(R.drawable.ic_add)
    }

    private fun copyFileToHiddenDirectory(uri: Uri) {
        currentFolder?.let { destinationFolder ->
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val fileName = getFileNameFromUri(uri) ?: "unknown_file"
                val destinationFile = File(destinationFolder, fileName)

                inputStream?.use { input ->
                    val outputStream: OutputStream = FileOutputStream(destinationFile)
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("HiddenActivity", "File copied to: ${destinationFile.absolutePath}")
                // Refresh the file list in the RecyclerView
                currentFolder?.let { openFolder(it) }
            } catch (e: Exception) {
                Log.e("HiddenActivity", "Error copying file", e)
                // TODO: Show error message to user
            }
        } ?: run {
            Log.e("HiddenActivity", "Current folder is null, cannot copy file")
            // TODO: Show error message to user
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use { it ->
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                it.moveToFirst()
                name = it.getString(nameIndex)
            }
        }
        return name
    }

    private fun showFileOptionsDialog(file: File) {
        val options = arrayOf("Delete", "Rename", "Share")
        AlertDialog.Builder(this)
            .setTitle("Choose an action for ${file.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> deleteFile(file)
                    1 -> renameFile(file)
                    2 -> shareFile(file)
                }
            }
            .create()
            .show()
    }

    private fun deleteFile(file: File) {
        Log.d("HiddenActivity", "Deleting file: ${file.name}")
        if (file.exists()) {
            if (file.delete()) {
                Log.d("HiddenActivity", "File deleted successfully")
                // Refresh the file list in the RecyclerView
                currentFolder?.let { openFolder(it) }
            } else {
                Log.e("HiddenActivity", "Failed to delete file: ${file.absolutePath}")
                // TODO: Show error message to user
            }
        } else {
            Log.e("HiddenActivity", "File not found for deletion: ${file.absolutePath}")
            // TODO: Show error message to user
        }
    }

    private fun renameFile(file: File) {
        Log.d("HiddenActivity", "Renaming file: ${file.name}")
        val inputEditText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Rename ${file.name}")
            .setView(inputEditText)
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = inputEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val parentDir = file.parentFile
                    if (parentDir != null) {
                        val newFile = File(parentDir, newName)
                        if (file.renameTo(newFile)) {
                            Log.d("HiddenActivity", "File renamed to: ${newFile.name}")
                            currentFolder?.let { openFolder(it) }
                        } else {
                            Log.e("HiddenActivity", "Failed to rename file: ${file.absolutePath} to ${newFile.absolutePath}")
                        }
                    } else {
                        Log.e("HiddenActivity", "Parent directory is null for renaming: ${file.absolutePath}")
                    }
                } else {
                    Log.d("HiddenActivity", "New file name is empty")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    private fun shareFile(file: File) {
        val uri: Uri? = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        uri?.let { fileUri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = contentResolver.getType(fileUri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
        } ?: run {
            Log.e("HiddenActivity", "Could not get URI for sharing file: ${file.absolutePath}")
            //Show error message to user
        }
    }

    override fun onBackPressed() {
        if (currentFolder != null) {
            currentFolder = null
            if (isFabOpen) {
                closeFabs()
            }
            if (folderAdapter != null) {
                binding.recyclerView.adapter = folderAdapter
            }
            listFoldersInHiddenDirectory()
            binding.fabExpend.visibility = View.GONE
            binding.addImage.visibility = View.GONE
            binding.addVideo.visibility = View.GONE
            binding.addAudio.visibility = View.GONE
            binding.addDocument.visibility = View.GONE
            binding.addFolder.visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }
}