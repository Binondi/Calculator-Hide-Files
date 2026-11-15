package devs.org.calculator.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import devs.org.calculator.R
import devs.org.calculator.databinding.BottomSheetConfirmationBinding
import devs.org.calculator.databinding.BottomSheetInputBinding

class DialogUtil(private val context: Context) {

    private var activeDialog: Dialog? = null

    fun showMaterialDialog(
        title: String,
        message: String = "",
        positiveButtonText: String,
        negativeButtonText: String,
        callback: DialogCallback,
        view: View? = null
    ): Dialog? {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return null
        }

        dismissActiveDialog()

        val bottomSheet = BottomSheetDialog(context)
        val binding = BottomSheetConfirmationBinding.inflate(LayoutInflater.from(context))
        bottomSheet.setContentView(binding.root)

        binding.tvDialogTitle.text = title
        if (message.isBlank()) {
            binding.tvDialogMessage.visibility = View.GONE
        } else {
            binding.tvDialogMessage.text = message
            binding.tvDialogMessage.visibility = View.VISIBLE
        }

        if (view != null) {
            binding.customViewContainer.visibility = View.VISIBLE
            binding.customViewContainer.addView(view)
        } else {
            binding.customViewContainer.visibility = View.GONE
        }

        val isDestructive = title.contains("delete", ignoreCase = true) ||
                positiveButtonText.contains("delete", ignoreCase = true) ||
                title.contains("clear", ignoreCase = true)

        if (isDestructive) {
            binding.dialogIcon.setImageResource(R.drawable.ic_delete)
            val errorColor = androidx.core.content.ContextCompat.getColor(context, R.color.md_theme_error)
            val errorContainerColor = androidx.core.content.ContextCompat.getColor(context, R.color.md_theme_errorContainer)
            binding.dialogIcon.imageTintList = ColorStateList.valueOf(errorColor)
            binding.dialogIconCard.setCardBackgroundColor(errorContainerColor)
            binding.btnPositive.backgroundTintList = ColorStateList.valueOf(errorColor)
        } else if (title.contains("permission", ignoreCase = true) || message.contains("permission", ignoreCase = true)) {
            binding.dialogIcon.setImageResource(R.drawable.ic_privacy_tip)
        } else if (title.contains("un-hide", ignoreCase = true) || title.contains("unhide", ignoreCase = true)) {
            binding.dialogIcon.setImageResource(R.drawable.ic_redo)
        }

        binding.btnPositive.text = positiveButtonText
        binding.btnPositive.setOnClickListener {
            bottomSheet.dismiss()
            callback.onPositiveButtonClicked()
        }

        binding.btnNegative.text = negativeButtonText
        binding.btnNegative.setOnClickListener {
            bottomSheet.dismiss()
            callback.onNegativeButtonClicked()
        }

        bottomSheet.setOnDismissListener {
            if (activeDialog == bottomSheet) {
                activeDialog = null
            }
        }

        activeDialog = bottomSheet
        bottomSheet.show()
        return bottomSheet
    }

    fun createInputDialog(
        title: String,
        hint: String,
        initialText: String = "",
        positiveButtonText: String = context.getString(R.string.create),
        callback: InputDialogCallback
    ): Dialog? {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return null
        }

        dismissActiveDialog()

        val bottomSheet = BottomSheetDialog(context)
        val binding = BottomSheetInputBinding.inflate(LayoutInflater.from(context))
        bottomSheet.setContentView(binding.root)

        binding.tvInputTitle.text = title
        binding.tilInputField.hint = hint
        if (initialText.isNotEmpty()) {
            binding.etInputField.setText(initialText)
            binding.etInputField.selectAll()
        }
        binding.btnPositiveInput.text = positiveButtonText

        binding.btnPositiveInput.setOnClickListener {
            val text = binding.etInputField.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                bottomSheet.dismiss()
                callback.onPositiveButtonClicked(text)
            }
        }

        binding.btnNegativeInput.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.setOnDismissListener {
            if (activeDialog == bottomSheet) {
                activeDialog = null
            }
        }

        activeDialog = bottomSheet
        bottomSheet.show()
        return bottomSheet
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
        fun onNaturalButtonClicked() {}
    }

    interface InputDialogCallback {
        fun onPositiveButtonClicked(input: String)
    }
}