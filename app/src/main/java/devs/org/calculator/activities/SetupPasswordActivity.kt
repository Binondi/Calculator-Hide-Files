package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import devs.org.calculator.databinding.ActivitySetupPasswordBinding
import devs.org.calculator.utils.PrefsUtil

class SetupPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupPasswordBinding
    private lateinit var prefsUtil: PrefsUtil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefsUtil = PrefsUtil(this)

        binding.btnSavePassword.setOnClickListener {
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (password == confirmPassword && password.isNotEmpty()) {
                prefsUtil.savePassword(password)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                binding.etPassword.error = "Passwords don't match"
            }
        }

        binding.btnResetPassword.setOnClickListener {
            // Implement password reset logic
            // Could use security questions or email verification
        }
    }
}