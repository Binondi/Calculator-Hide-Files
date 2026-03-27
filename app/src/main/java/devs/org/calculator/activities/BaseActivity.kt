package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import devs.org.calculator.CalculatorApp
import devs.org.calculator.utils.PrefsUtil

abstract class BaseActivity : AppCompatActivity() {

    protected val prefs: PrefsUtil by lazy { PrefsUtil(this) }
    private var isDynamicThemeEnabledAtStart = true
    private var currentThemeModeAtStart = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM


    override fun onCreate(savedInstanceState: Bundle?) {
        isDynamicThemeEnabledAtStart = prefs.getBoolean("dynamic_theme", true)
        if (isDynamicThemeEnabledAtStart) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        currentThemeModeAtStart = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        setupFlagSecure()
    }

    override fun onResume() {
        super.onResume()
        val currentDynamicTheme = prefs.getBoolean("dynamic_theme", true)
        val currentThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (currentDynamicTheme != isDynamicThemeEnabledAtStart || currentThemeMode != currentThemeModeAtStart) {
            isDynamicThemeEnabledAtStart = currentDynamicTheme
            currentThemeModeAtStart = currentThemeMode
            recreate()
        }
        setupFlagSecure()
        val app = application as CalculatorApp
        if (!app.isVaultSessionActive && prefs.getBoolean("is_vault_enabled", true)) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            )
            startActivity(intent)
            finish()
        }
    }

    protected fun setupFlagSecure() {
        if (prefs.getBoolean("screenshot_restriction", true)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
