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

/** Sanitized router-reply reproductions: wire validity is separate from useful identity. */
object ResolverTest {
    private var checks: Int = 0

    private fun check(ok: Boolean, message: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(message)
        }
    }

    private fun encoded(vararg labels: ByteArray): ByteArray {
        var out: ByteArrayOutputStream = ByteArrayOutputStream()
        for (label in labels) {
            out.write(label.size)
            out.write(label)
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun name(value: String): ByteArray {
        var labels: MutableList<ByteArray> = ArrayList()
        for (part in java.util.regex.Pattern.compile("\\.").split(value, 0)) {
            labels.add(part.toByteArray(StandardCharsets.US_ASCII))
        }
        return encoded(*labels.toTypedArray())
    }

    private fun reply(vararg values: ByteArray): ByteArray {
        var question: ByteArray = ReverseDns.query("192.0.2.20", 534)
        question[2] = (133).toByte()
        question[3] = (128).toByte()
        question[7] = (values.size).toByte()
        var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
        bytes.write(question)
        var out: DataOutputStream = DataOutputStream(bytes)
        for (value in values) {
            out.writeShort(49164)
            out.writeShort(12)
            out.writeShort(1)
            out.writeInt(120)
            out.writeShort(value.size)
            out.write(value)
        }
        return bytes.toByteArray()
    }

    private fun parse(b: ByteArray): ReverseDns.Result {
        return ReverseDns.response(b, b.size, 534, "192.0.2.20")
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        for (value in
            arrayOf(
                "02:11:22:33:44:55",
                "02-11-22-33-44-55",
                "021122334455",
                "00:00:00:00:00:00",
                "FF:FF:FF:FF:FF:FF",
                "192.0.2.20",
            )) {
            var r: ReverseDns.Result = parse(reply(name(value)))
            check(
                (r.status === ReverseDns.Status.POSITIVE),
                "A matching PTR identifier is valid DNS data",
            )
            check(
                r.name.isEmpty(),
                "A MAC-shaped or address-only identifier is not promoted to a hostname",
            )
            check(
                r.identifier.equals(value),
                "Retain the reported identifier without asserting a hardware address",
            )
        }
        var host: ByteArray = name("fixture.home")
        var mac: ByteArray = name("02:11:22:33:44:55")
        for (order in arrayOf(arrayOf(host, mac), arrayOf(mac, host))) {
            var r: ReverseDns.Result = parse(reply(*order))
            check(
                (r.name.equals("fixture.home") && r.identifier.equals("02:11:22:33:44:55")),
                "A useful hostname wins in either PTR arrival order and the identifier remains separate",
            )
        }
        for (order in
            arrayOf(
                arrayOf(name("zeta.home"), name("Alpha.home")),
                arrayOf(name("Alpha.home"), name("zeta.home")),
            )) {
            check(
                (parse(reply(*order)))!!.name.equals("Alpha.home"),
                "Deterministic selection of multiple PTR names",
            )
        }
        for (labels in
            arrayOf(
                arrayOf(byteArrayOf('a'.code.toByte(), '.'.code.toByte(), 'b'.code.toByte())),
                arrayOf(byteArrayOf('a'.code.toByte(), '\\'.code.toByte(), 'b'.code.toByte())),
                arrayOf(byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte())),
                arrayOf(byteArrayOf('a'.code.toByte(), 10, 'b'.code.toByte())),
                arrayOf(byteArrayOf((195).toByte(), (169).toByte())),
                arrayOf(byteArrayOf((255).toByte())),
            )) {
            var r: ReverseDns.Result = parse(reply(encoded(*labels)))
            check(
                ((r.status === ReverseDns.Status.POSITIVE) && r.name.isEmpty()),
                "Valid binary DNS labels remain metadata rather than malformed packets or hostnames",
            )
            check(
                (((r.identifier.indexOf('\\') >= 0) && !(r.identifier.contains("\n"))) &&
                    !(r.identifier.contains("\u0000"))),
                "Escape non-host bytes without introducing controls or ambiguous label separators",
            )
        }
        check(
            (parse(
                    reply(
                        encoded(
                            byteArrayOf('a'.code.toByte(), '.'.code.toByte(), 'b'.code.toByte())
                        )
                    )
                ))!!
                .identifier
                .equals("a\\046b"),
            "A dot inside one wire label is distinct from a label separator",
        )
        check(
            (parse(reply(name("a.b"))))!!.name.equals("a.b"),
            "Actual label separators still form a hostname",
        )
        var good: ByteArray = reply(mac)
        run {
            var i: Int = 0
            while ((i < good.size)) {
                check(
                    ((ReverseDns.response(good, i, 534, "192.0.2.20"))!!.status ===
                        ReverseDns.Status.INVALID),
                    "Every truncated router-reply prefix is rejected",
                )
                i++
            }
        }
        check(
            ((ReverseDns.response(good, good.size, 535, "192.0.2.20"))!!.status ===
                ReverseDns.Status.INVALID),
            "Transaction ID correlation remains required",
        )
        check(
            ((ReverseDns.response(good, good.size, 534, "192.0.2.21"))!!.status ===
                ReverseDns.Status.INVALID),
            "Question-to-address correlation remains required",
        )
        var bad: ByteArray = good.clone()
        bad[3] = (131).toByte()
        check(
            ((parse(bad))!!.status === ReverseDns.Status.INVALID),
            "NXDOMAIN plus an asserted PTR answer is contradictory",
        )
        bad = good.clone()
        bad[2] = ((bad[2]).toInt() or (2).toInt()).toByte()
        check(
            ((parse(bad))!!.status === ReverseDns.Status.INVALID),
            "UDP truncation is not accepted as a complete answer",
        )
        var error: ByteArray = ReverseDns.query("192.0.2.20", 534)
        error[2] = (133).toByte()
        error[3] = (130).toByte()
        check(
            (((parse(error))!!.status === ReverseDns.Status.ERROR) &&
                (parse(error))!!.reason.contains("2")),
            "A correlated server error is distinguished from malformed DNS",
        )
        var negative: ByteArray = ReverseDns.query("192.0.2.20", 534)
        negative[2] = (133).toByte()
        negative[3] = (131).toByte()
        check(
            ((parse(negative))!!.status === ReverseDns.Status.NEGATIVE),
            "A well-formed no-name response is normal completion",
        )
        for (reverse in booleanArrayOf(false, true)) {
            var hints: IdentityHints = IdentityHints()
            if (reverse) {
                hints.offer("Name", "hubv2-fixture", "DNS hostname", 40)
            }
            hints.offer("Name", "0123456789ABCDEF-FEDCBA9876543210", "mDNS instance", 65)
            check(
                (hints.offer("Name", "hubv2-fixture", "DNS hostname", 40))!!
                    .value
                    .equals("hubv2-fixture"),
                "Useful DNS hostname beats an opaque service identifier in either order",
            )
            check(
                (hints.offer("Name", "Kitchen hub", "Friendly TXT", 90))!!
                    .value
                    .equals("Kitchen hub"),
                "A useful friendly name retains priority",
            )
        }
        check(
            (IdentityHints.specificName("Cafe-Beef") &&
                !(IdentityHints.specificName("01234567-89ab-cdef-0123-456789abcdef"))),
            "Short human labels are not discarded as opaque IDs",
        )
        var health: DiscoveryHealth = DiscoveryHealth()
        health.resolving("fixture", "_http._tcp.")
        health.resolveFailed(
            "fixture",
            "No usable data after 4001 ms (registered); first: Android registration error 4",
        )
        var status: String? = (health.sources())!!.get("mDNS _http._tcp.")
        check(
            (((status)!!.startsWith("Failed") && (status)!!.contains("registered")) &&
                !((status)!!.contains("Service resolution failed"))),
            "Preserve the specific final outcome instead of replacing it with a generic label",
        )
        var random: Random = Random(216)
        run {
            var i: Int = 0
            while ((i < 20000)) {
                var b: ByteArray = ByteArray(random.nextInt(4200))
                random.nextBytes(b)
                ReverseDns.response(b, b.size, 534, "192.0.2.20")
                i++
            }
        }
        check(true, "Malformed-reply decoder samples terminate")
        System.out.println(
            (("PASS · " + checks) + " resolver assertions and 20,000 malformed-input runs")
        )
    }
}
