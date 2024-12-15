package devs.org.calculator.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class PrefsUtil(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("Calculator", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean {
        return prefs.getString("password", "")?.isNotEmpty() ?: false
    }

    fun savePassword(password: String) {
        val hashedPassword = hashPassword(password)
        prefs.edit()
            .putString("password", hashedPassword)
            .apply()
    }

    fun resetPassword(){
        prefs.edit()
            .remove("password")
            .remove("security_question")
            .remove("security_answer")
            .apply()
    }

    fun validatePassword(input: String): Boolean {
        val stored = prefs.getString("password", "") ?: ""
        return stored == hashPassword(input)
    }

    fun saveSecurityQA(question: String, answer: String) {
        prefs.edit()
            .putString("security_question", question)
            .putString("security_answer", hashPassword(answer))
            .apply()
    }

    fun validateSecurityAnswer(answer: String): Boolean {
        val stored = prefs.getString("security_answer", "") ?: ""
        return stored == hashPassword(answer)
    }

    fun getSecurityQuestion(): String? {
        return prefs.getString("security_question", null)
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}