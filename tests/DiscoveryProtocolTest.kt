@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import dev.netlens.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

/** Wire-format fixtures and malformed input for new discovery paths; no live network. */
object DiscoveryProtocolTest {
    private var checks: Int = 0

    private fun check(ok: Boolean, message: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(message)
        }
    }

    fun response(id: Int): ByteArray {
        var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
        var out: DataOutputStream = DataOutputStream(bytes)
        out.writeShort(id)
        out.writeShort(33792)
        out.writeShort(0)
        out.writeShort(1)
        out.writeInt(0)
        var question: ByteArray = NetBios.query(id)
        out.write(question, 12, 34)
        out.writeShort(33)
        out.writeShort(1)
        out.writeInt(0)
        out.writeShort(((1 + 18) + 46))
        out.writeByte(1)
        var name: ByteArray = ByteArray(15)
        Arrays.fill(name, (' ').toByte())
        System.arraycopy("OFFICE-PC".toByteArray(StandardCharsets.US_ASCII), 0, name, 0, 9)
        out.write(name)
        out.writeByte(0)
        out.writeShort(1024)
        out.write(byteArrayOf(0, 17, 34, 51, 68, 85))
        out.write(ByteArray(40))
        return bytes.toByteArray()
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        check(
            (Address.mac("00-11-22-aa-bb-cc"))!!.equals("00:11:22:AA:BB:CC"),
            "Normalize actual hardware address",
        )
        check(
            (Address.mac("001122aabbcc"))!!.equals("00:11:22:AA:BB:CC"),
            "Normalize advertised compact hardware ID",
        )
        for (invalid in
            arrayOf(
                "",
                "00:00:00:00:00:00",
                "02:00:00:00:00:00",
                "FF:FF:FF:FF:FF:FF",
                "01:00:5e:00:00:01",
                "x;id",
                "00:11:22:33:44",
            )) {
            check((Address.mac(invalid))!!.isEmpty(), "Reject placeholder, group, or invalid MAC")
        }
        check(
            (Address.mac("02:11:22:33:44:55"))!!.equals("02:11:22:33:44:55"),
            "Allow real locally administered addresses",
        )
        check(Address.inPrefix("192.168.1.8", "192.168.1.10", 24), "IPv4 prefix membership")
        check(!(Address.inPrefix("192.168.2.8", "192.168.1.10", 24)), "Exclude other IPv4 subnet")
        check(
            Address.inPrefix("fe80::1234%wlan0", "fe80::5678%wlan0", 64),
            "IPv6 link-local prefix and scope",
        )
        check(!(Address.inPrefix("fd00:2::8", "fd00:1::10", 64)), "Exclude another IPv6 subnet")
        check(
            ((Address.bytes("example.com") === null) && (Address.bytes("fe80::1;id") === null)),
            "Numeric addresses only; no DNS/shell input",
        )
        check((Address.compare("192.168.1.9", "192.168.1.10") < 0), "Numeric IPv4 sorting")
        check((Address.compare("fd00::9", "fd00::10") < 0), "Numeric IPv6 sorting")
        var query: ByteArray = NetBios.query(4660)
        check(
            ((((query.size == 50) && ((((query[0]).toInt() and (255).toInt())) == 18)) &&
                ((((query[1]).toInt() and (255).toInt())) == 52)) &&
                ((query[47]).toInt() == (33).toInt())),
            "RFC 1002 node-status question",
        )
        var packet: ByteArray = response(4660)
        var parsed: NetBios.Result? = NetBios.parse(packet, packet.size, 4660)
        check(
            ((parsed !== null) && parsed.name.equals("OFFICE-PC")),
            "Decode NetBIOS workstation name",
        )
        check((parsed)!!.mac.equals("00:11:22:33:44:55"), "Decode reported NetBIOS adapter address")
        check((NetBios.parse(packet, packet.size, 39030) === null), "Reject mismatched transaction")
        run {
            var n: Int = 0
            while ((n < packet.size)) {
                check(
                    (NetBios.parse(packet, n, 4660) === null),
                    ("Reject truncated status response at " + n),
                )
                n++
            }
        }
        var bad: ByteArray = packet.clone()
        bad[2] = 0
        check(
            (NetBios.parse(bad, bad.size, 4660) === null),
            "Reject a query presented as a response",
        )
        bad = packet.clone()
        bad[55] = (255).toByte()
        check((NetBios.parse(bad, bad.size, 4660) === null), "Reject oversized RDATA")
        bad = packet.clone()
        bad[56] = (255).toByte()
        check((NetBios.parse(bad, bad.size, 4660) === null), "Reject oversized name count")
        var ssdp: ByteArray =
            (("HTTP/1.1 200 OK\r\nSERVER: Linux / UPnP\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nLOCATION: http://169.254.169.254/private\r\nUSN: uuid:fixture\r\n\r\n"))!!
                .toByteArray(StandardCharsets.ISO_8859_1)
        var headers: Map<String, String> = Ssdp.parse(ssdp, ssdp.size)
        check(
            ((headers.get("server"))!!.equals("Linux / UPnP") &&
                (headers.get("usn"))!!.equals("uuid:fixture")),
            "Read whitelisted SSDP metadata",
        )
        check(
            (headers.containsKey("location") && (headers.size == 4)),
            "Retain bounded raw location for separate same-responder policy",
        )
        check(
            (UpnpDescription.target(
                headers.get("location"),
                java.net.InetAddress.getByName("192.168.1.2"),
            ) === null),
            "Cross-device metadata URL rejected before networking",
        )
        check(
            (Ssdp.parse("NOTIFY * HTTP/1.1".toByteArray(StandardCharsets.US_ASCII), 17))!!
                .isEmpty(),
            "Reject unrelated SSDP messages",
        )
        var random: Random = Random(1234)
        run {
            var n: Int = 0
            while ((n < 10000)) {
                var fuzz: ByteArray = ByteArray(random.nextInt(4097))
                random.nextBytes(fuzz)
                NetBios.parse(fuzz, fuzz.size, 4660)
                Ssdp.parse(fuzz, fuzz.size)
                n++
            }
        }
        check(true, "10,000 malformed packet pairs do not crash or loop")
        System.out.println(
            (("PASS · " + checks) +
                " address and discovery-protocol checks (including 10,000 malformed packet pairs)")
        )
    }
}
