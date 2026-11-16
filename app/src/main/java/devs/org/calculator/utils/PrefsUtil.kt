package devs.org.calculator.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest

class PrefsUtil(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("Calculator", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean {
        return prefs.getString("password", "")?.isNotEmpty() ?: false
    }

    fun savePassword(password: String) {
        val hashedPassword = hashPassword(password)
        prefs.edit {
            putString("password", hashedPassword)
        }
    }

    fun setBoolean(key: String, value: Boolean) {
        return prefs.edit { putBoolean(key, value) }

    }

    fun setInt(key: String, value: Int) {
        return prefs.edit { putInt(key, value) }

    }



    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defValue)
    }

    fun getInt(key: String, defValue: Int): Int {
        return prefs.getInt(key, defValue)
    }

    fun resetPassword() {
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

    fun shouldShowDonateOnVaultOpen(): Boolean {
        if (getBoolean(PREF_DONATE_NEVER_SHOW_AGAIN, false)) {
            return false
        }
        val hasShownFirst = getBoolean(PREF_DONATE_SHOWN_FIRST_TIME, false)
        if (!hasShownFirst) {
            return true
        }
        val openCount = getInt(PREF_DONATE_VAULT_OPEN_COUNT, 0) + 1
        setInt(PREF_DONATE_VAULT_OPEN_COUNT, openCount)
        if (openCount >= 15) {
            setInt(PREF_DONATE_VAULT_OPEN_COUNT, 0)
            return true
        }
        return false
    }

    fun setDonateShown() {
        setBoolean(PREF_DONATE_SHOWN_FIRST_TIME, true)
    }

    fun setDonateNeverShowAgain(neverShow: Boolean) {
        setBoolean(PREF_DONATE_NEVER_SHOW_AGAIN, neverShow)
    }

    companion object {
        const val PREF_DONATE_NEVER_SHOW_AGAIN = "donate_never_show_again"
        const val PREF_DONATE_SHOWN_FIRST_TIME = "donate_shown_first_time"
        const val PREF_DONATE_VAULT_OPEN_COUNT = "donate_vault_open_count"
    }
}