package io.github.kazands.networklens

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Native branded dialog with independent body scrolling and fixed actions. */
open class AppDialog(context: Context, @JvmField protected val colors: Colors, title: String) : Dialog(context) {
    class Colors(
        @JvmField val background: Int, @JvmField val surface: Int, @JvmField val text: Int,
        @JvmField val muted: Int, @JvmField val border: Int, @JvmField val accent: Int, @JvmField val soft: Int
    ) {
        @JvmField val danger = if (Color.luminance(background).toDouble() < .3) Color.rgb(242, 180, 173) else Color.rgb(154, 52, 45)
    }
    fun interface Action { fun run(): Boolean }

    @JvmField protected val shell: LinearLayout
    @JvmField protected val body: LinearLayout
    @JvmField protected val scroll: ScrollView
    private val footer: LinearLayout
    private var primary: TextView? = null
    private var secondary: TextView? = null

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(true)
        shell = object : LinearLayout(context) {
            override fun onMeasure(width: Int, height: Int) {
                val maximum = (resources.displayMetrics.heightPixels * .86f).toInt()
                val offered = if (View.MeasureSpec.getMode(height) == View.MeasureSpec.UNSPECIFIED) maximum
                    else minOf(maximum, View.MeasureSpec.getSize(height))
                super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(offered, View.MeasureSpec.AT_MOST))
            }
        }
        shell.orientation = LinearLayout.VERTICAL
        shell.background = rounded(colors.background, colors.border, 24)
        shell.clipToOutline = true
        shell.isFocusableInTouchMode = true
        shell.requestFocus()
        val heading = paragraph(title, 22, colors.text, true)
        heading.setPadding(px(22), px(22), px(22), px(16))
        if (Build.VERSION.SDK_INT >= 28) heading.isAccessibilityHeading = true
        shell.addView(heading)
        scroll = ScrollView(context)
        scroll.isFillViewport = false
        scroll.clipToPadding = false
        body = vertical()
        body.setPadding(px(20), 0, px(20), px(18))
        scroll.addView(body)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, -2, 1f))
        val rule = View(context)
        rule.setBackgroundColor(colors.border)
        shell.addView(rule, LinearLayout.LayoutParams(-1, px(1)))
        footer = LinearLayout(context)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        footer.setPadding(px(20), px(14), px(20), px(18))
        shell.addView(footer)
        setContentView(shell)
        window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.setDimAmount(.42f)
            it.setTitle(title)
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    fun primary(label: String, action: Action?): AppDialog = primary(label, false, action)
    fun primary(label: String, destructive: Boolean, action: Action?): AppDialog {
        primary = actionButton(label, true, destructive).also { button ->
            button.setOnClickListener { if (action == null || action.run()) dismiss() }
        }
        actions()
        return this
    }
    fun secondary(label: String, action: Action?): AppDialog {
        secondary = actionButton(label, false, false).also { button ->
            button.setOnClickListener { if (action == null || action.run()) dismiss() }
        }
        actions()
        return this
    }
    private fun actions() {
        footer.removeAllViews()
        secondary?.let {
            footer.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
            footer.addView(View(context), LinearLayout.LayoutParams(px(10), 1))
        }
        primary?.let { footer.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
    }
    override fun onStart() { super.onStart(); resize() }
    fun resize() {
        val window = window ?: return
        val width = minOf(px(560), context.resources.displayMetrics.widthPixels - px(32))
        window.setLayout(maxOf(px(200), width), WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
        shell.requestLayout()
    }
    fun content(): LinearLayout = body
    fun paragraph(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(context).also {
            it.text = value
            it.setTextSize(size.toFloat())
            it.setTextColor(color)
            it.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            it.includeFontPadding = false
            it.setLineSpacing(px(3).toFloat(), 1f)
        }

    fun actionButton(value: String, filled: Boolean, destructive: Boolean): TextView {
        val fill = if (destructive) colors.danger else colors.accent
        return paragraph(value, 14, if (filled) colors.surface else colors.accent, true).also {
            it.gravity = Gravity.CENTER
            it.minHeight = px(52)
            it.setPadding(px(14), px(14), px(14), px(14))
            it.background = RippleDrawable(ColorStateList.valueOf(0x22336644),
                rounded(if (filled) fill else colors.surface, if (filled) fill else colors.border, 14), null)
            it.isFocusable = true
            it.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                }
            }
        }
    }
    fun vertical(): LinearLayout = LinearLayout(context).also { it.orientation = LinearLayout.VERTICAL }
    fun surface(): LinearLayout = vertical().also { it.background = rounded(colors.surface, colors.border, 18) }
    fun gap(parent: LinearLayout, height: Int) { parent.addView(View(context), LinearLayout.LayoutParams(1, px(height))) }
    fun rounded(fill: Int, border: Int, radius: Int): GradientDrawable = GradientDrawable().also {
        it.setColor(fill)
        it.cornerRadius = px(radius).toFloat()
        it.setStroke(px(1), border)
    }
    protected fun px(value: Float): Int = Math.round(value * context.resources.displayMetrics.density)
    protected fun px(value: Int): Int = px(value.toFloat())
}

