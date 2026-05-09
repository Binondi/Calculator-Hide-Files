package devs.org.calculator.views

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.abs

class CustomInputKeyBoard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val maxTextSize = 90f
    private val minTextSize = 24f
    private val testPaint = Paint()

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
        post { 
            adjustTextSize()
            if (maxLines > 1) {
                scrollToCursor()
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            adjustTextSize()
            if (maxLines > 1) {
                scrollToCursor()
            }
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (maxLines > 1) {
            scrollToCursor()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        adjustTextSize()
        if (maxLines > 1) {
            scrollToCursor()
        }
    }

    fun resetTextSize() {
        updateTextSizeAndLines(maxTextSize, 1)
        post { adjustTextSize() }
    }

    private fun adjustTextSize() {
        if (width <= 0) return

        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val currentText = text?.toString() ?: ""

        if (currentText.isEmpty() || currentText == "0") {
            updateTextSizeAndLines(maxTextSize, 1)
            return
        }

        var size = maxTextSize
        while (size > minTextSize) {
            val textWidth = getTextWidthAtSize(currentText, size)
            if (textWidth <= availableWidth) break
            size -= 1f
        }

        val textWidthAtMin = getTextWidthAtSize(currentText, size)
        val targetMaxLines = if (size <= minTextSize && textWidthAtMin > availableWidth) {
            Int.MAX_VALUE
        } else {
            1
        }

        updateTextSizeAndLines(size, targetMaxLines)
    }

    private fun updateTextSizeAndLines(spSize: Float, targetMaxLines: Int) {
        val targetPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, spSize, resources.displayMetrics
        )

        if (abs(this.textSize - targetPx) > 0.5f) {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
        }

        if (this.maxLines != targetMaxLines) {
            this.maxLines = targetMaxLines
            if (targetMaxLines > 1) {
                post { scrollToCursor() }
            } else {
                scrollTo(0, 0)
            }
        }
    }

    private fun scrollToCursor() {
        post {
            if (layout == null) return@post
            val cursorOffset = selectionStart
            if (cursorOffset < 0) return@post
            
            val line = layout.getLineForOffset(cursorOffset)
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)
            
            val visibleHeight = height - paddingTop - paddingBottom
            val currentScrollY = scrollY
            
            var targetScrollY = currentScrollY
            
            if (lineTop < currentScrollY) {
                targetScrollY = lineTop
            } else if (lineBottom > currentScrollY + visibleHeight) {
                targetScrollY = lineBottom - visibleHeight
            }
            
            if (this.scrollY != targetScrollY) {
                scrollTo(0, targetScrollY)
            }
        }
    }

    private fun getTextWidthAtSize(text: String, spSize: Float): Float {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, spSize, resources.displayMetrics
        )
        testPaint.textSize = px
        testPaint.typeface = typeface
        return testPaint.measureText(text)
    }
}