package devs.org.calculator

import android.app.Application
import com.google.android.material.color.DynamicColors

class CalculatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply dynamic colors to enable Material You theming
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
} 