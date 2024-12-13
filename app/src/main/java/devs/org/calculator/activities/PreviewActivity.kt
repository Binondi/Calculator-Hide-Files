package devs.org.calculator.activities

import ImagePreviewAdapter
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import devs.org.calculator.databinding.ActivityPreviewBinding
import devs.org.calculator.utils.FileManager
import java.io.File

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_TYPE = "file_type"
    }

    private lateinit var binding: ActivityPreviewBinding
    private var currentPosition: Int = 0
    private lateinit var files: List<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        currentPosition = intent.getIntExtra("position", 0)
        val filePath = intent.getStringExtra("file_path") ?: return
        val file = File(filePath)
        files = file.parentFile?.listFiles()?.toList() ?: listOf(file)

        setupImagePreview()
    }

    private fun setupImagePreview() {
        val adapter = ImagePreviewAdapter(this, files)
        binding.viewPager.adapter = adapter
        binding.viewPager.currentItem = currentPosition
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}