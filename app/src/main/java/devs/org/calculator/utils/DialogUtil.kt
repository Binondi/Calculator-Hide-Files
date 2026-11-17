package devs.org.calculator.utils

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import devs.org.calculator.R
import devs.org.calculator.databinding.BottomSheetConfirmationBinding
import devs.org.calculator.databinding.BottomSheetDonateBinding
import devs.org.calculator.databinding.BottomSheetInputBinding
import devs.org.calculator.databinding.BottomSheetShakeFeatureBinding

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
        bottomSheet.expandFully()
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
        bottomSheet.expandFully()
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

    fun showDonateBottomSheet(onDismiss: (() -> Unit)? = null): BottomSheetDialog? {
        return Companion.showDonateBottomSheet(context, onDismiss)
    }

    interface DialogCallback {
        fun onPositiveButtonClicked()
        fun onNegativeButtonClicked()
        fun onNaturalButtonClicked() {}
    }

    interface InputDialogCallback {
        fun onPositiveButtonClicked(input: String)
    }

    companion object {
        fun showDonateBottomSheet(context: Context, onDismiss: (() -> Unit)? = null): BottomSheetDialog? {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                return null
            }

            val prefsUtil = PrefsUtil(context)
            val bottomSheet = BottomSheetDialog(context)
            bottomSheet.expandFully()
            val binding = BottomSheetDonateBinding.inflate(LayoutInflater.from(context))
            bottomSheet.setContentView(binding.root)

            prefsUtil.setDonateShown()

            val openUrl = { url: String ->
                prefsUtil.setDonateNeverShowAgain(true)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                }
            }

            binding.cardGitHubSponsors.setOnClickListener {
                openUrl(context.getString(R.string.github_sponsors_url))
            }

            binding.cardBuyMeACoffee.setOnClickListener {
                openUrl(context.getString(R.string.coffee_url))
            }

            binding.cardPayPal.setOnClickListener {
                openUrl(context.getString(R.string.paypal_url))
            }

            binding.cardKofi.setOnClickListener {
                openUrl(context.getString(R.string.ko_fi_url))
            }

            binding.cardUpi.setOnClickListener {
                prefsUtil.setDonateNeverShowAgain(true)
                val upiId = context.getString(R.string.upi_id)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("UPI ID", upiId)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.upi_copied_toast), Toast.LENGTH_SHORT).show()

                try {
                    val upiUri = Uri.parse("upi://pay?pa=$upiId&pn=Binondi%20Borthakur&cu=INR")
                    val intent = Intent(Intent.ACTION_VIEW, upiUri)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Handled: clipboard copy is sufficient
                }
            }

            val currentNeverShow = prefsUtil.getBoolean(PrefsUtil.PREF_DONATE_NEVER_SHOW_AGAIN, false)
            binding.cbNeverShowAgain.isChecked = currentNeverShow

            binding.cbNeverShowAgain.setOnCheckedChangeListener { _, isChecked ->
                prefsUtil.setDonateNeverShowAgain(isChecked)
            }

            binding.btnMaybeLater.setOnClickListener {
                prefsUtil.setDonateNeverShowAgain(binding.cbNeverShowAgain.isChecked)
                bottomSheet.dismiss()
            }

            bottomSheet.setOnDismissListener {
                prefsUtil.setDonateNeverShowAgain(binding.cbNeverShowAgain.isChecked)
                onDismiss?.invoke()
            }

            bottomSheet.show()
            return bottomSheet
        }

        fun showShakeFeatureAnnouncement(context: Context, onDismiss: (() -> Unit)? = null): BottomSheetDialog? {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                return null
            }

            val prefsUtil = PrefsUtil(context)
            val bottomSheet = BottomSheetDialog(context)
            bottomSheet.expandFully()
            val binding = BottomSheetShakeFeatureBinding.inflate(LayoutInflater.from(context))
            bottomSheet.setContentView(binding.root)

            binding.btnEnableShake.setOnClickListener {
                prefsUtil.setShakeToCloseEnabled(true)
                prefsUtil.setSeenShakeAnnouncement(true)
                Toast.makeText(context, context.getString(R.string.shake_enabled_toast), Toast.LENGTH_LONG).show()
                bottomSheet.dismiss()
            }

            binding.btnMaybeLater.setOnClickListener {
                prefsUtil.setSeenShakeAnnouncement(true)
                bottomSheet.dismiss()
            }

            bottomSheet.setOnDismissListener {
                prefsUtil.setSeenShakeAnnouncement(true)
                onDismiss?.invoke()
            }

            bottomSheet.show()
            return bottomSheet
        }
    }
}

fun BottomSheetDialog.expandFully(onShow: ((DialogInterface) -> Unit)? = null) {
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    behavior.skipCollapsed = true
    behavior.isFitToContents = true

    setOnShowListener { dialog ->
        val d = dialog as? BottomSheetDialog ?: return@setOnShowListener
        val bottomSheetInternal = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheetInternal != null) {
            val b = BottomSheetBehavior.from(bottomSheetInternal)
            b.state = BottomSheetBehavior.STATE_EXPANDED
            b.skipCollapsed = true
            b.isFitToContents = true
        }
        onShow?.invoke(dialog)
    }
}