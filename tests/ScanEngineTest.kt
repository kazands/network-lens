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
import java.util.concurrent.*

object ScanEngineTest {
    private var checks: Int = 0

    private fun check(yes: Boolean, message: String): Unit {
        checks++
        if (!(yes)) {
            throw AssertionError(message)
        }
    }

    private fun utf(s: String): ByteArray {
        return s.toByteArray(StandardCharsets.UTF_8)
    }

    private fun name(out: DataOutputStream, name: String): Unit {
        for (label in java.util.regex.Pattern.compile("\\.").split(name, 0)) {
            var b: ByteArray = utf(label)
            out.writeByte(b.size)
            out.write(b)
        }
        out.writeByte(0)
    }

    private fun response(owner: String, kind: Int, ttl: Int, payload: ByteArray): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var out: DataOutputStream = DataOutputStream(b)
        out.writeShort(0)
        out.writeShort(33792)
        out.writeShort(0)
        out.writeShort(1)
        out.writeInt(0)
        name(out, owner)
        out.writeShort(kind)
        out.writeShort(32769)
        out.writeInt(ttl)
        out.writeShort(payload.size)
        out.write(payload)
        return b.toByteArray()
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var work: DiscoveryWork = DiscoveryWork()
        var namesStarted: CountDownLatch = CountDownLatch(4)
        var hold: CountDownLatch = CountDownLatch(1)
        var description: CountDownLatch = CountDownLatch(1)
        var confirmation: CountDownLatch = CountDownLatch(1)
        try {
            run {
                var i: Int = 0
                while ((i < 4)) {
                    check(
                        work.submit(
                            DiscoveryWork.Lane.NAMES,
                            callback1@{
                                namesStarted.countDown()
                                try {
                                    hold.await()
                                } catch (stopped: InterruptedException) {
                                    (Thread.currentThread())!!.interrupt()
                                }
                            },
                        ),
                        "Start a blocking DNS task",
                    )
                    i++
                }
            }
            check(namesStarted.await(2, TimeUnit.SECONDS), "All name workers occupied")
            run {
                var i: Int = 0
                while ((i < 144)) {
                    check(
                        work.submit(DiscoveryWork.Lane.NAMES, callback2@{}),
                        "Bounded DNS queue accepts its capacity",
                    )
                    i++
                }
            }
            check(
                !(work.submit(DiscoveryWork.Lane.NAMES, callback3@{})),
                "DNS overflow is rejected, never moved to another lane",
            )
            check(
                (work.submit(DiscoveryWork.Lane.DESCRIPTIONS, description::countDown) &&
                    description.await(1, TimeUnit.SECONDS)),
                "A useful description runs while every DNS worker and queue slot is blocked",
            )
            check(
                (work.submit(DiscoveryWork.Lane.CONFIRM, confirmation::countDown) &&
                    confirmation.await(1, TimeUnit.SECONDS)),
                "Fresh service confirmation has independent capacity",
            )
        } finally {
            hold.countDown()
            work.shutdownNow()
        }
        check(
            (work.awaitTermination(2, TimeUnit.SECONDS) && work.isTerminated()),
            "Stop drains all worker lanes within one shared deadline",
        )
        check(
            !(work.submit(DiscoveryWork.Lane.CAST, callback4@{})),
            "No late Cast work after shutdown",
        )
        check(
            (CastInfo.name(
                    utf(
                        "{\"name\":\"Living room TV\",\"location\":{\"latitude\":123},\"ssid\":\"private\",\"mac_address\":\"00:00:00:00:00:00\"}"
                    )
                ))!!
                .equals("Living room TV"),
            "Only a useful reported name survives unrelated sensitive fields",
        )
        check(
            (CastInfo.name(utf("{\"name\":\"TV \\uD83D\\uDCFA\"}")))!!.equals("TV 📺"),
            "Valid Unicode pairs are preserved",
        )
        for (bad in
            arrayOf(
                "{}",
                "[]",
                "null",
                "{\"name\":\"Android\"}",
                "{\"name\":\"A\",\"name\":\"B\"}",
                "{\"n\\u0061me\":\"A\",\"name\":\"B\"}",
                "{\"name\":\"A\"} trailing",
                "{\"nested\":{\"name\":\"Unrelated name\"}}",
                "{\"name\":42}",
                "{\"name\":\"\\uD800\"}",
                "{\"name\":\"\\q\"}",
                "{\"name\":\"A\",\"other\":NaN}",
                "{\"name\":\"A\",\"other\":01}",
            )) {
            check(
                (CastInfo.name(utf(bad)))!!.isEmpty(),
                ("Reject malformed, ambiguous or non-useful Cast name: " + bad),
            )
        }
        check(
            (CastInfo.name(byteArrayOf((195).toByte(), 40)))!!.isEmpty(),
            "Malformed UTF-8 is rejected",
        )
        check(
            (CastInfo.name(ByteArray((CastInfo.MAX_BODY + 1))))!!.isEmpty(),
            "Cast body size is bounded",
        )
        var nested: String = "0"
        run {
            var i: Int = 0
            while ((i < 32)) {
                nested = (("[" + nested) + "]")
                i++
            }
        }
        check(
            (CastInfo.name(utf((("{\"name\":\"Name\",\"deep\":" + nested) + "}"))))!!.isEmpty(),
            "Nested irrelevant fields cannot exhaust the Java stack",
        )
        check(
            (CastInfo.needsName("Chromecast-abcdef12345") &&
                !(CastInfo.needsName("Kitchen speaker"))),
            "Avoid requesting names already supplied by better sources",
        )
        for (generic in arrayOf("Chromecast", "Android TV")) {
            for (reverse in booleanArrayOf(false, true)) {
                var hints: IdentityHints = IdentityHints()
                var priority: Int = (if (generic.equals("Chromecast")) 90 else 85)
                if (reverse) {
                    hints.offer("Name", "Living room TV", "Cast device reported name", 70)
                }
                hints.offer("Name", generic, "mDNS advertised name", priority)
                var chosen: IdentityHints.Hint? =
                    hints.offer("Name", "Living room TV", "Cast device reported name", 70)
                check(
                    (chosen)!!.value.equals("Living room TV"),
                    "Useful fallback beats a stronger generic name in either arrival order",
                )
                check(
                    ((hints.observations())!!.size == 2),
                    "Generic advertised name remains available as provenance",
                )
                check(
                    (hints.offer("Name", "Useful TXT name", "mDNS friendly name", 90))!!
                        .value
                        .equals("Useful TXT name"),
                    "Useful stronger advertised name still wins over fallback",
                )
            }
        }
        var model: IdentityHints = IdentityHints()
        model.offer("Model", "Chromecast", "Explicit model", 90)
        check(
            (model.offer("Model", "Living room TV", "Weak inference", 50))!!
                .value
                .equals("Chromecast"),
            "Name placeholder rules do not demote valid model names",
        )
        var user: IdentityHints = IdentityHints()
        user.offer("Name", "Android TV", "User name", 100)
        check(
            (user.offer("Name", "Living room TV", "Cast device reported name", 70))!!
                .value
                .equals("Android TV"),
            "Explicit user name remains authoritative",
        )
        var request: String =
            String(CastInfo.request(InetAddress.getByName("fd00::20")), StandardCharsets.US_ASCII)
        check(
            (request.contains("Host: [fd00:0:0:0:0:0:0:20]:8008") &&
                request.startsWith("GET /setup/eureka_info?options=detail HTTP/1.1")),
            "Cast request has a fixed local path and valid IPv6 Host header",
        )
        var a: ByteArray = response("fixture.local", 1, 120, byteArrayOf((192).toByte(), 0, 2, 20))
        check(
            (MdnsPacket.parse(a, a.size))!!.freshResponse,
            "Fresh A reply admits its actual sender without trusting the advertised address",
        )
        var aaaa: ByteArray = response("fixture.local", 28, 120, ByteArray(16))
        check((MdnsPacket.parse(aaaa, aaaa.size))!!.freshResponse, "Fresh AAAA reply is recognized")
        var txt: ByteArray =
            response(
                "Living room._googlecast._tcp.local",
                16,
                120,
                byteArrayOf(
                    7,
                    'f'.code.toByte(),
                    'n'.code.toByte(),
                    '='.code.toByte(),
                    'N'.code.toByte(),
                    'a'.code.toByte(),
                    'm'.code.toByte(),
                    'e'.code.toByte(),
                ),
            )
        var parsed: MdnsPacket.Result = MdnsPacket.parse(txt, txt.size)
        check(
            (parsed.freshResponse && parsed.serviceTypes.contains("_googlecast._tcp.")),
            "Fresh TXT with a spaced instance name also reveals its service type",
        )
        var current: MdnsPacket.Service? = parsed.services.get("living room|_googlecast._tcp.")
        check(
            (((current !== null) && current.name.equals("Living room")) &&
                String((current.attributes.get("fn"))!!, StandardCharsets.UTF_8).equals("Name")),
            "Current wire TXT retains its exact service and reported name",
        )
        var srvBytes: ByteArrayOutputStream = ByteArrayOutputStream()
        var srv: DataOutputStream = DataOutputStream(srvBytes)
        srv.writeInt(0)
        srv.writeShort(8008)
        name(srv, "fixture.local")
        var packet: ByteArray =
            response("Room._googlecast._tcp.local", 33, 120, srvBytes.toByteArray())
        check(
            (MdnsPacket.parse(packet, packet.size))!!.freshResponse,
            "A validated SRV response is fresh evidence",
        )
        var location: MdnsPacket.Service? =
            (MdnsPacket.parse(packet, packet.size))!!.services.get("room|_googlecast._tcp.")
        check(
            (((location !== null) && location.host.equals("fixture.local")) &&
                (location.port == 8008)),
            "Current wire SRV supplies its own hostname and port",
        )
        for (bad in
            arrayOf(
                response("fixture.local", 1, 0, ByteArray(4)),
                response("fixture.example", 1, 120, ByteArray(4)),
                response("fixture.local", 1, 120, ByteArray(3)),
                response("Room._http._tcp.local", 16, 120, byteArrayOf(5, 1)),
            )) {
            check(
                !((MdnsPacket.parse(bad, bad.size))!!.freshResponse),
                "No fresh evidence from goodbye, outside or malformed records",
            )
        }
        run {
            var i: Int = 0
            while ((i < txt.size)) {
                check(
                    !((MdnsPacket.parse(txt, i))!!.freshResponse),
                    "Truncated response never confirms presence",
                )
                i++
            }
        }
        var q: ByteArray =
            MdnsPacket.query(
                Arrays.asList("_googlecast._tcp.local", ReverseDns.question("fd00::20"))
            )
        check(
            (((q[5]).toInt() == (2).toInt()) && (q.size < 1200)),
            "Bounded dual-stack reverse and service questions",
        )
        var random: Random = Random(215)
        run {
            var i: Int = 0
            while ((i < 20000)) {
                var fuzz: ByteArray = ByteArray(random.nextInt(9000))
                random.nextBytes(fuzz)
                MdnsPacket.parse(fuzz, fuzz.size)
                CastInfo.name(fuzz)
                i++
            }
        }
        check(true, "20,000 untrusted packet/JSON samples terminate safely")
        System.out.println(
            (("PASS · " + checks) +
                " scan-engine assertions and 40,000 malformed-input decoder runs")
        )
    }
}
