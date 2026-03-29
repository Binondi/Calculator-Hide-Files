package devs.org.calculator.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    private var DEV_GITHUB_URL = ""
    private var GITHUB_URL = ""
    private lateinit var binding : ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.toolBar.setNavigationOnClickListener {
            finish()
        }
        DEV_GITHUB_URL = getString(R.string.github_profile)
        GITHUB_URL = getString(R.string.calculator_hide_files, DEV_GITHUB_URL)

        binding.sourceCode.setOnClickListener {
            openUrl(GITHUB_URL)
        }

        binding.developer.setOnClickListener {
            openUrl(DEV_GITHUB_URL)
        }

        binding.koFiLayout.setOnClickListener {
            openUrl(getString(R.string.ko_fi_url))
        }

        binding.paypalLayout.setOnClickListener {
            copyToClipboard(getString(R.string.paypal_id), getString(R.string.paypal))
        }

        binding.upiLayout.setOnClickListener {
            copyToClipboard(getString(R.string.upi_id), getString(R.string.upi))
        }

        binding.instagramLayout.setOnClickListener {
            openUrl(getString(R.string.instagram_url))
        }

        binding.telegramLayout.setOnClickListener {
            openUrl(getString(R.string.telegram_url))
        }

        binding.emailLayout.setOnClickListener {
            copyToClipboard(getString(R.string.contact_email), getString(R.string.email))
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(binding.root,
                getString(R.string.could_not_open_url), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Snackbar.make(binding.root, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT).show()
    }
}
