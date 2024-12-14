package devs.org.calculator.activities


import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.adapters.ImagePreviewAdapter
import devs.org.calculator.databinding.ActivityPreviewBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private var currentPosition: Int = 0
    private lateinit var files: List<File>
    private lateinit var type: String
    private lateinit var filetype: FileManager.FileType
    private lateinit var adapter: ImagePreviewAdapter
    private lateinit var fileManager: FileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)

        currentPosition = intent.getIntExtra("position", 0)


        type = intent.getStringExtra("type").toString()

        clickListeners()

        when(type){
            "IMAGE" ->{
                filetype = FileManager.FileType.IMAGE
                binding.title.text = "Preview Images"
            }

            "VIDEO" ->{
                filetype = FileManager.FileType.VIDEO
                binding.title.text = "Preview Videos"
            }

            "AUDIO" ->{
                filetype = FileManager.FileType.AUDIO
                binding.title.text = "Preview Audios"
            }

            else -> {
                filetype = FileManager.FileType.DOCUMENT
                binding.title.text = "Preview Docomnts"
            }
        }
        files = fileManager.getFilesInHiddenDir(filetype)

        setupImagePreview()
    }

    private fun clickListeners() {
        binding.delete.setOnClickListener{
            var fileUri = FileManager.FileManager().getContentUri(this, files[binding.viewPager.currentItem])
            if (fileUri != null) {
                DialogUtil(this, this).showMaterialDialog(
                    "Delete File",
                    "Are you sure you want to delete this file ?",
                    "Delete",
                    "Cancel",
                    fileUri!!
                )
            }
        }
        binding.unHide.setOnClickListener{
            DialogUtil(this, this).showMaterialDialog("Unhide File","Are you sure you want to unhide this file ?", "Unhide", "Cancel")

        }
    }

    private fun setupImagePreview() {
        adapter = ImagePreviewAdapter(this, files,filetype)
        binding.viewPager.adapter = adapter

        val fileUri = Uri.fromFile(files[currentPosition])
        val filesName = FileManager.FileName(this).getFileNameFromUri(fileUri!!).toString()
        binding.viewPager.setCurrentItem(currentPosition, false)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


}