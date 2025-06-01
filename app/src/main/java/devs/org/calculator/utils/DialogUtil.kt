package devs.org.calculator.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R

class DialogUtil(private val context: Context) {

    fun showMaterialDialog(
        title: String,
        message: String,
        positiveButtonText: String,
        neutralButtonText: String,
        callback: DialogCallback
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ -> callback.onPositiveButtonClicked() }
            .setNegativeButton(neutralButtonText) { _, _ -> callback.onNegativeButtonClicked() }
            .show()
    }

    fun createInputDialog(
        title: String,
        hint: String,
        callback: InputDialogCallback
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.hint = hint

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.create) { _, _ ->
                callback.onPositiveButtonClicked(editText.text.toString())
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
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