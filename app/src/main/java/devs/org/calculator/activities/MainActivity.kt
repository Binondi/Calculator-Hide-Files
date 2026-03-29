package devs.org.calculator.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import devs.org.calculator.CalculatorApp
import devs.org.calculator.R
import devs.org.calculator.callbacks.DialogActionsCallback
import devs.org.calculator.databinding.ActivityMainBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.PrefsUtil
import devs.org.calculator.utils.StoragePermissionUtil
import devs.org.calculator.utils.formatResult
import devs.org.calculator.utils.formatWithCommas
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.regex.Pattern

class MainActivity : BaseCalculatorActivity(), DialogActionsCallback, DialogUtil.DialogCallback {
    private lateinit var binding: ActivityMainBinding
    private var currentExpression = ""
    private var lastWasOperator = false
    private var hasDecimal = false
    private var lastWasPercent = false
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var baseDocumentTreeUri: Uri
    private val dialogUtil = DialogUtil(this)
    private val fileManager = FileManager(this, this)
    private lateinit var storagePermissionUtil: StoragePermissionUtil
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private val deleteHandler = Handler(Looper.getMainLooper())
    private var isDeleting = false
    private val deleteRunnable = object : Runnable {
        override fun run() {
            if (isDeleting && currentExpression.isNotEmpty()) {
                cutNumbers()
                deleteHandler.postDelayed(this, 80)
            }
        }
    }
    private var soundEnabled = true
    private var vibrationEnabled = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.display.post {
            binding.display.requestFocus()
            binding.display.setSelection(binding.display.text?.length ?: 0)
        }
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            storagePermissionUtil.handlePermissionResult(permissions)
        }
        storagePermissionUtil = StoragePermissionUtil(this)

        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleActivityResult(result)
        }

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            dialogUtil.showMaterialDialog(
                getString(R.string.storage_permission),
                getString(R.string.to_ensure_the_app_works_properly_and_allows_you_to_easily_hide_or_un_hide_your_private_files_please_grant_storage_access_permission) +
                        "\n" +
                        getString(R.string.for_devices_running_android_11_or_higher_you_ll_need_to_grant_the_all_files_access_permission),
                getString(R.string.grant_permission),
                getString(R.string.later),
                object : DialogUtil.DialogCallback {
                    override fun onPositiveButtonClicked() {
                        storagePermissionUtil.requestStoragePermission(permissionLauncher) {
                            Toast.makeText(this@MainActivity, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onNegativeButtonClicked() {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.storage_permission_is_required_for_the_app_to_function_properly),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    override fun onNaturalButtonClicked() {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.you_can_grant_permission_later_from_settings),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
        }

        setupNumberButton(binding.btn0, "0")
        setupNumberButton(binding.btn00, "00")
        setupNumberButton(binding.btn1, "1")
        setupNumberButton(binding.btn2, "2")
        setupNumberButton(binding.btn3, "3")
        setupNumberButton(binding.btn4, "4")
        setupNumberButton(binding.btn5, "5")
        setupNumberButton(binding.btn6, "6")
        setupNumberButton(binding.btn7, "7")
        setupNumberButton(binding.btn8, "8")
        setupNumberButton(binding.btn9, "9")
        setupOperatorButton(binding.btnPlus, "+")
        setupOperatorButton(binding.btnMinus, "-")
        setupOperatorButton(binding.btnMultiply, "×")
        setupOperatorButton(binding.btnDivide, "/")

        binding.btnClear.setOnClickListener { 
            applyHaptics(it)
            clearDisplay() 
        }
        binding.btnDot.setOnClickListener { 
            applyHaptics(it)
            addDecimal() 
        }
        binding.btnEquals.setOnClickListener { 
            applyHaptics(it)
            calculateResult() 
        }
        binding.btnPercent.setOnClickListener { 
            applyHaptics(it)
            addPercentage() 
        }
        binding.cut.setOnClickListener { 
            applyHaptics(it)
            cutNumbers() 
        }
        binding.cut.setOnLongClickListener {
            applyHaptics(it)
            startRapidDelete()
            true
        }
        binding.cut.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL) {
                stopRapidDelete()
            }
            false
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when(menuItem.itemId) {
                R.id.settings -> {
                    startActivity(Intent(this, CalculatorSettingsActivity::class.java))
                    true
                }
                R.id.about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun applyHaptics(view: View) {
        if (soundEnabled) {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
        if (vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun startRapidDelete() {
        isDeleting = true
        deleteHandler.postDelayed(deleteRunnable, 80)
    }

    private fun stopRapidDelete() {
        isDeleting = false
        deleteHandler.removeCallbacks(deleteRunnable)
    }

    override fun onResume() {
        super.onResume()
        soundEnabled = prefs.getBoolean("sound_haptic", true)
        vibrationEnabled = prefs.getBoolean("vibration_haptic", true)
        updateDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRapidDelete()
    }

    private fun handleActivityResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                baseDocumentTreeUri = uri
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                val preferences = getSharedPreferences("com.example.fileutility", MODE_PRIVATE)
                preferences.edit { putString("filestorageuri", uri.toString()) }
            }
        }
    }

    private fun setupNumberButton(button: TextView, number: String) {
        button.setOnClickListener {
            applyHaptics(it)
            currentExpression += number
            lastWasOperator = false
            lastWasPercent = false
            updateDisplay()
        }
    }

    private fun setupOperatorButton(button: TextView, operator: String) {
        button.setOnClickListener {
            applyHaptics(it)
            val internalOperator = if (operator == "×") "*" else operator

            if (operator == "×" || operator == "/") {
                if (currentExpression == "0" || currentExpression.isEmpty()) return@setOnClickListener
            }

            if (lastWasOperator) {
                currentExpression = currentExpression.dropLast(1) + internalOperator
            } else if (!lastWasPercent) {
                currentExpression += internalOperator
                lastWasOperator = true
                lastWasPercent = false
                hasDecimal = false
            }
            updateDisplay()
        }
    }

    private fun addPercentage() {
        if (!lastWasOperator && !lastWasPercent && currentExpression != "0" && currentExpression.isNotEmpty()) {
            currentExpression += "%"
            lastWasPercent = true
            updateDisplay()
        }
    }

    private fun clearDisplay() {
        currentExpression = ""
        binding.total.text = ""
        lastWasOperator = false
        lastWasPercent = false
        hasDecimal = false
        binding.display.resetTextSize()
        updateDisplay()
    }

    private fun addDecimal() {
        if (!hasDecimal && !lastWasOperator && !lastWasPercent) {
            currentExpression += "."
            hasDecimal = true
            updateDisplay()
        }
    }


    private fun preprocessExpression(expression: String): String {
        val percentagePattern = Pattern.compile("(\\d+\\.?\\d*)%")
        val operatorPercentPattern = Pattern.compile("([+\\-*/])(\\d+\\.?\\d*)%")

        var processedExpression = expression
        val matcher = percentagePattern.matcher(processedExpression)
        while (matcher.find()) {
            val fullMatch = matcher.group(0)
            val number = matcher.group(1)

            val start = matcher.start()
            if (start == 0 || !isOperator(processedExpression[start-1].toString())) {
                val percentageValue = number!!.toDouble() / 100
                processedExpression = processedExpression.replace(fullMatch!!, percentageValue.toString())
            }
        }
        val opMatcher = operatorPercentPattern.matcher(processedExpression)
        val sb = StringBuilder(processedExpression)
        val matches = mutableListOf<Triple<Int, Int, String>>()

        while (opMatcher.find()) {
            val operator = opMatcher.group(1)
            val percentValue = opMatcher.group(2)!!.toDouble()
            val start = opMatcher.start()
            val end = opMatcher.end()

            matches.add(Triple(start, end, "$operator$percentValue%"))
        }

        for (match in matches.reversed()) {
            val (start, end, fullMatch) = match

            var leftNumberStart = start - 1

            if (leftNumberStart >= 0 && sb[leftNumberStart] == ')') {
                var openParens = 1
                leftNumberStart--

                while (leftNumberStart >= 0 && openParens > 0) {
                    if (sb[leftNumberStart] == ')') openParens++
                    else if (sb[leftNumberStart] == '(') openParens--
                    leftNumberStart--
                }

                if (leftNumberStart >= 0) {
                    while (leftNumberStart >= 0 && (isDigit(sb[leftNumberStart].toString()) || sb[leftNumberStart] == '.' || sb[leftNumberStart] == '-')) {
                        leftNumberStart--
                    }
                    leftNumberStart++
                } else {
                    leftNumberStart = 0
                }
            } else {

                while (leftNumberStart >= 0 && (isDigit(sb[leftNumberStart].toString()) || sb[leftNumberStart] == '.')) {
                    leftNumberStart--
                }
                leftNumberStart++
            }

            if (leftNumberStart < start) {
                val leftPart = sb.substring(leftNumberStart, start)

                try {

                    val baseNumber = evaluateExpression(leftPart)
                    val operator = fullMatch.substring(0, 1)
                    val percentNumber = fullMatch.substring(1, fullMatch.length - 1).toDouble()

                    val percentValue = baseNumber * (percentNumber / 100)

                    val newValue = when (operator) {
                        "+" -> baseNumber + percentValue
                        "-" -> baseNumber - percentValue
                        "*" -> baseNumber * (percentNumber / 100)
                        "/" -> baseNumber / (percentNumber / 100)
                        else -> baseNumber
                    }

                    sb.replace(leftNumberStart, end, newValue.toString())
                } catch (e: Exception) {
                    Log.e("Calculator", "Error processing percentage expression: $e")
                }
            }
        }

        return sb.toString()
    }

    private fun isOperator(char: String): Boolean {
        return char == "+" || char == "-" || char == "*" || char == "/"
    }

    private fun isDigit(char: String): Boolean {
        return char.matches(Regex("[0-9]"))
    }

    private fun evaluateExpression(expression: String): Double {
        return try {
            ExpressionBuilder(expression).build().evaluate()
        } catch (_: Exception) {
            expression.toDouble()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun calculateResult() {
        val rawExpression = currentExpression.replace(",", "")
        if (rawExpression == "123456") {
            prefs.setBoolean("isFirst", false)
            val app = application as CalculatorApp
            app.isVaultSessionActive = true
            val intent = Intent(this, SetupPasswordActivity::class.java)
            intent.putExtra("password", rawExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        if (PrefsUtil(this).validatePassword(rawExpression)) {
            val app = application as CalculatorApp
            app.isVaultSessionActive = true
            val intent = Intent(this, HiddenActivity::class.java)
            intent.putExtra("password", rawExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        try {
            var processedExpression = rawExpression.replace("×", "*")

            if (processedExpression.contains("%")) {
                processedExpression = preprocessExpression(processedExpression)
            }

            val result = ExpressionBuilder(processedExpression).build().evaluate()
            val precision = prefs.getInt("precision", 3)
            currentExpression = formatResult(result, precision)

            lastWasOperator = false
            lastWasPercent = false
            hasDecimal = currentExpression.contains(".")

            updateDisplay()
            binding.total.text = ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateDisplay() {
        val displayText = currentExpression.replace("*", "×")
        binding.display.setText(formatWithCommas(displayText))
        binding.display.setSelection(binding.display.text?.length ?: 0)

        if (currentExpression.isEmpty()) {
            if (prefs.getBoolean("isFirst", true)) {
                binding.display.setText(getString(R.string.enter_123456))
            } else {
                binding.display.setText("")
            }
            binding.total.text = ""
            return
        }

        try {
            var processedExpression = currentExpression.replace("×", "*")

            if (isOperator(processedExpression.last().toString())) {
                processedExpression = processedExpression.dropLast(1)
            }

            if (processedExpression.isEmpty()) {
                binding.total.text = ""
                return
            }

            if (processedExpression.contains("%")) {
                processedExpression = preprocessExpression(processedExpression)
            }

            val result = ExpressionBuilder(processedExpression).build().evaluate()
            val formattedResult = formatWithCommas(formatResult(result, prefs.getInt("precision", 3)))

            binding.total.text = if (prefs.getBoolean("isFirst", true) && currentExpression == "123456") {
                getString(R.string.now_enter_button)
            } else {
                formattedResult
            }
        } catch (_: Exception) {
            binding.total.text = ""
        }
    }

    private fun cutNumbers() {
        if (currentExpression.isEmpty()) return

        val displayText = binding.display.text?.toString() ?: ""
        val cursorPos = binding.display.getCursorPosition()

        if (cursorPos <= 0) {
            updateDisplay()
            return
        }

        val cleanDisplayText = displayText.replace(",", "")
        val cleanCursorPos = displayText.substring(0, cursorPos).replace(",", "").length

        if (cleanCursorPos <= 0 || cleanCursorPos > cleanDisplayText.length) {
            updateDisplay()
            return
        }

        val charToDelete = cleanDisplayText[cleanCursorPos - 1]

        currentExpression = cleanDisplayText.substring(0, cleanCursorPos - 1) +
                cleanDisplayText.substring(cleanCursorPos)

        when {
            charToDelete == '%' -> lastWasPercent = false
            isOperator(charToDelete.toString()) -> lastWasOperator = false
            charToDelete == '.' -> hasDecimal = false
        }

        if (currentExpression.isEmpty()) {
            lastWasOperator = false
            lastWasPercent = false
            hasDecimal = false
        }

        val newCursorPos = cleanCursorPos - 1

        updateDisplay()

        binding.display.post {
            val newDisplayText = binding.display.text?.toString() ?: ""
            val adjustedPos = getAdjustedCursorPos(newDisplayText, newCursorPos)
            binding.display.setSelection(adjustedPos.coerceIn(0, newDisplayText.length))
        }
    }

    private fun getAdjustedCursorPos(displayText: String, cleanPos: Int): Int {
        var cleanCount = 0
        for (i in displayText.indices) {
            if (displayText[i] != ',') cleanCount++
            if (cleanCount == cleanPos) return i + 1
        }
        return displayText.length
    }

    override fun onPositiveButtonClicked() {
        fileManager.askPermission(this)
    }

    override fun onNegativeButtonClicked() {
        Toast.makeText(this, getString(R.string.storage_permission_is_required_for_the_app_to_function_properly), Toast.LENGTH_LONG).show()
    }

    override fun onNaturalButtonClicked() {
        Toast.makeText(this, getString(R.string.you_can_grant_permission_later_from_settings), Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 6767) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
