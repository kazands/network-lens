package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*
import java.util.concurrent.*

/** No Android Network and an injected transport: these fixtures cannot open LAN sockets. */
internal object RecognitionPlanningChecks {
    private fun get(o: Any, key: String) = o.javaClass.getDeclaredField(key).apply { isAccessible = true }.get(o)
    private fun set(o: Any, key: String, value: Any) = o.javaClass.getDeclaredField(key).apply { isAccessible = true }.set(o, value)
    @Suppress("UNCHECKED_CAST")
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val gate = CountDownLatch(1); val started = CountDownLatch(1); val recovered = CountDownLatch(1)
        val requests = java.util.Collections.synchronizedList(ArrayList<LocalMetadata.Request>())
        val deadlines = java.util.Collections.synchronizedList(ArrayList<Long>())
        val net = NetworkState().apply { ip = "198.18.0.1"; prefix = 24; key = "synthetic-planning"; prefixes[ip] = 24; addresses.add(ip) }
        val op = Discovery(test.targetContext, net, Discovery.EchoFactory { _, _ -> error("No echo fixture") },
            Discovery.NetworkCheck { _, _ -> }, Discovery.MetadataReader { _, request, deadline, active ->
                requests.add(request); deadlines.add(deadline)
                if (requests.size == 1) {
                    started.countDown(); gate.await(2, TimeUnit.SECONDS)
                    if (!active()) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
                    throw MetadataHttp.Failure(MetadataHttp.Reason.HTTP_STATUS, 404)
                }
                recovered.countDown(); LocalMetadata.Report(mapOf("Model" to "Synthetic receiver", "Manufacturer" to "Synthetic Audio"))
            })
        set(op, "accepting", true); set(op, "enriching", true)
        val peer = Device().apply { ip = "198.18.0.2" }
        (get(op, "found") as MutableMap<String, Device>)[peer.ip] = peer
        (get(op, "freshResponders") as MutableSet<String>).add(peer.ip)
        val request = Discovery::class.java.getDeclaredMethod("requestMetadata", Device::class.java, LocalMetadata.Request::class.java).apply { isAccessible = true }
        val first = LocalMetadata.Request(LocalMetadata.Kind.SPOTIFY, 8080, "/first")
        try {
            request.invoke(op, peer, first)
            check(started.await(2, TimeUnit.SECONDS), "Initial validated metadata target runs")
            request.invoke(op, peer, first.copy(port = 8081, path = "/alternate"))
            gate.countDown(); check(recovered.await(2, TimeUnit.SECONDS), "Failure tries the independently advertised alternate target")
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while ((peer.metadataLookups.snapshot().single().state == MetadataLookups.State.QUEUED || op.sources()[first.kind.source]?.startsWith("Running") == true) && System.nanoTime() < end) Thread.sleep(10)
            check(peer.model == "Synthetic receiver" && peer.manufacturer == "Synthetic Audio", "Recovered same-peer report reaches identity")
            check(requests.map { it.port } == listOf(8080, 8081) && deadlines.distinct().size == 1, "Two candidates share one original deadline")
            check(peer.metadataLookups.snapshot().single().state == MetadataLookups.State.SUCCESS, "Final endpoint diagnostic describes recovery")
            check(peer.observations().any { it.source == LocalMetadata.Kind.SPOTIFY.source + " (reported)" }, "Fallback preserves reported source")
            request.invoke(op, peer, first.copy(port = 8082)); check(requests.size == 2, "Success and advertisement churn cannot grow work")
            val finalize = Discovery::class.java.getDeclaredMethod("finishMetadataLookups", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            finalize.invoke(op, false)
            check(op.sources()[first.kind.source]!!.startsWith("OK"), "Recovered candidate failure does not leave the final source failed")
            op.cancel(); request.invoke(op, peer, first.copy(port = 8083)); check(requests.size == 2, "Stopped scans refuse further targets")
        } finally { gate.countDown(); op.cancel() }
        val expired = Discovery(test.targetContext, net, Discovery.EchoFactory { _, _ -> error("No echo") },
            Discovery.NetworkCheck { _, _ -> }, Discovery.MetadataReader { _, _, _, _ -> error("Expired metadata cannot open transport") })
        try {
            set(expired, "accepting", true); set(expired, "enriching", true)
            set(expired, "scanDeadline", System.nanoTime() - 1)
            val d = Device().apply { ip = "198.18.0.3" }
            (get(expired, "found") as MutableMap<String, Device>)[d.ip] = d
            (get(expired, "freshResponders") as MutableSet<String>).add(d.ip)
            request.invoke(expired, d, first)
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (d.metadataLookups.snapshot().single().state == MetadataLookups.State.QUEUED && System.nanoTime() < end) Thread.sleep(10)
            val finalize = Discovery::class.java.getDeclaredMethod("finishMetadataLookups", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            finalize.invoke(expired, false)
            check(d.metadataLookups.snapshot().single().state == MetadataLookups.State.INCOMPLETE &&
                expired.sources()[first.kind.source]!!.startsWith("Truncated"),
                "Queued metadata that expires before execution cannot report a complete source")
        } finally { expired.cancel() }
    }
}
