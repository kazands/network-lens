package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import io.github.kazands.networklens.*
import java.io.File

/** Repeated connection notifications must not detach the page under a scrolling reader. */
internal object DetailStabilityChecks {
    private fun field(a: Any, name: String) = a.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun texts(v: View): List<TextView> = buildList {
        if (v is TextView) add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(texts(v.getChildAt(i)))
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val qemu = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        check(qemu.inputStream.bufferedReader().use { it.readText().trim() } == "1", "Details fixtures reject physical devices")
        val store = Store(test.targetContext); store.clear()
        DeviceCatalogLoader.load(test.targetContext)
        val live = NetworkState.current(test.targetContext)
        val fixture = Device().apply {
            ip = live.gateway; name = "Fixture media device"; seen = true
            service("_androidtvremote2._tcp.")
            for (i in 1..24) detail("Fixture observation $i", "Synthetic device information for scrolling verification $i")
        }
        store.retainIfCurrent(store.token(), live.key, listOf(fixture), true)
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val original = Configuration(activity.resources.configuration)
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
            failure?.let { throw it }
        }
        fun call(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
        fun settle() { test.waitForIdleSync(); SystemClock.sleep(250); test.waitForIdleSync() }
        fun page() = field(activity, "detailScroll").get(activity) as ScrollView
        try {
            settle()
            for (scale in listOf(1f, 2f)) for (dark in listOf(false, true)) {
                main {
                    activity.resources.updateConfiguration(Configuration(original).apply { fontScale = scale }, activity.resources.displayMetrics)
                    store.dark(dark); field(activity, "selected").set(activity, fixture.copy())
                    call("render")
                    texts(activity.window.decorView).single { it.text.toString() == "Show technical details" }.performClick()
                }
                settle()
                lateinit var before: ScrollView
                main { before = page(); before.scrollTo(0, 480) }
                settle()
                var y = 0
                main { y = before.scrollY; check(y > 100, "Details fixture is genuinely scrolled") }
                repeat(12) {
                    main {
                        call("refreshConnection")
                        check(page() === before, "Unchanged Wi-Fi notification keeps the same details view while scrolling")
                        check(before.scrollY == y, "Unchanged Wi-Fi notification keeps the scroll position")
                    }
                }
                var firstDrawY = -1
                main {
                    check(texts(before).any { it.text.toString() == "Hide technical details" }, "Expanded evidence survives connection notifications")
                    // A changed visible connection field still takes the normal refresh path.
                    (field(activity, "network").get(activity) as NetworkState).dns = "192.0.2.53"
                    call("refreshConnection")
                    check(page() !== before, "Changed connection information is still rendered")
                    val refreshed = page()
                    refreshed.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            refreshed.viewTreeObserver.removeOnPreDrawListener(this)
                            firstDrawY = refreshed.scrollY
                            return true
                        }
                    })
                    refreshed.invalidate()
                }
                settle()
                main {
                    check(firstDrawY == y, "Necessary redraw shows the restored position on its first frame")
                    check(page().scrollY == y, "Necessary redraw restores the details position")
                    check(texts(page()).any { it.text.toString() == "Hide technical details" }, "Necessary redraw preserves expanded evidence")
                    texts(page()).single { it.text.toString() == "Hide technical details" }.performClick()
                }
                settle()
                val folder = File(activity.getExternalFilesDir(null), "detail-stability-api${Build.VERSION.SDK_INT}").apply { mkdirs() }
                val image = test.uiAutomation.takeScreenshot()
                File(folder, "details-${scale}-${dark}.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            }
            main {
                (field(activity, "network").get(activity) as NetworkState).key = "synthetic-retired-session"
                call("refreshConnection")
                check(field(activity, "selected").get(activity) == null, "A replaced network still exits the old device details")
                check(store.devices(live.key).isEmpty(), "A replaced network still clears old scan results")
                check(texts(activity.window.decorView).any { it.text.toString() == "Your devices" }, "A replaced network returns to the devices page")
            }
        } finally {
            main { activity.resources.updateConfiguration(original, activity.resources.displayMetrics); activity.finish() }
            store.clear()
        }
    }
}
