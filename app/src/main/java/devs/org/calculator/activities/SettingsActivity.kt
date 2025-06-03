package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivitySettingsBinding
import devs.org.calculator.utils.PrefsUtil

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs:PrefsUtil by lazy { PrefsUtil(this) }
    private var DEV_GITHUB_URL = ""
    private var GITHUB_URL = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        DEV_GITHUB_URL = getString(R.string.github_profile)
        GITHUB_URL = getString(R.string.calculator_hide_files, DEV_GITHUB_URL)
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
            prefs.setBoolean("dynamic_theme", isChecked)
            if (!isChecked) {
                showThemeModeDialog()
            }else{
                showThemeModeDialog()
                if (!prefs.getBoolean("isAppReopened",false)){
                    DynamicColors.applyToActivityIfAvailable(this)
                }
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
    }

    private fun updateThemeModeVisibility() {
        binding.themeRadioGroup.visibility = if (binding.themeModeSwitch.isChecked) View.VISIBLE else View.GONE
    }

    private fun showThemeModeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.attention))
            .setMessage(getString(R.string.if_you_turn_on_off_this_option_dynamic_theme_changes_will_be_visible_after_you_reopen_the_app))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->

            }
            .show()
    }

    private fun applyThemeMode(themeMode: Int) {
        prefs.setInt("theme_mode", themeMode)
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
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root,
                getString(R.string.could_not_open_url), Snackbar.LENGTH_SHORT).show()
        }
    }
}