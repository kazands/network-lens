import io.github.kazands.networklens.*
import java.net.InetAddress

/** Synthetic protocol boundaries; no sockets or household identifiers. */
object Ipv4ScopeTest {
    private var checks = 0
    private fun check(value: Boolean, message: String) { checks++; if (!value) error(message) }
    @JvmStatic fun main(args: Array<String>) {
        val plan = ScanPlan.create("198.18.0.1", 24, null)
        check(plan.hosts.size == 254 && plan.scope == "198.18.0.0/24 · IPv4 devices only", "Normal IPv4 sweep and explicit scope")
        check(ScanPlan.create("198.18.0.1", 16, null).let { it.sweepOmitted && it.hosts.isEmpty() && it.scope.contains("IPv4 service discovery only") },
            "Large networks retain bounded service discovery and an honest sweep limitation")
        for (range in listOf(null, "", "198.18.0.0/24")) {
            var rejected = false
            try { ScanPlan.create("", 0, range) } catch (error: IllegalArgumentException) { rejected = error.message == ScanPlan.IPV4_REQUIRED }
            check(rejected, "IPv6-only networks cannot silently return an empty successful scan")
        }
        val instance = listOf("Synthetic endpoint", "_airplay", "_tcp", "local")
        val host = listOf("fixture", "local")
        val packet = MdnsPacket.Result().apply {
            freshResponse = true; parsedResponse = true
            records.add(MdnsPacket.Record(instance.drop(1), 12, 120, false, target = instance))
            records.add(MdnsPacket.Record(instance, 33, 120, true, target = host, port = 7000))
            records.add(MdnsPacket.Record(instance, 16, 120, true, attributes = mapOf("model" to "Synthetic model".toByteArray())))
            records.add(MdnsPacket.Record(host, 1, 120, true, address = "198.18.0.2"))
            records.add(MdnsPacket.Record(host, 28, 120, true, address = "fd70::2"))
        }
        val graph = MdnsAssembly()
        val now = System.nanoTime()
        check(graph.accept(InetAddress.getByName("198.18.0.2"), packet, now).single().attributes["model"]!!.toString(Charsets.UTF_8) == "Synthetic model",
            "Mixed A/AAAA records preserve current same-sender IPv4 identity")
        check(graph.accept(InetAddress.getByName("fd70::2"), packet, now).isEmpty(), "IPv6 packet senders cannot enter discovery assembly")
        check(graph.due(now, 4).none { it.kind == 28 }, "No IPv6 address follow-up is queued")
        graph.close()
        check(graph.accept(InetAddress.getByName("198.18.0.2"), packet, now).isEmpty(), "Closed scans cannot publish late metadata")

        var time = 0L
        var received = 0
        var resolved = 0
        val queries = ArrayList<String>()
        val reply = "HTTP/1.1 200 OK\r\nST: ssdp:all\r\nUSN: uuid:synthetic-ipv4\r\n\r\n".toByteArray()
        val result = SsdpDiscovery.discover(object : SsdpDiscovery.Channel {
            override fun send(bytes: ByteArray) { queries.add(bytes.toString(Charsets.US_ASCII)) }
            override fun receive(timeoutMillis: Int): SsdpDiscovery.Packet? {
                time += timeoutMillis * 1_000_000L
                return when (received++) {
                    0 -> SsdpDiscovery.Packet(InetAddress.getByName("fd70::2"), reply, reply.size)
                    1 -> SsdpDiscovery.Packet(InetAddress.getByName("198.18.0.2"), reply, reply.size)
                    else -> null
                }
            }
        }, 12_000_000_000L, { true }, { true }, { time }) { peer, _ ->
            check(peer.address.size == 4, "Even a permissive caller cannot admit an IPv6 SSDP sender"); resolved++
        }
        check(resolved == 1 && result.unique == 1, "Only the current IPv4 SSDP response is retained")
        check(queries.size == 10 && queries.all { it.contains("HOST: 239.255.255.250:1900") && !it.contains("FF02") },
            "Both bounded SSDP waves use IPv4 multicast")
        println("PASS · $checks IPv4 scope, mixed-record identity, unsupported-network and transport checks")
    }
}
