package devs.org.calculator

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import devs.org.calculator.utils.PrefsUtil

class CalculatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = PrefsUtil(this)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)

        val dynamicColorsOptions = DynamicColorsOptions.Builder()
            .setPrecondition { _, _ ->
                PrefsUtil(this).getBoolean("dynamic_theme", true)
            }
            .build()
            
        DynamicColors.applyToActivitiesIfAvailable(this, dynamicColorsOptions)
    }
}
