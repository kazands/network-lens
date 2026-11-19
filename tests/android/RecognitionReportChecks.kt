package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*
import java.io.File

internal object RecognitionReportChecks {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun texts(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val qemu = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        check(qemu.inputStream.bufferedReader().use { it.readText().trim() } == "1", "Report fixtures reject physical devices")
        val store = Store(test.targetContext); store.clear()
        DeviceCatalogLoader.load(test.targetContext)
        val network = NetworkState.current(test.targetContext)
        val prefix = network.ip.substringBeforeLast('.')
        val secret = "SYNTHETIC_PRIVATE_MARKER_5729"
        fun device(octet: Int) = Device().apply {
            ip = "$prefix.$octet"; name = secret; hostname = "$secret.local"; seen = true
            detail("Synthetic secret", secret); evidence(secret)
        }
        val product = device(100).apply {
            link("synthetic:report-thermostat")
            hint("Manufacturer", "ecobee", "Synthetic current response", 80)
            hint("Model", "ECB601", "Synthetic current response", 80)
            hardware("02:00:00:00:00:42", "Synthetic current response")
            metadataLookups.queue(LocalMetadata.Request(LocalMetadata.Kind.SPOTIFY, 12345, "/"))
            metadataLookups.failure(LocalMetadata.Kind.SPOTIFY, MetadataHttp.Failure(MetadataHttp.Reason.READ_FAILED))
        }
        val alias = device(101).apply {
            link("synthetic:report-thermostat")
            hardware("02:00:00:00:00:42", "Synthetic current response")
        }
        val family = device(102).apply { recognizeSmartThings("Synthetic current response") }
        val service = device(103).apply { service("_synthetic._tcp.") }
        val unmapped = device(104).apply { hint("Model", secret, "Synthetic current response", 80) }
        val conflicting = device(105).apply {
            hint("Model", "Google Home", "Synthetic response one", 80)
            hint("Model", "Google Home Mini", "Synthetic response two", 80)
        }
        val self = device(106).apply { self = true; ip = network.ip }
        val fixtures = listOf(product, alias, family, service, unmapped, conflicting, self)
        val sources = mapOf("mDNS _$secret._tcp." to "Failed · $secret · read timeouts 2", EapInfo.SOURCE to "OK")
        fun retain(stopped: Boolean = false) {
            store.retainIfCurrent(store.token(), network.key, fixtures,
                ScanReport("$secret 192.0.2.0/24", false, if (stopped) "Scan stopped. $secret" else secret,
                    254, 254, System.currentTimeMillis(), sources))
        }
        retain()
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val original = Configuration(activity.resources.configuration)
        fun main(block: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
            failure?.let { throw it }
        }
        fun call(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
        fun dialog() = field(activity, "scanDetailsDialog").get(activity) as ScanDetailsDialog
        fun button() = texts(dialog().window!!.decorView).single { it.text.toString() == "Copy recognition report" }
        fun settle() { test.waitForIdleSync(); SystemClock.sleep(250); test.waitForIdleSync() }
        fun clip() = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        try {
            settle()
            main {
                val grouped = Device.grouped(fixtures)
                check(grouped.size == 6, "Dual-address fixture is one device group")
                check(grouped.single { it.endpoints().size == 2 }.recognitionReportGroup().level == RecognitionReport.Level.PRODUCT, "Grouped product remains identified")
                check(family.recognitionReportGroup().level == RecognitionReport.Level.FAMILY, "Hardware family is not an exact product")
                check(service.recognitionReportGroup().level == RecognitionReport.Level.SERVICES, "Uninterpreted service stays service-only")
                check(unmapped.recognitionReportGroup().modelWithoutMatch, "Reported unmatched model is counted")
                check(conflicting.recognitionReportGroup().conflict, "Same-category model conflict is surfaced")
                conflictSnapshots(check)
                field(activity, "query").set(activity, "filter-that-matches-nothing")
            }
            for (scale in listOf(1f, 2f)) for (dark in listOf(false, true)) {
                main {
                    activity.resources.updateConfiguration(Configuration(original).apply { fontScale = scale }, activity.resources.displayMetrics)
                    store.dark(dark); call("render"); call("scanDetails")
                }
                settle()
                main { button().requestRectangleOnScreen(Rect(0, 0, button().width, button().height), true) }
                settle()
                main {
                    check(button().isEnabled, "Finished scan enables report in both themes and text sizes")
                    check(button().height >= (48 * activity.resources.displayMetrics.density).toInt(), "Copy target stays accessible")
                    val visible = Rect()
                    check(button().getGlobalVisibleRect(visible) && visible.height() >= (48 * activity.resources.displayMetrics.density).toInt(), "Copy action can be scrolled fully into view")
                    check(button().performClick(), "Copy action handled")
                }
                settle()
                main {
                    val report = clip()
                    check(report.startsWith("Network Lens recognition report"), "Clipboard contains the actual report")
                    check(report.contains("Device groups: 5 · address endpoints: 6"), "All groups included despite search filter; this phone excluded")
                    check(report.contains("Product matches: 1") && report.contains("Family or integration matches only: 1"), "Product and family totals are separate")
                    check(report.contains("Groups with recorded identity or type conflicts: 1"), "Actual clipboard includes the model conflict")
                    check(report.contains("Response read failed: 1"), "Typed lookup failure copied")
                    check(report.contains("read timeouts: 2"), "Method diagnostics preserved as safe counters")
                    check(!report.contains(secret) && !report.contains(prefix) && !report.contains("02:00:00:00:00:42"), "Names, addresses, identifiers and raw reasons never copied")
                    check(!report.contains("ECB601") && !report.contains("ecobee"), "Raw manufacturer/model fields never copied")
                }
                val folder = File(activity.getExternalFilesDir(null), "recognition-report-api${Build.VERSION.SDK_INT}").apply { mkdirs() }
                val image = test.uiAutomation.takeScreenshot()
                File(folder, "report-${scale}-${dark}.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
                main { dialog().dismiss() }
            }
            TroubleshootingChecks.run(test, activity, check)
            // Queue a preview behind controlled work, then retire its generation before completion.
            val executor = field(activity, "presentation").get(activity) as java.util.concurrent.ScheduledExecutorService
            val started = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            executor.execute { started.countDown(); release.await(10, java.util.concurrent.TimeUnit.SECONDS) }
            check(started.await(2, java.util.concurrent.TimeUnit.SECONDS), "Controlled presentation work started")
            try {
                main {
                    MainActivity::class.java.getDeclaredMethod("prepareTroubleshooting", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, false)
                    check(field(activity, "reportPreparing").getBoolean(activity), "Report preparation is pending")
                    call("resetSessionData")
                    check(!field(activity, "reportPreparing").getBoolean(activity), "Clearing session resets preparation ownership")
                    retain(); call("loadNetwork")
                    field(activity, "query").set(activity, "filter-that-matches-nothing")
                }
            } finally { release.countDown() }
            main {
                call("troubleshootingOptions")
                val options = field(activity, "troubleshootingDialog").get(activity) as AppDialog
                check(texts(options.window!!.decorView).filterIsInstance<android.widget.CheckBox>().single().isChecked == false, "Detailed report defaults to masked names")
                texts(options.window!!.decorView).single { it.text.toString() == "Preview report" }.performClick()
            }
            var prepared = false
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (!prepared && SystemClock.elapsedRealtime() < deadline) {
                settle()
                main { prepared = field(activity, "troubleshootingDialog").get(activity) is TroubleshootingDialog }
            }
            main {
                check(prepared, "Whole report prepares through the actual activity flow")
                val preview = field(activity, "troubleshootingDialog").get(activity) as TroubleshootingDialog
                check(texts(preview.window!!.decorView).any { it.text.toString().contains("all 6 device groups / 7 address endpoints") }, "Actual report ignores search filtering and includes the scanning phone")
                val before = clip(); store.clear()
                texts(preview.window!!.decorView).single { it.text.toString() == "Copy this page" }.performClick()
                check(clip() == before, "Retired detailed report cannot copy stale scan data")
                preview.dismiss()
            }
            main {
                retain(true); call("scanDetails")
                button().performClick()
                check(clip().contains("Scan: Stopped"), "Stopped scans remain honestly labelled")
                dialog().dismiss()
                field(activity, "scanning").setBoolean(activity, true); call("scanDetails")
                check(!button().isEnabled, "Running scan cannot copy a changing report")
                dialog().dismiss(); field(activity, "scanning").setBoolean(activity, false)
                field(activity, "demo").setBoolean(activity, true); call("scanDetails")
                check(!button().isEnabled, "Sample devices cannot produce a household report")
                dialog().dismiss(); field(activity, "demo").setBoolean(activity, false)
                val before = clip()
                store.clear(); call("scanDetails")
                check(!button().isEnabled, "No scan disables copying")
                call("copyRecognitionReport")
                check(clip() == before, "Direct stale action does not replace clipboard with an old scan")
                dialog().dismiss()
            }
        } finally {
            main { activity.resources.updateConfiguration(original, activity.resources.displayMetrics); activity.finish() }
            store.clear()
        }
    }

    private fun conflictSnapshots(check: (Boolean, String) -> Unit) {
        for (models in listOf(listOf("Google Home", "Google Home Mini"), listOf("Google Home Mini", "Google Home"),
            listOf("Google Home Mini", "Google Home Mini (1st gen)"), listOf("Google Home Mini (1st gen)", "Google Home Mini"))) {
            val conflict = "Google Home" in models
            fun peer(ip: String, model: String) = Device().apply {
                this.ip = ip; seen = true; link("synthetic:report-speaker")
                hint("Model", model, "Synthetic current response", 80)
            }
            val first = peer("192.0.2.20", models.first())
            val second = peer("192.0.2.21", models.last())
            val group = Device.grouped(listOf(first, second)).single()
            val single = first.copy().apply { hint("Model", models.last(), "Synthetic second response", 80) }
            for (snapshot in listOf(single, single.copy(), group, group.copy())) {
                val facts = snapshot.recognitionReportGroup()
                check(facts.conflict == conflict, "Model conflict and alias counts survive single/grouped snapshots")
                check((snapshot.groupProduct() == null) == conflict, "Report diagnostics preserve existing product matching")
            }
            check(!first.recognitionReportGroup().conflict, "Grouping does not contaminate the original endpoint")
        }
    }
}
