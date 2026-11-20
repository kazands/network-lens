package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.nsd.NsdServiceInfo
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

/** Synthetic fixtures without an Android Network; refuses physical devices. */
internal object RecogChecks {
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
    private fun capture(activity: MainActivity, name: String) {
        val view = activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        val folder = File(activity.getExternalFilesDir(null), "recog-api${Build.VERSION.SDK_INT}").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Suppress("UNCHECKED_CAST")
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val property = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        check(property.inputStream.bufferedReader().use { it.readText().trim() } == "1", "Recog fixtures reject physical devices")
        RecognitionConflicts.run(check)
        fun operation() = Discovery(test.targetContext, NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-recog"
            prefixes[ip] = prefix; addresses.add(ip)
        }).also { set(it, "accepting", true); set(it, "enriching", true) }
        val op = operation()
        val apply = Discovery::class.java.getDeclaredMethod("applyCurrentService", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        fun service(code: String, suffix: String = "fixture", type: String = "_device-info._tcp.") =
            MdnsPacket.Service("Synthetic $suffix", type).apply {
                host = "$suffix.local"; port = 8080; attributes["model"] = code.toByteArray()
            }
        fun device(code: String, n: Int): Device = Device().apply {
            ip = "192.0.2.$n"
            apply.invoke(op, this, service(code, "fixture$n"))
        }
        val fixtures = listOf(device("MacBookPro18,3", 10), device("iPad13,18", 11), device("Macmini9,1", 12),
            device("AppleTV11,1", 13), device("AudioAccessory5,1", 14), device("FixtureUnknown99,1", 15))
        val sharedModels = listOf("MacBookPro15,1" to "MacBook Pro (15-inch)",
            "MacBookPro15,2" to "MacBook Pro (13-inch, Four Thunderbolt 3 ports)",
            "Macmini3,1" to "Mac mini (2009)")
        val sharedFixtures = sharedModels.mapIndexed { n, (code, _) -> device(code, 60 + n) }
        try {
            for ((n, expected) in sharedModels.withIndex()) {
                val (code, description) = expected
                val d = sharedFixtures[n]
                check(d.locallyRecognized()?.let { it.code == code && it.description == description && it.familyOnly } == true,
                    "Current $code response retains only the shared model details")
                check(d.model == code && d.manufacturer.isEmpty() && !d.seen && d.json().getJSONObject("localRecognition").getBoolean("familyOnly"),
                    "Shared $code preserves its reported code, unknown manufacturer and independent presence")
            }
            val laptop = fixtures[0]
            check(laptop.locallyRecognized()?.category == Recog.Category.LAPTOP, "Current-wire device-info reaches offline recognition")
            check(laptop.model == "MacBookPro18,3" && laptop.manufacturer.isEmpty() && laptop.mac.isEmpty() && laptop.kind.isEmpty(),
                "Recognition preserves reported model and unknown manufacturer, MAC and reported type")
            check(!laptop.seen, "Interpreting model data does not create presence")
            check(laptop.observations().none { it.source.contains("Recog") }, "Inferences never enter reported observations")
            check(laptop.json().getJSONObject("localRecognition").getString("source").contains(RecogModels.REVISION), "JSON keeps interpreted provenance separate")
            val expected = listOf("laptop", "tablet", "desktop computer", "TV media player", "smart speaker")
            for ((n, category) in expected.withIndex()) check(DeviceIcon.forGroup(fixtures[n]).label == "Locally recognized $category", "Exact hardware selects the $category icon")
            check(DeviceIcon.forGroup(fixtures.last()).label == "Device type unknown", "Unmatched hardware retains the generic icon")
            val cached = Device().apply { ip = "198.18.0.5" }
            (get(op, "found") as MutableMap<String, Device>)[cached.ip] = cached
            (get(op, "freshResponders") as MutableSet<String>).add(cached.ip)
            val native = NsdServiceInfo().apply {
                serviceName = "Synthetic cached"; serviceType = "_device-info._tcp."; host = InetAddress.getByName(cached.ip); port = 8080
                if (Build.VERSION.SDK_INT >= 34) hostAddresses = listOf(InetAddress.getByName(cached.ip))
                setAttribute("model", "MacBookPro18,3")
            }
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java).apply { isAccessible = true }.invoke(op, native)
            check(cached.locallyRecognized() == null && cached.model.isEmpty(), "Native cached metadata cannot authorize recognition, even for a confirmed peer")
            val software = Device().apply { ip = "192.0.2.30" }
            apply.invoke(op, software, service("MacBookPro18,3", "software", "_http._tcp."))
            check(software.locallyRecognized() == null, "Other protocols cannot reuse a device-info fingerprint")
            val conflicting = laptop.copy()
            apply.invoke(op, conflicting, service("iPad14,1", "conflict"))
            check(conflicting.locallyRecognized() == null && DeviceIcon.forGroup(conflicting).label == "Conflicting device types", "Conflicting hardware categories suppress description and expose the disagreement")
            check(laptop.locallyRecognized() != null, "Progress copies have independent conflict state")
            val reported = laptop.copy().apply { hint("Model", "Fixture genuine model", "UPnP description", 80) }
            check(reported.locallyRecognized() == null && reported.model == "Fixture genuine model", "A contradictory directly reported model outranks interpretation")
            val manufacturer = laptop.copy().apply { hint("Manufacturer", "Fixture Devices", "UPnP description", 80) }
            check(manufacturer.locallyRecognized() == null && manufacturer.manufacturer == "Fixture Devices", "Reported manufacturer is never rewritten to Apple")
            manufacturer.hint("Manufacturer", "Apple", "Higher-priority fixture report", 90)
            check(manufacturer.locallyRecognized() == null, "A lower-ranked conflicting manufacturer is not hidden by the selected report")
            check(laptop.copy().apply { model = "Fixture replaced model" }.locallyRecognized() == null,
                "A replaced selected model cannot inherit an earlier recognition")
            for ((flag, label) in listOf("self" to "This phone", "gateway" to "Router")) {
                val d = laptop.copy(); if (flag == "self") d.self = true else d.gateway = true
                check(DeviceIcon.forGroup(d).label == label, "The $flag role keeps icon precedence")
            }
            val light = laptop.copy().apply { hint("Device type", "HomeKit light", "HomeKit category", 80) }
            check(DeviceIcon.forGroup(light).label == "Conflicting device types", "Contradictory physical categories are explicit rather than resolved by branch order")
            val v6 = Device().apply { ip = "2001:db8::10"; link("fixture:dual") }
            val v4 = laptop.copy().apply { link("fixture:dual") }
            val grouped = Device.grouped(listOf(v6, v4)).single()
            check(grouped.groupRecognition() != null && grouped.endpoints()[1].locallyRecognized() == null,
                "A group may use a recognized icon while each endpoint retains its own evidence")
            val rival = fixtures[1].copy().apply { link("fixture:dual") }
            check(Device.grouped(listOf(v4, rival)).single().groupRecognition() == null, "Conflicting grouped hardware has no shared inferred description")
            op.cancel()
            apply.invoke(op, laptop, service("iPad14,1", "late"))
            check(laptop.locallyRecognized()?.code == "MacBookPro18,3", "Cancelled discovery rejects late recognition changes")
            val next = operation()
            try { check(next.snapshot().isEmpty() && Device().locallyRecognized() == null, "A new scan inherits no recognized devices") } finally { next.cancel() }
        } finally { op.cancel() }

        ComposeFixtureViews.run(test,check,fixtures+sharedFixtures)
    }
}
