package devs.org.calculator.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import devs.org.calculator.R
import devs.org.calculator.callbacks.DialogActionsCallback
import devs.org.calculator.databinding.ActivityMainBinding
import devs.org.calculator.utils.DialogUtil
import devs.org.calculator.utils.FileManager
import devs.org.calculator.utils.PrefsUtil
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), DialogActionsCallback {
    private lateinit var binding: ActivityMainBinding
    private var currentExpression = "0"
    private var lastWasOperator = false
    private var hasDecimal = false
    private var lastWasPercent = false
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var baseDocumentTreeUri: Uri
    private val dialogUtil = DialogUtil(this)
    private val fileManager = FileManager(this, this)
    private lateinit var sp :SharedPreferences

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sp = getSharedPreferences("app", MODE_PRIVATE)

        if (!sp.contains("isFirstTime") || sp.getBoolean("isFirstTime", true)) {
            binding.display.text = getString(R.string.enter_123456)
        }
        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleActivityResult(result)
        }

        // Ask permission
        if(!Environment.isExternalStorageManager()) {
            dialogUtil.showMaterialDialog(
                "Storage Permission",
                "To ensure the app works properly and allows you to easily hide or unhide your private files, please grant storage access permission.\n" +
                        "\n" +
                        "For devices running Android 11 or higher, you'll need to grant the 'All Files Access' permission.",
                "Grant",
                "Cancel",
                this
            )
        }
        setupNumberButton(binding.btn0, "0")
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

        binding.btnClear.setOnClickListener { clearDisplay() }
        binding.btnDot.setOnClickListener { addDecimal() }
        binding.btnEquals.setOnClickListener { calculateResult() }
        binding.btnPercent.setOnClickListener { addPercentage() }
        binding.cut.setOnClickListener { cutNumbers() }
    }

    private fun handleActivityResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                baseDocumentTreeUri = uri
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                val preferences = getSharedPreferences("com.example.fileutility", MODE_PRIVATE)
                preferences.edit().putString("filestorageuri", uri.toString()).apply()
            }
        } else {
            Log.e("FileUtility", "Error occurred or operation cancelled: $result")
        }
    }

    private fun setupNumberButton(button: MaterialButton, number: String) {
        button.setOnClickListener {
            if (currentExpression == "0") {
                currentExpression = number
            } else {
                currentExpression += number
            }
            lastWasOperator = false
            lastWasPercent = false
            updateDisplay()
        }
    }

    private fun setupOperatorButton(button: MaterialButton, operator: String) {
        button.setOnClickListener {
            if (lastWasOperator) {
                currentExpression = currentExpression.substring(0, currentExpression.length - 1) +
                        when (operator) {
                            "×" -> "*"
                            else -> operator
                        }
            } else if (!lastWasPercent) {
                currentExpression += when (operator) {
                    "×" -> "*"
                    else -> operator
                }
                lastWasOperator = true
                lastWasPercent = false
                hasDecimal = false
            }
            updateDisplay()
        }
    }

    private fun clearDisplay() {
        currentExpression = "0"
        binding.total.text = ""
        lastWasOperator = false
        lastWasPercent = false
        hasDecimal = false
        updateDisplay()
    }

    private fun addDecimal() {
        if (!hasDecimal && !lastWasOperator && !lastWasPercent) {
            currentExpression += "."
            hasDecimal = true
            updateDisplay()
        }
    }

    private fun addPercentage() {
        if (!lastWasOperator && !lastWasPercent) {
            currentExpression += "%"
            lastWasPercent = true
            updateDisplay()
        }
    }

    private fun calculatePercentage() {
        try {
            val value = currentExpression.toDouble()
            currentExpression = (value / 100).toString()
            updateDisplay()
        } catch (e: Exception) {
            binding.display.text = getString(R.string.invalid_message)
        }
    }

    private fun preprocessExpression(expression: String): String {
        val percentagePattern = Pattern.compile("(\\d+\\.?\\d*)%")
        val operatorPercentPattern = Pattern.compile("([+\\-*/])(\\d+\\.?\\d*)%")

        var processedExpression = expression

        // Replace standalone percentages (like "50%") with their decimal form (0.5)
        val matcher = percentagePattern.matcher(processedExpression)
        while (matcher.find()) {
            val fullMatch = matcher.group(0)
            val number = matcher.group(1)

            // Check if it's a standalone percentage or part of an operation
            val start = matcher.start()
            if (start == 0 || !isOperator(processedExpression[start-1].toString())) {
                val percentageValue = number.toDouble() / 100
                processedExpression = processedExpression.replace(fullMatch, percentageValue.toString())
            }
        }

        // Handle operator-percentage combinations (like "100-20%")
        val opMatcher = operatorPercentPattern.matcher(processedExpression)
        val sb = StringBuilder(processedExpression)

        // We need to process matches from right to left to maintain indices
        val matches = mutableListOf<Triple<Int, Int, String>>()

        while (opMatcher.find()) {
            val operator = opMatcher.group(1)
            val percentValue = opMatcher.group(2)!!.toDouble()
            val start = opMatcher.start()
            val end = opMatcher.end()

            matches.add(Triple(start, end, "$operator$percentValue%"))
        }

        // Process matches from right to left
        for (match in matches.reversed()) {
            val (start, end, fullMatch) = match

            // Find the number before this operator
            var leftNumberEnd = start
            var leftNumberStart = start - 1

            // Skip parentheses and move to the actual number
            if (leftNumberStart >= 0 && sb[leftNumberStart] == ')') {
                var openParens = 1
                leftNumberStart--

                while (leftNumberStart >= 0 && openParens > 0) {
                    if (sb[leftNumberStart] == ')') openParens++
                    else if (sb[leftNumberStart] == '(') openParens--
                    leftNumberStart--
                }

                // Now we need to find the start of the expression
                if (leftNumberStart >= 0) {
                    while (leftNumberStart >= 0 && (isDigit(sb[leftNumberStart].toString()) || sb[leftNumberStart] == '.' || sb[leftNumberStart] == '-')) {
                        leftNumberStart--
                    }
                    leftNumberStart++
                } else {
                    leftNumberStart = 0
                }
            } else {
                // For simple numbers, just find the start of the number
                while (leftNumberStart >= 0 && (isDigit(sb[leftNumberStart].toString()) || sb[leftNumberStart] == '.')) {
                    leftNumberStart--
                }
                leftNumberStart++
            }

            if (leftNumberStart < leftNumberEnd) {
                val leftPart = sb.substring(leftNumberStart, leftNumberEnd)

                try {
                    // Extract the numerical values
                    val baseNumber = evaluateExpression(leftPart)
                    val operator = fullMatch.substring(0, 1)
                    val percentNumber = fullMatch.substring(1, fullMatch.length - 1).toDouble()

                    // Calculate the percentage of the base number
                    val percentValue = baseNumber * (percentNumber / 100)

                    // Calculate the new value based on the operator
                    val newValue = when (operator) {
                        "+" -> baseNumber + percentValue
                        "-" -> baseNumber - percentValue
                        "*" -> baseNumber * (percentNumber / 100)
                        "/" -> baseNumber / (percentNumber / 100)
                        else -> baseNumber
                    }

                    // Replace the entire expression "number operator percent%" with the result
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
        } catch (e: Exception) {
            expression.toDouble()
        }
    }

    private fun calculateResult() {
        if (currentExpression == "123456") {
            val intent = Intent(this, SetupPasswordActivity::class.java)
            intent.putExtra("password", currentExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        if (PrefsUtil(this).validatePassword(currentExpression)) {
            val intent = Intent(this, HiddenVaultActivity::class.java)
            intent.putExtra("password", currentExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        try {
            // Replace '×' with '*' for the expression evaluator
            var processedExpression = currentExpression.replace("×", "*")

            // Process percentages in the expression
            if (processedExpression.contains("%")) {
                processedExpression = preprocessExpression(processedExpression)
            }

            val expression = ExpressionBuilder(processedExpression).build()
            val result = expression.evaluate()

            currentExpression = if (result.toLong().toDouble() == result) {
                result.toLong().toString()
            } else {
                String.format("%.2f", result)
            }

            lastWasOperator = false
            lastWasPercent = false
            hasDecimal = currentExpression.contains(".")

            updateDisplay()
            binding.total.text = ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateDisplay() {
        if (!sp.contains("isFirstTime") || sp.getBoolean("isFirstTime", true)) {
            if (currentExpression == "123456") {
                binding.total.text = getString(R.string.now_enter_button)
                return
            }
            else if (currentExpression != "0" && currentExpression.isNotEmpty()) {
                binding.display.text = currentExpression.replace("*", "×")
                return
            }
            else if (currentExpression == "0") {
                binding.display.text = getString(R.string.enter_123456)
                return
            }
        }


        binding.display.text = currentExpression.replace("*", "×")

        if (currentExpression == "0") {
            binding.total.text = ""
            return
        }

        try {
            // Don't show preview result if the expression ends with an operator
            // (but allow percentage at the end)
            if (currentExpression.isEmpty() ||
                (isOperator(currentExpression.last().toString()) && currentExpression.last() != '%')) {
                binding.total.text = ""
                return
            }

            // Process the expression for preview calculation
            var processedExpression = currentExpression.replace("×", "*")

            if (processedExpression.contains("%")) {
                processedExpression = preprocessExpression(processedExpression)
            }

            val expression = ExpressionBuilder(processedExpression).build()
            val result = expression.evaluate()

            val formattedResult = if (result.toLong().toDouble() == result) {
                result.toLong().toString()
            } else {
                String.format("%.2f", result)
            }

            binding.total.text = formattedResult
        } catch (e: Exception) {
            binding.total.text = ""
        }
    }

    private fun cutNumbers() {
        if (currentExpression.isNotEmpty()){
            if (currentExpression.length == 1){
                currentExpression = "0"
            } else {
                val lastChar = currentExpression.last()
                currentExpression = currentExpression.substring(0, currentExpression.length - 1)

                // Update flags based on what was removed
                if (lastChar == '%') {
                    lastWasPercent = false
                } else if (isOperator(lastChar.toString())) {
                    lastWasOperator = false
                } else if (lastChar == '.') {
                    hasDecimal = false
                }
            }
        } else {
            currentExpression = "0"
        }
        updateDisplay()
    }

    override fun onPositiveButtonClicked() {
        fileManager.askPermission(this)
    }

    override fun onNegativeButtonClicked() {

    }

    override fun onNaturalButtonClicked() {

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 6767) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}