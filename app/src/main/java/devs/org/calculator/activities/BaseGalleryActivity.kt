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

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    abstract val fileType: FileManager.FileType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupIntentSenderLauncher()
        checkPermissions()
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)
        setupRecyclerView()
        loadFiles()
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
        adapter = FileAdapter(
            fileType,
            this
        )
        binding.recyclerView.adapter = adapter
    }

    protected fun loadFiles() {
        val files = fileManager.getFilesInHiddenDir(fileType)
        adapter.submitList(files)
    }

    abstract fun openPreview()

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${applicationContext.packageName}")
                startActivityForResult(intent, 2296)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    1001
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2296) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // Permission granted
                    loadFiles()
                }
            }
        }
    }
}