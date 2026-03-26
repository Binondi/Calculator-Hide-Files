package devs.org.calculator.customViews

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.graphics.Paint
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
        maxLines = 1
        isSingleLine = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun getCursorPosition(): Int = selectionStart

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        post { adjustTextSize() }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        adjustTextSize()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        adjustTextSize()
    }

    fun resetTextSize() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, maxTextSize)
        maxLines = 1
        post { adjustTextSize() }
    }

    private fun adjustTextSize() {
        if (width <= 0) return

        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val currentText = text?.toString() ?: ""

        if (currentText.isEmpty() || currentText == "0") {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, maxTextSize)
            maxLines = 1
            return
        }

        var size = maxTextSize
        while (size > minTextSize) {
            val textWidth = getTextWidthAtSize(currentText, size)
            if (textWidth <= availableWidth) break
            size -= 1f
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)

        val textWidthAtMin = getTextWidthAtSize(currentText, size)
        if (size <= minTextSize && textWidthAtMin > availableWidth) {
            maxLines = Int.MAX_VALUE
            post { scrollToBottom() }
        } else {
            maxLines = 1
        }
    }

    private fun scrollToBottom() {
        post {
            if (layout == null) return@post
            val lastLineBottom = layout.getLineBottom(lineCount - 1)
            val visibleHeight = height - paddingTop - paddingBottom
            val scrollY = (lastLineBottom - visibleHeight).coerceAtLeast(0)
            scrollTo(0, scrollY)
        }
    }

    private fun getTextWidthAtSize(text: String, spSize: Float): Float {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, spSize, resources.displayMetrics
        )
        val testPaint = Paint()
        testPaint.textSize = px
        testPaint.typeface = typeface
        return testPaint.measureText(text)
    }
}