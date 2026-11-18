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
            for (matterFirst in listOf(false, true)) {
                val d = peer("198.18.0.10")
                current.invoke(op, d, if (matterFirst) matter() else service())
                current.invoke(op, d, if (matterFirst) service() else matter())
                current.invoke(op, d, MdnsPacket.Service("hub-" + "0".repeat(32), SmartThingsInfo.HEDGE_TYPE).apply { host = name + ".local"; port = 8766 })
                check(d.presentation().title == "SmartThings Hub" && d.presentation().subtitle == "Smart-home hub", "Hub title replaces opaque IDs in either arrival order")
                check(d.classification().category == DeviceCatalog.Category.HUB && DeviceIcon.categoryLabel(d) == "Smart-home hub", "Physical hub takes precedence over Matter capability")
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
