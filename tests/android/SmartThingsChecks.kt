package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import io.github.kazands.networklens.*
import java.net.InetAddress

/** Synthetic current records; no Android Network or household identifiers. */
internal object SmartThingsChecks {
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner)!! }
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val net = NetworkState().apply { ip = "198.18.0.1"; prefix = 24; key = "synthetic-smartthings"; addresses.add(ip); prefixes[ip] = prefix }
        val op = Discovery(test.targetContext, net)
        set(op, "accepting", true)
        val current = Discovery::class.java.getDeclaredMethod("applyCurrentService", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        val found = get(op, "found") as MutableMap<String, Device>
        fun peer(ip: String) = Device().apply { this.ip = ip; found[ip] = this }
        fun service() = MdnsPacket.Service("0000000000000001", SmartThingsInfo.TYPE).apply { host = "hubv2-00000000000000001.local"; port = 8081 }
        fun matter() = MdnsPacket.Service("0000000000000001-0000000000000002", "_matter._tcp.").apply { host = service().host; port = 5540 }
        try {
            val harmony = peer("198.18.0.20")
            harmony.hint("Name", "Harmony Hub", "Synthetic friendly name", 80)
            check(DeviceIcon.forGroup(harmony).drawable == R.drawable.ic_device_network, "Friendly name alone cannot select a remote icon")
            harmony.hint("Name", "Fixture media room", "Synthetic friendly name", 100)
            harmony.hint("Model", "Harmony Hub", "Synthetic current UPnP model", 80)
            harmony.hint("Manufacturer", "Logitech", "Synthetic current UPnP maker", 80)
            check(DeviceIcon.forGroup(harmony).drawable == R.drawable.ic_device_remote && DeviceIcon.categoryLabel(harmony) == "Remote-control hub", "Exact Harmony model selects remote hardware and icon")
            check(harmony.presentation().title == "Logitech Harmony Hub" && harmony.presentation().subtitle == "Remote-control hub · Fixture media room", "Harmony keeps its useful room name")
            val otherEndpoint = Device().apply { ip = "2001:db8::20"; link("synthetic:harmony") }
            harmony.link("synthetic:harmony")
            check(DeviceIcon.forGroup(Device.grouped(listOf(otherEndpoint, harmony)).single()).drawable == R.drawable.ic_device_remote, "Remote icon survives endpoint grouping")
            val homeKitRemote = Device().apply { hint("Device type", "HomeKit remote", "Synthetic current HomeKit category", 80) }
            check(DeviceIcon.forGroup(homeKitRemote).drawable == R.drawable.ic_device_remote && DeviceIcon.categoryLabel(homeKitRemote) == "Remote control", "Other known remotes share the icon without becoming hubs")
            harmony.hint("Device type", "HomeKit thermostat", "Synthetic conflicting hardware", 80)
            check(DeviceIcon.forGroup(harmony).drawable == R.drawable.ic_device_network, "Conflicting hardware does not retain a confident remote icon")
            val bridge = Device().apply { hint("Device type", "HomeKit bridge", "Synthetic current HomeKit category", 80) }
            check(DeviceIcon.forGroup(bridge).drawable == R.drawable.ic_device_smart_home, "Smart-home bridges share the home icon")
            for (dark in listOf(false, true)) {
                val bitmap = android.graphics.Bitmap.createBitmap(240, 120, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.parseColor(if (dark) "#0c1b15" else "#f5faf6"))
                for ((n, id) in listOf(R.drawable.ic_device_smart_home, R.drawable.ic_device_remote).withIndex()) {
                    val icon = test.targetContext.getDrawable(id)!!.mutate()
                    icon.setTint(android.graphics.Color.parseColor(if (dark) "#e2ede5" else "#243b2e"))
                    icon.setBounds(24 + n * 120, 24, 96 + n * 120, 96)
                    icon.draw(canvas)
                }
                val file = java.io.File(test.targetContext.getExternalFilesDir(null), "hub-icons-${if (dark) "dark" else "light"}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            for (matterFirst in listOf(false, true)) {
                val d = peer("198.18.0.10")
                current.invoke(op, d, if (matterFirst) matter() else service())
                current.invoke(op, d, if (matterFirst) service() else matter())
                current.invoke(op, d, MdnsPacket.Service("hub-" + "0".repeat(32), SmartThingsInfo.HEDGE_TYPE).apply { host = name + ".local"; port = 8766 })
                check(d.presentation().title == "SmartThings Hub" && d.presentation().subtitle == "Smart-home hub", "Hub title replaces opaque IDs in either arrival order")
                check(d.classification().category == DeviceCatalog.Category.HUB && DeviceIcon.categoryLabel(d) == "Smart-home hub", "Physical hub takes precedence over Matter capability")
                check(DeviceIcon.forGroup(d).drawable == R.drawable.ic_device_smart_home, "Identified hub uses the connected-home icon")
                check(d.model.isEmpty() && d.manufacturer.isEmpty() && d.mac.isEmpty(), "No inferred model, maker or MAC in reported fields")
                check(d.capabilities() == listOf("Matter", "SmartThings"), "Radio capabilities not invented")
                check(d.observations().any { it.value == service().host } && d.services.contains(SmartThingsInfo.HEDGE_TYPE), "Raw names and services retained")
                check(d.identifiedProduct()!!.source.contains("mDNS current") && d.identifiedProduct()!!.product.familyOnly, "Recognition is source labelled and family only")
                val copy = d.copy()
                d.hint("Name", "Synthetic family room", "Synthetic friendly name", 100)
                check(d.presentation().subtitle == "Smart-home hub · Synthetic family room" && copy.presentation().subtitle == "Smart-home hub", "Friendly name and immutable snapshot")
                val v6 = peer("2001:db8::10")
                current.invoke(op, v6, service())
                v6.hint("Name", service().host, "Synthetic reverse reply", 100)
                check(v6.presentation().subtitle == "Smart-home hub", "Reverse hostname typography does not expose hub identifier")
                for (order in listOf(listOf(d, v6), listOf(v6, d))) {
                    val grouped = Device.grouped(order).single()
                    check(grouped.presentation().title == "SmartThings Hub" && grouped.classification().category == DeviceCatalog.Category.HUB, "IPv4 and IPv6 use same identity in either order")
                }
            }
            val tv = peer("198.18.0.11")
            current.invoke(op, tv, service().apply { host = "synthetic-tv.local" })
            check(tv.groupProduct() == null && tv.classification().category == null && tv.capabilities() == listOf("SmartThings"), "Service alone cannot call a TV or appliance a hub")
            val hedge = peer("198.18.0.12")
            current.invoke(op, hedge, MdnsPacket.Service("hub-" + "0".repeat(32), SmartThingsInfo.HEDGE_TYPE).apply { host = service().host; port = 8766 })
            check(hedge.groupProduct() == null, "Hedge alone lacks corroboration")
            val self = peer("198.18.0.1").apply { self = true }
            current.invoke(op, self, service())
            check(self.groupProduct() == null, "Own phone is not reclassified")
            val cached = peer("198.18.0.13")
            val native = NsdServiceInfo().apply { serviceName = service().name; serviceType = SmartThingsInfo.TYPE; host = InetAddress.getByName(cached.ip); port = 8081 }
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java).apply { isAccessible = true }.invoke(op, native)
            check(cached.groupProduct() == null && cached.name.isEmpty(), "Native cached candidates cannot supply identity")
            for (hubFirst in listOf(false, true)) {
                val mixed = peer("198.18.0.14")
                if (hubFirst) current.invoke(op, mixed, service())
                mixed.hint("Model", "Google Nest Mini", "Synthetic current Cast TXT", 80)
                if (!hubFirst) current.invoke(op, mixed, service())
                check(mixed.groupProduct() == null && mixed.classification().conflicted && mixed.classification().category == null, "Conflicting Google identity cannot replace hub in either order")
                val other = peer("2001:db8::14").apply { link("mdns-host:" + service().host); hint("Model", "Google Nest Mini", "Synthetic current Cast TXT", 80) }
                check(Device.grouped(listOf(mixed.copy(), other)).single().classification().conflicted, "Grouped neighbor cannot restore rejected category")
            }
            val apple = peer("198.18.0.15")
            current.invoke(op, apple, service())
            apple.recognizeAppleModel("iPhone14,5")
            check(apple.locallyRecognized() == null && apple.groupRecognition() == null, "Apple fallback cannot overwrite hub identity")
            op.cancel()
            val stopped = peer("198.18.0.16")
            current.invoke(op, stopped, service())
            check(stopped.groupProduct() == null && stopped.services.isEmpty(), "Stop rejects late records")
            check(Device().apply { ip = "198.18.0.10" }.groupProduct() == null, "Fresh scan does not reuse old identity")
        } finally { op.cancel() }
    }
}
