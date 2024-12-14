package devs.org.calculator.activities


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import devs.org.calculator.adapters.ImagePreviewAdapter
import devs.org.calculator.databinding.ActivityPreviewBinding
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

        filetype = when(type){
            "IMAGE" ->{
                FileManager.FileType.IMAGE
            }

            "VIDEO" ->{
                FileManager.FileType.VIDEO
            }

            "AUDIO" ->{
                FileManager.FileType.AUDIO
            }

            else -> {
                FileManager.FileType.DOCUMENT
            }
        }
        files = fileManager.getFilesInHiddenDir(filetype)

        setupImagePreview()
    }

    private fun setupImagePreview() {
        adapter = ImagePreviewAdapter(this, files,filetype)
        binding.viewPager.adapter = adapter

        binding.viewPager.setCurrentItem(currentPosition, false)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}