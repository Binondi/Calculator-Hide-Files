package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import devs.org.calculator.databinding.ActivityHiddenVaultBinding
import devs.org.calculator.utils.FileManager

class HiddenVaultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHiddenVaultBinding
    private lateinit var fileManager: FileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager(this, this)
        setupNavigation()
    }

    private fun setupNavigation() {
    }
}