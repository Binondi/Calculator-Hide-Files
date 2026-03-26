package devs.org.calculator.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import devs.org.calculator.R
import devs.org.calculator.databinding.ActivityChangePasswordBinding
import devs.org.calculator.databinding.ActivitySetupPasswordBinding

class SetupPasswordActivity : BaseActivity() {
    private lateinit var binding: ActivitySetupPasswordBinding
    private lateinit var binding2: ActivityChangePasswordBinding
    private var hasPassword = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupPasswordBinding.inflate(layoutInflater)
        binding2 = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hasPassword = prefs.hasPassword()
        if (hasPassword){
            setContentView(binding2.root)
        }else{
            setContentView(binding.root)
        }
        binding.etSecurityQuestion.isFocusable = true
        enableEdgeToEdge()
        setViewPadding()
        setupSecurityQuestions()
        clickListeners()

    }

    private fun setupSecurityQuestions() {
        val questions = arrayOf(
            getString(R.string.what_is_your_pet_name),
            getString(R.string.what_is_your_birth_city),
            getString(R.string.what_is_your_favorite_book),
            getString(R.string.what_is_your_mother_maiden_name),
            getString(R.string.what_is_your_favorite_color),
            getString(R.string.custom_question)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, questions)
        binding.etSecurityQuestion.setAdapter(adapter)
        binding.etSecurityQuestion.inputType = android.text.InputType.TYPE_NULL

        binding.etSecurityQuestion.isFocusable = false
        binding.etSecurityQuestion.isFocusableInTouchMode = false
        binding.etSecurityQuestion.isCursorVisible = false
        binding.etSecurityQuestion.keyListener = null

        binding.etSecurityQuestion.setOnItemClickListener { _, _, position, _ ->
            if (questions[position] == getString(R.string.custom_question)) {
                binding.etSecurityQuestion.setText("")
                binding.etSecurityQuestion.isFocusable = true
                binding.etSecurityQuestion.isFocusableInTouchMode = true
                binding.etSecurityQuestion.isCursorVisible = true
                binding.etSecurityQuestion.keyListener = android.text.method.TextKeyListener.getInstance()

                binding.etSecurityQuestion.requestFocus()

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etSecurityQuestion, InputMethodManager.SHOW_IMPLICIT)
            } else {
                binding.etSecurityQuestion.isFocusable = false
                binding.etSecurityQuestion.isFocusableInTouchMode = false
                binding.etSecurityQuestion.isCursorVisible = false
                binding.etSecurityQuestion.keyListener = null

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etSecurityQuestion.windowToken, 0)
            }
        }
    }

    private fun setViewPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
            prefs.savePassword(password)
            prefs.saveSecurityQA(securityQuestion, securityAnswer)
            Toast.makeText(this, getString(R.string.password_set_successfully), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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

            if (prefs.validatePassword(oldPassword)){
                if (oldPassword != newPassword){
                    prefs.savePassword(newPassword)
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
            if (prefs.getSecurityQuestion() != null) showSecurityQuestionDialog(prefs.getSecurityQuestion().toString())
            else Toast.makeText(this, getString(R.string.this_field_can_t_be_empty), Toast.LENGTH_SHORT).show()
        }
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        binding2.toolbar.setNavigationOnClickListener {
            finish()
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
                    if (prefs.validateSecurityAnswer(userAnswer)){
                        prefs.resetPassword()
                        Toast.makeText(this,
                            getString(R.string.password_successfully_reset), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        finish()
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
