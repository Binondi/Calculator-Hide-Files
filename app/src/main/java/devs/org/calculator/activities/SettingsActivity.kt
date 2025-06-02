package devs.org.calculator.activities

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private val DEV_GITHUB_URL = "https://github.com/binondi"
    private val GITHUB_URL = "$DEV_GITHUB_URL/calculator-hide-files"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        setupUI()
        loadSettings()
        setupListeners()
    }

    private fun setupUI() {
        binding.back.setOnClickListener {
            onBackPressed()
        }
    }

    private fun loadSettings() {

        binding.dynamicThemeSwitch.isChecked = prefs.getBoolean("dynamic_theme", true)

        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        binding.themeModeSwitch.isChecked = themeMode != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        
        when (themeMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.darkThemeRadio.isChecked = true
            AppCompatDelegate.MODE_NIGHT_NO -> binding.lightThemeRadio.isChecked = true
            else -> binding.systemThemeRadio.isChecked = true
        }

        binding.screenshotRestrictionSwitch.isChecked = prefs.getBoolean("screenshot_restriction", true)
        binding.showFileNames.isChecked = prefs.getBoolean("showFileName", true)

        updateThemeModeVisibility()
    }

    private fun setupListeners() {

        binding.githubButton.setOnClickListener {
            openUrl(GITHUB_URL)
        }

        binding.devGithubButton.setOnClickListener {
            openUrl(DEV_GITHUB_URL)
        }

        binding.dynamicThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dynamic_theme", isChecked).apply()
            if (!isChecked) {
                showThemeModeDialog()
            }
        }


        binding.themeModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.themeRadioGroup.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                binding.systemThemeRadio.isChecked = true
                applyThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val themeMode = when (checkedId) {
                R.id.lightThemeRadio -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.darkThemeRadio -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            applyThemeMode(themeMode)
        }

        binding.screenshotRestrictionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("screenshot_restriction", isChecked).apply()
            if (isChecked) {
                enableScreenshotRestriction()
            } else {
                disableScreenshotRestriction()
            }
        }
        binding.showFileNames.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("showFileName", isChecked).apply()
        }
    }

    private fun updateThemeModeVisibility() {
        binding.themeRadioGroup.visibility = if (binding.themeModeSwitch.isChecked) View.VISIBLE else View.GONE
    }

    private fun showThemeModeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Theme Mode")
            .setMessage("Would you like to set a specific theme mode?")
            .setPositiveButton("Yes") { _, _ ->
                binding.themeModeSwitch.isChecked = true
            }
            .setNegativeButton("No") { _, _ ->
                binding.systemThemeRadio.isChecked = true
                applyThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            .show()
    }

    private fun applyThemeMode(themeMode: Int) {
        prefs.edit().putInt("theme_mode", themeMode).apply()
        AppCompatDelegate.setDefaultNightMode(themeMode)
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Could not open URL", Snackbar.LENGTH_SHORT).show()
        }
    }
}