package devs.org.calculator.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import devs.org.calculator.R
import devs.org.calculator.callbacks.FileProcessCallback

class ImageGalleryActivity : BaseGalleryActivity(), FileProcessCallback  {
    override val fileType = FileManager.FileType.IMAGE
    private val STORAGE_PERMISSION_CODE = 100

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupIntentSenderLauncher()
        setupFabButton()

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){
            if (it.resultCode != RESULT_OK) Toast.makeText(this,
                getString(R.string.failed_to_hide_unhide_photo), Toast.LENGTH_SHORT).show()
        }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val clipData = result.data?.clipData
                val uriList = mutableListOf<Uri>()

                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        uriList.add(uri)
                    }
                } else {
                    result.data?.data?.let { uriList.add(it) }
                }

                if (uriList.isNotEmpty()) {
                    lifecycleScope.launch {
                        FileManager(this@ImageGalleryActivity, this@ImageGalleryActivity)
                            .processMultipleFiles(uriList, fileType,this@ImageGalleryActivity )
                    }
                } else {
                    Toast.makeText(this, getString(R.string.no_files_selected), Toast.LENGTH_SHORT).show()
                }
            }
        }
        askPermissiom()
    }

    override fun onFilesProcessedSuccessfully(copiedFiles: List<File>) {
        Toast.makeText(this@ImageGalleryActivity,  "${copiedFiles.size} ${getString(R.string.images_hidden_successfully)}", Toast.LENGTH_SHORT).show()
        loadFiles()
    }

    override fun onFileProcessFailed() {
        Toast.makeText(this@ImageGalleryActivity,
            getString(R.string.failed_to_hide_images), Toast.LENGTH_SHORT).show()
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

    private fun askPermissiom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if (!Environment.isExternalStorageManager()){
                val intent = Intent().setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
        else {
            checkAndRequestStoragePermission()
        }
    }

    private fun checkAndRequestStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_CODE
            )
        } else {
            //storage permission granted
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                    getString(R.string.storage_permissions_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this,
                    getString(R.string.storage_permissions_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFabButton() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            pickImageLauncher.launch(intent)
        }
    }

    override fun openPreview() {
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("type", fileType)
        }
        startActivity(intent)
    }
}