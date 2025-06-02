package devs.org.calculator

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

class CalculatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize theme settings
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        
        // Apply saved theme mode
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        
        // Apply dynamic colors only if dynamic theme is enabled
        if (prefs.getBoolean("dynamic_theme", true)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
} 