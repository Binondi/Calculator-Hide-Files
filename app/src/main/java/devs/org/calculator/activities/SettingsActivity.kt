package devs.org.calculator.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivitySettingsBinding
import devs.org.calculator.update.Update.checkForAppUpdate
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
        val versionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
        } else {
            packageManager.getPackageInfo(packageName, 0).versionName
        }
        binding.version.text = getString(R.string.version, versionName)
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

        binding.dynamicColorsSwitch.isChecked = prefs.getBoolean("dynamic_theme", true)

        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        when (themeMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> {
                binding.chooseThemeButtonToggleGroup.check(R.id.chooseThemeDarkButton)
                binding.chooseThemeImage.setImageResource(R.drawable.baseline_dark_mode)
            }
            AppCompatDelegate.MODE_NIGHT_NO -> {
                binding.chooseThemeButtonToggleGroup.check(R.id.chooseThemeLightButton)
                binding.chooseThemeImage.setImageResource(R.drawable.baseline_light_mode)
            }
            else -> {
                binding.chooseThemeButtonToggleGroup.check(R.id.chooseThemeAutoButton)
                val isSystemDark = (resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                binding.chooseThemeImage.setImageResource(
                    if (isSystemDark) R.drawable.baseline_dark_mode else R.drawable.baseline_light_mode
                )
            }
        }

        val isUsingCustomKey = SecurityUtils.isUsingCustomKey(this)
        binding.customKeyStatus.isChecked = isUsingCustomKey
        binding.screenshotRestrictionSwitch.isChecked = prefs.getBoolean("screenshot_restriction", true)
        binding.showFileNames.isChecked = prefs.getBoolean("showFileName", true)
        binding.encryptionSwitch.isChecked = prefs.getBoolean("encryption", false)
    }

    private fun setupListeners() {

        binding.githubButton.setOnClickListener {
            openUrl(GITHUB_URL)
        }

        binding.devGithubButton.setOnClickListener {
            openUrl(DEV_GITHUB_URL)
        }

        binding.dynamicColorsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (prefs.getBoolean("dynamic_theme", true) != isChecked) {
                prefs.setBoolean("dynamic_theme", isChecked)
                recreate()
            }
        }
        
        binding.encryptionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("encryption", isChecked)
        }

        binding.chooseThemeButtonToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val themeMode = when (checkedId) {
                    R.id.chooseThemeLightButton -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.chooseThemeDarkButton -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                applyThemeMode(themeMode)
            }
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
        binding.checkUpdate.setOnClickListener {
            binding.checkUpdate.visibility = View.GONE
            binding.versionChackingProgress.visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                checkForAppUpdate(this){
                    if (!it) Toast.makeText(this,"Using the latest version",Toast.LENGTH_SHORT).show()
                    binding.checkUpdate.visibility = View.VISIBLE
                    binding.versionChackingProgress.visibility = View.GONE
                }
            },1000)
        }
    }

    private fun applyThemeMode(themeMode: Int) {
        if (prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != themeMode) {
            prefs.setInt("theme_mode", themeMode)
            AppCompatDelegate.setDefaultNightMode(themeMode)
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
