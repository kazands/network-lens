package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import io.github.kazands.networklens.*
import java.io.File

/** Real TextView layout and clipboard checks, using documentation addresses only. */
internal object TypographyChecks {
    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner) }
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    private fun invoke(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).let { it.isAccessible = true; it.invoke(owner) }
    private fun main(test: Instrumentation, action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
        failure?.let { throw it }
    }
    private fun texts(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
    }
    private fun settle(test: Instrumentation) { test.waitForIdleSync(); SystemClock.sleep(120); test.waitForIdleSync() }
    private fun capture(activity: MainActivity, name: String) {
        val view = activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        val directory = File(activity.getExternalFilesDir(null), "typography-api${Build.VERSION.SDK_INT}").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val property = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        check(property.inputStream.bufferedReader().use { it.readText().trim() } == "1", "Typography checks reject physical devices")
        Store(test.targetContext).clear()
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo", true)) as MainActivity
        settle(test)
        val original = Configuration(activity.resources.configuration)
        val ipv4 = "192.0.2.5"
        val variants = listOf("2001:db8:1234:5678:90ab:cdef:1234:5678",
            "fe80:0000:0000:0000:1234:5678:9abc:def0%wlan_fixture0", "2001:db8::192.0.2.123")
        fun fixture(v6: String) = listOf(
            Device().apply { ip = ipv4; name = "Synthetic dual-stack device"; model = "Fixture model"; link("typography:dual") },
            Device().apply { ip = v6; link("typography:dual") })
        fun inspect(view: TextView, expected: String, context: String) {
            val layout = view.layout
            check(view.text.toString() == expected, "$context preserves the exact address and scope")
            check(layout != null && layout.lineCount > 0, "$context has a measured native layout")
            if (layout == null) return
            check(layout.getLineEnd(layout.lineCount - 1) == expected.length, "$context lays out the final address character")
            check((0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 }, "$context never ellipsizes an address")
            val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
            check((0 until layout.lineCount).all { layout.getLineWidth(it) <= width + 1 }, "$context fits every line inside its available width")
            check(view.height - view.compoundPaddingTop - view.compoundPaddingBottom >= layout.height,
                "$context keeps all measured lines inside the text view")
            check(view.textDirection == View.TEXT_DIRECTION_LTR, "$context keeps numeric addresses left to right")
            check(view.autoSizeTextType == TextView.AUTO_SIZE_TEXT_TYPE_NONE, "$context never shrinks the user's chosen text size")
            val addressStart = if (expected.startsWith("IPv6  ")) 6 else 0
            val scope = expected.indexOf('%').let { if (it < 0) expected.length else it }
            val dot = expected.indexOf('.')
            val groupEnd = if (dot >= 0) expected.lastIndexOf(':', dot) + 1 else scope
            val hex = "0123456789abcdefABCDEF"
            check((0 until layout.lineCount - 1).all {
                val end = layout.getLineEnd(it)
                end <= addressStart || end >= groupEnd || expected[end - 1] !in hex || expected[end] !in hex
            }, "$context wraps between IPv6 groups without splitting hex digits")
        }
        try {
            for (scale in listOf(1f, 2f)) for (width in listOf(411, 320)) for (dark in listOf(false, true)) {
                for ((variant, v6) in variants.withIndex()) {
                    val kind = when (variant) { 0 -> "global"; 1 -> "scoped"; else -> "mixed" }
                    val label = "${width}dp-${scale.toInt()}x-${if (dark) "dark" else "light"}-$kind"
                    main(test) {
                        val config = Configuration(original).apply { fontScale = scale; screenWidthDp = width }
                        @Suppress("DEPRECATION")
                        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
                        val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width.toFloat(), activity.resources.displayMetrics).toInt()
                        activity.window.setLayout(pixels, WindowManager.LayoutParams.MATCH_PARENT)
                        Store(activity).dark(dark)
                        set(activity, "selected", null); set(activity, "devices", fixture(v6)); set(activity, "groupedSource", null)
                        set(activity, "query", ""); invoke(activity, "render")
                    }
                    settle(test)
                    main(test) {
                        val all = texts(activity.window.decorView)
                        val v4view = all.single { it.text.toString() == "IPv4  $ipv4" }
                        val v6view = all.single { it.text.toString() == "IPv6  $v6" }
                        inspect(v6view, "IPv6  $v6", "List $label")
                        val a = IntArray(2); val b = IntArray(2)
                        v4view.getLocationInWindow(a); v6view.getLocationInWindow(b)
                        check(b[1] >= a[1] + v4view.height, "List $label keeps IPv6 below IPv4 without overlap")
                        if (width == 320 && scale == 2f && variant == 1)
                            check(v6view.lineCount > 3, "Narrow large-text scoped IPv6 exercises the former three-line cutoff")
                        val search = all.filterIsInstance<EditText>().single()
                        check(search.hint.toString() == "Search devices", "Search has a concise accessible hint")
                        val rows = get(activity, "deviceRows") as Map<*, *>
                        check(rows[ipv4] === rows[v6], "A dual-stack fixture shares one device row")
                        val scroll = get(activity, "deviceScroll") as ScrollView
                        val addressPosition = IntArray(2); val scrollPosition = IntArray(2)
                        v6view.getLocationInWindow(addressPosition); scroll.getLocationInWindow(scrollPosition)
                        scroll.scrollTo(0, scroll.scrollY + addressPosition[1] - scrollPosition[1])
                    }
                    settle(test)
                    main(test) {
                        if (variant == 1 && (width == 411 && scale == 1f || width == 320 && scale == 2f)) capture(activity, "list-$label")
                        val rows = get(activity, "deviceRows") as Map<*, *>
                        (rows[ipv4] as View).performClick()
                    }
                    settle(test)
                    main(test) {
                        val all = texts(activity.window.decorView)
                        inspect(all.single { it.text.toString() == v6 }, v6, "Details $label")
                        check(all.single { it.text.toString() == v6 }.isTextSelectable, "Details $label allow selecting the original address")
                        val missing = all.filter { it.text.toString() == "Not available" }
                        check(missing.isNotEmpty() && missing.all { it.typeface == AppTypography.face(14, false) }, "Unknown MAC values use interface text")
                        if (variant == 1 && width == 320 && scale == 2f) {
                            val address = all.single { it.text.toString() == v6 }
                            var panel: View = address
                            val scroll = get(activity, "detailScroll") as ScrollView
                            while (panel.parent !== scroll.getChildAt(0)) panel = panel.parent as View
                            scroll.scrollTo(0, panel.top)
                        }
                    }
                    settle(test)
                    main(test) {
                        if (variant == 1 && (width == 411 && scale == 1f || width == 320 && scale == 2f)) capture(activity, "details-$label")
                        texts(activity.window.decorView).single { it.text.toString() == "Copy device details" }.performClick()
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
                        check(copied.contains("IP: $ipv4\n") && copied.contains("IP: $v6\n"), "Copy $label retains every full address including its scope")
                        check(copied.none { it in "\u200b\u00ad\u2060" }, "Copy $label contains no inserted wrapping characters")
                    }
                }
            }
            main(test) {
                val v6 = Device().apply { ip = "2001:db8::beef"; name = "Synthetic IPv6-only device" }
                set(activity, "selected", null); set(activity, "devices", listOf(v6)); set(activity, "query", "::beef"); invoke(activity, "render")
            }
            settle(test)
            main(test) {
                val all = texts(activity.window.decorView)
                check(all.none { it.text.toString().startsWith("IPv4  ") }, "IPv6-only search results never invent an IPv4 address")
                inspect(all.single { it.text.toString() == "IPv6  2001:db8::beef" }, "IPv6  2001:db8::beef", "Compressed IPv6 search")
            }
        } finally {
            main(test) {
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(original, activity.resources.displayMetrics)
                activity.finish()
            }
            Store(test.targetContext).clear()
        }
    }
}
