package devs.org.calculator.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import androidx.core.content.edit

class MainActivity : AppCompatActivity(), DialogActionsCallback, DialogUtil.DialogCallback {
    private lateinit var binding: ActivityMainBinding
    private var currentExpression = "0"
    private var lastWasOperator = false
    private var hasDecimal = false
    private var lastWasPercent = false
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var baseDocumentTreeUri: Uri
    private val dialogUtil = DialogUtil(this)
    private val fileManager = FileManager(this, this)
    private val sp by lazy { getSharedPreferences("app", MODE_PRIVATE) }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleActivityResult(result)
        }

        if (sp.getBoolean("isFirst", true)){
            binding.display.text = getString(R.string.enter_123456)
        }

        if(!Environment.isExternalStorageManager()) {
            dialogUtil.showMaterialDialog(
                getString(R.string.storage_permission),
                getString(R.string.to_ensure_the_app_works_properly_and_allows_you_to_easily_hide_or_un_hide_your_private_files_please_grant_storage_access_permission) +
                        "\n" +
                        getString(R.string.for_devices_running_android_11_or_higher_you_ll_need_to_grant_the_all_files_access_permission),
                getString(R.string.grant_permission),
                getString(R.string.later),
                object : DialogUtil.DialogCallback {
                    override fun onPositiveButtonClicked() {
                        fileManager.askPermission(this@MainActivity)
                    }

                    override fun onNegativeButtonClicked() {
                        Toast.makeText(this@MainActivity,
                            getString(R.string.storage_permission_is_required_for_the_app_to_function_properly),
                            Toast.LENGTH_LONG).show()
                    }

                    override fun onNaturalButtonClicked() {
                        Toast.makeText(this@MainActivity,
                            getString(R.string.you_can_grant_permission_later_from_settings),
                            Toast.LENGTH_LONG).show()
                    }
                }
            )
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
                preferences.edit { putString("filestorageuri", uri.toString()) }
            }
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
                processedExpression = processedExpression.replace(fullMatch!!.toString(), percentageValue.toString())
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
        if (currentExpression == "123456") {
            val intent = Intent(this, SetupPasswordActivity::class.java)
            sp.edit { putBoolean("isFirst", false) }
            intent.putExtra("password", currentExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        if (PrefsUtil(this).validatePassword(currentExpression)) {
            val intent = Intent(this, HiddenActivity::class.java)
            intent.putExtra("password", currentExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        try {
            var processedExpression = currentExpression.replace("×", "*")

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
        binding.display.text = currentExpression.replace("*", "×")

        if (currentExpression == "0") {
            binding.total.text = ""
            return
        }

        try {
            if (currentExpression.isEmpty()) {
                binding.total.text = ""
                return
            }

            var processedExpression = currentExpression.replace("×", "*")

            if (isOperator(processedExpression.last().toString())) {
                processedExpression = processedExpression.substring(0, processedExpression.length - 1)
            }

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
            if (sp.getBoolean("isFirst", true) && (currentExpression == "123456" || binding.display.text.toString() == "123456")){
                binding.total.text = getString(R.string.now_enter_button)
            }else binding.total.text = formattedResult
        } catch (_: Exception) {
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