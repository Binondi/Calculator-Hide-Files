package devs.org.calculator.utils

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R

class DialogUtil(private val context: Context) {

    private var activeDialog: AlertDialog? = null

    fun showMaterialDialog(
        title: String,
        message: String = "",
        positiveButtonText: String,
        negativeButtonText: String,
        callback: DialogCallback,
        view: View? = null
    ): AlertDialog? {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return null
        }

        dismissActiveDialog()

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ -> callback.onPositiveButtonClicked() }
            .setNegativeButton(negativeButtonText) { _, _ -> callback.onNegativeButtonClicked() }

        if (view != null) {
            builder.setView(view)
        }

        val dialog = builder.create()
        dialog.setOnDismissListener {
            if (activeDialog == dialog) {
                activeDialog = null
            }
        }
        activeDialog = dialog
        dialog.show()
        return dialog
    }

    fun createInputDialog(
        title: String,
        hint: String,
        callback: InputDialogCallback
    ): AlertDialog? {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return null
        }

        dismissActiveDialog()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.hint = hint

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.create) { _, _ ->
                callback.onPositiveButtonClicked(editText.text.toString())
            }
            .setNegativeButton(R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.setOnDismissListener {
            if (activeDialog == dialog) {
                activeDialog = null
            }
        }
        activeDialog = dialog
        dialog.show()
        return dialog
    }

    fun dismissActiveDialog() {
        try {
            activeDialog?.let {
                if (it.isShowing) {
                    it.dismiss()
                }
            }
        } catch (_: Exception) {}
        activeDialog = null
    }

    interface DialogCallback {
        fun onPositiveButtonClicked()
        fun onNegativeButtonClicked()
        fun onNaturalButtonClicked()
    }

    interface InputDialogCallback {
        fun onPositiveButtonClicked(input: String)
    }
}