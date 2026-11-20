package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import io.github.kazands.networklens.*
import java.net.InetAddress

/** Synthetic fixtures only. Executed by emulator-guarded kidde/device-identities modes. */
internal object KiddeChecks {
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner)!! }
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val net = NetworkState().apply { ip = "198.18.0.1"; prefix = 24; key = "synthetic-kidde"; addresses.add(ip); prefixes[ip] = prefix }
        val op = Discovery(test.targetContext, net)
        set(op, "accepting", true)
        val current = Discovery::class.java.getDeclaredMethod("applyCurrentService", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        val found = get(op, "found") as MutableMap<String, Device>
        fun peer(ip: String) = Device().apply { this.ip = ip; found[ip] = this }
        fun service(type: String = KiddeInfo.TYPE) = MdnsPacket.Service("Kidde-WD-000000000001", type).apply {
            host = "$name.local"; port = if (type == KiddeInfo.TYPE) 5353 else 443
            if (type == KiddeInfo.TYPE) { attributes["md"] = "WD".toByteArray(); attributes["sn"] = "synthetic-private-serial".toByteArray() }
        }
        try {
            for (httpFirst in listOf(false, true)) {
                val d = peer("198.18.0.10")
                current.invoke(op, d, if (httpFirst) service("_http._tcp.") else service())
                current.invoke(op, d, if (httpFirst) service() else service("_http._tcp."))
                check(d.presentation().title == "Kidde Smart Detector" && d.presentation().subtitle == "Safety detector", "Kidde family in either service order")
                check(d.classification().category == DeviceCatalog.Category.SENSOR && DeviceIcon.forGroup(d).drawable == R.drawable.ic_device_alarm_smoke && DeviceIcon.forGroup(d).label == "Safety detector", "Kidde alarm icon with a safety-detector label")
                check(d.model == "WD" && d.manufacturer.isEmpty() && d.mac.isEmpty(), "Reported fields preserved; no retail model, manufacturer or MAC invented")
                check(d.identifiedProduct()!!.product.familyOnly && d.identifiedProduct()!!.source.contains("mDNS current"), "Family-only evidence is source labelled")
                check(d.capabilities() == listOf("HomeKit advertisement"), "Advertisement is not a HomeKit compatibility promise")
                check(d.details["Kidde discovery"]!!.contains("compatibility has not been verified"), "Compatibility limitation retained")
                check(d.diagnostics.snapshot().none { it.value.contains("synthetic-private-serial") }, "Serial excluded from diagnostics")
                check(d.observations().any { it.value == service().host } && d.services.contains(KiddeInfo.TYPE), "Raw evidence retained")
                val copy = d.copy()
                check(copy.presentation() == d.presentation() && copy.capabilities() == d.capabilities(), "Snapshot retains classification and service qualifier")
                d.hint("Name", "Fixture landing", "Synthetic friendly name", 100)
                check(d.presentation().subtitle == "Safety detector · Fixture landing", "Useful friendly name preserved")
                val v6 = peer("2001:db8::10")
                current.invoke(op, v6, service())
                for (order in listOf(listOf(d, v6), listOf(v6, d))) {
                    val group = Device.grouped(order).single()
                    check(group.presentation().title == "Kidde Smart Detector" && group.classification().category == DeviceCatalog.Category.SENSOR, "IPv4 and IPv6 agree")
                }
            }
            val unknown = peer("198.18.0.11")
            current.invoke(op, unknown, service().apply { host = "other.local" })
            check(unknown.groupProduct() == null && unknown.classification().category == null, "Mismatched hostname not identified")
            val proxy = peer("2001:db8::11")
            Discovery::class.java.getDeclaredMethod("applyService", Device::class.java, MdnsPacket.Service::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(op, proxy, service(), "Wi-Fi proxy advertisement")
            check(proxy.groupProduct() == null, "Proxy record cannot establish direct Kidde fingerprint")
            val direct = peer("198.18.0.21")
            current.invoke(op, direct, service())
            for (order in listOf(listOf(direct, proxy), listOf(proxy, direct))) {
                val grouped = Device.grouped(order).single()
                check(grouped.groupProduct()?.product?.id == KiddeInfo.PRODUCT_ID && grouped.capabilities() == listOf("HomeKit advertisement"), "Proxy endpoint cannot restore unverified HomeKit compatibility")
            }
            val httpOnly = peer("198.18.0.12")
            current.invoke(op, httpOnly, service("_http._tcp."))
            check(httpOnly.groupProduct() == null, "HTTP name alone not sufficient")
            val cached = peer("198.18.0.13")
            val native = NsdServiceInfo().apply { serviceName = service().name; serviceType = KiddeInfo.TYPE; host = InetAddress.getByName(cached.ip); port = 5353; setAttribute("md", "WD") }
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java).apply { isAccessible = true }.invoke(op, native)
            check(cached.groupProduct() == null && cached.model.isEmpty(), "Native cached callbacks cannot supply identity")
            for (first in listOf(false, true)) {
                val mixed = peer("198.18.0.14")
                if (first) current.invoke(op, mixed, service())
                mixed.hint("Model", "Google Nest Mini", "Synthetic current Cast", 80)
                if (!first) current.invoke(op, mixed, service())
                check(mixed.groupProduct() == null && mixed.classification().conflicted && mixed.classification().category == null, "Conflict cannot become a speaker in either order")
                val other = peer("2001:db8::14").apply { link("mdns-host:" + service().host.lowercase(java.util.Locale.ROOT)); hint("Model", "Google Nest Mini", "synthetic", 80) }
                check(Device.grouped(listOf(other, mixed.copy())).single().classification().conflicted, "Grouping cannot restore rejected identity")
            }
            val apple = peer("198.18.0.15")
            current.invoke(op, apple, service()); apple.recognizeAppleModel("iPhone14,5")
            check(apple.locallyRecognized() == null && apple.groupRecognition() == null, "Apple fallback cannot override Kidde evidence")
            val self = peer(net.ip).apply { self = true }
            current.invoke(op, self, service())
            check(self.groupProduct() == null, "Own phone excluded")
            op.cancel()
            val stopped = peer("198.18.0.16")
            current.invoke(op, stopped, service())
            check(stopped.groupProduct() == null && stopped.services.isEmpty(), "Late response after Stop rejected")
            check(Device().apply { ip = "198.18.0.10" }.groupProduct() == null, "Fresh scan has no previous identity")
        } finally { op.cancel() }
    }
}
