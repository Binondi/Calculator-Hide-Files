package devs.org.calculator.utils

import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DialogUtil(private val context: Context, private var lifecycleOwner: LifecycleOwner) {
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    fun showMaterialDialog(
        title: String,
        message: String,
        positiveButton: String,
        negativeButton: String,
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton) { dialog, _ ->
                // Handle positive button click
                dialog.dismiss()
            }
            .setNegativeButton(negativeButton) { dialog, _ ->
                // Handle negative button click
                dialog.dismiss()
            }
            .show()
    }
    fun showMaterialDialog(
        title: String,
        message: String,
        positiveButton: String,
        negativeButton: String,
        uri: Uri
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton) { dialog, _ ->
                // Handle positive button click
                if (positiveButton == "Delete") {
                    lifecycleOwner.lifecycleScope.launch {
                        deletePhotoFromExternalStorage(uri)
                    }
                }else{
                    // copy file to a visible directory

                }
            }
            .setNegativeButton(negativeButton) { dialog, _ ->
                // Handle negative button click
                dialog.dismiss()
            }
            .show()
    }

    suspend fun deletePhotoFromExternalStorage(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // First try to delete using DocumentFile
                val documentFile = DocumentFile.fromSingleUri(context, photoUri)
                if (documentFile?.exists() == true && documentFile.canWrite()) {
                    val deleted = documentFile.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
//                            Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@withContext
                }

                // If DocumentFile approach fails, try content resolver
                try {
                    context.contentResolver.delete(photoUri, null, null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: SecurityException) {
                    // Handle security exception for Android 10 and above
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val intentSender = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                MediaStore.createDeleteRequest(context.contentResolver, listOf(photoUri)).intentSender
                            }
                            else -> {
                                val recoverableSecurityException = e as? RecoverableSecurityException
                                recoverableSecurityException?.userAction?.actionIntent?.intentSender
                            }
                        }
                        intentSender?.let { sender ->
                            intentSenderLauncher.launch(
                                IntentSenderRequest.Builder(sender).build()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error deleting file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}