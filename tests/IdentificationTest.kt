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

/** Protocol and policy tests at untrusted network boundaries; no external networking. */
object IdentificationTest {
    private var checks: Int = 0

    private fun check(ok: Boolean, why: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(why)
        }
    }

    private fun ascii(s: String): ByteArray {
        return s.toByteArray(StandardCharsets.US_ASCII)
    }

    private fun dns(id: Int): ByteArray {
        var question: ByteArray = ReverseDns.query("192.168.1.20", id)
        var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
        bytes.write(question)
        var h: ByteArray = bytes.toByteArray()
        h[2] = (129).toByte()
        h[3] = (128).toByte()
        h[7] = 1
        bytes.reset()
        bytes.write(h)
        var out: DataOutputStream = DataOutputStream(bytes)
        out.writeShort(49164)
        out.writeShort(12)
        out.writeShort(1)
        out.writeInt(60)
        var name: ByteArray = ascii("\u0006office\u0004home\u0000")
        out.writeShort(name.size)
        out.write(name)
        return bytes.toByteArray()
    }

    private fun httpRejected(s: String, why: String): Unit {
        try {
            UpnpDescription.readResponse(ByteArrayInputStream(ascii(s)))
            throw AssertionError(why)
        } catch (expected: IOException) {
            check(true, why)
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var values: List<String> = Arrays.asList("Android", "Office printer", "Studio printer")
        var winner: String = ""
        run {
            var seed: Int = 0
            while ((seed < 60)) {
                var hints: IdentityHints = IdentityHints()
                var order: List<String> = ArrayList(values)
                Collections.shuffle(order, Random((seed).toLong()))
                for (value in order) {
                    hints.offer("Name", value, "mDNS friendly name", 90)
                }
                var now: String =
                    (hints.offer("Name", "generic HTTP service", "HTTP instance", 25))!!.value
                if ((seed == 0)) {
                    winner = now
                }
                check(
                    (winner.equals(now) && !(now.equals("Android"))),
                    "Identity is stable across callback order and avoids generic ties",
                )
                seed++
            }
        }
        var hints: IdentityHints = IdentityHints()
        hints.offer("Model", "Xserve", "mDNS _device-info", 20)
        hints.offer("Model", "Real model", "UPnP description", 70)
        check(
            (hints.offer("Model", "Printer 500", "mDNS printer TXT", 80))!!
                .value
                .equals("Printer 500"),
            "Explicit model beats compatibility string and UPnP",
        )
        check(((hints.observations())!!.size == 3), "Retain alternative reports with provenance")
        run {
            var i: Int = 0
            while ((i < 5000)) {
                hints.offer("Name", ("name-" + i), ("service-" + i), 60)
                i++
            }
        }
        check(((hints.observations())!!.size == 11), "Bound observations to eight per field")
        check(
            (hints.offer("Untrusted field", "text", "peer", 100) === null),
            "Only defined identity fields accepted",
        )
        var health: DiscoveryHealth = DiscoveryHealth()
        health.starting("_ipp._tcp.")
        health.started("_ipp._tcp.")
        health.resolving("printer", "_ipp._tcp.")
        health.finish(false)
        check(
            (!((health.summary())!!.isEmpty()) &&
                ((health.sources())!!.get("mDNS _ipp._tcp."))!!.startsWith("Truncated")),
            "Unfinished resolution cannot report complete",
        )
        health = DiscoveryHealth()
        health.starting("_http._tcp.")
        health.startFailed("_http._tcp.")
        health.ok("mDNS _http._tcp.")
        health.finish(false)
        check(
            ((health.sources())!!.get("mDNS _http._tcp."))!!.startsWith("Failed"),
            "Success cannot erase an earlier source failure",
        )
        health = DiscoveryHealth()
        health.starting("_ipp._tcp.")
        health.started("_ipp._tcp.")
        health.resolving("printer", "_ipp._tcp.")
        health.resolved("printer")
        health.closeBrowseWindow()
        health.finish(false)
        check((health.summary())!!.isEmpty(), "Completed source has no false warning")
        health = DiscoveryHealth()
        health.begin("IPv4 checks")
        health.finish(true)
        check(
            ((health.sources())!!.get("IPv4 checks"))!!.startsWith("Truncated"),
            "Cancellation is reflected in source status",
        )
        var admission: ServiceAdmission = ServiceAdmission(1)
        check(
            (((admission.admit("a", "type") === ServiceAdmission.Result.ADMITTED) &&
                (admission.admit("a", "type") === ServiceAdmission.Result.DUPLICATE)) &&
                (admission.admit("b", "type") === ServiceAdmission.Result.LIMIT)),
            "Distinguish duplicates from genuine truncation at capacity",
        )
        check(
            (ReverseDns.question("192.168.1.20"))!!.equals("20.1.168.192.in-addr.arpa"),
            "IPv4 reverse question",
        )
        check(
            (ReverseDns.question("fd00::1"))!!.endsWith(".d.f.ip6.arpa"),
            "IPv6 nibble reverse question",
        )
        var packet: ByteArray = dns(4660)
        check(
            (ReverseDns.parse(packet, packet.size, 4660, "192.168.1.20"))!!.equals("office.home"),
            "Decode matching PTR response with compressed owner",
        )
        check(
            (ReverseDns.parse(packet, packet.size, 4661, "192.168.1.20"))!!.isEmpty(),
            "Reject wrong DNS transaction",
        )
        check(
            (ReverseDns.parse(packet, packet.size, 4660, "192.168.1.21"))!!.isEmpty(),
            "Reject another endpoint's PTR",
        )
        run {
            var n: Int = 0
            while ((n < packet.size)) {
                check(
                    (ReverseDns.parse(packet, n, 4660, "192.168.1.20"))!!.isEmpty(),
                    "Reject truncated PTR packet",
                )
                n++
            }
        }
        var bad: ByteArray = packet.clone()
        bad[2] = ((bad[2]).toInt() or (2).toInt()).toByte()
        check(
            (ReverseDns.parse(bad, bad.size, 4660, "192.168.1.20"))!!.isEmpty(),
            "Reject truncated DNS flag",
        )
        bad = packet.clone()
        bad[12] = (192).toByte()
        bad[13] = 12
        check(
            (ReverseDns.parse(bad, bad.size, 4660, "192.168.1.20"))!!.isEmpty(),
            "Reject compression self-loop",
        )
        var peer: InetAddress = InetAddress.getByName("192.168.1.20")
        check(
            (UpnpDescription.target("http://192.168.1.20:8008/device.xml", peer) !== null),
            "Allow numeric same-responder description",
        )
        var description: URI? = UpnpDescription.target("http://192.168.1.20:8088/description", peer)
        check((description !== null), "Support advertised extensionless Harmony descriptions")
        check(
            String(UpnpDescription.request((description)!!), StandardCharsets.US_ASCII)
                .equals(
                    "GET /description HTTP/1.1\r\nHost: 192.168.1.20:8088\r\nAccept: text/xml, application/xml\r\nConnection: close\r\n\r\n"
                ),
            "Use HTTP/1.1 with explicit host/close for Cast-compatible requests",
        )
        check(
            ((UpnpDescription.target("http://192.168.1.21:8088/description", peer) === null) &&
                (UpnpDescription.target("http://192.168.1.20/description?reboot=1", peer) ===
                    null)),
            "Extensionless support retains same-responder and query restrictions",
        )
        for (url in
            arrayOf(
                "http://192.168.1.21/device.xml",
                "http://127.0.0.1/device.xml",
                "http://169.254.169.254/device.xml",
                "http://example.com/device.xml",
                "http://user@192.168.1.20/device.xml",
                "http://192.168.1.20/device.xml?reboot=1",
                "http://192.168.1.20/device.xml#x",
                "file:///device.xml",
                "https://192.168.1.20/device.xml",
                "http://192.168.1.20/reboot",
                "http://192.168.001.020/device.xml",
                "http://192.168.1.20:0/device.xml",
                "http://192.168.1.20/device.xml\r\nX: bad",
            )) {
            check(
                (UpnpDescription.target(url, peer) === null),
                ("Reject unsafe/unsupported location: " + url),
            )
        }
        check(
            (UpnpDescription.target(
                "http://[fd00::20]/device.xml",
                InetAddress.getByName("fd00::20"),
            ) !== null),
            "Allow exact numeric IPv6 responder",
        )
        var xml: String =
            "<root xmlns='urn:schemas-upnp-org:device-1-0'><device><friendlyName>Living room</friendlyName><manufacturer>Fixture Inc</manufacturer><modelName>Model 500</modelName><modelNumber>500-A</modelNumber><deviceList><device><friendlyName>Wrong child</friendlyName></device></deviceList></device></root>"
        var fields: Map<String, String> = UpnpDescription.parse(ascii(xml))
        check(
            (((fields.get("friendlyName"))!!.equals("Living room") &&
                (fields.get("modelName"))!!.equals("Model 500")) &&
                (fields.get("modelNumber"))!!.equals("500-A")),
            "Parse root device fields without embedded-device contamination",
        )
        check(
            (UpnpDescription.parse(
                    ascii(
                        "<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root><device><friendlyName>&x;</friendlyName></device></root>"
                    )
                ))!!
                .isEmpty(),
            "Reject XXE and every DTD",
        )
        check(
            (UpnpDescription.parse(
                    ascii(
                        "<!DOCTYPE root [<!ENTITY a 'expand'>]><root><device><modelName>&a;</modelName></device></root>"
                    )
                ))!!
                .isEmpty(),
            "Reject internal entity expansion",
        )
        check(
            (UpnpDescription.parse(ByteArray(65537)))!!.isEmpty(),
            "Reject oversized XML before parsing",
        )
        check(
            (UpnpDescription.parse(
                    ascii("<html><device><modelName>Wrong document</modelName></device></html>")
                ))!!
                .isEmpty(),
            "Reject unrelated XML",
        )
        var response: String =
            ((("HTTP/1.1 200 OK\r\nContent-Length: " + xml.length) + "\r\n\r\n") + xml)
        check(
            Arrays.equals(
                UpnpDescription.readResponse(ByteArrayInputStream(ascii(response))),
                ascii(xml),
            ),
            "Read bounded content length",
        )
        var chunked: String =
            (((("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" +
                Integer.toHexString(xml.length)) + "\r\n") + xml) + "\r\n0\r\n\r\n")
        check(
            Arrays.equals(
                UpnpDescription.readResponse(ByteArrayInputStream(ascii(chunked))),
                ascii(xml),
            ),
            "Read chunked description safely",
        )
        httpRejected(
            "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1/private\r\n\r\n",
            "Never follow redirects",
        )
        httpRejected(
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\na",
            "Reject duplicate framing",
        )
        httpRejected(
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\na",
            "Reject ambiguous framing",
        )
        httpRejected(
            "HTTP/1.1 200 OK\r\nContent-Length: 65537\r\n\r\n",
            "Reject oversized body before reading",
        )
        httpRejected("HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\na", "Reject incomplete body")
        httpRejected(
            "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n\r\n",
            "Reject compressed bodies",
        )
        var id: String = "12345678-1234-1234-1234-123456789abc"
        var sadp: String =
            (("<ProbeMatch><Uuid>" + id) +
                "</Uuid><Types>inquiry</Types><IPv4Address>192.168.1.20</IPv4Address><DeviceDescription>Fixture recorder</DeviceDescription><SoftwareVersion>1.2.3</SoftwareVersion><MAC>00-11-22-33-44-55</MAC><HttpPort>8082</HttpPort><DeviceSN>private serial</DeviceSN><Salt>private salt</Salt><Salt>another salt</Salt></ProbeMatch>")
        var inquiry: ByteArray = ascii(sadp)
        var camera: Map<String, String> = Sadp.parse(inquiry, inquiry.size, id, peer)
        check(
            ("Fixture recorder".equals(camera.get("DeviceDescription")) &&
                "1.2.3".equals(camera.get("SoftwareVersion"))),
            "Read correlated SADP model and firmware",
        )
        check(
            ((!(camera.containsKey("DeviceSN")) && !(camera.containsKey("Salt"))) &&
                !(camera.containsKey("Uuid"))),
            "Do not retain serial, security fields or transaction IDs",
        )
        check(
            (Sadp.parse(inquiry, inquiry.size, "22345678-1234-1234-1234-123456789abc", peer))!!
                .isEmpty(),
            "Reject unrelated SADP transaction",
        )
        check(
            (Sadp.parse(inquiry, inquiry.size, id, InetAddress.getByName("192.168.1.21")))!!
                .isEmpty(),
            "Advertised SADP address must equal actual responder",
        )
        for (invalid in
            arrayOf(
                sadp.replace("<Types>inquiry</Types>", "<Types>activate</Types>"),
                sadp.replace("ProbeMatch", "Probe"),
                (sadp.replace("<DeviceDescription>", "<DeviceDescription><nested>"))!!.replace(
                    "</DeviceDescription>",
                    "</nested></DeviceDescription>",
                ),
                sadp.replace("</ProbeMatch>", "<MAC>00:11:22:33:44:66</MAC></ProbeMatch>"),
                ("<!DOCTYPE ProbeMatch [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" +
                    sadp.replace("Fixture recorder", "&x;")),
                sadp.replace(
                    "Fixture recorder",
                    java.lang.String.join("", Collections.nCopies(257, "x")),
                ),
            )) {
            var bytes: ByteArray = ascii(invalid)
            check(
                (Sadp.parse(bytes, bytes.size, id, peer))!!.isEmpty(),
                "Reject malformed, mismatched, entity-bearing or oversized SADP field",
            )
        }
        check(
            (Sadp.parse(ByteArray(8193), 8193, id, peer))!!.isEmpty(),
            "Reject oversized SADP packets before XML parsing",
        )
        check(
            (((Sadp.port("8082") == 8082) && (Sadp.port("65536") == 0)) &&
                (Sadp.port("80\r\n") == 0)),
            "Only valid advertised ports accepted",
        )
        check(
            String(Sadp.query(id), StandardCharsets.US_ASCII).contains("<Types>inquiry</Types>"),
            "Only read-only inquiry is generated",
        )
        try {
            Sadp.query("bad</Uuid><Types>activate</Types>")
            throw AssertionError("Injected inquiry ID")
        } catch (expected: IllegalArgumentException) {
            check(true, "Inquiry identifier cannot inject an alternative command")
        }
        var random: Random = Random(17)
        run {
            var i: Int = 0
            while ((i < 10000)) {
                var fuzz: ByteArray = ByteArray(random.nextInt(1024))
                random.nextBytes(fuzz)
                ReverseDns.parse(fuzz, fuzz.size, 4660, "192.168.1.20")
                if ((i < 1000)) {
                    UpnpDescription.parse(fuzz)
                    Sadp.parse(fuzz, fuzz.size, id, peer)
                }
                i++
            }
        }
        check(true, "Malformed DNS/XML inputs do not crash or hang")
        System.out.println(
            (("PASS · " + checks) +
                " identification policy/parser checks (10,000 DNS and 1,000 XML pairs malformed inputs)")
        )
    }
}
