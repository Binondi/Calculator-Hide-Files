package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.HiddenFileRepository
import devs.org.calculator.databinding.ActivitySettingsBinding
import devs.org.calculator.databinding.MigrationProgressDialogBinding
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        val hasKey = SecurityUtils.hasCustomKey(this)
        binding.customKeyTitleText.text = if (hasKey) {
            getString(R.string.change_encryption_key)
        } else {
            getString(R.string.set_custom_encryption_key)
        }
        binding.customKeyDescriptionText.text = if (hasKey) {
            getString(R.string.key_configured_subtitle)
        } else {
            getString(R.string.no_key_configured)
        }
        binding.screenshotRestrictionSwitch.isChecked = prefs.getBoolean("screenshot_restriction", true)
        binding.encryptionSwitch.isChecked = prefs.getBoolean("encryption", false) && hasKey
        binding.gotomain.isChecked = prefs.getBoolean("is_vault_enabled", true)
    }

    private fun setupListeners() {
        binding.encryptionSwitch.setOnClickListener {
            val isChecked = binding.encryptionSwitch.isChecked
            if (isChecked) {
                if (!SecurityUtils.hasCustomKey(this)) {
                    binding.encryptionSwitch.isChecked = false
                    SecurityUtils.showSetKeyBottomSheet(
                        activity = this,
                        title = getString(R.string.encryption_key_required_title),
                        subtitle = getString(R.string.encryption_key_required_message),
                        onKeySet = {
                            binding.encryptionSwitch.isChecked = true
                            prefs.setBoolean("encryption", true)
                            updateUI()
                        },
                        onDismissed = {
                            binding.encryptionSwitch.isChecked = false
                            prefs.setBoolean("encryption", false)
                            updateUI()
                        }
                    )
                } else {
                    prefs.setBoolean("encryption", true)
                }
            } else {
                prefs.setBoolean("encryption", false)
            }
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
        binding.gotomain.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("is_vault_enabled", isChecked)
        }

        binding.customKeyStatusLayout.setOnClickListener {
            SecurityUtils.showSetKeyBottomSheet(
                activity = this,
                onKeySet = {
                    updateUI()
                },
                onDismissed = {
                    updateUI()
                }
            )
        }

        binding.migrateEncryptionLayout.setOnClickListener {
            val hiddenDir = File(Environment.getExternalStorageDirectory(), FileManager.HIDDEN_DIR)
            lifecycleScope.launch(Dispatchers.IO) {
                val legacyFiles = SecurityUtils.findLegacyFiles(hiddenDir)
                withContext(Dispatchers.Main) {
                    if (legacyFiles.isEmpty()) {
                        Toast.makeText(this@SettingsActivity, getString(R.string.no_legacy_files_to_migrate), Toast.LENGTH_SHORT).show()
                    } else {
                        SecurityUtils.showMigrationBottomSheet(this@SettingsActivity, legacyFiles)
                    }
                }
            }
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

    private fun updateUI() {
        val hasKey = SecurityUtils.hasCustomKey(this)
        binding.customKeyTitleText.text = if (hasKey) {
            getString(R.string.change_encryption_key)
        } else {
            getString(R.string.set_custom_encryption_key)
        }
        binding.encryptionSwitch.isChecked = prefs.getBoolean("encryption", false) && hasKey
        binding.customKeyDescriptionText.text = if (hasKey) {
            getString(R.string.key_configured_subtitle)
        } else {
            getString(R.string.no_key_configured)
        }
    }
}
