import io.github.kazands.networklens.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.SocketTimeoutException
import java.util.Random

/** Synthetic DNS packets. No household identifiers, network access or downloaded fingerprints. */
object LocalNetworkNamesTest {
    private var checks = 0
    private fun check(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
    private fun name(value: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        for (label in value.split('.')) { bytes.write(label.length); bytes.write(label.toByteArray()) }
        bytes.write(0)
    }.toByteArray()
    private data class RR(val owner: String, val type: Int, val value: ByteArray)
    private fun reply(query: ByteArray, records: List<RR>, rcode: Int = 0): ByteArray {
        val q = query.clone().also { it[2] = 0x81.toByte(); it[3] = (0x80 or rcode).toByte(); it[7] = records.size.toByte() }
        return ByteArrayOutputStream().also { bytes ->
            bytes.write(q)
            val out = DataOutputStream(bytes)
            for (rr in records) {
                out.write(name(rr.owner)); out.writeShort(rr.type); out.writeShort(1); out.writeInt(0)
                out.writeShort(rr.value.size); out.write(rr.value)
            }
        }.toByteArray()
    }
    @JvmStatic fun main(args: Array<String>) {
        val ip = "192.0.2.45"
        val host = "hydrawise-a1b2.home"
        val target = Address.bytes(ip)!!
        fun reverse(q: ByteArray, hosts: List<String> = listOf(host)) = reply(q, hosts.map { RR(ReverseDns.question(ip), 12, name(it)) })
        fun forward(q: ByteArray, bytes: ByteArray = target, owner: String = host) = reply(q, listOf(RR(owner, 1, bytes)))
        fun lookup(channel: LocalNetworkNames.Channel, active: () -> Boolean = { true }, deadline: Long = System.nanoTime() + 1_000_000_000) =
            LocalNetworkNames.lookup(ip, 42, deadline, active, channel)
        var calls = 0
        val good = LocalNetworkNames.Channel { q, timeout ->
            check(timeout in 1..350, "Per-query timeout bounded")
            calls++; if (calls == 1) reverse(q) else forward(q)
        }
        val result = lookup(good)
        check(result == LocalNetworkNames.Result(LocalNetworkNames.Status.CONFIRMED, host), "TTL-zero reverse/forward agreement usable in this transaction")
        check(calls == 2, "Exactly two queries; no resolver search domains or extra probes")
        calls = 0
        check(lookup(LocalNetworkNames.Channel { q, _ -> calls++; if (calls == 1) reverse(q) else forward(q, Address.bytes("192.0.2.46")!!) }).status == LocalNetworkNames.Status.NOT_CONFIRMED, "Wrong forward address rejected")
        calls = 0
        check(lookup(LocalNetworkNames.Channel { q, _ -> calls++; if (calls == 1) reverse(q) else forward(q, owner = "other.home") }).status == LocalNetworkNames.Status.NOT_CONFIRMED, "Unrelated answer owner rejected")
        calls = 0
        check(lookup(LocalNetworkNames.Channel { q, _ -> calls++; reverse(q, listOf(host, "bhyve-bh1-abcd.home")) }).status == LocalNetworkNames.Status.AMBIGUOUS && calls == 1, "Multiple reverse names do not select a convenient fingerprint")
        val many = lookup(LocalNetworkNames.Channel { q, _ -> reverse(q, (1..7).map { "fixture-$it.home" }) })
        check(many.status == LocalNetworkNames.Status.AMBIGUOUS && many.returnedNames.size == 4 && many.omittedNames == 3, "Returned name retention is bounded and omissions are explicit")
        calls = 0
        val mismatch = lookup(LocalNetworkNames.Channel { q, _ -> calls++; if (calls == 1) reverse(q) else forward(q, Address.bytes("192.0.2.46")!!) })
        check(mismatch.name.isEmpty() && mismatch.returnedNames == listOf(host), "Rejected candidate is retained for troubleshooting without becoming a usable name")
        check(lookup(LocalNetworkNames.Channel { q, _ -> reply(q, emptyList(), 3) }).status == LocalNetworkNames.Status.NO_NAME, "NXDOMAIN completes normally")
        check(lookup(LocalNetworkNames.Channel { _, _ -> throw SocketTimeoutException() }).status == LocalNetworkNames.Status.TIMED_OUT, "Silent resolver has explicit outcome")
        calls = 0
        check(lookup(LocalNetworkNames.Channel { _, _ -> calls++; byteArrayOf() }, { false }).status == LocalNetworkNames.Status.STOPPED && calls == 0, "Stop prevents all queries")
        var active = true
        check(lookup(LocalNetworkNames.Channel { q, _ -> active = false; reverse(q) }, { active }).status == LocalNetworkNames.Status.STOPPED, "Stop between responses prevents forward request")
        calls = 0
        check(lookup(LocalNetworkNames.Channel { _, _ -> calls++; byteArrayOf() }, deadline = System.nanoTime() - 1).status == LocalNetworkNames.Status.TIMED_OUT && calls == 0, "Expired deadline sends nothing")
        val q = LocalNetworkNames.query(host, 1, 43)
        val packet = forward(q)
        for (size in 0 until packet.size) check(LocalNetworkNames.confirms(packet.copyOf(size), host, 1, 43, target) == null, "Truncated forward reply rejected")
        check(LocalNetworkNames.confirms(packet, host, 1, 44, target) == null, "Transaction ID correlated")
        check(LocalNetworkNames.confirms(packet, "different.home", 1, 43, target) == null, "Question correlated")
        check(LocalNetworkNames.confirms(packet.clone().also { it[2] = 0x83.toByte() }, host, 1, 43, target) == null, "Truncated UDP flag rejected")
        val alias = "controller.home"
        val cname = reply(q, listOf(RR(host, 5, name(alias)), RR(alias, 1, target)))
        check(LocalNetworkNames.confirms(cname, host, 1, 43, target) == true, "In-packet CNAME chain may confirm address")
        check(LocalNetworkNames.confirms(reply(q, listOf(RR(host, 5, name(alias)), RR(alias, 5, name(host)))), host, 1, 43, target) == null, "CNAME loop rejected")
        check(LocalNetworkNames.confirms(reply(q, listOf(RR(host, 5, name(alias)), RR(host, 5, name("different.home")))), host, 1, 43, target) == null, "Conflicting aliases rejected")
        val v6 = Address.bytes("2001:db8::45")!!
        val q6 = LocalNetworkNames.query(host, 28, 44)
        check(LocalNetworkNames.confirms(reply(q6, listOf(RR(host, 28, v6))), host, 28, 44, v6) == true, "AAAA bytes supported")
        for (value in listOf("10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.1.1", "fd00::1", "fe80::1%wlan0"))
            check(LocalNetworkNames.privateResolver(value), "Private resolver eligible after on-link check")
        for (value in listOf("8.8.8.8", "1.1.1.1", "172.15.0.1", "172.32.0.1", "127.0.0.1", "0.0.0.0", "2001:4860:4860::8888", "ff02::fb", "dns.home"))
            check(!LocalNetworkNames.privateResolver(value), "Public, unspecified and non-numeric resolver excluded")
        val random = Random(418)
        repeat(10000) {
            val bytes = ByteArray(random.nextInt(4200)).also(random::nextBytes)
            LocalNetworkNames.confirms(bytes, host, 1, 43, target)
        }
        println("PASS · $checks local network name assertions and 10,000 malformed packets")
    }
}
