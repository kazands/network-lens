import io.github.kazands.networklens.PlayStationDiscovery as P
import java.net.InetAddress
import java.util.ArrayDeque
import java.util.Random
import java.util.concurrent.TimeUnit

object PlayStationDiscoveryTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
    private fun response(status: String = "200 OK", type: String = "PS5", version: String = "00030010", extra: String = "") =
        "HTTP/1.1 $status\nhost-type:$type\ndevice-discovery-protocol-version:$version\nhost-name:Fixture console\n$extra\n".toByteArray()
    private class Fixture : P.Channel {
        var time = 1_000_000_000L
        var onReceive: (() -> Unit)? = null
        var flood = false
        val packets = ArrayDeque<P.Packet>()
        val sent = ArrayList<String>()
        override fun send(bytes: ByteArray) { sent.add(String(bytes)) }
        override fun receive(timeoutMillis: Int): P.Packet? {
            check(timeoutMillis in 1..250, "Receive timeout remains bounded")
            time += TimeUnit.MILLISECONDS.toNanos(if (packets.isEmpty() && !flood) timeoutMillis.toLong() else 1)
            onReceive?.invoke()
            return if (flood) P.Packet(InetAddress.getByName("192.0.2.40"), P.PORT, ByteArray(1), 1) else packets.poll()
        }
        fun add(ip: String = "192.0.2.40", port: Int = P.PORT, data: ByteArray = response()) {
            packets.add(P.Packet(InetAddress.getByName(ip), port, data, data.size))
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        for (status in listOf("200 OK", "200 Ok", "200", "620 Server Standby", "620")) {
            val bytes = response(status); val parsed = P.parse(bytes, bytes.size)!!
            check(parsed.name == "Fixture console" && parsed.standby == status.startsWith("620"), "Ready/standby reports retain state and name")
        }
        for (data in listOf(response(type = "PS4"), response(version = "00020020"), response(type = "PS5\u0000"), response(status = "500 Error"),
            response(extra = "HOST-TYPE:PS5\n"), response(extra = "device-discovery-protocol-version:00030010\n"),
            response(extra = "host-name:Duplicate fixture\n"), ByteArray(P.MAX_PACKET + 1), response(extra = "x".repeat(513))))
            check(P.parse(data, data.size) == null, "Malformed, conflicting and wrong-family responses cannot identify PS5")
        val request = String(P.query())
        check(request.startsWith("SRCH * HTTP/1.1\n") && !request.contains("WAKEUP") && !request.contains("credential"), "Discovery request has no wake or account action")
        val accepted = ArrayList<String>()
        val f = Fixture().apply { add(); add(); add("192.0.2.41", 1900); add("198.51.100.2"); add("192.0.2.42") }
        val result = P.discover(f, f.time + TimeUnit.SECONDS.toNanos(55), { true }, { it.hostAddress.startsWith("192.0.2.") }, { f.time }) { peer, _ -> accepted.add(peer.hostAddress) }
        check(f.sent.size == 2 && result.sent == 2, "At most two discovery requests are sent")
        check(accepted == listOf("192.0.2.40", "192.0.2.42"), "Only unique in-scope replies from port 9302 are admitted")
        check(!result.limited && !result.interrupted && result.devices == 2, "Normal discovery completes its finite response window")
        val flood = Fixture().apply { flood = true }
        val capped = P.discover(flood, Long.MAX_VALUE, { true }, { true }, { flood.time }) { _, _ -> error("Malformed flood admitted") }
        check(capped.limited && capped.packets == 64, "Untrusted traffic cannot exceed the raw packet cap")
        val many = Fixture().apply { for (n in 1..40) add("192.0.2.$n") }
        var count = 0
        val deviceCap = P.discover(many, Long.MAX_VALUE, { true }, { true }, { many.time }) { _, _ -> count++ }
        check(deviceCap.limited && count == 32, "Discovery cannot admit more than 32 console responses")
        val expired = Fixture()
        val expiredResult = P.discover(expired, expired.time, { true }, { true }, { expired.time }) { _, _ -> error("Expired response") }
        check(expired.sent.isEmpty() && expiredResult.interrupted, "An expired scan deadline sends nothing and reports incomplete work")
        val stopped = Fixture().apply { add() }; var active = true
        stopped.onReceive = { active = false }
        val stoppedResult = P.discover(stopped, Long.MAX_VALUE, { active }, { true }, { stopped.time }) { _, _ -> error("Late response after cancellation") }
        check(stoppedResult.interrupted && stoppedResult.devices == 0, "Cancellation rejects a reply already waiting on the socket")
        val random = Random(18)
        repeat(10000) { val bytes = ByteArray(random.nextInt(5000)); random.nextBytes(bytes); check(P.parse(bytes, bytes.size) == null, "Malformed input cannot identify a console") }
        println("PASS · $checks PlayStation parsing, scope, budget and cancellation checks")
    }
}
