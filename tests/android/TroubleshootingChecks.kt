package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*
import java.io.File

internal object TroubleshootingChecks {
    private fun texts(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
    }
    fun run(test: Instrumentation, activity: MainActivity, check: (Boolean, String) -> Unit) {
        fun main(block: () -> Unit) { var error: Throwable? = null; test.runOnMainSync { try { block() } catch(t: Throwable) { error = t } }; error?.let { throw it } }
        val secret = "SYNTHETIC_PRIVATE_9274"
        val first = Device().apply {
            ip = "192.0.2.31"; name = secret; hostname = "$secret.local"; seen = true
            hint("Model", "Google Home Mini", "Synthetic mDNS", 80)
            link("synthetic:shared-speaker")
            repeat(20) { detail("Extra fixture field $it", "secret-$it") }
            diagnostics.txt("mDNS _fixture._tcp.", mapOf("features" to "123".toByteArray(), "password" to "PRIVATE_PASSWORD".toByteArray()))
            metadataLookups.queue(LocalMetadata.Request(LocalMetadata.Kind.CAST, 8008, "/setup/eureka_info"))
            metadataLookups.failure(LocalMetadata.Kind.CAST, MetadataHttp.Failure(MetadataHttp.Reason.READ_TIMEOUT))
            metadataLookups.beginCandidate(LocalMetadata.Request(LocalMetadata.Kind.CAST, 8008, "/setup/eureka_info"))
            metadataLookups.success(LocalMetadata.Kind.CAST, LocalMetadata.Report())
        }
        val second = Device().apply { ip = "2001:db8::31"; seen = true; link("synthetic:shared-speaker") }
        val unnamed = Device().apply { ip = "192.0.2.32"; seen = true; evidence("ICMP echo response") }
        val phone = Device().apply { ip = "192.0.2.33"; seen = true; self = true }
        val groups = Device.grouped(listOf(first, second, unnamed, phone))
        val document = TroubleshootingReport.create("Synthetic aggregate", groups.map { it.troubleshootingGroup() }, emptyList())
        val report = document.text()
        check(report.contains("all 3 device groups / 4 address endpoints"), "Whole report includes grouped addresses, unnamed endpoint and phone")
        check(report.contains("Manufacturer: No retained value"), "Missing retained data is not claimed to have been absent from the response")
        check(report.contains("Device 3 / Address 1") && report.contains("Google Home Mini"), "Identified and unnamed devices retained")
        check(report.contains("Connection") || report.contains("Response read timed out"), "Earlier failed lookup retained")
        check(!report.contains(secret) && !report.contains("PRIVATE_PASSWORD") && !report.contains("2001:db8::31"), "Private names, IPv6 and raw password excluded")
        check(first.details.size == 16 && first.copy().diagnostics.snapshot().count { it.field.startsWith("Extra fixture") } == 20, "UI detail eviction does not erase diagnostic observations")
        val apple = Device().apply { ip="192.0.2.40"; link("synthetic:apple"); recognizeAppleModel("iPhone14,5"); hint("Model", "iPhone14,5", AppleDeviceInfo.MODEL_SOURCE, 80) }
        val contradiction = Device().apply { ip="192.0.2.41"; link("synthetic:apple"); hint("Manufacturer", "Samsung", "Synthetic report", 80) }
        check(Device.grouped(listOf(apple, contradiction)).single().recognitionReportGroup().conflict, "Grouped Apple maker contradiction counted")
        for ((vendor, product, maker, model) in listOf(listOf("4882","1","Yeelight","Yeelight Smart Lamp"), listOf("5621","35204","Bouygues Telecom","BboxTV-4B"))) {
            val d = Device().apply {
                hint("Manufacturer", maker, "Synthetic", 80); hint("Model", model, "Synthetic", 80)
                recognizeMatter(MatterInfo.Report(vendor.toInt(), product.toInt(), null, ""), "Synthetic")
            }
            check(d.groupProduct() != null && !d.recognitionReportGroup().conflict, "Compatible Matter registry aliases do not create diagnostic conflicts")
        }
        for (stopped in listOf(false, true)) {
            val audio = Device().apply {
                ip = "192.0.2.50"; link("synthetic:audio")
                audioPlayerStatuses["HEOS:1255"] = "HEOS player information · port 1255: Waiting or checking"
                finishAudioLookups(stopped)
            }
            val partner = Device().apply { ip = "192.0.2.51"; link("synthetic:audio") }
            val outcome = if (stopped) "Stopped before lookup finished" else "Lookup did not finish"
            for (snapshot in listOf(audio, audio.copy(), Device.grouped(listOf(audio, partner)).single())) {
                val text = TroubleshootingReport.create("", listOf(snapshot.troubleshootingGroup()), emptyList()).text()
                check(text.contains(outcome), "Final audio status survives single/copy/grouped report")
                check(snapshot.endpoints().any { it.diagnostics.snapshot().any { entry -> entry.value.contains(outcome) } }, "Audio terminal transition retained in history")
            }
        }
        for (filled in listOf(DiagnosticEvidence.LIMIT - 1, DiagnosticEvidence.LIMIT)) {
            val saturated = Device().apply {
                ip = "192.0.2.52"; seen = true; link("synthetic:saturated-audio")
                repeat(filled) { diagnostics.record("Synthetic response", "field$it", "value$it") }
                audioStatus = "Sonos zone information · port 1400: Lookup did not finish"
            }
            val entries = saturated.diagnostics.snapshot()
            val omitted = saturated.diagnostics.omissions()
            check(omitted == if (filled == DiagnosticEvidence.LIMIT) 1L else 0L, "Audio fixture covers both retained and capacity-omitted status")
            val peer = Device().apply { ip = "192.0.2.53"; seen = true; link("synthetic:saturated-audio") }
            val snapshots = listOf(saturated.copy(), saturated.copy().copy().copy(),
                Device.grouped(listOf(saturated)).single(), Device.grouped(listOf(saturated, peer)).single())
            for (snapshot in snapshots) {
                val endpoint = snapshot.endpoints().single { it.ip == saturated.ip }
                check(endpoint.diagnostics.snapshot() == entries && endpoint.diagnostics.omissions() == omitted,
                    "Copies and grouped endpoints preserve journal entries and omission count exactly")
                val text = TroubleshootingReport.create("", listOf(snapshot.troubleshootingGroup()), emptyList()).text()
                check(text.contains("Diagnostic record events omitted by capacity limit: $omitted"), "Export preserves actual scan omission count")
                check(text.contains(saturated.audioStatus), "Final audio status survives even when absent from the full journal")
            }
            val detached = saturated.copy()
            detached.audioStatus = "Sonos zone information · port 1400: Stopped before lookup finished"
            check(detached.diagnostics.omissions() == omitted + 1, "A genuinely new transition still counts capacity loss on the copy")
            check(saturated.diagnostics.snapshot() == entries && saturated.diagnostics.omissions() == omitted,
                "Updating a snapshot does not change the original journal")
        }
        val reported = SamsungInfo.parse("""{"device":{"type":"Samsung SmartTV","name":"Private Room","modelName":"Private Room television"}}""".toByteArray())!!
        for ((index, source) in listOf(LocalMetadata.Kind.SAMSUNG.source + " (reported)",
            LanIdentity.Method.GOVEE.source + " (reported)", OmadaDiscovery.MODEL_SOURCE).withIndex()) {
            val generated = Device().apply {
                ip = "192.0.2.${70 + index}"; seen = true; link("synthetic:generated-title-$index")
                if (index == 0) reported.hints.forEach { (field, value) -> hint(field, value, source, 80) }
                else {
                    hint("Name", "Private Room", "Synthetic current service", 80)
                    hint("Model", "Private Room television", source, 80)
                }
            }
            val alias = Device().apply { ip = "2001:db8::${70 + index}"; seen = true; link("synthetic:generated-title-$index") }
            for (snapshot in listOf(generated, generated.copy(), Device.grouped(listOf(generated, alias)).single())) {
                check(snapshot.groupProduct()?.product?.titleSource == DeviceCatalog.TitleSource.DEVICE,
                    "Protocol model remains a recognized product with device-supplied title provenance")
                val text = TroubleshootingReport.create("", listOf(snapshot.troubleshootingGroup()), emptyList()).text()
                val identification = text.lineSequence().single { it.startsWith("Identification:") }
                val endpointTitle = text.lineSequence().single { it.startsWith("Endpoint product match:") }
                check(identification.contains("[private value") && !identification.contains("Private Room"),
                    "Group identification masks names embedded in peer-generated titles")
                check(endpointTitle.contains("[private value") && !endpointTitle.contains("Private Room"),
                    "Endpoint product title masks names embedded in peer-generated titles")
                check(!text.contains("Private Room"), "Default report has no alternate generated-title route around name masking")
                val named = TroubleshootingReport.create("", listOf(snapshot.troubleshootingGroup()), emptyList(), true).text()
                check(named.contains("Private Room television"), "Explicit names mode still includes requested device text")
            }
        }
        val factory = Device().apply {
            ip = "192.0.2.60"; seen = true
            hint("Name", "Google Home Mini", "Synthetic mDNS", 80)
            hint("Model", "Google Home Mini", "Synthetic mDNS", 80)
            hint("Firmware", "1.2.3.4", "Synthetic mDNS", 80)
            diagnostics.txt("Synthetic mDNS", mapOf("md" to "Google Home Mini".toByteArray(), "fv" to "1.2.3.4".toByteArray()))
        }
        for (snapshot in listOf(factory, factory.copy(), Device.grouped(listOf(factory)).single())) {
            val text = TroubleshootingReport.create("", listOf(snapshot.troubleshootingGroup()), emptyList()).text()
            check(text.contains("Identification: Google Home Mini"), "Canonical product title survives factory-name collision")
            check(text.contains("Endpoint product match: Google Home Mini"), "Bundled endpoint title retains the factory-name exemption")
            check(text.contains("Model: Google Home Mini") && text.contains("TXT md: Google Home Mini"), "Reported and diagnostic models survive factory-name collision")
            check(text.contains("Firmware: 1.2.3.4") && text.contains("TXT fv: 1.2.3.4"), "Dotted firmware survives report generation")
            check(!text.contains("192.0.2.60") && text.contains("Network name: [private value"), "Addresses and names remain masked in the same report")
        }
        val shortNames = Device().apply {
            ip = "192.0.2.61"; seen = true
            for (n in listOf("a", "e", "i", "l", "p", "r", "t", "v")) hint("Name", n, "Synthetic name $n", 80)
            hint("Model", "Google Home Mini", "Synthetic mDNS", 80)
        }
        val shortReport = TroubleshootingReport.create("", listOf(shortNames.troubleshootingGroup()), emptyList()).text()
        check(shortReport.length < 12000, "Eight short retained names cannot amplify one endpoint into a huge report")
        check(shortReport.contains("Manufacturer:") && shortReport.contains("Model: Google Home Mini"), "Short names preserve field labels and product words")
        check(!shortReport.contains("[priv["), "Privacy placeholders are not recursively rewritten")
        val resolver = activity.contentResolver
        val uri = ReportShareProvider.publish(activity.packageName, document)
        val received = resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        check(received == report, "Share provider streams every page byte-for-byte without a file cache")
        val cursor = resolver.query(uri, null, null, null, null)!!
        cursor.use { it.moveToFirst(); check(it.getLong(it.getColumnIndex("_size")) == report.toByteArray(Charsets.UTF_8).size.toLong(), "Shared size matches full UTF-8 content") }
        var blocked = false
        try { resolver.openFileDescriptor(uri, "w")?.close() } catch (_: Exception) { blocked = true }
        check(blocked, "Share URI rejects writes")
        blocked = false
        try { resolver.openInputStream(uri.buildUpon().appendPath("other").build())?.close() } catch (_: Exception) { blocked = true }
        check(blocked, "Share URI rejects other paths")
        ReportShareProvider.clear()
        blocked = false
        try { resolver.openInputStream(uri)?.close() } catch (_: Exception) { blocked = true }
        check(blocked, "Retired report cannot be reopened")
        val stalledUri = ReportShareProvider.publish(activity.packageName, TroubleshootingReport.Document(List(64) { "X".repeat(12000) }))
        val stalledReader = resolver.openInputStream(stalledUri)!!
        var drained = false
        try {
            SystemClock.sleep(100)
            ReportShareProvider.clear()
            val limit = SystemClock.elapsedRealtime() + 1500
            while (!drained && SystemClock.elapsedRealtime() < limit) {
                drained = Thread.getAllStackTraces().keys.none { it.name == "report-share" && it.isAlive }
                if (!drained) SystemClock.sleep(25)
            }
        } finally { stalledReader.close() }
        check(drained, "Revoking a stalled recipient drains the writer thread without waiting for its reader")
        val original = Configuration(activity.resources.configuration)
        for (scale in listOf(1f, 2f)) for (dark in listOf(false, true)) {
            lateinit var dialog: TroubleshootingDialog
            main {
                activity.resources.updateConfiguration(Configuration(original).apply { fontScale = scale }, activity.resources.displayMetrics)
                Store(activity).dark(dark)
                val method = MainActivity::class.java.getDeclaredMethod("dialogColors").apply { isAccessible = true }
                // Render refreshes the palette before creating the native preview.
                MainActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
                dialog = TroubleshootingDialog(activity, method.invoke(activity) as AppDialog.Colors, document, {}, { text ->
                    (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Fixture report", text))
                })
                dialog.show()
            }
            test.waitForIdleSync(); SystemClock.sleep(200)
            main {
                val share = texts(dialog.window!!.decorView).single { it.text.toString() == "Share full report" }
                val rect = Rect()
                check(share.getGlobalVisibleRect(rect) && rect.height() >= (48 * activity.resources.displayMetrics.density).toInt(), "Share remains visible and accessible in theme/text-size matrix")
                texts(dialog.window!!.decorView).single { it.text.toString() == "Next" }.performClick()
                check(texts(dialog.window!!.decorView).any { it.text.toString().contains("Device 1 / Address 1") }, "Preview advances to actual device evidence")
                texts(dialog.window!!.decorView).single { it.text.toString() == "Copy this page" }.performClick()
                val clip = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
                check(clip.contains("page 2/") && clip.contains("Google Home Mini"), "Clipboard page labelled and includes diagnostic evidence")
            }
            test.waitForIdleSync()
            val image = test.uiAutomation.takeScreenshot()
            val folder = File(activity.getExternalFilesDir(null), "recognition-report-api${Build.VERSION.SDK_INT}").apply { mkdirs() }
            File(folder, "troubleshooting-${scale}-${dark}.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            main { dialog.dismiss() }
        }
        main { activity.resources.updateConfiguration(original, activity.resources.displayMetrics) }
    }
}
