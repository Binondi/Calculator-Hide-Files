package devs.org.calculator.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import androidx.core.content.edit

class PrefsUtil(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("Calculator", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean {
        return prefs.getString("password", "")?.isNotEmpty() ?: false
    }

    fun savePassword(password: String) {
        val hashedPassword = hashPassword(password)
        prefs.edit {
            putString("password", hashedPassword)
        }
    }

    fun setBoolean(key:String, value: Boolean){
        return prefs.edit { putBoolean(key, value) }

    }
    fun setInt(key:String, value: Int){
        return prefs.edit { putInt(key, value) }

    }

    fun getBoolean(key: String, defValue: Boolean = false): Boolean{
        return prefs.getBoolean(key,defValue)
    }
    fun getInt(key: String, defValue: Int): Int{
        return prefs.getInt(key,defValue)
    }

    fun resetPassword(){
        prefs.edit {
            remove("password")
                .remove("security_question")
                .remove("security_answer")
        }
    }

    fun validatePassword(input: String): Boolean {
        val stored = prefs.getString("password", "") ?: ""
        return stored == hashPassword(input)
    }

    fun saveSecurityQA(question: String, answer: String) {
        prefs.edit {
            putString("security_question", question)
                .putString("security_answer", hashPassword(answer))
        }
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