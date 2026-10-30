package io.github.kazands.networklens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/** Shared native text metrics; sizes and leading follow Android's font scaling. */
internal object AppTypography {
    const val LABEL = 12
    const val METADATA = 12
    const val LIST_ADDRESS = 13
    const val DETAIL_ADDRESS = 16

    fun text(context: Context, value: String, size: Int, color: Int, emphasis: Boolean): TextView =
        TextView(context).also {
            it.text = value
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            it.setTextColor(color)
            it.typeface = face(size, emphasis)
            it.includeFontPadding = false
            leading(it, size)
        }

    fun face(size: Int, emphasis: Boolean): Typeface {
        if (!emphasis) return Typeface.create("sans-serif", Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28)
            Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), if (size >= 20) 600 else 500, false)
        else Typeface.create(if (size >= 20) "sans-serif" else "sans-serif-medium", if (size >= 20) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun leading(view: TextView, size: Int) {
        val height = size + when { size <= 12 -> 4; size <= 14 -> 6; size <= 18 -> 8; else -> 6 }
        if (Build.VERSION.SDK_INT >= 34) view.setLineHeight(TypedValue.COMPLEX_UNIT_SP, height.toFloat())
        else {
            val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, height.toFloat(), view.resources.displayMetrics)
            view.setLineSpacing(maxOf(0f, pixels - view.paint.fontMetricsInt.let { it.descent - it.ascent }), 1f)
        }
    }

    /** Wrap the original identifier, including an IPv6 scope, without adding copyable characters. */
    fun identifier(view: TextView, size: Int) {
        view.typeface = Typeface.MONOSPACE
        view.textDirection = View.TEXT_DIRECTION_LTR
        view.setHorizontallyScrolling(false)
        view.maxLines = Int.MAX_VALUE
        view.ellipsize = null
        view.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        view.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        leading(view, size)
    }

    /** Atomic hex groups give the line breaker useful boundaries without changing the address. */
    fun address(value: String): CharSequence {
        val start = if (value.startsWith("IPv6  ")) 6 else 0
        val address = value.substring(start)
        if (Address.bytes(address)?.size != 16) return value
        val end = value.indexOf('%', start).let { if (it < 0) value.length else it }
        val styled = SpannableString(value)
        var from = start
        while (from < end) {
            var next = from
            while (next < end && value[next] != ':') next++
            if (next < end) next++
            if (next < end && value[next] == ':') next++
            // A mixed IPv6/IPv4 tail can exceed a narrow line; let its decimal groups wrap normally.
            if ('.' !in value.subSequence(from, next))
                styled.setSpan(HexGroup(), from, next, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            from = next
        }
        return styled
    }

    private class HexGroup : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            if (fm != null) paint.getFontMetricsInt(fm)
            return kotlin.math.ceil(paint.measureText(text, start, end).toDouble()).toInt()
        }
        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
            top: Int, y: Int, bottom: Int, paint: Paint) {
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
        }
    }
}
