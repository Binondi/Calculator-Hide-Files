package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivitySettingsBinding
import devs.org.calculator.utils.SecurityUtils

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var DEV_GITHUB_URL = ""
    private var GITHUB_URL = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        DEV_GITHUB_URL = getString(R.string.github_profile)
        GITHUB_URL = getString(R.string.calculator_hide_files, DEV_GITHUB_URL)
        setupUI()
        loadSettings()
        setupListeners()
    }

    private fun setupUI() {
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }


    private fun loadSettings() {

        val isUsingCustomKey = SecurityUtils.isUsingCustomKey(this)
        binding.customKeyStatus.isChecked = isUsingCustomKey
        binding.screenshotRestrictionSwitch.isChecked = prefs.getBoolean("screenshot_restriction", true)
        binding.showFileNames.isChecked = prefs.getBoolean("showFileName", true)
        binding.encryptionSwitch.isChecked = prefs.getBoolean("encryption", false)
    }

    private fun setupListeners() {
        
        binding.encryptionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("encryption", isChecked)
        }
        binding.changePassLayout.setOnClickListener {
            startActivity(Intent(this, SetupPasswordActivity::class.java))
        }

        binding.screenshotRestrictionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("screenshot_restriction", isChecked)
            if (isChecked) {
                enableScreenshotRestriction()
            } else {
                disableScreenshotRestriction()
            }
        }
        binding.showFileNames.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("showFileName", isChecked)
        }

        binding.customKeyStatusLayout.setOnClickListener {
            showCustomKeyDialog()
        }
    }

    private fun enableScreenshotRestriction() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    private fun disableScreenshotRestriction() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun showCustomKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_key, null)
        val keyInput = dialogView.findViewById<EditText>(R.id.keyInput)
        val confirmKeyInput = dialogView.findViewById<EditText>(R.id.confirmKeyInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.set_custom_encryption_key))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.set)) { _, _ ->
                val key = keyInput.text.toString()
                val confirmKey = confirmKeyInput.text.toString()

                if (key.isEmpty()) {
                    Toast.makeText(this, getString(R.string.key_cannot_be_empty), Toast.LENGTH_SHORT).show()
                    updateUI()
                    return@setPositiveButton
                }

                if (key != confirmKey) {
                    Toast.makeText(this, getString(R.string.keys_do_not_match), Toast.LENGTH_SHORT).show()
                    updateUI()
                    return@setPositiveButton
                }

                if (SecurityUtils.setCustomKey(this, key)) {
                    Toast.makeText(this,
                        getString(R.string.custom_key_set_successfully), Toast.LENGTH_SHORT).show()
                    updateUI()
                } else {
                    Toast.makeText(this,
                        getString(R.string.failed_to_set_custom_key), Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            }
            .setNegativeButton(getString(R.string.cancel)){ _, _ ->
                updateUI()
            }
            .setNeutralButton(getString(R.string.delete_key)) { _, _ ->
                SecurityUtils.clearCustomKey(this)
                Toast.makeText(this,
                    getString(R.string.custom_encryption_key_cleared), Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .show()
    }

    private fun updateUI() {
        binding.showFileNames.isChecked = prefs.getBoolean("showFileName", true)
        binding.encryptionSwitch.isChecked = prefs.getBoolean("encryption", false)
        val isUsingCustomKey = SecurityUtils.isUsingCustomKey(this)
        binding.customKeyStatus.isChecked = isUsingCustomKey
    }


}
