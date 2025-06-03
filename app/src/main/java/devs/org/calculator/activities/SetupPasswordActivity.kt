package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
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
    private val prefs:PrefsUtil by lazy { PrefsUtil(this) }

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
                binding.etPassword.error = getString(R.string.enter_password)
                return@setOnClickListener
            }
            if (confirmPassword.isEmpty()){
                binding.etConfirmPassword.error = getString(R.string.confirm_password)
                return@setOnClickListener
            }
            if (securityQuestion.isEmpty()){
                binding.etSecurityQuestion.error = getString(R.string.enter_security_question)
                return@setOnClickListener
            }
            if (securityAnswer.isEmpty()){
                binding.etSecurityAnswer.error = getString(R.string.enter_security_answer)
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                binding.etPassword.error = getString(R.string.passwords_don_t_match)
                return@setOnClickListener
            }
            prefsUtil.savePassword(password)
            prefsUtil.saveSecurityQA(securityQuestion, securityAnswer)
            Toast.makeText(this, getString(R.string.password_set_successfully), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.btnResetPassword.setOnClickListener {
            if (prefsUtil.getSecurityQuestion() != null) showSecurityQuestionDialog(prefsUtil.getSecurityQuestion().toString())
            else Toast.makeText(this,
                getString(R.string.security_question_not_set_yet), Toast.LENGTH_SHORT).show()

        }
        binding2.btnChangePassword.setOnClickListener{
            val oldPassword = binding2.etOldPassword.text.toString()
            val newPassword = binding2.etNewPassword.text.toString()
            if (oldPassword.isEmpty()) {
                binding2.etOldPassword.error = getString(R.string.this_field_can_t_be_empty)
                return@setOnClickListener
            }
            if (newPassword.isEmpty()) {
                binding2.etNewPassword.error = getString(R.string.this_field_can_t_be_empty)
                return@setOnClickListener
            }

            if (prefsUtil.validatePassword(oldPassword)){
                if (oldPassword != newPassword){
                    prefsUtil.savePassword(newPassword)
                    Toast.makeText(this,
                        getString(R.string.password_reset_successfully), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()

                }else {
                    Toast.makeText(this,
                        getString(R.string.old_password_and_new_password_not_be_same), Toast.LENGTH_SHORT).show()
                    binding2.etNewPassword.error = getString(R.string.old_password_and_new_password_not_be_same)
                }
            }else {
                Toast.makeText(this, getString(R.string.wrong_password_entered), Toast.LENGTH_SHORT).show()
                binding2.etOldPassword.error = getString(R.string.old_password_not_matching)
            }
        }
        binding2.btnResetPassword.setOnClickListener{
            if (prefsUtil.getSecurityQuestion() != null) showSecurityQuestionDialog(prefsUtil.getSecurityQuestion().toString())
            else Toast.makeText(this, getString(R.string.this_field_can_t_be_empty), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSecurityQuestionDialog(securityQuestion: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_security_question, null)

        val questionTextView: TextView = dialogView.findViewById(R.id.security_question)
        questionTextView.text = securityQuestion


        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.answer_the_security_question))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.verify)) { dialog, _ ->
                val inputEditText: TextInputEditText = dialogView.findViewById(R.id.text_input_edit_text)
                val userAnswer = inputEditText.text.toString().trim()

                if (userAnswer.isEmpty()) {
                    Toast.makeText(this,
                        getString(R.string.answer_cannot_be_empty), Toast.LENGTH_SHORT).show()
                } else {
                    if (prefsUtil.validateSecurityAnswer(userAnswer)){
                        prefsUtil.resetPassword()
                        Toast.makeText(this,
                            getString(R.string.password_successfully_reset), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }else {
                        Toast.makeText(this, getString(R.string.invalid_answer), Toast.LENGTH_SHORT).show()
                    }

                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->

                dialog.dismiss()
            }
            .show()
    }
}