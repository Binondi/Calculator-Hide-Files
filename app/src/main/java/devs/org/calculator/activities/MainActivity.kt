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
import androidx.lifecycle.lifecycleScope
import devs.org.calculator.CalculatorApp
import devs.org.calculator.R
import devs.org.calculator.callbacks.DialogActionsCallback
import devs.org.calculator.database.AppDatabase
import devs.org.calculator.database.CalculationHistory
import devs.org.calculator.databinding.ActivityMainBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.PrefsUtil
import devs.org.calculator.utils.StoragePermissionUtil
import devs.org.calculator.utils.formatResult
import devs.org.calculator.utils.formatWithCommas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val historyDao by lazy { AppDatabase.getDatabase(this).calculationHistoryDao() }

    private var isUpdatingDisplay = false

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

        binding.display.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdatingDisplay) return
                
                val rawText = s?.toString() ?: ""
                val cleanText = rawText.replace(",", "").replace("×", "*")
                
                if (cleanText != currentExpression) {
                    currentExpression = cleanText
                    // Re-calculate flags
                    if (currentExpression.isNotEmpty()) {
                        val lastChar = currentExpression.last()
                        lastWasOperator = isOperator(lastChar.toString())
                        lastWasPercent = lastChar == '%'
                        
                        val lastOperatorIndex = currentExpression.lastIndexOfAny(charArrayOf('+', '-', '*', '/'))
                        val partAfterLastOperator = if (lastOperatorIndex == -1) currentExpression else currentExpression.substring(lastOperatorIndex + 1)
                        hasDecimal = partAfterLastOperator.contains(".")
                    } else {
                        lastWasOperator = false
                        lastWasPercent = false
                        hasDecimal = false
                    }
                    
                    binding.display.post { 
                        val currentPos = binding.display.selectionStart
                        val cleanPos = getCleanCursorPos(binding.display.text.toString(), currentPos)
                        updateDisplay(cleanPos) 
                    }
                }
            }
        })

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
                R.id.history -> {
                    startActivity(Intent(this, CalculationHistoryActivity::class.java))
                    true
                }
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
            insertIntoExpression(number)
            lastWasOperator = false
            lastWasPercent = false
        }
    }

    private fun setupOperatorButton(button: TextView, operator: String) {
        button.setOnClickListener {
            applyHaptics(it)
            val internalOperator = if (operator == "×") "*" else operator

            if (operator == "×" || operator == "/") {
                if (currentExpression == "0" || currentExpression.isEmpty()) return@setOnClickListener
            }

            val displayText = binding.display.text?.toString() ?: ""
            val cursorPos = binding.display.getCursorPosition()
            val cleanCursorPos = getCleanCursorPos(displayText, cursorPos)

            if (lastWasOperator && cleanCursorPos == currentExpression.length) {
                currentExpression = currentExpression.dropLast(1) + internalOperator
                updateDisplay(currentExpression.length)
            } else {
                insertIntoExpression(internalOperator)
                lastWasOperator = true
                lastWasPercent = false
                hasDecimal = false
            }
        }
    }

    private fun addPercentage() {
        if (!lastWasOperator && !lastWasPercent && currentExpression != "0" && currentExpression.isNotEmpty()) {
            insertIntoExpression("%")
            lastWasPercent = true
        }
    }

    private fun clearDisplay() {
        currentExpression = ""
        binding.total.text = ""
        lastWasOperator = false
        lastWasPercent = false
        hasDecimal = false
        binding.display.resetTextSize()
        updateDisplay(0)
    }

    private fun addDecimal() {
        if (!hasDecimal && !lastWasOperator && !lastWasPercent) {
            insertIntoExpression(".")
            hasDecimal = true
        }
    }

    private fun getCleanCursorPos(displayText: String, cursorPos: Int): Int {
        if (cursorPos <= 0) return 0
        val textBeforeCursor = displayText.substring(0, cursorPos.coerceAtMost(displayText.length))
        return textBeforeCursor.replace(",", "").length
    }

    private fun insertIntoExpression(toInsert: String) {
        val start = binding.display.selectionStart
        val end = binding.display.selectionEnd
        val displayText = binding.display.text?.toString() ?: ""

        val cleanStart = getCleanCursorPos(displayText, start)
        val cleanEnd = getCleanCursorPos(displayText, end)

        val sb = StringBuilder(currentExpression)
        if (cleanStart < cleanEnd) {
            sb.replace(cleanStart, cleanEnd, toInsert)
        } else {
            sb.insert(cleanStart, toInsert)
        }

        currentExpression = sb.toString()
        updateDisplay(cleanStart + toInsert.length)
    }

    private fun updateDisplay(targetCleanPos: Int? = null) {
        if (isUpdatingDisplay) return
        isUpdatingDisplay = true

        val displayText = currentExpression.replace("*", "×")
        val formatted = formatWithCommas(displayText)
        
        if (binding.display.text.toString() != formatted) {
            binding.display.setText(formatted)
        }
        
        val newPos = if (targetCleanPos != null) {
            getAdjustedCursorPos(formatted, targetCleanPos)
        } else {
            formatted.length
        }
        binding.display.setSelection(newPos.coerceIn(0, formatted.length))
        
        isUpdatingDisplay = false

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

        val start = binding.display.selectionStart
        val end = binding.display.selectionEnd
        val displayText = binding.display.text?.toString() ?: ""

        if (start < end) {
            val cleanStart = getCleanCursorPos(displayText, start)
            val cleanEnd = getCleanCursorPos(displayText, end)
            currentExpression = StringBuilder(currentExpression).delete(cleanStart, cleanEnd).toString()
            updateDisplay(cleanStart)
        } else {
            if (start <= 0) {
                updateDisplay()
                return
            }

            val cleanCursorPos = getCleanCursorPos(displayText, start)

            if (cleanCursorPos <= 0 || cleanCursorPos > currentExpression.length) {
                updateDisplay()
                return
            }

            val charToDelete = currentExpression[cleanCursorPos - 1]

            currentExpression = StringBuilder(currentExpression).deleteAt(cleanCursorPos - 1).toString()

            when {
                charToDelete == '%' -> lastWasPercent = false
                isOperator(charToDelete.toString()) -> lastWasOperator = false
                charToDelete == '.' -> hasDecimal = false
            }

            updateDisplay(cleanCursorPos - 1)
        }

        if (currentExpression.isEmpty()) {
            lastWasOperator = false
            lastWasPercent = false
            hasDecimal = false
         }
    }

    private fun preprocessExpression(expression: String): String {
        var sb = StringBuilder(expression)
        val percentPattern = Pattern.compile("(\\d*\\.?\\d+)%")
        var matcher = percentPattern.matcher(sb)
        var offset = 0

        while (matcher.find(offset)) {
            val percentNumberStr = matcher.group(1)
            val start = matcher.start()
            val end = matcher.end()

            val prevIdx = start - 1
            if (prevIdx >= 0 && (sb[prevIdx] == '+' || sb[prevIdx] == '-')) {
                val baseExpression = sb.substring(0, prevIdx)
                if (baseExpression.isNotEmpty()) {
                    try {
                        val baseValue = evaluateExpression(baseExpression)
                        val percentNumber = percentNumberStr.toDouble()
                        
                        val replacement = "(($baseValue) * $percentNumber / 100.0)"
                        
                        sb.replace(start, end, replacement)
                        offset = start + replacement.length
                        matcher = percentPattern.matcher(sb)
                        continue
                    } catch (_: Exception) {}
                }
            }

            val percentNumber = percentNumberStr.toDouble()
            val replacement = "($percentNumber / 100.0)"
            sb.replace(start, end, replacement)
            offset = start + replacement.length
            matcher = percentPattern.matcher(sb)
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
            val cleanExpression = currentExpression.replace("*", "×")
            var processedExpression = rawExpression.replace("×", "*")

            if (processedExpression.contains("%")) {
                processedExpression = preprocessExpression(processedExpression)
            }

            val result = ExpressionBuilder(processedExpression).build().evaluate()
            val precision = prefs.getInt("precision", 3)
            val formattedResult = formatResult(result, precision)

            lifecycleScope.launch(Dispatchers.IO) {
                historyDao.insert(CalculationHistory(
                    expression = cleanExpression,
                    result = "= $formattedResult"
                ))
            }

            currentExpression = formattedResult

            lastWasOperator = false
            lastWasPercent = false
            hasDecimal = currentExpression.contains(".")

            updateDisplay()
            binding.total.text = ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAdjustedCursorPos(displayText: String, cleanPos: Int): Int {
        if (cleanPos <= 0) return 0
        var cleanCount = 0
        for (i in displayText.indices) {
            if (displayText[i] != ',') {
                cleanCount++
            }
            if (cleanCount == cleanPos) {
                return i + 1
            }
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
