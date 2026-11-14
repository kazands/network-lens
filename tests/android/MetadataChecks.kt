package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.system.ErrnoException
import android.system.OsConstants
import io.github.kazands.networklens.*
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Synthetic selected-scope fixtures. No Android Network is supplied, so no LAN socket can open. */
internal object MetadataChecks {
    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner) }
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    private fun operation(test: Instrumentation) = Discovery(test.targetContext, NetworkState().apply {
        ip = "198.18.0.1"; prefix = 24; key = "synthetic-metadata"
        prefixes[ip] = prefix; addresses.add(ip)
    }).also { set(it, "accepting", true); set(it, "enriching", true) }
    private fun service(type: String, vararg pairs: Pair<String, String>) = MdnsPacket.Service("Synthetic device", type).apply {
        host = "fixture.local"; port = 8080
        for ((key, value) in pairs) attributes[key] = value.toByteArray(StandardCharsets.UTF_8)
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val op = operation(test)
        val work = get(op, "enrichment") as DiscoveryWork
        val gate = CountDownLatch(1); val started = CountDownLatch(2)
        repeat(2) { work.submit(DiscoveryWork.Lane.METADATA) { started.countDown(); try { gate.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() } } }
        check(started.await(3, TimeUnit.SECONDS), "Metadata lane is independently bounded")
        val apply = Discovery::class.java.getDeclaredMethod("applyCurrentService", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        val request = Discovery::class.java.getDeclaredMethod("requestMetadata", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        val resolved = Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java).apply { isAccessible = true }
        val fresh = get(op, "freshResponders") as MutableSet<String>
        val plans = get(op, "metadataRequests") as ServiceAdmission
        try {
            val d = Device().apply { ip = "198.18.0.2" }
            (get(op, "found") as MutableMap<String, Device>)[d.ip] = d
            request.invoke(op, d, service("_shelly._tcp."))
            check(plans.size() == 0, "An unconfirmed candidate cannot trigger metadata HTTP")
            check(d.metadataLookups.snapshot().isEmpty(), "Unconfirmed candidates have no fabricated lookup outcomes")
            fresh.add(d.ip)
            val cached = NsdServiceInfo().apply {
                serviceName = "Cached-only fixture"; serviceType = "_shelly._tcp."; host = InetAddress.getByName(d.ip); port = 8080
                if (Build.VERSION.SDK_INT >= 34) hostAddresses = listOf(InetAddress.getByName(d.ip))
                setAttribute("model", "Stale cached model")
            }
            resolved.invoke(op, cached)
            check(plans.size() == 0 && d.model.isEmpty(), "A native callback cannot supply identity or trigger HTTP even after presence confirmation")
            apply.invoke(op, d, service("_meshcop._udp.", "mn" to "Fixture Hub 2", "vn" to "Fixture Devices", "xa" to "0011223344556677"))
            check(d.model == "Fixture Hub 2" && d.manufacturer == "Fixture Devices", "Current Thread fields reach displayed identity")
            check(d.observations().any { it.field == "Model" && it.source.contains("_meshcop._udp.") }, "Thread identity retains service provenance")
            check(d.mac.isEmpty(), "Thread extended addresses never become hardware MAC claims")
            val esp = Device().apply { ip = "198.18.0.3" }; fresh.add(esp.ip)
            apply.invoke(op, esp, service("_esphomelib._tcp.", "board" to "fixture_board", "platform" to "ESP32", "friendly_name" to "Fixture sensor", "version" to "2026.10.0", "mac" to "001122334455"))
            check(esp.model.isEmpty() && esp.manufacturer.isEmpty(), "Firmware compilation target never invents a retail model or manufacturer")
            check(esp.name == "Fixture sensor" && esp.details["ESPHome board (advertised)"] == "fixture_board", "ESPHome name and board detail reach the device")
            check(esp.mac == "00:11:22:33:44:55" && esp.observations().any { it.field == "Firmware" }, "ESPHome explicit MAC and firmware are retained")
            apply.invoke(op, d, MdnsPacket.Service("ShElLy1-A1B2C3", "_http._tcp.").apply { host = "fixture.local"; port = 8080 })
            check(plans.size() == 1, "A current six-digit Gen1 instance authorizes its metadata request")
            check(d.mac.isEmpty(), "A short Gen1 device ID is not treated as a hardware MAC")
            apply.invoke(op, d, service("_shelly._tcp."))
            repeat(8) { apply.invoke(op, d, service("_shelly._tcp.").apply { port = 8100 + it }) }
            check(plans.size() == 1, "Repeated advertisements and changing ports cannot expand metadata work")
            apply.invoke(op, d, service("_spotify-connect._tcp.", "CPath" to "/info?action=resetUsers"))
            check(plans.size() == 1, "Unsafe advertised actions never enter the metadata queue")
            apply.invoke(op, d, service("_spotify-connect._tcp.", "CPath" to "/spotifyzc"))
            apply.invoke(op, d, service("_ipp._tcp.", "rp" to "ipp/print"))
            apply.invoke(op, d, service("_wled._tcp."))
            check(plans.size() == 4, "Supported protocols have independent per-peer opportunities")
            check(d.metadataLookups.snapshot().size == 4 && d.metadataLookupRows().all { it.first.contains("IPv4") && it.second == "Waiting or checking\nModel available from mDNS current _meshcop._udp. TXT\nManufacturer available from mDNS current _meshcop._udp. TXT" },
                "Queued outcomes identify their endpoint family and current alternative sources")
            check(LocalMetadata.Kind.values().all { op.sources()[it.source]?.startsWith("Running") == true }, "Queued metadata appears as pending source work")
            val outside = Device().apply { ip = "203.0.113.20" }; fresh.add(outside.ip)
            request.invoke(op, outside, service("_shelly._tcp."))
            d.self = true; request.invoke(op, d, service("_shelly._tcp.")); d.self = false
            check(plans.size() == 4, "Out-of-scope and self endpoints cannot trigger metadata requests")
            // Exercise the same report-to-device path used after a successful HTTP response.
            val applyReport = Discovery::class.java.getDeclaredMethod("applyMetadata", Device::class.java, LocalMetadata.Report::class.java, String::class.java).apply { isAccessible = true }
            val audio = Device().apply { ip = "198.18.0.4" }
            val info = LocalMetadata.parse(LocalMetadata.Kind.SPOTIFY, """{"status":101,"spotifyError":0,"brandDisplayName":"Fixture Audio","modelDisplayName":"Fixture Speaker","deviceType":"SPEAKER","deviceID":"discard","publicKey":"discard"}""".toByteArray())!!
            applyReport.invoke(op, audio, info, "Spotify Connect device information (reported)")
            check(audio.model == "Fixture Speaker" && audio.manufacturer == "Fixture Audio" && audio.mac.isEmpty(), "HTTP report fields reach identity without inventing a MAC")
            check(audio.observations().all { it.source.contains("Spotify Connect") } && !audio.json().toString().contains("discard"), "HTTP provenance is retained and account/key/identifier fields are discarded")
            val health = get(op, "health") as DiscoveryHealth
            val source = LocalMetadata.Kind.SPOTIFY.source
            MetadataHttp.recordFailure(health, source, MetadataHttp.Failure(MetadataHttp.Reason.HTTP_STATUS, 401), false)
            MetadataHttp.recordFailure(health, source, MetadataHttp.Failure(MetadataHttp.Reason.READ_TIMEOUT), false)
            check(op.sources()[source]?.let { it.contains("HTTP response error (401)") && it.contains("HTTP errors 1") && it.contains("read timeouts 1") } == true,
                "Android scan sources retain precise fixed HTTP/read diagnostics")
            val traffic = MdnsTraffic(health, false)
            val query = MdnsPacket.query(listOf("_http._tcp.local"))
            check(traffic.receive(query, query.size, 5353, true) == null, "Diagnostic query counters cannot add presence evidence")
            check(op.sources()[MdnsTraffic.SOURCE]?.let { it.contains("packets received 1") && it.contains("non-response packets 1") } == true,
                "Android scan sources expose aggregate receive categories")
            op.cancel(); gate.countDown()
            check(work.awaitTermination(3, TimeUnit.SECONDS), "Cancellation drains metadata workers")
            check(d.metadataLookups.snapshot().all { it.state == MetadataLookups.State.STOPPED }, "Stop finalizes even queued work that never enters a worker")
            val retained = op.snapshot().single { it.ip == d.ip }
            d.metadataLookups.success(LocalMetadata.Kind.SPOTIFY, info)
            check(retained.metadataLookups.snapshot().all { it.state == MetadataLookups.State.STOPPED }, "Retained endpoint diagnostics cannot be rewritten by a late response")
            check((get(op, "metadataTimeouts") as ScheduledExecutorService).isShutdown, "Cancellation shuts down the deadline timer")
            request.invoke(op, Device().apply { ip = "198.18.0.5" }, service("_shelly._tcp."))
            check(plans.size() == 4, "Cancelled scans admit no later metadata work")
            check(op.sources().values.none { it.contains("requests 1") }, "Cancelled queued fixtures opened no network requests")
        } finally { op.cancel(); gate.countDown() }
        val next = operation(test)
        try {
            check((get(next, "metadataRequests") as ServiceAdmission).size() == 0 && next.snapshot().isEmpty(), "A new scan inherits neither metadata requests nor device results")
            check(next.sources().values.none { it.contains("HTTP errors 1") || it.contains("packets received 1") }, "A new Android scan inherits no diagnostic counters")
        } finally { next.cancel() }
        endpointOutcomes(check)
        limits(test, check)
    }

    private fun endpointOutcomes(check: (Boolean, String) -> Unit) {
        val request = LocalMetadata.Request(LocalMetadata.Kind.SPOTIFY, 8080, "/fixture")
        val a = Device().apply { ip = "192.0.2.8"; link("fixture:dual") }
        val b = Device().apply { ip = "2001:db8::8"; link("fixture:dual") }
        a.metadataLookups.queue(request)
        a.metadataLookups.success(request.kind, LocalMetadata.Report(mapOf("Model" to "Fixture speaker")))
        a.hint("Model", "Fixture speaker", request.kind.source + " (reported)", 80)
        b.metadataLookups.queue(request)
        b.metadataLookups.failure(request.kind, MetadataHttp.Failure(MetadataHttp.Reason.CONNECTION_REFUSED))
        val group = Device.grouped(listOf(b, a)).single()
        check(group.endpoints().map { it.ip } == listOf(a.ip, b.ip), "Grouped lookup endpoints preserve IPv4 before IPv6")
        val v4 = group.endpoints()[0].metadataLookupRows().single()
        val v6 = group.endpoints()[1].metadataLookupRows().single()
        check(v4.first.contains("IPv4") && v4.second.startsWith("Succeeded") && v6.first.contains("IPv6") && v6.second == "Connection refused",
            "IPv4 success and IPv6 refusal stay separate without borrowing the group's model")
        b.hint("Model", "Unknown", "mDNS fixture", 80)
        check(!b.metadataLookupRows().single().second.contains("available from"), "Generic alternative metadata cannot be presented as a model fallback")
        b.hint("Model", "Fixture audio", "UPnP description (reported)", 80)
        b.hint("Manufacturer", "Fixture Devices", "mDNS _fixture._tcp. (advertised)", 80)
        val alternative = b.metadataLookupRows().single().second
        check(alternative.contains("Model available from UPnP description (reported)") && alternative.contains("Manufacturer available from mDNS _fixture._tcp. (advertised)"),
            "A failed lookup exposes current same-endpoint model/manufacturer alternatives with their sources")
        val snapshot = b.copy()
        b.hint("Model", "Fixture newer", "Fixture newer source", 100)
        check(snapshot.metadataLookupRows().single().second == alternative, "Device progress copies retain an independent set of outcome and identity observations")
        check(Device().metadataLookupRows().isEmpty(), "Fresh device details have no prior outcomes or alternative-source claims")
        fun classify(errno: Int, reason: MetadataHttp.Reason = MetadataHttp.Reason.CONNECTION_FAILED, expired: Boolean = false) =
            AndroidMetadataFailure.classify(MetadataHttp.Failure(reason, cause = java.net.ConnectException("fixture-secret").apply {
                initCause(ErrnoException("fixture-secret", errno))
            }), expired)
        check(classify(OsConstants.ECONNREFUSED).reason == MetadataHttp.Reason.CONNECTION_REFUSED, "Android's typed refusal reaches per-endpoint diagnostics")
        check(classify(OsConstants.ENETUNREACH).reason == MetadataHttp.Reason.ROUTING_FAILED && classify(OsConstants.EHOSTUNREACH).reason == MetadataHttp.Reason.ROUTING_FAILED,
            "Android's typed network/host routing errors stay distinct")
        check(classify(OsConstants.EACCES).reason == MetadataHttp.Reason.CONNECTION_FAILED, "Permission denial is not mislabeled as refusal or routing")
        check(classify(OsConstants.ECONNREFUSED, MetadataHttp.Reason.READ_FAILED).reason == MetadataHttp.Reason.READ_FAILED, "A response-phase failure is never relabeled as a connection failure")
        check(classify(OsConstants.ECONNREFUSED, expired = true).reason == MetadataHttp.Reason.DEADLINE, "Scan deadline takes precedence over a closing socket's errno")
        check(AndroidMetadataFailure.classify(MetadataHttp.Failure(MetadataHttp.Reason.CONNECTION_FAILED, cause = java.net.ConnectException("ECONNREFUSED")), false).reason == MetadataHttp.Reason.CONNECTION_FAILED,
            "Exception message strings never manufacture typed errno evidence")
        check(!classify(OsConstants.ECONNREFUSED).message.orEmpty().contains("fixture-secret"), "Only fixed failure text reaches the UI")
    }

    private fun limits(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val op = operation(test)
        val work = get(op, "enrichment") as DiscoveryWork
        val gate = CountDownLatch(1); val started = CountDownLatch(2)
        repeat(2) { work.submit(DiscoveryWork.Lane.METADATA) { started.countDown(); try { gate.await() } catch (_: InterruptedException) {} } }
        val request = Discovery::class.java.getDeclaredMethod("requestMetadata", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        val fresh = get(op, "freshResponders") as MutableSet<String>
        val found = get(op, "found") as MutableMap<String, Device>
        fun queue(index: Int, type: String): Device = Device().apply {
            ip = "198.18.0.$index"; found[ip] = this; fresh.add(ip); request.invoke(op, this, service(type, "CPath" to "/fixture"))
        }
        try {
            check(started.await(3, TimeUnit.SECONDS), "Synthetic queue-limit fixture owns both metadata workers")
            repeat(32) { queue(it + 2, "_wled._tcp.") }
            check(queue(34, "_wled._tcp.").metadataLookups.snapshot().single().state == MetadataLookups.State.WORK_LIMIT,
                "Per-protocol admission exhaustion is visible on the affected endpoint")
            check(queue(35, "_spotify-connect._tcp.").metadataLookups.snapshot().single().state == MetadataLookups.State.WORK_LIMIT,
                "A full worker queue is an unattempted work limit, not a device connection failure")
            op.cancel(); gate.countDown()
            check(work.awaitTermination(3, TimeUnit.SECONDS) && op.sources().values.none { it.contains("requests 1") }, "Limit diagnostics open no LAN socket and cancellation drains pending workers")
        } finally { op.cancel(); gate.countDown() }
    }
}
