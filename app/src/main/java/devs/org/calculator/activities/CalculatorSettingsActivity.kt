package devs.org.calculator.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivityCalculatorSettingsBinding
import devs.org.calculator.update.Update.checkForAppUpdate
import devs.org.calculator.utils.formatResult

class CalculatorSettingsActivity : BaseCalculatorActivity() {

    private lateinit var binding: ActivityCalculatorSettingsBinding
    private var DEV_GITHUB_URL = ""
    private var GITHUB_URL = ""
    private var DEFAULT_PRECISION = 12345.6789123456789123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalculatorSettingsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
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
        binding.vibrationStatus.isChecked = prefs.getBoolean("vibration_haptic", true)
        binding.soundStatus.isChecked = prefs.getBoolean("sound_haptic", true)
        val precision = prefs.getInt("precision", 3)
        binding.precisionSlider.value = precision.toFloat()
        binding.previewFormatText.text = formatResult(DEFAULT_PRECISION,precision)

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
    }

    private fun setupListeners() {

        binding.githubButton.setOnClickListener {
            openUrl(GITHUB_URL)
        }
        binding.sourceCodeLayout.setOnClickListener {
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
        binding.vibrationStatus.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("vibration_haptic", isChecked)
            if (isChecked) {
                binding.vibrationStatus.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        binding.soundStatus.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBoolean("sound_haptic", isChecked)
            if (isChecked) {
                binding.soundStatus.playSoundEffect(SoundEffectConstants.CLICK)
            }
        }
        binding.precisionSlider.addOnChangeListener { _, value, _ ->
            prefs.setInt("precision", value.toInt())

            binding.previewFormatText.text = formatResult(DEFAULT_PRECISION,value.toInt())
        }
    }

    private fun applyThemeMode(themeMode: Int) {
        if (prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != themeMode) {
            prefs.setInt("theme_mode", themeMode)
            AppCompatDelegate.setDefaultNightMode(themeMode)
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

}