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
import devs.org.calculator.R
import devs.org.calculator.adapters.FileAdapter
import devs.org.calculator.databinding.ActivityGalleryBinding
import devs.org.calculator.utils.FileManager
import java.io.File

abstract class BaseGalleryActivity : AppCompatActivity() {
    protected lateinit var binding: ActivityGalleryBinding
    private lateinit var fileManager: FileManager
    private lateinit var adapter: FileAdapter
    private lateinit var files: List<File>

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            loadFiles()
        } else {
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

        binding.fabAdd.text = when(fileType){
            FileManager.FileType.IMAGE -> {
                getString(R.string.add_image)
            }
            FileManager.FileType.AUDIO -> {
                getString(R.string.add_audio)
            }
            FileManager.FileType.VIDEO -> {
                getString(R.string.add_video)
            }
            FileManager.FileType.DOCUMENT -> {
                getString(R.string.add_files)
            }
        }
        binding.recyclerView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY && binding.fabAdd.isExtended) {

                binding.fabAdd.shrink()
            } else if (scrollY < oldScrollY && !binding.fabAdd.isExtended) {

                binding.fabAdd.extend()
            }
        }
        setupRecyclerView()
        checkPermissionsAndLoadFiles()
    }

    private fun setupIntentSenderLauncher() {
        intentSenderLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                loadFiles()
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
        // permission denied
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2296 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadFiles()
            }
        }
    }
}
