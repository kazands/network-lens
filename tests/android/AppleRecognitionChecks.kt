package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import io.github.kazands.networklens.*
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** No Android Network is supplied: these fixtures cannot connect to household devices. */
internal object AppleRecognitionChecks {
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner)!! }
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val op = Discovery(test.targetContext, NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-apple"
            prefixes[ip] = prefix; addresses.add(ip)
        })
        set(op, "accepting", true); set(op, "enriching", true)
        val found = get(op, "found") as MutableMap<String, Device>
        val fresh = get(op, "freshResponders") as MutableSet<String>
        val plans = get(op, "appleRequests") as ServiceAdmission
        val request = Discovery::class.java.getDeclaredMethod("requestAppleModel", Device::class.java).apply { isAccessible = true }
        val apply = Discovery::class.java.getDeclaredMethod("applyAppleReport", Device::class.java, AppleDeviceInfo.Report::class.java).apply { isAccessible = true }
        val current = Discovery::class.java.getDeclaredMethod("applyCurrentService", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        val work = get(op, "enrichment") as DiscoveryWork
        val gate = CountDownLatch(1); val started = CountDownLatch(2)
        repeat(2) { work.submit(DiscoveryWork.Lane.METADATA) { started.countDown(); try { gate.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() } } }
        check(started.await(3, TimeUnit.SECONDS), "Apple requests use bounded metadata workers")
        fun peer(last: Int) = Device().apply { ip = "198.18.0.$last"; found[ip] = this }
        fun queue(d: Device) { fresh.add(d.ip); d.port(AppleDeviceInfo.PORT); request.invoke(op, d) }
        try {
            val candidate = peer(2)
            candidate.port(AppleDeviceInfo.PORT); request.invoke(op, candidate)
            check(plans.size() == 0 && candidate.model.isEmpty(), "An open candidate port without fresh presence cannot queue an Apple request")
            val cached = NsdServiceInfo().apply {
                serviceName = "Synthetic cached Apple"; serviceType = "_apple-mobdev2._tcp."
                host = InetAddress.getByName(candidate.ip); port = AppleDeviceInfo.PORT
                setAttribute("model", "iPhone17,1")
            }
            fresh.add(candidate.ip)
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java).apply { isAccessible = true }.invoke(op, cached)
            check(candidate.model.isEmpty() && plans.size() == 0, "Cached Apple metadata never supplies a model or triggers direct metadata")
            queue(candidate)
            repeat(4) { request.invoke(op, candidate) }
            check(plans.size() == 1 && candidate.details[AppleDeviceInfo.SOURCE] == "Waiting or checking", "Duplicate candidate discoveries have one request per scan")
            val outside = peer(3).apply { ip = "203.0.113.3"; found[ip] = this }
            queue(outside)
            val self = peer(4).apply { this.self = true }; queue(self)
            val noPort = peer(5); fresh.add(noPort.ip); request.invoke(op, noPort)
            check(plans.size() == 1, "Scope, self and observed-port gates reject ineligible work")

            for ((index, pair) in listOf(
                "iPhone17,1" to DeviceCatalog.Category.PHONE,
                "iPad13,18" to DeviceCatalog.Category.TABLET,
                "AppleTV6,2" to DeviceCatalog.Category.TV_PLAYER,
                "AudioAccessory5,1" to DeviceCatalog.Category.SPEAKER).withIndex()) {
                val (code, category) = pair
                val d = peer(10 + index); queue(d)
                val response = "<plist version=\"1.0\"><dict><key>Request</key><string>GetValue</string><key>Key</key><string>ProductType</string><key>Value</key><string>$code</string></dict></plist>"
                val report = AppleDeviceInfo.modelResponse(AppleDeviceInfo.parse(response.toByteArray()))
                check(apply.invoke(op, d, report) == true, "Current Apple response admitted")
                check(d.model == code && d.classification().category == category && d.identifiedProduct()?.product?.brand == "Apple", "Apple model selects correct hardware category: $code")
                check(d.manufacturer.isEmpty() && d.mac.isEmpty() && d.name.isEmpty(), "Apple interpretation does not invent reported manufacturer, name or MAC")
                check(d.observations().any { it.value == code && it.source == AppleDeviceInfo.MODEL_SOURCE }, "Direct model is source-labelled and searchable")
                val wanted = when (category) {
                    DeviceCatalog.Category.PHONE -> R.drawable.ic_device_smartphone
                    DeviceCatalog.Category.TABLET -> R.drawable.ic_device_tablet
                    DeviceCatalog.Category.TV_PLAYER -> R.drawable.ic_device_tv_player
                    else -> R.drawable.ic_device_speaker
                }
                // Compare to the established model route rather than assuming drawable names.
                val advertised = Device().apply { recognizeServiceModel("_airplay._tcp.", mapOf("model" to code.toByteArray())) }
                check(DeviceIcon.forGroup(d).drawable == wanted && DeviceIcon.forGroup(d).drawable == DeviceIcon.forGroup(advertised).drawable, "Direct and AirPlay models use the correct established icons")
                current.invoke(op, d, MdnsPacket.Service("Synthetic Apple", "_airplay._tcp.").apply {
                    port = 7000; attributes["model"] = code.toByteArray()
                })
                check(d.groupProduct() != null && d.capabilities().contains("AirPlay"), "Agreeing secondary services preserve physical identity")
                val copy = d.copy()
                d.hint("Manufacturer", "Synthetic conflicting maker", "Synthetic response", 90)
                check(d.identifiedProduct() == null && copy.identifiedProduct() != null, "Conflicting manufacturer suppresses product without mutating snapshot")
                check(Device().apply { ip = d.ip }.model.isEmpty(), "New scan never inherits an IP's former model")
            }
            val withheld = peer(20); queue(withheld)
            apply.invoke(op, withheld, AppleDeviceInfo.Report(AppleDeviceInfo.Status.WITHHELD))
            check(withheld.model.isEmpty() && withheld.manufacturer.isEmpty() && withheld.identifiedProduct() == null &&
                DeviceIcon.forGroup(withheld).label == AppleDeviceInfo.SERVICE, "Withheld model preserves verified service without guessing an iPhone or iPad")
            val unavailable = peer(24); queue(unavailable)
            apply.invoke(op, unavailable, AppleDeviceInfo.Report(AppleDeviceInfo.Status.UNAVAILABLE, failure = MetadataHttp.Reason.READ_FAILED))
            check(unavailable.services.contains(AppleDeviceInfo.SERVICE) && unavailable.model.isEmpty() &&
                unavailable.details[AppleDeviceInfo.SOURCE]!!.contains("Response read failed"),
                "Failed model lookup retains verified Apple service and its diagnostic without guessing hardware")
            val other = peer(21); queue(other); apply.invoke(op, other, AppleDeviceInfo.Report(AppleDeviceInfo.Status.OTHER))
            check(other.services.isEmpty() && other.identifiedProduct() == null, "Non-Apple service on the same port creates no identity")
            val unknown = peer(22); queue(unknown); apply.invoke(op, unknown, AppleDeviceInfo.Report(AppleDeviceInfo.Status.MODEL, "iPad999,1"))
            check(unknown.model == "iPad999,1" && unknown.identifiedProduct() == null, "Unknown model stays a raw report")
            val lost = peer(23); queue(lost); fresh.remove(lost.ip)
            check(apply.invoke(op, lost, AppleDeviceInfo.Report(AppleDeviceInfo.Status.MODEL, "iPhone17,1")) == false, "No late identity without current presence")
            for (i in 40..80) queue(peer(i))
            check(plans.size() == 32 && op.sources()[AppleDeviceInfo.SOURCE]!!.startsWith("Truncated"), "Work admission has a finite 32-endpoint cap and reports limitation")
            val pending = candidate.copy()
            op.cancel()
            check(candidate.details[AppleDeviceInfo.SOURCE] == "Stopped before lookup finished" && pending.details[AppleDeviceInfo.SOURCE] == "Waiting or checking", "Cancellation seals pending outcomes and leaves prior snapshots immutable")
            check(apply.invoke(op, candidate, AppleDeviceInfo.Report(AppleDeviceInfo.Status.MODEL, "iPhone17,1")) == false && candidate.model.isEmpty(), "Late response after Stop cannot mutate identity")
        } finally { op.cancel(); gate.countDown() }
    }
}
