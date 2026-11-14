import io.github.kazands.networklens.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object NativeServiceEvidenceTest {
    private var checks = 0
    private val type = "_fixture._tcp."
    private val instance = listOf("Room.fixture", "_fixture", "_tcp", "local")
    private val host = listOf("fixture-host", "local")
    private val peer = InetAddress.getByName("192.0.2.21")
    private val start = TimeUnit.SECONDS.toNanos(20)
    private fun check(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
    private fun name(labels: List<String>) = ByteArrayOutputStream().apply {
        labels.forEach { val b = it.toByteArray(Charsets.UTF_8); write(b.size); write(b) }; write(0)
    }.toByteArray()
    private fun rr(owner: List<String>, kind: Int, payload: ByteArray, ttl: Int = 120) = ByteArrayOutputStream().apply {
        val out = DataOutputStream(this); out.write(name(owner)); out.writeShort(kind); out.writeShort(1)
        out.writeInt(ttl); out.writeShort(payload.size); out.write(payload)
    }.toByteArray()
    private fun packet(vararg records: ByteArray): MdnsPacket.Result {
        val bytes = ByteArrayOutputStream().apply {
            val out = DataOutputStream(this); out.writeShort(0); out.writeShort(0x8400)
            out.writeShort(0); out.writeShort(records.size); out.writeInt(0); records.forEach { write(it) }
        }.toByteArray()
        return MdnsPacket.parse(bytes, bytes.size)
    }
    private fun parts(labels: List<String> = instance, address: InetAddress = peer): List<ByteArray> = listOf(
        rr(labels.drop(1), 12, name(labels)),
        rr(labels, 33, ByteArrayOutputStream().apply { val out = DataOutputStream(this); out.writeInt(0); out.writeShort(8080); write(name(host)) }.toByteArray()),
        rr(labels, 16, byteArrayOf(0)),
        rr(host, if (address.address.size == 4) 1 else 28, address.address)
    )
    @JvmStatic fun main(args: Array<String>) {
        val assembly = MdnsAssembly()
        val tracker = NativeServiceEvidence(assembly)
        check(assembly.wireEvidence(instance[0], type, start) == MdnsAssembly.WireEvidence.NOT_OBSERVED, "No captured matching records is not a stale-cache verdict")
        val query = MdnsPacket.query(listOf("_fixture._tcp.local"))
        assembly.accept(peer, MdnsPacket.parse(query, query.size), start)
        check(assembly.wireEvidence(instance[0], type, start) == MdnsAssembly.WireEvidence.NOT_OBSERVED, "Queries cannot supply wire-reply correlation")
        check(tracker.failed(instance[0], type, start) == DiscoveryHealth.Diagnostic.NSD_WIRE_NOT_OBSERVED, "Final native failure records its failure-time evidence")
        check(tracker.failed("ROOM.fixture", type, start) == null, "ASCII DNS case variants do not double-count a native failure")
        val p = parts()
        assembly.accept(peer, packet(p[0]), start + 1)
        check(assembly.wireEvidence(instance[0], type, start + 1) == MdnsAssembly.WireEvidence.INCOMPLETE, "PTR alone records current incomplete wire evidence")
        check(assembly.wireEvidence("Room", "_fixture._tcp.", start + 1) == MdnsAssembly.WireEvidence.NOT_OBSERVED, "Dots in an instance remain literal label content")
        assembly.accept(peer, packet(*p.drop(1).toTypedArray()), start + 2)
        check(assembly.wireEvidence(instance[0], type, start + 2) == MdnsAssembly.WireEvidence.COMPLETE, "Only a current same-sender complete graph qualifies as complete")
        val later = tracker.finish(start + 3).getValue(type)
        check(later.observed == 1 && later.complete == 1, "Cleanup detects replies and completion arriving after native failure")
        check(tracker.finish(start + 4).isEmpty() && tracker.failed("Another", type, start + 4) == null, "Cleanup is idempotent and rejects late failure writes")
        check(assembly.wireEvidence(instance[0], type, start + TimeUnit.SECONDS.toNanos(11)) == MdnsAssembly.WireEvidence.HISTORY_ONLY, "Expired records are seen history, never current metadata")
        check(assembly.ready(start + TimeUnit.SECONDS.toNanos(11)).isEmpty(), "Diagnostic history does not revive expired metadata")
        val expiredTracker = NativeServiceEvidence(assembly)
        check(expiredTracker.failed(instance[0], type, start + TimeUnit.SECONDS.toNanos(11)) == DiscoveryHealth.Diagnostic.NSD_WIRE_HISTORY, "Failure after expiry has a distinct finite category")
        check(expiredTracker.finish(start + TimeUnit.SECONDS.toNanos(12)).isEmpty(), "Earlier observations are not incorrectly counted as late replies")
        val delayedAssembly = MdnsAssembly()
        val delayedTracker = NativeServiceEvidence(delayedAssembly)
        delayedTracker.failed(instance[0], type, start)
        delayedAssembly.accept(peer, packet(*p.toTypedArray()), start + TimeUnit.SECONDS.toNanos(1))
        val expiredLate = delayedTracker.finish(start + TimeUnit.SECONDS.toNanos(12)).getValue(type)
        check(expiredLate.observed == 1 && expiredLate.complete == 1,
            "A complete graph arriving after failure remains counted after its identity records expire")
        val split = MdnsAssembly()
        split.accept(peer, packet(*p.take(3).toTypedArray()), start)
        split.accept(InetAddress.getByName("192.0.2.22"), packet(p[3]), start)
        check(split.wireEvidence(instance[0], type, start) == MdnsAssembly.WireEvidence.INCOMPLETE, "Fragments across senders cannot become a complete diagnostic graph")
        val proxy = MdnsAssembly()
        proxy.accept(InetAddress.getByName("192.0.2.22"), packet(*p.toTypedArray()), start)
        check(proxy.wireEvidence(instance[0], type, start) == MdnsAssembly.WireEvidence.INCOMPLETE, "Proxy-only address association remains incomplete")
        check(NativeServiceEvidence(proxy).failed(instance[0], type, start) == DiscoveryHealth.Diagnostic.NSD_WIRE_INCOMPLETE, "Incomplete current replies get their own native-failure counter")
        val complete = MdnsAssembly()
        complete.accept(peer, packet(*p.toTypedArray()), start)
        check(NativeServiceEvidence(complete).failed(instance[0], type, start) == DiscoveryHealth.Diagnostic.NSD_WIRE_COMPLETE, "Native failure despite a complete current graph remains observable")
        val goodbye = MdnsAssembly()
        goodbye.accept(peer, packet(rr(instance.drop(1), 12, name(instance), 0)), start)
        check(goodbye.wireEvidence(instance[0], type, start) == MdnsAssembly.WireEvidence.HISTORY_ONLY, "A goodbye is captured evidence without current presence authority")
        check(goodbye.ready(start).isEmpty(), "A goodbye-only observation cannot produce a service")
        check(assembly.wireEvidence("é".repeat(32), type, start) == MdnsAssembly.WireEvidence.UNAVAILABLE, "Native names outside DNS byte bounds are not silently classed as absent")
        check(assembly.wireEvidence("x".repeat(100000), type, start) == MdnsAssembly.WireEvidence.UNAVAILABLE, "Overlong native names are rejected before encoding")
        check(assembly.wireEvidence("Bad\u0000name", type, start) == MdnsAssembly.WireEvidence.UNAVAILABLE, "Control characters cannot form correlation keys")
        check(assembly.wireEvidence(instance[0], "_fixture._tcp.invalid", start) == MdnsAssembly.WireEvidence.UNAVAILABLE, "Nonlocal/native-invalid types remain uncorrelatable")
        val limited = MdnsAssembly()
        for (i in 0..256) {
            val labels = listOf("Fixture-$i", "_fixture", "_tcp", "local")
            limited.accept(InetAddress.getByName("192.0.2.${1 + i / 16}"), packet(rr(labels.drop(1), 12, name(labels))), start)
        }
        check(!limited.limited, "Diagnostic capacity does not exhaust discovery's existing record budgets")
        check(limited.wireEvidence("Unknown", type, start) == MdnsAssembly.WireEvidence.UNAVAILABLE, "Full history cannot manufacture a no-observation conclusion")
        check(limited.wireEvidence("Fixture-256", type, start) == MdnsAssembly.WireEvidence.INCOMPLETE, "Current records remain visible even beyond diagnostic history capacity")
        check(limited.wireEvidence("Fixture-256", type, start + TimeUnit.SECONDS.toNanos(11)) == MdnsAssembly.WireEvidence.UNAVAILABLE, "Untracked expired observations stay explicitly unavailable")
        val many = NativeServiceEvidence(MdnsAssembly())
        repeat(256) { check(many.failed("Native-$it", type, start) == DiscoveryHealth.Diagnostic.NSD_WIRE_NOT_OBSERVED, "Bounded native-failure slot admitted") }
        check(many.failed("Native-overflow", type, start) == DiscoveryHealth.Diagnostic.NSD_WIRE_UNAVAILABLE, "Native failure history remains capped at 256")
        assembly.close()
        check(assembly.wireEvidence(instance[0], type, start) == MdnsAssembly.WireEvidence.UNAVAILABLE, "Closed scans cannot leak observations into a new report")
        check(MdnsAssembly().wireEvidence(instance[0], type, start) == MdnsAssembly.WireEvidence.NOT_OBSERVED, "A fresh scan inherits no diagnostic history")
        val health = DiscoveryHealth()
        health.resolving("synthetic-key", type); health.resolveFailed("synthetic-key", "No usable service data")
        health.diagnostic("mDNS $type", DiscoveryHealth.Diagnostic.NSD_WIRE_NOT_OBSERVED)
        val output = health.sources().getValue("mDNS $type")
        check(output.startsWith("Failed") && output.contains("wire not observed 1"), "Correlation cannot turn a failed native source into success")
        check(!output.contains(instance[0]) && !output.contains(peer.hostAddress!!), "Only fixed labels and counts reach diagnostics")
        println("PASS · $checks native-service/wire correlation checks")
    }
}
