package devs.org.calculator.customViews

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatEditText

class CustomInputKeyBoard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val maxTextSize = 80f
    private val minTextSize = 50f

    init {
        showSoftInputOnFocus = false
        isCursorVisible = true
        isFocusable = true
        isFocusableInTouchMode = true
        isLongClickable = false
        setTextIsSelectable(false)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, maxTextSize)
        maxLines = 3
        isSingleLine = false
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        adjustTextSize()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        adjustTextSize()
    }

    fun resetTextSize() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, maxTextSize)
    }

    private fun adjustTextSize() {
        if (width <= 0) return

        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val currentText = text?.toString() ?: ""

        if (currentText.isEmpty() || currentText == "0") {
            resetTextSize()
            return
        }

        var size = maxTextSize

        while (size > minTextSize) {
            val textPaint = paint
            textPaint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics
            )
            val textWidth = textPaint.measureText(currentText)
            if (textWidth <= availableWidth * 3) break
            size -= 1f
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)

        maxLines = if (size <= minTextSize) {
            Int.MAX_VALUE
        } else {
            3
        }
    }
}