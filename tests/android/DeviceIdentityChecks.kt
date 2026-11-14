package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import java.net.InetAddress

/** Synthetic current-scan identities only; never opens a network socket. */
internal object DeviceIdentityChecks {
    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner) }
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    private fun main(test: Instrumentation, action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
        failure?.let { throw it }
    }
    private fun texts(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) for (n in 0 until view.childCount) addAll(texts(view.getChildAt(n)))
    }
    private fun settle(test: Instrumentation) { test.waitForIdleSync(); SystemClock.sleep(120); test.waitForIdleSync() }
    private fun capture(test: Instrumentation, activity: MainActivity, name: String) {
        val folder = File(activity.getExternalFilesDir(null), "identities-api${Build.VERSION.SDK_INT}").apply { mkdirs() }
        val image = test.uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun model(value: String, n: Int) = Device().apply {
        ip = "192.0.2.$n"; seen = true
        hint("Model", value, if (value == "PS5") "PlayStation discovery host-type" else "Synthetic current model response", 80)
    }
    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        check(process.inputStream.bufferedReader().use { it.readText().trim() } == "1", "Identity fixtures reject physical devices")
        DeviceCatalogLoader.load(test.targetContext)
        check(DeviceCatalog.extendedSize() == 256338, "The packaged global catalogue loads all pinned aliases offline")
        val broadCases = listOf(Triple("Samsung", "SM-S918B", "Samsung Galaxy S23 Ultra"),
            Triple("Google", "Pixel 8", "Google Pixel 8"), Triple("QNAP", "TS-469U", "QNAP TS-469U"),
            Triple("Sony", "PlayStation 3", "Sony PlayStation 3"))
        for ((maker, code, title) in broadCases) {
            val d = model(code, 80).apply { seen = false }
            check(d.identifiedProduct() == null, "$code without a manufacturer cannot acquire a broad catalogue identity")
            d.hint("Manufacturer", maker, "Synthetic current manufacturer response", 80)
            check(d.presentation().title == title && !d.seen && d.mac.isEmpty(), "Current $maker / $code uses its global canonical title without guessing a MAC")
            check(d.identifiedProduct()!!.source.contains("catalogue") && d.identifiedProduct()!!.source.contains("manufacturer response"), "$code retains catalogue and manufacturer provenance")
            d.hint("Model", "$maker $code", "Synthetic current make-and-model response", 80)
            check(d.identifiedProduct()?.product?.title == title, "$code corroborates its exact make-and-model spelling")
            val copy = d.copy(); d.hint("Manufacturer", "Fixture rival", "Synthetic conflicting maker", 80)
            check(d.identifiedProduct() == null && copy.identifiedProduct() != null, "$code conflicts suppress the broad match without changing previous snapshots")
        }
        val printerReports = listOf("Manufacturer" to "HP Inc.", "Model" to "LaserJet P4015", "Model" to "HP LaserJet P4015")
        for (order in listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))) {
            val printer = Device().apply { ip = "192.0.2.81" }
            for (n in order) printerReports[n].let { (field, value) -> printer.hint(field, value, "Synthetic current printer response $n", 80) }
            check(printer.presentation().title == "HP LaserJet P4015" && !printer.seen && printer.manufacturer == "HP Inc.", "Printer reports $order resolve without inventing presence or replacing reported manufacturer")
            val snapshot = printer.copy()
            printer.hint("Model", "Fixture conflicting printer", "Z conflicting printer response", 80)
            repeat(12) { printer.hint("Model", "LaserJet P4015", "A later printer response $it", 90) }
            check(printer.observations().filter { it.field == "Model" }.let { it.size == 8 && it.none { h -> h.value == "Fixture conflicting printer" } }, "Printer conflict $order reaches display-observation eviction")
            check(printer.identifiedProduct() == null && printer.copy().identifiedProduct() == null && snapshot.presentation().title == "HP LaserJet P4015", "Printer conflict $order survives eviction while earlier snapshots stay independent")
        }
        val recorder = model("DS-7608NI-I2/8P", 10).apply {
            hint("Name", "DS-7608NI-I2/8P", "Synthetic current name", 80)
            hint("Device type", "Network surveillance device", "Synthetic surveillance service", 80)
            hardware("02:00:00:00:00:10", "Synthetic adapter response")
        }
        val shield = model("SHIELD Android TV", 11).apply { hint("Name", "SHIELD", "Synthetic current name", 80) }
        check(recorder.presentation().title == "Hikvision DS-7608NI-I2/8P" && DeviceIcon.forGroup(recorder).label == "Network video recorder", "A recorder model overrides its broad surveillance icon")
        check(shield.presentation().title == "NVIDIA SHIELD TV" && shield.presentation().subtitle == "Streaming device", "SHIELD uses a family title without repeating its network name")
        check(shield.manufacturer.isEmpty() && shield.model == "SHIELD Android TV" && shield.json().getJSONObject("localRecognition").getString("recognizedBrand") == "NVIDIA", "Interpreted brand stays separate from unknown reported manufacturer")
        val nameOnly = Device().apply { ip = "192.0.2.12"; hint("Name", "PS5", "Synthetic name only", 80) }
        check(nameOnly.identifiedProduct() == null && DeviceIcon.forGroup(nameOnly).label == "Device type unknown", "A product-like name cannot establish console hardware")
        for (field in listOf("Model", "Manufacturer")) for (before in listOf(false, true)) {
            val d = if (before) Device() else shield.copy()
            val bad = if (field == "Model") "Fixture other model" else "Fixture Devices"
            val good = if (field == "Model") "SHIELD Android TV" else "NVIDIA"
            d.hint(field, bad, "Z conflicting response", 80)
            repeat(12) { d.hint(field, good, "A corroborating response $it", 90) }
            if (before) d.hint("Model", "SHIELD Android TV", "Current model", 80)
            check(d.observations().filter { it.field == field }.let { it.size == 8 && it.none { h -> h.value == bad } }, "Catalogue $field reaches observation eviction")
            check(d.identifiedProduct() == null && d.copy().identifiedProduct() == null && d.groupProduct() == null, "Catalogue $field conflict survives eviction and progress copies")
        }
        check(shield.identifiedProduct() != null, "Copies do not change earlier catalogue evidence")
        val v6 = Device().apply { ip = "2001:db8::11"; link("synthetic:shield") }
        val linked = shield.copy().apply { link("synthetic:shield") }
        val group = Device.grouped(listOf(v6, linked)).single()
        check(group.groupProduct()?.product?.title == "NVIDIA SHIELD TV" && group.endpoints().last().identifiedProduct() == null, "A dual-stack group shares presentation while preserving endpoint evidence")
        check(Device.grouped(listOf(linked, v6.copy().apply { hint("Manufacturer", "Fixture Devices", "Current response", 80) })).single().groupProduct() == null, "A conflicting grouped endpoint suppresses shared recognition")
        check(Device.grouped(listOf(linked, model("PS5", 13).apply { link("synthetic:shield") })).single().groupProduct() == null, "Different known products cannot produce a shared product title")
        val fake = Discovery(test.targetContext, NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-console"; prefixes[ip] = prefix; addresses.add(ip)
        }).also { set(it, "accepting", true); set(it, "enriching", true) }
        val receive = Discovery::class.java.getDeclaredMethod("playStationResolved", InetAddress::class.java, PlayStationDiscovery.Response::class.java).apply { isAccessible = true }
        // Parse the exact synthetic packet length rather than treating identifiers as MACs.
        val bytes = "HTTP/1.1 200 Ok\nhost-type:PS5\ndevice-discovery-protocol-version:00030010\nhost-name:Fixture console\nhost-id:001122334455\n".toByteArray()
        val parsed = PlayStationDiscovery.parse(bytes, bytes.size)!!
        try {
            receive.invoke(fake, InetAddress.getByName("198.18.0.20"), parsed)
            val console = fake.snapshot().single()
            check(console.seen && console.presentation().title == "Sony PlayStation 5" && DeviceIcon.forGroup(console).label == "Game console", "A current in-scope console response supplies presence, product and icon")
            check(console.model == "PS5" && console.manufacturer.isEmpty() && console.mac.isEmpty() && console.links().isEmpty() && console.details.values.none { it.contains("001122334455") }, "Console host ID never becomes a MAC, grouping key or saved detail")
            receive.invoke(fake, InetAddress.getByName("198.19.0.20"), parsed)
            check(fake.snapshot().size == 1, "Out-of-scope console replies are rejected")
            fake.cancel()
            receive.invoke(fake, InetAddress.getByName("198.18.0.21"), parsed)
            check(fake.snapshot().size == 1, "Cancelled console discovery rejects late replies")
            check(Device().apply { ip = console.ip }.identifiedProduct() == null, "A fresh occupant of the same IP inherits no catalogue state")
        } finally { fake.cancel() }
        val hostname = Device().apply { ip = "192.0.2.14"; hint("Name", "thinkstation-X900.local", "Synthetic current hostname", 80); hint("Hostname", name, "Synthetic current hostname", 80) }
        val cast = Device().apply { ip = "192.0.2.15"; hint("Name", "Fixture lounge", "Synthetic casting name", 80); service("_googlecast._tcp.") }
        val unknown = Device().apply { ip = "192.0.2.16" }
        val ps5 = model("PS5", 17).apply { hint("Name", "Fixture console", "Synthetic current name", 80) }
        val samsung = model("SM-S918B", 12).apply { hint("Manufacturer", "Samsung", "Synthetic current manufacturer", 80) }
        val pixel = model("Pixel 8", 13).apply { hint("Manufacturer", "Google", "Synthetic current manufacturer", 80) }
        val fixtures = listOf(recorder, shield, samsung, pixel, hostname, cast, unknown, ps5)
        Store(test.targetContext).clear()
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo", true)) as MainActivity
        settle(test)
        val original = Configuration(activity.resources.configuration)
        fun render() = MainActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
        fun layout(view: TextView, label: String) {
            val l = view.layout!!
            check(l.getLineEnd(l.lineCount - 1) == view.text.length && (0 until l.lineCount).all { l.getEllipsisCount(it) == 0 && l.getLineMax(it) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1 } && view.height - view.compoundPaddingTop - view.compoundPaddingBottom >= l.height, "$label fits without clipping or ellipsis")
        }
        try {
            for (scale in listOf(1f, 2f)) for (width in listOf(411, 320)) for (dark in listOf(false, true)) {
                val label = "${width}dp-${scale.toInt()}x-${if (dark) "dark" else "light"}"
                main(test) {
                    activity.resources.updateConfiguration(Configuration(original).apply { fontScale = scale; screenWidthDp = width }, activity.resources.displayMetrics)
                    activity.window.setLayout(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width.toFloat(), activity.resources.displayMetrics).toInt(), WindowManager.LayoutParams.MATCH_PARENT)
                    Store(activity).dark(dark); set(activity, "selected", null); set(activity, "devices", fixtures); set(activity, "groupedSource", null); set(activity, "query", ""); render()
                }
                settle(test)
                main(test) {
                    val all = texts(activity.window.decorView)
                    for (title in listOf("Hikvision DS-7608NI-I2/8P", "NVIDIA SHIELD TV", "ThinkStation X900", "Fixture lounge", "Unidentified device", "Sony PlayStation 5", "Samsung Galaxy S23 Ultra", "Google Pixel 8")) layout(all.single { it.text.toString() == title }, "List $title $label")
                    check(all.none { it.text.toString() == "MAC not available" || it.text.toString().startsWith("Locally recognized ·") }, "List $label removes repetitive missing-MAC and interpretation lines")
                    check(all.any { it.text.toString() == "MAC   02:00:00:00:00:10" } && all.any { it.text.toString() == "Casting device" }, "List $label retains known MACs and cautious service categories")
                    val rows = get(activity, "deviceRows") as Map<String, View>
                    check(rows[ps5.ip]!!.contentDescription.contains("Sony PlayStation 5") && !rows[unknown.ip]!!.contentDescription.contains("MAC not available"), "Accessibility $label follows the cleaned identity")
                    check(rows[unknown.ip]!!.contentDescription.contains("1 IP address, IPv4") && !rows[unknown.ip]!!.contentDescription.contains(", ,"), "Accessibility $label omits empty fields and uses singular address wording")
                }
                // Let the previous explicit-copy toast/clipboard preview leave the surface.
                SystemClock.sleep(4500); settle(test)
                capture(test, activity, "list-$label")
                main(test) {
                    val scroll = get(activity, "deviceScroll") as ScrollView
                    val row = (get(activity, "deviceRows") as Map<String, View>).getValue(ps5.ip)
                    val bounds = android.graphics.Rect(0, 0, row.width, row.height)
                    (scroll.getChildAt(0) as ViewGroup).offsetDescendantRectToMyCoords(row, bounds)
                    scroll.scrollTo(0, bounds.top)
                }
                settle(test)
                main(test) {
                    val row = (get(activity, "deviceRows") as Map<String, View>).getValue(ps5.ip)
                    val visible = android.graphics.Rect()
                    val scroll = get(activity, "deviceScroll") as ScrollView
                    check(row.getLocalVisibleRect(visible) && visible.height() == row.height,
                        "Console row $label remains reachable and fully visible by scrolling; row=${row.height}, visible=${visible.height()}, viewport=${scroll.height}, y=${scroll.scrollY}")
                }
                capture(test, activity, "list-bottom-$label")
                for (term in listOf("Hikvision", "DS-7608NI-I2/8P", "recorder", "SHIELD Android TV", "ThinkStation X900", "192.0.2.16", "Galaxy S23 Ultra", "SM-S918B", "Google Pixel 8")) {
                    main(test) { texts(activity.window.decorView).filterIsInstance<EditText>().single().setText(term) }
                    settle(test)
                    main(test) { check((get(activity, "deviceRows") as Map<*, *>).size == 1, "Search $label finds $term") }
                }
                main(test) { texts(activity.window.decorView).filterIsInstance<EditText>().single().setText("Hikvision") }
                settle(test)
                main(test) { (get(activity, "deviceRows") as Map<String, View>)[recorder.ip]!!.performClick() }
                settle(test)
                main(test) {
                    val all = texts(activity.window.decorView)
                    check(all.any { it.text.toString() == "Network name" } && all.any { it.text.toString() == "DS-7608NI-I2/8P" }, "Details $label retain raw network name and model")
                    all.filter { it.text.toString() == "Hikvision DS-7608NI-I2/8P" }.forEach { layout(it, "Detail title $label") }
                    all.single { it.text.toString() == "Show discovery evidence" }.performClick()
                }
                settle(test)
                main(test) {
                    val all = texts(activity.window.decorView)
                    check(all.any { it.text.toString().contains("Local product catalogue · hikvision:") }, "Details $label expose catalogue provenance")
                    all.single { it.text.toString() == "Copy device details" }.performClick()
                    val copied = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
                    check(copied.startsWith("Hikvision DS-7608NI-I2/8P\n") && copied.contains("Network name: DS-7608NI-I2/8P\n") && copied.contains("Model: DS-7608NI-I2/8P\n") && copied.contains("Manufacturer: Not reported") && copied.contains("Recognition input: Model=DS-7608NI-I2/8P"), "Copy $label separates canonical identity, raw fields and provenance")
                    activity.onBackPressed()
                }
                settle(test)
                main(test) { check(get(activity, "selected") == null && (get(activity, "deviceRows") as Map<*, *>).size == 1, "Back $label preserves search") }
            }
            main(test) { texts(activity.window.decorView).filterIsInstance<EditText>().single().setText("SHIELD") }
            settle(test)
            main(test) { (get(activity, "deviceRows") as Map<String, View>).getValue(shield.ip).performClick() }
            settle(test)
            main(test) {
                val all = texts(activity.window.decorView)
                check(all.any { it.text.toString() == "Not available" } && all.any { it.text.toString().startsWith("Android limits MAC access.") }, "Missing-MAC explanation remains in Details")
                val scroll = get(activity, "detailScroll") as ScrollView
                var panel: View = all.single { it.text.toString() == "Locally recognized" }
                while (panel.parent !== scroll.getChildAt(0)) panel = panel.parent as View
                scroll.scrollTo(0, panel.top)
            }
            SystemClock.sleep(4500); settle(test)
            capture(test, activity, "details-shield-320dp-2x-dark")
        } finally { main(test) { activity.resources.updateConfiguration(original, activity.resources.displayMetrics); activity.finish() }; Store(test.targetContext).clear() }
    }
}
