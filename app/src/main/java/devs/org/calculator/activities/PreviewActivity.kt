package devs.org.calculator.activities


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import devs.org.calculator.adapters.ImagePreviewAdapter
import devs.org.calculator.databinding.ActivityPreviewBinding
import java.io.File

class PreviewActivity : AppCompatActivity() {

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
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}