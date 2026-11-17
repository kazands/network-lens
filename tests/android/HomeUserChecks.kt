package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*
import java.io.File

/** Synthetic identities only; verifies the home-user summary and native action semantics. */
internal object HomeUserChecks {
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    private fun texts(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) for (n in 0 until view.childCount) addAll(texts(view.getChildAt(n)))
    }
    private fun main(test: Instrumentation, action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        check(process.inputStream.bufferedReader().use { it.readText().trim() } == "1", "Home-user fixtures reject physical devices")
        Store(test.targetContext).clear()
        DeviceCatalogLoader.load(test.targetContext)
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val original = Configuration(activity.resources.configuration)
        val registry = MatterInfo.index
        val render = MainActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }
        fun settle() { test.waitForIdleSync(); SystemClock.sleep(200); test.waitForIdleSync() }
        fun capture(name: String) {
            val folder = File(activity.getExternalFilesDir(null), "home-user-api${Build.VERSION.SDK_INT}").apply { mkdirs() }
            val image = test.uiAutomation.takeScreenshot()
            File(folder, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        fun role(view: View, name: String) {
            val node = view.createAccessibilityNodeInfo()
            try { check(node.className == "android.widget.Button" && node.isClickable && node.isFocusable, "$name exposes a focusable native button action") }
            finally { node.recycle() }
        }
        val unknown = Device().apply { ip = "192.0.2.40"; seen = true }
        val thermostat = Device().apply {
            ip = "192.0.2.41"; seen = true; name = "Fixture hallway"
            hint("Manufacturer", "ecobee", "Synthetic report", 80)
            hint("Model", "ECB601", "Synthetic report", 80)
            hint("Device type", "AirPlay device", "Synthetic service", 50)
            service("_airplay._tcp.")
        }
        MatterInfo.index = MatterInfo.Index.read("DCL1\t20261117\n123\t456\tFixture Maker\tFixture Thermostat\tFT-1\t769\n".byteInputStream())
        val matter = Device().apply { ip = "192.0.2.42"; seen = true; recognizeMatter(MatterInfo.Report(123,456,769,""), "Synthetic Matter TXT") }
        try {
            for ((width, scale) in listOf(411 to 1f, 320 to 2f)) for (dark in listOf(false, true)) {
                val label = "$width-${scale}x-${if (dark) "dark" else "light"}"
                main(test) {
                    val config = Configuration(original).apply { screenWidthDp = width; fontScale = scale }
                    activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
                    activity.window.setLayout((width * activity.resources.displayMetrics.density).toInt(), -1)
                    Store(activity).dark(dark); set(activity,"demo",true); set(activity,"selected",null)
                    set(activity,"devices",listOf(unknown)); set(activity,"groupedSource",null); set(activity,"query","")
                    render.invoke(activity)
                    val all = texts(activity.window.decorView)
                    check(all.any { it.text.toString() == "Unnamed device" }, "Unknown list title is friendly and does not invent identity")
                    role(all.single { it.text.toString() == "Scan Wi-Fi" }, "Scan Wi-Fi")
                    fun actions(view: View) {
                        if (view.contentDescription?.toString() in listOf("Options", "Switch to light mode", "Switch to dark mode")) role(view,"Toolbar action")
                        if (view is ViewGroup) for (n in 0 until view.childCount) actions(view.getChildAt(n))
                    }
                    actions(activity.window.decorView)
                    set(activity,"selected",unknown); render.invoke(activity)
                    val details = texts(activity.window.decorView)
                    check(details.any { it.text.toString() == RecognitionCopy.UNNAMED } && details.any { it.text.toString() == RecognitionCopy.NEXT_SCAN }, "Unknown details explain the evidence and a bounded next step")
                    role(details.single { it.text.toString() == "Show technical details" }, "Technical disclosure")
                    role(details.single { it.text.toString() == "Copy device details" }, "Copy details")
                    role(details.single { it.contentDescription?.toString() == "Back to devices" }, "Back")
                }
                settle(); capture("unnamed-$label")
                main(test) {
                    set(activity,"selected",thermostat); render.invoke(activity)
                    val all = texts(activity.window.decorView)
                    check(all.any { it.text.toString() == "Thermostat" } && all.none { it.text.toString() == "AirPlay device" || it.text.toString() == "ECB601" }, "Physical identity leads while raw service/model reports start collapsed")
                    check(all.count { it.text.toString() == "ecobee Smart Thermostat Premium" } == 1, "Product title is not repeated in recognition panels")
                    check(all.any { it.text.toString() == "Fixture hallway" }, "Useful custom name remains visible without expanding technical details")
                    all.single { it.text.toString() == "Show technical details" }.performClick()
                    val expanded = texts(activity.window.decorView)
                    check(expanded.any { it.text.toString() == "ECB601" } && expanded.any { it.text.toString() == "Service-reported type" }, "Technical disclosure preserves reported model and service type")
                    expanded.single { it.text.toString() == "Hide technical details" }.performClick()
                    check(texts(activity.window.decorView).none { it.text.toString() == "ECB601" }, "Collapsing removes raw fields from the accessibility tree")
                }
                settle(); capture("thermostat-$label")
                main(test) {
                    set(activity,"selected",matter); render.invoke(activity)
                    check(texts(activity.window.decorView).any { it.text.toString().contains("vendor and product IDs") }, "Matter summary explains the actual recognition basis")
                    check(matter.model.isEmpty() && matter.manufacturer.isEmpty(), "Friendly copy never manufactures reported identity")
                }
            }
        } finally {
            MatterInfo.index = registry
            main(test) { activity.resources.updateConfiguration(original, activity.resources.displayMetrics); activity.finish() }
            Store(test.targetContext).clear()
        }
    }
}
