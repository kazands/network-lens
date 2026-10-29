@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import dev.netlens.*
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*

/** Regression fixtures for confirmed URL, completion, DNS and grouping defects. */
object ConfirmedFixesTest {
    private var checks: Int = 0

    private fun check(value: Boolean, reason: String): Unit {
        checks++
        if (!(value)) {
            throw AssertionError(reason)
        }
    }

    private fun ascii(value: String): ByteArray {
        return value.toByteArray(StandardCharsets.US_ASCII)
    }

    private fun wire(target: URI, expected: String): Unit {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            var failure: kotlin.Array<Throwable?> = arrayOf(null)
            var responder: Thread =
                Thread(
                    callback2@{
                        try {
                            server.accept().use { socket ->
                                socket.setSoTimeout(2000)
                                var reader: BufferedReader =
                                    BufferedReader(
                                        InputStreamReader(
                                            socket.getInputStream(),
                                            StandardCharsets.US_ASCII,
                                        )
                                    )
                                var request: String = reader.readLine()
                                if (!(expected.equals(request))) {
                                    throw AssertionError(request)
                                }
                                while (!((reader.readLine())!!.isEmpty())) {}

                                var body: String =
                                    "<root><device><friendlyName>Fixture PC</friendlyName><modelName>Fixture model</modelName></device></root>"
                                (socket.getOutputStream())!!.write(
                                    ascii(
                                        ((("HTTP/1.1 200 OK\r\nContent-Length: " + body.length) +
                                            "\r\n\r\n") + body)
                                    )
                                )
                            }
                        } catch (e: Throwable) {
                            failure[0] = e
                        }
                    },
                    "local-test-server",
                )
            responder.start()
            Socket(InetAddress.getLoopbackAddress(), server.getLocalPort()).use { socket ->
                socket.setSoTimeout(2000)
                (socket.getOutputStream())!!.write(UpnpDescription.request(target))
                var fields: Map<String, String> =
                    UpnpDescription.parse(UpnpDescription.readResponse(socket.getInputStream()))
                check(
                    ("Fixture PC".equals(fields.get("friendlyName")) &&
                        "Fixture model".equals(fields.get("modelName"))),
                    "Wire response retains friendly name and model",
                )
            }
            responder.join(3000)
            check(
                (!(responder.isAlive()) && (failure[0] === null)),
                ("Exact HTTP request target was received: " + expected),
            )
        }
    }

    class Endpoint : Grouping.Endpoint {
        var mac: String? = null

        var self: Boolean = false

        var links: MutableList<String>? = null

        constructor(mac: String?, self: Boolean, vararg links: String) {
            this.mac = mac
            this.self = self
            this.links = Arrays.asList(*links)
        }

        override fun hardwareAddress(): String {
            return mac!!
        }

        override fun isSelf(): Boolean {
            return self
        }

        override fun links(): Collection<String> {
            return links!!
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var peer: InetAddress = InetAddress.getByName("192.0.2.5")
        var root: URI? = UpnpDescription.target("http://192.0.2.5:8060/", peer)
        check((root !== null), "Allow advertised root description")
        wire((root)!!, "GET / HTTP/1.1")
        var path: String =
            "/upnphost/udhisapi.dll?content=uuid:12345678-1234-1234-1234-123456789abc"
        var windows: URI? = UpnpDescription.target(("http://192.0.2.5:2869" + path), peer)
        check((windows !== null), "Allow documented Windows description selector")
        wire((windows)!!, (("GET " + path) + " HTTP/1.1"))
        for (suffix in
            arrayOf(
                "/?reboot=1",
                "/device.xml?content=uuid:12345678-1234-1234-1234-123456789abc",
                "/upnphost/udhisapi.dll?reboot=1",
                "/upnphost/udhisapi.dll?content=uuid:bad",
                "/upnphost/udhisapi.dll?content=uuid:12345678-1234-1234-1234-123456789abc&reboot=1",
                "/upnphost/udhisapi.dll?content=uuid%3A12345678-1234-1234-1234-123456789abc",
                "/reboot",
            )) {
            check(
                (UpnpDescription.target(("http://192.0.2.5" + suffix), peer) === null),
                "Reject unrelated or ambiguous query/action path",
            )
        }
        check(
            (UpnpDescription.target(("http://192.0.2.6" + path), peer) === null),
            "Windows descriptor cannot redirect to another host",
        )
        var health: DiscoveryHealth = DiscoveryHealth()
        health.begin("Fixture source")
        health.finish(false)
        check(
            ((health.sources())!!.get("Fixture source"))!!.startsWith("Truncated"),
            "Unconfirmed running source cannot become OK by cleanup",
        )
        health = DiscoveryHealth()
        health.starting("_ipp._tcp.")
        health.started("_ipp._tcp.")
        health.closeBrowseWindow()
        health.finish(false)
        check((health.summary())!!.isEmpty(), "Explicitly closed empty browse is successful")
        health = DiscoveryHealth()
        health.workStarted("Reverse DNS")
        health.workStarted("Reverse DNS")
        health.workFinished("Reverse DNS")
        health.finish(false)
        check(
            ((health.sources())!!.get("Reverse DNS"))!!.startsWith("Truncated"),
            "One completed lookup cannot hide another pending lookup",
        )
        health = DiscoveryHealth()
        health.workStarted("Reverse DNS")
        health.fail("Reverse DNS", "No valid reply")
        health.workFinished("Reverse DNS")
        health.finish(false)
        check(
            ((health.sources())!!.get("Reverse DNS"))!!.startsWith("Failed"),
            "Completion cannot erase DNS failure",
        )
        var negative: ByteArray = ReverseDns.query("192.0.2.5", 42)
        negative[2] = (129).toByte()
        negative[3] = (131).toByte()
        check(
            ((ReverseDns.response(negative, negative.size, 42, "192.0.2.5"))!!.status ===
                ReverseDns.Status.NEGATIVE),
            "Correlated NXDOMAIN is a valid no-name response",
        )
        negative[3] = (128).toByte()
        check(
            ((ReverseDns.response(negative, negative.size, 42, "192.0.2.5"))!!.status ===
                ReverseDns.Status.NEGATIVE),
            "Correlated NOERROR with no PTR is a valid no-name response",
        )
        negative[3] = (130).toByte()
        check(
            ((ReverseDns.response(negative, negative.size, 42, "192.0.2.5"))!!.status ===
                ReverseDns.Status.ERROR),
            "SERVFAIL is a server error, not a successful no-name response",
        )
        check(
            ((ReverseDns.response(negative, negative.size, 43, "192.0.2.5"))!!.status ===
                ReverseDns.Status.INVALID),
            "Wrong transaction is never counted as a DNS answer",
        )
        check(
            ((ReverseDns.response(negative, (negative.size - 1), 42, "192.0.2.5"))!!.status ===
                ReverseDns.Status.INVALID),
            "Malformed negative response is rejected",
        )
        // Compare indexed grouping with the established pairwise-clique policy over adversarial
        // fixtures.
        var random: Random = Random(93)
        run {
            var run: Int = 0
            while ((run < 100)) {
                var endpoints: MutableList<Endpoint> = ArrayList()
                run {
                    var n: Int = 0
                    while ((n < 60)) {
                        var links: MutableList<String>? = ArrayList()
                        run {
                            var k: Int = 0
                            while ((k < 4)) {
                                if (random.nextBoolean()) {
                                    links!!.add(("link:" + random.nextInt(15)))
                                }
                                k++
                            }
                        }
                        endpoints.add(
                            Endpoint(
                                (if (random.nextBoolean()) "" else ("mac:" + random.nextInt(5))),
                                (random.nextInt(20) == 0),
                                *links!!.toTypedArray(),
                            )
                        )
                        n++
                    }
                }
                var reference: MutableList<MutableList<Endpoint>> = ArrayList()
                for (e in endpoints) {
                    var match: MutableList<Endpoint>? = null
                    for (group in reference) {
                        var good: Boolean = true
                        for (member in group) {
                            if (
                                (if ((e.self || member.self)) (e.self != member.self)
                                else
                                    (((!(e.mac!!.isEmpty()) && !(member.mac!!.isEmpty())) &&
                                        !(e.mac!!.equals(member.mac))) ||
                                        Collections.disjoint(e.links, member.links)))
                            ) {
                                good = false
                                break
                            }
                        }
                        if (good) {
                            match = group
                            break
                        }
                    }
                    if ((match === null)) {
                        match = ArrayList()
                        reference.add(match)
                    }
                    match!!.add(e)
                }
                check(
                    (Grouping.groups(endpoints))!!.equals(reference),
                    "Indexed grouping preserves pairwise compatibility, conflicts and input ordering",
                )
                run++
            }
        }
        System.out.println(
            (("PASS · " + checks) +
                " confirmed-fix regressions (local wire fixtures, source completion, DNS and grouping equivalence)")
        )
    }
}
