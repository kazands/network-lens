import io.github.kazands.networklens.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress

object KiddeInfoTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; check(ok) { why } }
    private const val NAME = "Kidde-WD-000000000001"
    private fun service(name: String = NAME, type: String = KiddeInfo.TYPE) = MdnsPacket.Service(name, type).apply {
        host = "$NAME.local"; port = 5353; attributes["md"] = "WD".toByteArray()
    }
    private fun evidence() = DeviceCatalog.Evidence().apply {
        offer("Model", "WD", "Synthetic current TXT"); offerKidde("Synthetic current service")
    }
    private fun bytes(write: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { out -> DataOutputStream(out).use(write) }.toByteArray()
    private fun name(value: String) = bytes { value.split('.').forEach { writeByte(it.length); writeBytes(it) }; writeByte(0) }
    private fun record(owner: String, type: Int, data: ByteArray, ttl: Int = 120) = bytes {
        write(name(owner)); writeShort(type); writeShort(1); writeInt(ttl); writeShort(data.size); write(data)
    }
    private fun parse(records: List<ByteArray>): MdnsPacket.Result {
        val packet = bytes { writeShort(0); writeShort(0x8400); writeShort(0); writeShort(records.size); writeInt(0); records.forEach { write(it) } }
        return MdnsPacket.parse(packet, packet.size)
    }
    @JvmStatic fun main(args: Array<String>) {
        check(KiddeInfo.matches(service()), "Complete WD fingerprint matches")
        check(KiddeInfo.matches(service(NAME.lowercase()).apply { host = "$NAME.LOCAL."; attributes.clear(); attributes["MD"] = "wd".toByteArray(); port = 1234 }), "DNS case, trailing dot and alternate valid port")
        for (type in listOf("_http._tcp.", "_hap._udp.", "_googlecast._tcp.", ""))
            check(!KiddeInfo.matches(service(type = type)), "Other services cannot create identity")
        for (host in listOf("$NAME.local.evil", "$NAME.example", NAME, "Kidde-WD-000000000002.local", "x".repeat(512)))
            check(!KiddeInfo.matches(service().apply { this.host = host }), "Exact matching local target required")
        for (value in listOf("WD", "Kidde", "Kidde-WD-0001", "$NAME extra", "Other-WD-000000000001", "x".repeat(1024)))
            check(!KiddeInfo.matches(service(name = value).apply { host = "$value.local" }), "Name and model alone cannot identify unrelated devices")
        for (model in listOf("", "WD ", "WD\u0000", "WD-ESP32", "P4010ACSCO-WF", "x".repeat(1024)))
            check(!KiddeInfo.matches(service().apply { attributes["md"] = model.toByteArray() }), "Only reviewed model token matches")
        check(!KiddeInfo.matches(service().apply { attributes.clear() }), "Missing TXT is not identification")
        check(!KiddeInfo.matches(service().apply { attributes["MD"] = "TV".toByteArray() }), "Conflicting TXT case variants rejected")
        check(!KiddeInfo.matches(service().apply { attributes["md"] = byteArrayOf(-1, 68) }), "Invalid bytes rejected")
        for (port in listOf(-1, 0, 65536)) check(!KiddeInfo.matches(service().apply { this.port = port }), "Invalid ports rejected")
        check(DeviceCatalog.lookup("WD") == null && DeviceCatalog.lookup("WD", "Kidde") == null, "Short model token is not a global alias")
        val match = evidence().match()!!
        check(match.product.familyOnly && match.product.category == DeviceCatalog.Category.SENSOR, "Family and sensor classification only")
        check(RecognitionCopy.explanation(match).contains("exact model and sensor capabilities were not reported"), "Honest recognition explanation")
        for (value in listOf(NAME, "$NAME.local", "$NAME.local.")) {
            val label = DevicePresentation.labels(value, value, "WD", "", match.product, false, false)
            check(label.title == "Kidde Smart Detector" && label.subtitle == "Safety detector", "Technical name hidden only after recognition")
        }
        check(DevicePresentation.labels("Fixture hallway", "", "WD", "", match.product, false, false).subtitle == "Safety detector · Fixture hallway", "Friendly room name retained")
        for (first in listOf(true, false)) for ((field, value) in listOf("Manufacturer" to "Apple", "Model" to "Google Nest Mini", "Model" to "iPhone14,5")) {
            val e = DeviceCatalog.Evidence()
            if (first) e.offerKidde("synthetic")
            e.offer(field, value, "synthetic")
            if (!first) e.offerKidde("synthetic")
            check(e.match() == null && e.categories().isEmpty() && e.hasRecordedConflict(), "Conflicts suppress recognition in either order")
            check(e.copy().hasKiddeIdentity() && e.copy().match() == null, "Conflict and constraint survive copy")
        }
        for (reverse in listOf(false, true)) {
            val parts = listOf(evidence(), DeviceCatalog.Evidence().apply { offer("Model", "Google Nest Mini", "synthetic") })
            val merged = DeviceCatalog.Evidence().apply { (if (reverse) parts.reversed() else parts).forEach(::include) }
            check(merged.hasKiddeIdentity() && merged.match() == null && merged.categories().isEmpty(), "Group conflicts cannot restore another identity")
        }
        check(evidence().apply { offer("Manufacturer", "Kidde", "synthetic") }.match() != null, "Reported Kidde manufacturer compatible")
        // Exercise the actual packet decoder and same-sender/TTL admission, not just a constructed service.
        val peer = InetAddress.getByName("192.0.2.40")
        val type = "_hap._tcp.local"; val owner = "$NAME.$type"; val host = "$NAME.local"
        val records = listOf(record(type, 12, name(owner)), record(owner, 33, bytes { writeInt(0); writeShort(5353); write(name(host)) }),
            record(owner, 16, byteArrayOf(5) + "md=WD".toByteArray()), record(host, 1, peer.address))
        fun admitted(records: List<ByteArray>, sender: InetAddress = peer): List<MdnsPacket.Service> {
            val a = MdnsAssembly(); val now = 1_000_000_000L
            return a.accept(sender, parse(records), now) + a.ready(now + 1_000_000_000L).values.flatten()
        }
        check(admitted(records).any(KiddeInfo::matches), "Current complete wire response can identify Kidde")
        for (fields in listOf(listOf("md=WD", "MD=TV"), listOf("MD=TV", "md=WD"), listOf("md=WD", "MD=WD"))) {
            val txt = bytes { fields.forEach { writeByte(it.length); writeBytes(it) } }
            val duplicate = records.toMutableList().apply { this[2] = record(owner, 16, txt) }
            val services = admitted(duplicate)
            check(services.isNotEmpty() && services.none(KiddeInfo::matches), "Raw duplicate model keys rejected in either order")
            check(services.all { it.copy().duplicateModelKey }, "Duplicate metadata survives service copy and assembly")
            check(parse(duplicate).services.values.none(KiddeInfo::matches), "Legacy direct decoder also preserves duplicate marker")
        }
        for (missing in records.indices) check(admitted(records.filterIndexed { i, _ -> i != missing }).none(KiddeInfo::matches), "Partial response cannot identify Kidde")
        check(admitted(records, InetAddress.getByName("192.0.2.41")).none(KiddeInfo::matches), "Proxy cannot claim another endpoint")
        check(admitted(listOf(record(type, 12, name(owner), 0)) + records.drop(1)).none(KiddeInfo::matches), "Goodbye record cannot identify Kidde")
        val assembly = MdnsAssembly()
        assembly.accept(peer, parse(records.dropLast(1)), 1_000_000_000L)
        check(assembly.accept(peer, parse(records.takeLast(1)), 12_000_000_000L).none(KiddeInfo::matches), "Expired partial response cannot identify Kidde")
        val diagnostic = DiagnosticEvidence().apply { txt("synthetic", mapOf("md" to "WD".toByteArray(), "sn" to "synthetic-secret-serial".toByteArray())) }
        check(diagnostic.snapshot().none { it.value.contains("synthetic-secret-serial") }, "Serial value stays withheld")
        println("KiddeInfoTest: $checks checks passed")
    }
}
