package devs.org.calculator.utils

import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import devs.org.calculator.R
import devs.org.calculator.callbacks.DialogActionsCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DialogUtil(private val context: Context) {
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    fun showMaterialDialogWithNaturalButton(
        title: String,
        message: String,
        positiveButton: String,
        negativeButton: String,
        neutralButton: String,
        callback: DialogActionsCallback
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton) { dialog, _ ->
                // Handle positive button click
                callback.onPositiveButtonClicked()
                dialog.dismiss()
            }
            .setNegativeButton(negativeButton) { dialog, _ ->
                // Handle negative button click
                callback.onNegativeButtonClicked()
                dialog.dismiss()
            }
            .setNeutralButton(neutralButton) { dialog, _ ->
                callback.onNaturalButtonClicked()
                dialog.dismiss()
            }
            .show()
    }
    fun showMaterialDialog(
        title: String,
        message: String,
        positiveButton: String,
        negativeButton: String,
        callback: DialogActionsCallback
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton) { dialog, _ ->
                // Handle positive button click
                callback.onPositiveButtonClicked()
            }
            .setNegativeButton(negativeButton) { dialog, _ ->
                // Handle negative button click
                callback.onNegativeButtonClicked()
                dialog.dismiss()
            }
            .show()
    }
}