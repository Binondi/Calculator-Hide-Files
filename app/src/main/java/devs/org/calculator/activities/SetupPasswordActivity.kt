package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import devs.org.calculator.databinding.ActivitySetupPasswordBinding
import devs.org.calculator.utils.PrefsUtil
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivityChangePasswordBinding

class SetupPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupPasswordBinding
    private lateinit var binding2: ActivityChangePasswordBinding
    private lateinit var prefsUtil: PrefsUtil
    private var hasPassword = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupPasswordBinding.inflate(layoutInflater)
        binding2 = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsUtil = PrefsUtil(this)
        hasPassword = prefsUtil.hasPassword()

        if (hasPassword){
            setContentView(binding2.root)
        }else{
            setContentView(binding.root)
        }
        clickListeners()

    }

    private fun clickListeners(){
        binding.btnSavePassword.setOnClickListener {
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            val securityQuestion = binding.etSecurityQuestion.text.toString()
            val securityAnswer = binding.etSecurityAnswer.text.toString()

            if (password.isEmpty()){
                binding.etPassword.error = "Enter password"
                return@setOnClickListener
            }
            if (confirmPassword.isEmpty()){
                binding.etConfirmPassword.error = "Confirm password"
                return@setOnClickListener
            }
            if (securityQuestion.isEmpty()){
                binding.etSecurityQuestion.error = "Enter security question"
                return@setOnClickListener
            }
            if (securityAnswer.isEmpty()){
                binding.etSecurityAnswer.error = "Enter security answer"
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                binding.etPassword.error = "Passwords don't match"
                return@setOnClickListener
            }
            prefsUtil.savePassword(password)
            prefsUtil.saveSecurityQA(securityQuestion, securityAnswer)
            Toast.makeText(this, "Password set successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.btnResetPassword.setOnClickListener {
            // Implement password reset logic
            // Could use security questions or email verification
            if (prefsUtil.getSecurityQuestion() != null) showSecurityQuestionDialog(prefsUtil.getSecurityQuestion().toString())
            else Toast.makeText(this, "Security question not set yet.", Toast.LENGTH_SHORT).show()

        }
        binding2.btnChangePassword.setOnClickListener{
            val oldPassword = binding2.etOldPassword.text.toString()
            val newPassword = binding2.etNewPassword.text.toString()
            if (oldPassword.isEmpty()) {
                binding2.etOldPassword.error = "This field can't be empty"
                return@setOnClickListener
            }
            if (newPassword.isEmpty()) {
                binding2.etNewPassword.error = "This field can't be empty"
                return@setOnClickListener
            }

            if (prefsUtil.validatePassword(oldPassword)){
                if (oldPassword != newPassword){
                    prefsUtil.savePassword(newPassword)
                    Toast.makeText(this, "Password reset successfully", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()

                }else {
                    Toast.makeText(this, "Old Password And New Password Not Be Same", Toast.LENGTH_SHORT).show()
                    binding2.etNewPassword.error = "Old Password And New Password Not Be Same"
                }
            }else {
                Toast.makeText(this, "Wrong password entered", Toast.LENGTH_SHORT).show()
                binding2.etOldPassword.error = "Old Password Not Matching"
            }
        }
        binding2.btnResetPassword.setOnClickListener{
            if (prefsUtil.getSecurityQuestion() != null) showSecurityQuestionDialog(prefsUtil.getSecurityQuestion().toString())
            else Toast.makeText(this, "Security question not set yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSecurityQuestionDialog(securityQuestion: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_security_question, null)

        val questionTextView: TextView = dialogView.findViewById(R.id.security_question)
        questionTextView.text = securityQuestion


        MaterialAlertDialogBuilder(this)
            .setTitle("Answer the Security Question!")
            .setView(dialogView)
            .setPositiveButton("Verify") { dialog, _ ->
                val inputEditText: TextInputEditText = dialogView.findViewById(R.id.text_input_edit_text)
                val userAnswer = inputEditText.text.toString().trim()

                if (userAnswer.isEmpty()) {
                    Toast.makeText(this, "Answer cannot be empty!", Toast.LENGTH_SHORT).show()
                } else {
                    if (prefsUtil.validateSecurityAnswer(userAnswer)){
                        prefsUtil.resetPassword()
                        Toast.makeText(this, "Password successfully reset.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }else {
                        Toast.makeText(this, "Invalid answer!", Toast.LENGTH_SHORT).show()
                    }

                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->

                dialog.dismiss()
            }
            .show()
    }
}