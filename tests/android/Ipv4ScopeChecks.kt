package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.kazands.networklens.*
import java.net.InetAddress

/** Synthetic scope regression retained after the locally recorded dual-stack A/B comparison. */
internal object Ipv4ScopeChecks {
    private fun set(o: Any, key: String, value: Any) = o.javaClass.getDeclaredField(key).apply { isAccessible = true }.set(o, value)
    @Suppress("UNCHECKED_CAST")
    private fun <T> get(o: Any, key: String): T = o.javaClass.getDeclaredField(key).apply { isAccessible = true }.get(o) as T
    private fun call(op: Discovery, name: String, types: Array<Class<*>>, vararg args: Any) =
        Discovery::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(op, *args)
    private fun packet(host: String, address: String, model: String): MdnsPacket.Result {
        val type = listOf("_airplay", "_tcp", "local")
        val instance = listOf("Synthetic $host") + type
        val target = listOf(host, "local")
        return MdnsPacket.Result().apply {
            freshResponse = true; parsedResponse = true
            records.add(MdnsPacket.Record(type, 12, 120, false, target = instance))
            records.add(MdnsPacket.Record(instance, 33, 120, true, target = target, port = 7000))
            records.add(MdnsPacket.Record(instance, 16, 120, true, attributes = mapOf("model" to model.toByteArray())))
            records.add(MdnsPacket.Record(target, if (address.contains(':')) 28 else 1, 120, true, address = address))
            // Mixed A/AAAA records from an IPv4 responder must not lose its own identity.
            records.add(MdnsPacket.Record(target, 28, 120, true, address = "fd70::99"))
        }
    }
    private fun replay(test: Instrumentation, check: (Boolean, String) -> Unit): Map<String, List<Any>> {
        val net = NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; iface = "wlan0"; key = "synthetic-ipv4-comparison"
            addresses.addAll(listOf(ip, "fd70::1")); prefixes[ip] = 24; prefixes["fd70::1"] = 64
        }
        val op = Discovery(test.targetContext, net, Discovery.EchoFactory { _, _ -> error("No echo transport allowed") },
            Discovery.NetworkCheck { _, _ -> })
        try {
            set(op, "accepting", true)
            val types = Discovery::class.java.getDeclaredField("nsdTypes").apply { isAccessible = true }.get(op) as MutableSet<String>
            types.add("_airplay._tcp.")
            fun receive(address: String, host: String, model: String) = call(op, "mdnsResolved",
                arrayOf(InetAddress::class.java, MdnsPacket.Result::class.java), InetAddress.getByName(address), packet(host, address, model))
            receive("198.18.0.2", "dual", "AppleTV6,2")
            receive("fd70::2", "dual", "AppleTV6,2")
            receive("fd70::3", "v6-only", "AudioAccessory5,1")
            // An IPv4 address with useful identity advertised only by its IPv6 peer loses that identity.
            receive("198.18.0.4", "v6-identity-only", "")
            receive("fd70::4", "v6-identity-only", "AudioAccessory5,1")
            call(op, "observe", arrayOf(String::class.java, String::class.java), "198.18.0.5", "Synthetic IPv4 echo response")
            check(op.snapshot().single { it.ip == "198.18.0.5" }.model.isEmpty(), "A silent identity remains unknown")
            val native = NsdServiceInfo().apply {
                serviceName = "Synthetic cached service"; serviceType = "_airplay._tcp."; port = 7000
                host = InetAddress.getByName("198.18.0.6")
                if (Build.VERSION.SDK_INT >= 34) hostAddresses = listOf(host!!, InetAddress.getByName("fd70::6"))
            }
            call(op, "resolved", arrayOf(NsdServiceInfo::class.java), native)
            check(op.snapshot().none { it.ip == "198.18.0.6" }, "Cached callbacks never prove presence")
            val result = op.snapshot().associate { d -> d.ip to listOf(d.name, d.model, d.manufacturer, d.services.toSet(), d.ports.toSet()) }
            check(result["198.18.0.2"]?.get(1) == "AppleTV6,2", "Current IPv4 identity survives mixed address records")
            val mixed = Device.grouped(op.snapshot()).single { group -> group.endpoints().any { it.ip == "198.18.0.4" } }
            check((mixed.model == "AudioAccessory5,1") == false,
                "An existing IPv4 group can lose its reported model when its only model source used IPv6")
            op.cancel()
            receive("198.18.0.7", "late", "AppleTV6,2")
            check(op.snapshot().none { it.ip == "198.18.0.7" }, "Cancellation rejects late records")
            return result
        } finally { op.cancel() }
    }
    private fun modernUpdates(test: Instrumentation, check: (Boolean, String) -> Unit) {
        if (Build.VERSION.SDK_INT < 34) return
        val net = NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-native-updates"
            addresses.add(ip); prefixes[ip] = 24
        }
        fun operation() = Discovery(test.targetContext, net, Discovery.EchoFactory { _, _ -> error("No IPv6 echo transport allowed") },
            Discovery.NetworkCheck { _, _ -> }).also { set(it, "accepting", true) }
        fun info(name: String, vararg addresses: String) = NsdServiceInfo().apply {
            serviceName = name; serviceType = "_airplay._tcp."; port = 7000
            hostAddresses = addresses.map(InetAddress::getByName)
            setAttribute("model", "Synthetic cached model")
        }
        val type = Discovery::class.java.declaredClasses.single { it.simpleName == "ModernRequest" }
        fun request(op: Discovery, service: NsdServiceInfo): Any = type.getDeclaredConstructor(
            NsdServiceInfo::class.java, Int::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
            .newInstance(service, 0, "").also {
                set(it, "registered", true)
                get<MutableSet<Any>>(op, "modernActive").add(it)
                get<DiscoveryHealth>(op, "health").resolving(service.serviceName + "|_airplay._tcp.", "_airplay._tcp.")
            }
        fun update(op: Discovery, request: Any, service: NsdServiceInfo) =
            call(op, "modernUpdated", arrayOf(type, NsdServiceInfo::class.java), request, service)
        // Drive the production window-expiry boundary directly: no sleeps or live NSD registration.
        fun expire(op: Discovery, request: Any) =
            call(op, "finishModernWindow", arrayOf(type, String::class.java), request, "Synthetic resolution deadline")
        val op = operation()
        try {
            val first = info("Synthetic delayed IPv4", "fd70::6")
            val delayed = request(op, first)
            update(op, delayed, first)
            check(!get<Boolean>(delayed, "done") && !get<Boolean>(delayed, "usable"),
                "An AAAA-first update keeps the bounded callback open for a later IPv4 address")
            check(get<Map<String, Device>>(op, "found").isEmpty() && get<Set<String>>(op, "confirmQueued").isEmpty(),
                "An AAAA-only snapshot cannot create an endpoint or queue a presence probe")
            check(op.sources().values.none { it.contains("IPv6-only services excluded") },
                "IPv6-only exclusion is decided at the end of the resolution window")
            update(op, delayed, info(first.serviceName, "fd70::6", "198.18.0.6"))
            check(!get<Boolean>(delayed, "done") && get<Boolean>(delayed, "usable"),
                "A later eligible IPv4 address enters the normal settling window")
            check(get<Set<String>>(op, "confirmQueued") == setOf("198.18.0.6"),
                "The later IPv4 service retains its advertised-port TCP confirmation opportunity")
            check(get<Map<String, Device>>(op, "found").keys == setOf("198.18.0.6") && op.snapshot().isEmpty(),
                "Native updates remain candidates and never establish current presence")
            check(get<Map<String, Device>>(op, "found").values.all { it.name.isEmpty() && it.model.isEmpty() },
                "A later cached callback cannot supply device identity")
            expire(op, delayed)
            check(get<Boolean>(delayed, "done") && get<Set<Any>>(op, "modernActive").isEmpty(),
                "The bounded window releases a request that acquired an IPv4 candidate")
            val onlyV6 = info("Synthetic excluded service", "fd70::7")
            val excluded = request(op, onlyV6)
            update(op, excluded, onlyV6); update(op, excluded, onlyV6)
            check(!get<Boolean>(excluded, "done"), "Repeated AAAA snapshots do not prematurely close the request")
            expire(op, excluded)
            check(get<Boolean>(excluded, "done") && get<Set<Any>>(op, "modernActive").isEmpty(),
                "A permanently IPv6-only service releases its resolution slot at window expiry")
            check(get<java.util.Queue<Any>>(op, "modernQueue").isEmpty() && get<MdnsRetries>(op, "mdnsRetries").due(System.nanoTime(), 8).isEmpty(),
                "An excluded IPv6-only service schedules neither native nor direct-query retries")
            check(op.sources().values.any { it.contains("IPv6-only services excluded 1") } &&
                op.sources().values.none { it.startsWith("Failed") || it.startsWith("Truncated") },
                "Window expiry records one clean exclusion without inventing a failed IPv4 check")
            val sources = op.sources()
            expire(op, excluded); update(op, excluded, info(onlyV6.serviceName, "198.18.0.7"))
            check(op.sources() == sources && "198.18.0.7" !in get<Map<String, Device>>(op, "found"),
                "Callbacks after the bounded window cannot reopen or recount an excluded request")
        } finally { op.cancel() }
        for (addresses in listOf(emptyArray<String>(), arrayOf("203.0.113.9"))) {
            val unresolved = operation()
            try {
                val first = info("Synthetic unresolved service", "fd70::9")
                val pending = request(unresolved, first)
                update(unresolved, pending, first)
                update(unresolved, pending, info(first.serviceName, *addresses))
                synchronized(unresolved) {
                    expire(unresolved, pending)
                    check(get<java.util.Queue<Any>>(unresolved, "modernQueue").size == 1 &&
                        unresolved.sources().values.none { it.contains("IPv6-only services excluded") },
                        "An empty or off-scope IPv4 update preserves normal resolution failure handling")
                }
            } finally { unresolved.cancel() }
        }
        val wrongNetwork = operation()
        try {
            val other = info("Synthetic other network service", "fd70::10").apply {
                network = android.net.Network.fromNetworkHandle((42L shl 32) or 0xcafed00dL)
            }
            val pending = request(wrongNetwork, other)
            update(wrongNetwork, pending, other)
            synchronized(wrongNetwork) {
                expire(wrongNetwork, pending)
                check(get<java.util.Queue<Any>>(wrongNetwork, "modernQueue").size == 1 &&
                    wrongNetwork.sources().values.none { it.contains("IPv6-only services excluded") },
                    "An update from another network cannot convert a resolution failure into an exclusion")
            }
        } finally { wrongNetwork.cancel() }
        val cancelled = operation()
        try {
            val first = info("Synthetic cancelled service", "fd70::8")
            val pending = request(cancelled, first)
            update(cancelled, pending, first); cancelled.cancel()
            val sources = cancelled.sources()
            update(cancelled, pending, info(first.serviceName, "198.18.0.8")); expire(cancelled, pending)
            check(get<Map<String, Device>>(cancelled, "found").isEmpty() && get<Set<String>>(cancelled, "confirmQueued").isEmpty() && cancelled.sources() == sources,
                "Stop rejects later IPv4 updates and window-expiry work without new probes")
        } finally { cancelled.cancel() }
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        modernUpdates(test, check)
        val ipv4 = replay(test, check)
        check(ipv4.size == 3 && ipv4.keys.all { !it.contains(':') }, "Only three independently responding IPv4 endpoints are admitted")
        check(ipv4.values.none { it[1] == "AudioAccessory5,1" }, "IPv6-only identity is not borrowed by an IPv4 endpoint")
        val noV4 = NetworkState().apply { addresses.add("fd70::1"); prefixes["fd70::1"] = 64 }
        check(!noV4.local(InetAddress.getByName("fd70::2")), "IPv6 prefixes cannot admit a LAN target")
        var unsupported = false
        try { ScanPlan.create("", 0, null) } catch (error: IllegalArgumentException) { unsupported = error.message == ScanPlan.IPV4_REQUIRED }
        check(unsupported, "IPv6-only Wi-Fi reports the explicit IPv4 requirement")
        val before = NetworkState().apply { key = "synthetic-ipv4"; ip = "198.18.0.1"; prefix = 24; addresses.add(ip); prefixes[ip] = 24 }
        val changed = NetworkState().apply { key = before.key; ip = before.ip; prefix = 24; addresses.addAll(listOf(ip, "fd70::1")); prefixes[ip] = 24; prefixes["fd70::1"] = 64 }
        check(before.sameConfiguration(changed) && before.retainsScope(changed) && changed.retainsScope(before),
            "IPv6 additions and withdrawals do not invalidate IPv4 results or work")
        check(!before.contains(Device().apply { ip = "fd70::2" }), "Inventory pruning cannot restore an IPv6 endpoint")
        val finished = java.util.concurrent.CountDownLatch(1)
        var completed = true
        var reason = ""
        val unsupportedScan = Discovery(test.targetContext, noV4, Discovery.EchoFactory { _, _ -> error("Unsupported network opened echo") }, Discovery.NetworkCheck { _, _ -> })
        try {
            unsupportedScan.scan(null, object : Discovery.Listener {
                override fun progress(done: Int, total: Int, devices: List<Device>) { error("Unsupported network published scan progress") }
                override fun finished(devices: List<Device>, complete: Boolean, message: String) { completed = complete; reason = message; finished.countDown() }
            })
            check(finished.await(3, java.util.concurrent.TimeUnit.SECONDS) && !completed && reason == ScanPlan.IPV4_REQUIRED,
                "Direct callers receive an unsupported-network result before any scan work")
        } finally { unsupportedScan.cancel() }
        ComposeDriver(test, check).use { driver ->
            driver.start(false); driver.configure(320, 2f)
            driver.main {
                MainActivity::class.java.getDeclaredMethod("stopWatchingConnection").apply { isAccessible = true }.invoke(driver.activity)
                driver.set("network", noV4); driver.set("devices", emptyList<Device>()); driver.set("message", "")
            }
            repeat(2) {
                driver.main { driver.activity.handle(UiAction.Theme) }; driver.settle()
                driver.main {
                    check(driver.activity.uiState().status == ScanPlan.IPV4_REQUIRED, "Connected IPv6-only Wi-Fi explains the unsupported scan scope")
                    check(driver.tagged("scan")!!.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled),
                        "Scan stays disabled without IPv4 instead of claiming Wi-Fi is disconnected")
                    check(driver.words().contains("IPv4 address required"), "Empty state explains the IPv4 requirement")
                }
                driver.assertTextLayouts("IPv4 requirement at 200% text")
            }
        }
    }
}
