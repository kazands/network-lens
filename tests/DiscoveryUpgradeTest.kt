@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*

/** Tests the network-input and identity defects established in the discovery investigation. */
object DiscoveryUpgradeTest {
    private var checks: Int = 0

    private fun check(ok: Boolean, message: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(message)
        }
    }

    private fun name(out: DataOutputStream, name: String): Unit {
        for (label in java.util.regex.Pattern.compile("\\.").split(name, 0)) {
            var b: ByteArray = label.toByteArray(StandardCharsets.UTF_8)
            out.writeByte(b.size)
            out.write(b)
        }
        out.writeByte(0)
    }

    private fun reply(owner: String, target: String, ttl: Long, cls: Int): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var out: DataOutputStream = DataOutputStream(b)
        out.writeShort(0)
        out.writeShort(33792)
        out.writeShort(0)
        out.writeShort(1)
        out.writeInt(0)
        name(out, owner)
        out.writeShort(12)
        out.writeShort(cls)
        out.writeInt((ttl).toInt())
        var r: ByteArrayOutputStream = ByteArrayOutputStream()
        name(DataOutputStream(r), target)
        out.writeShort(r.size())
        out.write(r.toByteArray())
        return b.toByteArray()
    }

    private fun empty(r: MdnsPacket.Result): Boolean {
        return (r.serviceTypes.isEmpty() && r.hostnames.isEmpty())
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        run {
            var order: Int = 0
            while ((order < 2)) {
                var hints: IdentityHints = IdentityHints()
                if ((order == 0)) {
                    hints.offer("Name", "Living room TV", "UPnP", 80)
                }
                hints.offer("Name", "Android", "mDNS friendly", 90)
                if ((order == 1)) {
                    hints.offer("Name", "Living room TV", "UPnP", 80)
                }
                check(
                    (hints.offer("Name", "Device", "fallback", 99))!!
                        .value
                        .equals("Living room TV"),
                    "Specific name beats higher-priority generic values in both orders",
                )
                hints.offer("Model", "Useful Model 500", "UPnP", 70)
                check(
                    (hints.offer("Model", "Unknown", "TXT", 80))!!.value.equals("Useful Model 500"),
                    "Unknown model cannot replace useful metadata",
                )
                check(
                    ((hints.observations())!!.stream())!!.anyMatch(
                        callback1@{ h -> h.value.equals("Unknown") }
                    ),
                    "Conflicting generic evidence remains available",
                )
                order++
            }
        }
        for (generic in
            arrayOf(
                "192.168.1.2",
                "fd00::1",
                "00:11:22:33:44:55",
                "001122334455",
                "android_aabbccddeeff",
                "ESP_3A2F1C",
                "n/a",
                "unknown model",
            )) {
            var h: IdentityHints = IdentityHints()
            h.offer("Name", generic, "strong source", 95)
            check(
                (h.offer("Name", "Office printer", "useful source", 30))!!
                    .value
                    .equals("Office printer"),
                ("Raw addresses/generated names are fallbacks: " + generic),
            )
        }
        var h: IdentityHints = IdentityHints()
        check(
            (h.offer("Name", "Android", "only source", 90))!!.value.equals("Android"),
            "Generic fallback is retained when nothing else exists",
        )
        h.offer("Model", "Explicit model", "TXT", 80)
        check(
            (h.offer("Model", "Compatibility string", "device-info", 20))!!
                .value
                .equals("Explicit model"),
            "Source rank still selects between specific values",
        )
        var own: IdentityHints = IdentityHints()
        own.offer("Model", "Unknown", "This phone", 100)
        check(
            (own.offer("Model", "External claim", "network", 80))!!.value.equals("Unknown"),
            "This phone platform identity stays authoritative",
        )
        var budget: ServiceAdmission = ServiceAdmission(4, 2)
        check(
            (budget.reserve("a", "http") && budget.reserve("b", "http")),
            "First type receives its fair share",
        )
        check(
            (budget.admit("c", "http") === ServiceAdmission.Result.LIMIT),
            "Chatty type is capped",
        )
        check(
            (budget.reserve("printer", "ipp") && budget.reserve("audio", "raop")),
            "Other types retain capacity",
        )
        check(
            ((budget.admit("a", "http") === ServiceAdmission.Result.DUPLICATE) &&
                (budget.size() == 4)),
            "Duplicates consume no additional capacity",
        )
        check(
            (budget.admit("fifth", "ssh") === ServiceAdmission.Result.LIMIT),
            "Total safety cap remains",
        )
        var health: DiscoveryHealth = DiscoveryHealth()
        health.starting("http")
        health.started("http")
        health.resolving("slow", "http")
        health.truncated("mDNS http", "Per-type limit reached")
        health.finish(false)
        var state: String? = (health.sources())!!.get("mDNS http")
        check(
            ((state)!!.contains("Per-type limit") && (state)!!.contains("did not finish")),
            "Cleanup preserves earlier limit reason",
        )
        health.ok("mDNS http")
        check(!((health.summary())!!.isEmpty()), "Success cannot erase a limit")
        health.fail("mDNS http", "Permission denied")
        check(
            ((health.sources())!!.get("mDNS http"))!!.startsWith("Failed"),
            "Failure remains stronger than truncation",
        )
        health.count("mDNS http", "found")
        health.count("mDNS http", "found")
        check(
            ((health.sources())!!.get("mDNS http"))!!.contains("found 2"),
            "Private source counters are retained",
        )
        var descriptions: DescriptionQueue = DescriptionQueue(1, 3)
        var first: URI = URI("http://192.168.1.2/bad.xml")
        var second: URI = URI("http://192.168.1.2/good.xml")
        var third: URI = URI("http://192.168.1.2/other.xml")
        check(
            (descriptions.offer("peer", first) === DescriptionQueue.Offer.START),
            "First description schedules a worker",
        )
        check(
            (descriptions.next("peer"))!!.equals(first),
            "Worker receives first eligible location",
        )
        descriptions.record("peer", false)
        check(
            (descriptions.offer("peer", second) === DescriptionQueue.Offer.QUEUED),
            "Failure does not exclude an alternative",
        )
        check(
            (descriptions.next("peer"))!!.equals(second),
            "Alternative is serialized behind first request",
        )
        descriptions.record("peer", true)
        check(
            ((descriptions.next("peer") === null) && (descriptions.failedPeers() == 0)),
            "Fallback success clears final peer failure",
        )
        check(
            (descriptions.offer("peer", second) === DescriptionQueue.Offer.DUPLICATE),
            "Duplicate descriptions are not fetched again",
        )
        check(
            (descriptions.offer("peer", third) === DescriptionQueue.Offer.START),
            "A later new opportunity restarts a drained worker",
        )
        check(
            ((descriptions.offer("peer", URI("http://192.168.1.2/four.xml")) ===
                DescriptionQueue.Offer.LIMIT) &&
                (descriptions.offer("other", first) === DescriptionQueue.Offer.LIMIT)),
            "Per-peer and total peer limits remain bounded",
        )
        descriptions.next("peer")
        descriptions.record("peer", false)
        descriptions.next("peer")
        check(
            (descriptions.failedPeers() == 0),
            "A later failed alternative cannot hide earlier valid metadata",
        )
        check(
            (MdnsPacket.serviceType("_MieleAtHome._TCP.local."))!!.equals("_mieleathome._tcp."),
            "Normalize dynamically advertised service type",
        )
        for (invalid in
            arrayOf(
                "http._tcp.local",
                "_http._tcp.evil.com",
                "_http._tcp.local.evil",
                "_x._sub._http._tcp.local",
                "_http._tcp.local\u202e",
                "_http._tcp.local/",
            )) {
            check(
                (MdnsPacket.serviceType(invalid))!!.isEmpty(),
                "Reject unqualified/outside/ambiguous type",
            )
        }
        var packet: ByteArray = reply(MdnsPacket.ENUMERATION, "_mieleathome._tcp.local", 120, 1)
        check(
            (MdnsPacket.parse(packet, packet.size))!!.serviceTypes.contains("_mieleathome._tcp."),
            "Read enumeration PTR response",
        )
        var reverse: ByteArray =
            reply(ReverseDns.question("192.168.1.20"), "office.local", 120, 32769)
        check(
            "office.local"
                .equals(
                    (MdnsPacket.parse(reverse, reverse.size))!!
                        .hostnames
                        .get("20.1.168.192.in-addr.arpa")
                ),
            "Read reverse PTR and mask cache-flush bit",
        )
        var compressedBytes: ByteArrayOutputStream = ByteArrayOutputStream()
        var compressedOut: DataOutputStream = DataOutputStream(compressedBytes)
        compressedOut.write(byteArrayOf(0, 0, (132).toByte(), 0, 0, 1, 0, 1, 0, 0, 0, 0))
        name(compressedOut, MdnsPacket.ENUMERATION)
        compressedOut.writeShort(12)
        compressedOut.writeShort(1)
        compressedOut.writeShort(49164)
        compressedOut.writeShort(12)
        compressedOut.writeShort(1)
        compressedOut.writeInt(120)
        var targetBytes: ByteArrayOutputStream = ByteArrayOutputStream()
        var targetOut: DataOutputStream = DataOutputStream(targetBytes)
        name(targetOut, "_mieleathome._tcp.local")
        compressedOut.writeShort(targetBytes.size())
        compressedOut.write(targetBytes.toByteArray())
        var compressed: ByteArray = compressedBytes.toByteArray()
        check(
            (MdnsPacket.parse(compressed, compressed.size))!!
                .serviceTypes
                .contains("_mieleathome._tcp."),
            "Compressed owner can point back to the question name",
        )
        var mixedBytes: ByteArrayOutputStream = ByteArrayOutputStream()
        var mixedOut: DataOutputStream = DataOutputStream(mixedBytes)
        mixedOut.write(packet)
        name(mixedOut, "Living room._http._tcp.local")
        mixedOut.writeShort(16)
        mixedOut.writeShort(1)
        mixedOut.writeInt(120)
        mixedOut.writeShort(3)
        mixedOut.write(byteArrayOf(2, 120, 121))
        var mixed: ByteArray = mixedBytes.toByteArray()
        mixed[11] = 1
        check(
            (MdnsPacket.parse(mixed, mixed.size))!!.serviceTypes.contains("_mieleathome._tcp."),
            "Unrelated non-hostname additional record does not discard a useful PTR",
        )
        var goodbye: ByteArray = reply(MdnsPacket.ENUMERATION, "_mieleathome._tcp.local", 0, 1)
        check(empty(MdnsPacket.parse(goodbye, goodbye.size)), "Ignore goodbye TTL zero")
        var otherClass: ByteArray = reply(MdnsPacket.ENUMERATION, "_mieleathome._tcp.local", 120, 3)
        check(empty(MdnsPacket.parse(otherClass, otherClass.size)), "Ignore non-IN records")
        var outside: ByteArray =
            reply(ReverseDns.question("192.168.1.20"), "external.example", 120, 1)
        check(empty(MdnsPacket.parse(outside, outside.size)), "Only local reverse names accepted")
        var arbitrary: ByteArray =
            reply("Living room._http._tcp.local", "Other room._http._tcp.local", 120, 1)
        check(
            empty(MdnsPacket.parse(arbitrary, arbitrary.size)),
            "Unrelated instance records do not become hostnames or service types",
        )
        run {
            var i: Int = 0
            while ((i < packet.size)) {
                check(
                    empty(MdnsPacket.parse(packet, i)),
                    "Every truncated enumeration reply rejected",
                )
                i++
            }
        }
        var bad: ByteArray = packet.clone()
        bad[1] = 1
        check(empty(MdnsPacket.parse(bad, bad.size)), "Ignore nonzero mDNS transaction IDs")
        bad = packet.clone()
        bad[2] = ((bad[2]).toInt() or (2).toInt()).toByte()
        check(empty(MdnsPacket.parse(bad, bad.size)), "Reject DNS truncation flag")
        bad = packet.clone()
        bad[12] = (192).toByte()
        bad[13] = 12
        check(empty(MdnsPacket.parse(bad, bad.size)), "Reject cyclic compression pointer")
        bad = packet.clone()
        bad[6] = 127
        check(empty(MdnsPacket.parse(bad, bad.size)), "Bound resource-record counts")
        check(empty(MdnsPacket.parse(ByteArray(8193), 8193)), "Bound packet size")
        var query: ByteArray =
            MdnsPacket.query(
                Arrays.asList(MdnsPacket.ENUMERATION, ReverseDns.question("192.168.1.20"))
            )
        check(
            (((((query[0]).toInt() == (0).toInt()) && ((query[1]).toInt() == (0).toInt())) &&
                ((query[5]).toInt() == (2).toInt())) && (query.size < 1200)),
            "Generate bounded, nonrecursive multicast questions",
        )
        for (invalid in arrayOf("example.com", "evil..in-addr.arpa", "x\r\n.in-addr.arpa")) {
            try {
                MdnsPacket.query(Collections.singletonList(invalid))
                throw AssertionError("Invalid question accepted")
            } catch (expected: IllegalArgumentException) {
                check(true, "Reject unrelated or malformed questions")
            }
        }
        try {
            MdnsPacket.query(Collections.nCopies(9, MdnsPacket.ENUMERATION))
            throw AssertionError("Oversized question batch")
        } catch (expected: IllegalArgumentException) {
            check(true, "Bound question count")
        }
        var random: Random = Random(28)
        run {
            var i: Int = 0
            while ((i < 20000)) {
                var fuzz: ByteArray = ByteArray(random.nextInt(8194))
                random.nextBytes(fuzz)
                MdnsPacket.parse(fuzz, fuzz.size)
                i++
            }
        }
        check(true, "20,000 malformed mDNS packets cannot crash parsing")
        System.out.println(
            (("PASS · " + checks) +
                " discovery-upgrade assertions and 20,000 malformed mDNS inputs")
        )
    }
}
