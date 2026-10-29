@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import dev.netlens.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*

/** Original WS-Discovery fixtures; no captured home payloads or third-party scripts. */
object WsDiscoveryTest {
    private var checks: Int = 0

    private var ID: String = "12345678-1234-1234-1234-123456789abc"
    private var PEER: String = "192.168.1.20"

    private fun check(ok: Boolean, message: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(message)
        }
    }

    private fun d(v: WsDiscovery.Version): String {
        return (if ((v === WsDiscovery.Version.LEGACY))
            "http://schemas.xmlsoap.org/ws/2005/04/discovery"
        else "http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01")
    }

    private fun a(v: WsDiscovery.Version): String {
        return (if ((v === WsDiscovery.Version.LEGACY))
            "http://schemas.xmlsoap.org/ws/2004/08/addressing"
        else "http://www.w3.org/2005/08/addressing")
    }

    private fun match(peer: String): String {
        return (("<d:ProbeMatch><a:EndpointReference><a:Address>urn:uuid:ffffffff-ffff-ffff-ffff-ffffffffffff</a:Address></a:EndpointReference><d:Types xmlns:n='http://www.onvif.org/ver10/network/wsdl'>n:NetworkVideoTransmitter</d:Types><d:Scopes>onvif://www.onvif.org/name/Front%20door onvif://www.onvif.org/hardware/Fixture%20hardware onvif://www.onvif.org/MacAddress/00-11-22-33-44-55 onvif://www.onvif.org/location/private onvif://www.onvif.org/SerialNumber/private</d:Scopes><d:XAddrs>http://" +
            peer) +
            ":8899/onvif/device_service</d:XAddrs><d:MetadataVersion>1</d:MetadataVersion></d:ProbeMatch>")
    }

    private fun envelope(v: WsDiscovery.Version, body: String): String {
        return (((((((((("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:a='" +
            a(v)) + "' xmlns:d='") + d(v)) + "'><s:Header><a:Action>") + d(v)) +
            "/ProbeMatches</a:Action><a:RelatesTo>urn:uuid:") + ID) +
            "</a:RelatesTo></s:Header><s:Body><d:ProbeMatches>") + body) +
            "</d:ProbeMatches></s:Body></s:Envelope>")
    }

    private fun parse(xml: String, v: WsDiscovery.Version): List<WsDiscovery.Result> {
        var bytes: ByteArray = xml.toByteArray(StandardCharsets.UTF_8)
        return WsDiscovery.parse(bytes, bytes.size, v, ID, InetAddress.getByName(PEER))
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        for (v in WsDiscovery.Version.values()) {
            var xml: String = envelope(v, match(PEER))
            var list: List<WsDiscovery.Result> = parse(xml, v)
            check((list.size == 1), "Both supported discovery versions parse")
            var r: WsDiscovery.Result = list.get(0)
            check(r.names.equals(Collections.singletonList("Front door")), "Decode name scope")
            check(
                r.hardware.equals(Collections.singletonList("Fixture hardware")),
                "Keep reported hardware separate from a model claim",
            )
            check(
                r.macs.equals(Collections.singletonList("00:11:22:33:44:55")),
                "Accept valid reported MAC scope",
            )
            check(
                (r.kind.equals("Video surveillance device") &&
                    r.ports.equals(Collections.singletonList(8899))),
                "Namespace-qualified type and numeric advertised port",
            )
            check(r.identity.startsWith("wsd:"), "Protocol UUID supplies an explicit scoped link")
            check(
                !((r.fingerprint())!!.contains("private")),
                "Location and serial scopes are not retained",
            )
            var request: ByteArray = WsDiscovery.query(v, ID)
            var q: String = String(request, StandardCharsets.US_ASCII)
            check(
                ((q.contains((d(v) + "/Probe")) && q.contains(("urn:uuid:" + ID))) &&
                    (request.size < 1200)),
                "Generate one read-only bounded Probe",
            )
            check(
                (((!(q.contains("Get")) && !(q.contains("Set"))) && !(q.contains("Play"))) &&
                    (parse(q, v))!!.isEmpty()),
                "A request never becomes a reply",
            )
            var bytes: ByteArray = xml.toByteArray(StandardCharsets.UTF_8)
            check(
                (WsDiscovery.parse(
                        bytes,
                        bytes.size,
                        v,
                        "22345678-1234-1234-1234-123456789abc",
                        InetAddress.getByName(PEER),
                    ))!!
                    .isEmpty(),
                "Reject another transaction",
            )
            check(
                (WsDiscovery.parse(
                        bytes,
                        bytes.size,
                        v,
                        ID,
                        InetAddress.getByName("192.168.1.21"),
                    ))!!
                    .isEmpty(),
                "XAddr must identify the actual responder",
            )
            check(
                (parse(xml.replace("ProbeMatches", "Hello"), v))!!.isEmpty(),
                "Ignore announcements outside the requested transaction",
            )
            for (target in
                arrayOf(
                    "192.168.1.21",
                    "example.com",
                    "127.0.0.1",
                    "169.254.169.254",
                    ("user@" + PEER),
                    (PEER + ":80"),
                )) {
                var bad: String =
                    xml.replace((("http://" + PEER) + ":8899/"), (("http://" + target) + ":8899/"))
                check(
                    (parse(bad, v))!!.isEmpty(),
                    ("Reject unrelated or nonnumeric XAddr " + target),
                )
            }
            for (url in
                arrayOf(
                    "https://example.com/x",
                    "file:///etc/passwd",
                    (("http://" + PEER) + ":0/x"),
                    (("http://" + PEER) + ":65536/x"),
                    (("http://" + PEER) + "/x?reboot=1"),
                    (("http://" + PEER) + "/x#fragment"),
                )) {
                check(
                    (parse(
                            xml.replace((("http://" + PEER) + ":8899/onvif/device_service"), url),
                            v,
                        ))!!
                        .isEmpty(),
                    "Reject unsupported address without fetching",
                )
            }
            check(
                (parse(xml.replace("<a:Action>", "<a:Action><nested/>"), v))!!.isEmpty(),
                "Reject nested header values",
            )
            check(
                (parse(
                        xml.replace(
                            "</a:RelatesTo>",
                            (("</a:RelatesTo><a:RelatesTo>urn:uuid:" + ID) + "</a:RelatesTo>"),
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Reject repeated correlation header",
            )
            check(
                (parse(
                        xml.replace(
                            "</a:Action>",
                            (("</a:Action><a:Action>" + d(v)) + "/ProbeMatches</a:Action>"),
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Reject repeated action",
            )
            check(
                (parse(xml.replace("</d:Scopes>", "</d:Scopes><d:Scopes/>"), v))!!.isEmpty(),
                "Reject duplicate identity fields",
            )
            check(
                (parse(
                        (xml.replace("<d:Scopes>", "<d:Scopes><nested>"))!!.replace(
                            "</d:Scopes>",
                            "</nested></d:Scopes>",
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Reject nested identity scope",
            )
            check(
                ((parse(
                            xml.replace(
                                "http://www.onvif.org/ver10/network/wsdl",
                                "https://untrusted.example/type",
                            ),
                            v,
                        ))!!
                        .get(0))!!
                    .kind
                    .isEmpty(),
                "Prefix spelling cannot forge a known service type",
            )
            check(
                ((parse(
                            (xml.replace("<d:Scopes>", "<x:Scopes xmlns:x='urn:untrusted'>"))!!
                                .replace("</d:Scopes>", "</x:Scopes>"),
                            v,
                        ))!!
                        .get(0))!!
                    .names
                    .isEmpty(),
                "Wrong namespace cannot inject a friendly name",
            )
            var international: String = xml.replace("Front%20door", "Caf%C3%A9+printer")
            check(
                (((parse(international, v))!!.get(0))!!.names.get(0))!!.equals("Café+printer"),
                "Decode UTF-8 and preserve literal plus",
            )
            check(
                ((parse(xml.replace("Front%20door", "%FF"), v))!!.get(0))!!.names.isEmpty(),
                "Invalid percent UTF-8 is ignored",
            )
            check(
                (((parse(xml.replace("Front%20door", "Good%E2%80%AEname%0A"), v))!!.get(0))!!
                        .names
                        .get(0))!!
                    .equals("Goodname"),
                "Strip bidi/control characters from displayed labels",
            )
            var multi: String = envelope(v, (match("192.168.1.21") + match(PEER)))
            check(
                ((parse(multi, v))!!.size == 1),
                "A proxy cannot name other addresses by listing them",
            )
            check(
                (parse(
                        envelope(v, java.lang.String.join("", Collections.nCopies(9, match(PEER)))),
                        v,
                    ))!!
                    .isEmpty(),
                "Reject more than eight records atomically",
            )
            check(
                (parse(
                        xml.replace(
                            "Front%20door",
                            java.lang.String.join("", Collections.nCopies(4200, "x")),
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Bound scope text before retaining it",
            )
            check(
                (parse(
                        xml.replace(
                            "onvif://www.onvif.org/location/private",
                            java.lang.String.join(
                                " ",
                                Collections.nCopies(65, "onvif://www.onvif.org/name/x"),
                            ),
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Bound scope count",
            )
            var dtd: String =
                ("<!DOCTYPE s:Envelope [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" +
                    xml.replace("Front%20door", "&x;"))
            check((parse(dtd, v))!!.isEmpty(), "Reject external entities and DTDs")
            var utf16: ByteArray = dtd.toByteArray(StandardCharsets.UTF_16)
            check(
                (WsDiscovery.parse(utf16, utf16.size, v, ID, InetAddress.getByName(PEER)))!!
                    .isEmpty(),
                "DTD guard is parser based across XML encodings",
            )
            run {
                var i: Int = 0
                while ((i < bytes.size)) {
                    check(
                        (WsDiscovery.parse(bytes, i, v, ID, InetAddress.getByName(PEER)))!!
                            .isEmpty(),
                        "Reject truncated packets without partial results",
                    )
                    i += 7
                }
            }
            var roles: String =
                xml.replace(
                    "xmlns:n='http://www.onvif.org/ver10/network/wsdl'>n:NetworkVideoTransmitter",
                    "xmlns:p='http://schemas.microsoft.com/windows/2006/08/wdp/print'>p:PrintDeviceType",
                )
            check(
                ((parse(roles, v))!!.get(0))!!.kind.equals("Printer service"),
                "Recognize documented printer type by expanded QName",
            )
            var deep: String =
                (java.lang.String.join("", Collections.nCopies(13, "<x:N xmlns:x='urn:fixture'>")) +
                    java.lang.String.join("", Collections.nCopies(13, "</x:N>")))
            check(
                (parse(xml.replace("</s:Header>", (deep + "</s:Header>")), v))!!.isEmpty(),
                "Bound depth even in ignored header extensions",
            )
            var nodes: String =
                java.lang.String.join("", Collections.nCopies(513, "<x:N xmlns:x='urn:fixture'/>"))
            check(
                (parse(xml.replace("</s:Header>", (nodes + "</s:Header>")), v))!!.isEmpty(),
                "Bound total nodes including ignored extensions",
            )
            var declarations: StringBuilder = StringBuilder()
            var attributes: StringBuilder = StringBuilder()
            run {
                var n: Int = 0
                while ((n < 33)) {
                    ((((declarations.append(" xmlns:p"))!!.append(n))!!.append("='urn:fixture:"))!!
                            .append(n))!!
                        .append("'")
                    ((attributes.append(" a"))!!.append(n))!!.append("='x'")
                    n++
                }
            }
            check(
                (parse(xml.replace("<s:Envelope ", (("<s:Envelope" + declarations) + " ")), v))!!
                    .isEmpty(),
                "Bound namespace declarations",
            )
            check(
                (parse(xml.replace("<s:Envelope ", (("<s:Envelope" + attributes) + " ")), v))!!
                    .isEmpty(),
                "Bound per-element attributes",
            )
            check(
                (parse(
                        xml.replace(
                            "n:NetworkVideoTransmitter",
                            java.lang.String.join(
                                " ",
                                Collections.nCopies(17, "n:NetworkVideoTransmitter"),
                            ),
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Bound type QName list",
            )
            check(
                (parse(
                        xml.replace(
                            (("http://" + PEER) + ":8899/onvif/device_service"),
                            java.lang.String.join(
                                " ",
                                Collections.nCopies(9, (("http://" + PEER) + "/device")),
                            ),
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Bound advertised address list",
            )
            check(
                (parse(
                        xml.replace(
                            "</a:Address>",
                            "</a:Address><a:Address>urn:uuid:eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee</a:Address>",
                        ),
                        v,
                    ))!!
                    .isEmpty(),
                "Reject duplicate endpoint identities atomically",
            )
            check(
                (((parse(
                            xml.replace(
                                "Front%20door",
                                "Android onvif://www.onvif.org/name/Office",
                            ),
                            v,
                        ))!!
                        .get(0))!!
                    .names
                    .size == 2),
                "Preserve competing names for the existing identity policy",
            )
            try {
                (r.names as MutableList<String>).add("Injected")
                throw AssertionError("Mutable result")
            } catch (expected: UnsupportedOperationException) {
                check(true, "Parsed observations are immutable")
            }
        }
        for (id in arrayOf("", null, "bad</a:MessageID>", "123")) {
            try {
                WsDiscovery.query(WsDiscovery.Version.STANDARD, id)
                throw AssertionError("Bad ID accepted")
            } catch (expected: IllegalArgumentException) {
                check(true, "Request ID cannot inject XML")
            }
        }
        check(
            (WsDiscovery.parse(
                    ByteArray(16385),
                    16385,
                    WsDiscovery.Version.STANDARD,
                    ID,
                    InetAddress.getByName(PEER),
                ))!!
                .isEmpty(),
            "Reject oversized packet",
        )
        check(
            (WsDiscovery.parse(
                    null,
                    0,
                    WsDiscovery.Version.STANDARD,
                    ID,
                    InetAddress.getByName(PEER),
                ))!!
                .isEmpty(),
            "Reject absent packet",
        )
        var random: Random = Random(29)
        run {
            var i: Int = 0
            while ((i < 5000)) {
                var b: ByteArray = ByteArray(random.nextInt(2048))
                random.nextBytes(b)
                WsDiscovery.parse(
                    b,
                    b.size,
                    WsDiscovery.Version.STANDARD,
                    ID,
                    InetAddress.getByName(PEER),
                )
                i++
            }
        }
        check(true, "5000 malformed XML datagrams cannot crash parsing")
        System.out.println(
            (("PASS · " + checks) + " WS-Discovery assertions and 5,000 malformed XML inputs")
        )
    }
}
