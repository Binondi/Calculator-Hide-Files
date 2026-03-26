package devs.org.calculator.activities

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import devs.org.calculator.utils.PrefsUtil

abstract class BaseCalculatorActivity : AppCompatActivity() {

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
    }

}
