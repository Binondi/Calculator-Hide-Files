package devs.org.calculator.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import devs.org.calculator.adapters.FileAdapter
import devs.org.calculator.databinding.ActivityGalleryBinding
import devs.org.calculator.utils.FileManager
import java.io.File

abstract class BaseGalleryActivity : AppCompatActivity() {
    protected lateinit var binding: ActivityGalleryBinding
    protected lateinit var fileManager: FileManager
    protected lateinit var adapter: FileAdapter
    protected lateinit var files: List<File>

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            loadFiles()
        } else {
            // Handle permission denial case
            showPermissionDeniedDialog()
        }
    }

    abstract val fileType: FileManager.FileType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupIntentSenderLauncher()
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)

        setupRecyclerView()
        checkPermissionsAndLoadFiles()
    }

    private fun setupIntentSenderLauncher() {
        intentSenderLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                loadFiles() // Refresh the list after deletion
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = FileAdapter(fileType, this, this)
        binding.recyclerView.adapter = adapter
    }

    private fun checkPermissionsAndLoadFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .addCategory("android.intent.category.DEFAULT")
                    .setData(Uri.parse("package:${applicationContext.packageName}"))
                startActivityForResult(intent, 2296)
            } else {
                loadFiles()
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                storagePermissionLauncher.launch(permissions)
            } else {
                loadFiles()
            }
        }
    }

    protected open fun loadFiles() {
        files = fileManager.getFilesInHiddenDir(fileType)
        adapter.submitList(files)
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    abstract fun openPreview()

    private fun showPermissionDeniedDialog() {
        // Show a dialog or a message informing the user about the importance of permissions
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2296 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadFiles()
            }
        }
    }
}
