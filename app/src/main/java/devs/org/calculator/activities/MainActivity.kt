package devs.org.calculator.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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

class MainActivity : AppCompatActivity(), DialogActionsCallback {
    private lateinit var binding: ActivityMainBinding
    private var currentExpression = "0"
    private var lastWasOperator = false
    private var hasDecimal = false
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var baseDocumentTreeUri: Uri
    private val dialogUtil = DialogUtil(this)
    private val fileManager = FileManager(this, this)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ActivityResultLauncher
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
        // Number buttons
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

        // Operator buttons
        setupOperatorButton(binding.btnPlus, "+")
        setupOperatorButton(binding.btnMinus, "-")
        setupOperatorButton(binding.btnMultiply, "*")
        setupOperatorButton(binding.btnDivide, "/")

        // Special buttons
        binding.btnClear.setOnClickListener { clearDisplay() }
        binding.btnDot.setOnClickListener { addDecimal() }
        binding.btnEquals.setOnClickListener { calculateResult() }
        binding.btnPercent.setOnClickListener { calculatePercentage() }
        binding.cut.setOnClickListener { cutNumbers() }
    }

    private fun handleActivityResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                baseDocumentTreeUri = uri
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                // Take persistable Uri Permission for future use
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
            updateDisplay()
        }
    }

    private fun setupOperatorButton(button: MaterialButton, operator: String) {
        button.setOnClickListener {
            if (!lastWasOperator) {
                currentExpression += operator
                lastWasOperator = true
                hasDecimal = false
            }
            updateDisplay()
        }
    }

    private fun clearDisplay() {
        currentExpression = "0"
        lastWasOperator = false
        hasDecimal = false
        updateDisplay()
    }

    private fun addDecimal() {
        if (!hasDecimal && !lastWasOperator) {
            currentExpression += "."
            hasDecimal = true
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

    private fun calculateResult() {
        // Check for secret code
        if (currentExpression == "123456") { // Replace with your desired code
            val intent = Intent(this, SetupPasswordActivity::class.java)
            intent.putExtra("password", currentExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        // Validate password
        if (PrefsUtil(this).validatePassword(currentExpression)) {
            val intent = Intent(this, HiddenVaultActivity::class.java)
            intent.putExtra("password", currentExpression)
            startActivity(intent)
            clearDisplay()
            return
        }

        try {
            val expression = ExpressionBuilder(currentExpression).build()
            val result = expression.evaluate()

            currentExpression = if (result.toLong().toDouble() == result) {
                result.toLong().toString()
            } else {
                String.format("%.2f", result)
            }

            lastWasOperator = false
            hasDecimal = currentExpression.contains(".")
            updateDisplay()
        } catch (e: Exception) {
            binding.display.text = getString(R.string.invalid_message)
        }
    }

    private fun updateDisplay() {
        binding.display.text = currentExpression
    }
    private fun cutNumbers() {
        if (currentExpression.isNotEmpty()){
            if (currentExpression.length == 1){
                currentExpression = currentExpression.substring(0, currentExpression.length - 1)
                currentExpression = "0"
            }else currentExpression = currentExpression.substring(0, currentExpression.length - 1)
        }else currentExpression = "0"
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


