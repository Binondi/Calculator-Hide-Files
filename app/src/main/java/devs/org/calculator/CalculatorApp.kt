package devs.org.calculator

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import devs.org.calculator.utils.PrefsUtil

class CalculatorApp : Application(), Application.ActivityLifecycleCallbacks {

    private var activityCount = 0

    var isVaultSessionActive = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
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

    override fun onActivityStarted(activity: Activity) {
        activityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        activityCount--

        if (activityCount == 0) {
            isVaultSessionActive = false
        }
    }
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityResumed(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}

}
