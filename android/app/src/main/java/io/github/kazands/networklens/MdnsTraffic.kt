package io.github.kazands.networklens

/** Per-socket receive accounting. No packet, peer identifier or history is retained. */
internal class MdnsTraffic(private val health: DiscoveryHealth, private val ipv6: Boolean) {
    private var packets = 0
    private var bytes = 0L
    private var responses = 0
    private var queries = 0
    var limited = false
        private set

    fun receive(data: ByteArray, length: Int, port: Int, eligible: Boolean,
                onQuestions: (List<MdnsPacket.Question>) -> Unit = {}): MdnsPacket.Result? {
        if (limited) return null
        count(DiscoveryHealth.Diagnostic.MDNS_RECEIVED)
        count(if (ipv6) DiscoveryHealth.Diagnostic.MDNS_IPV6 else DiscoveryHealth.Diagnostic.MDNS_IPV4)
        ++packets; bytes += maxOf(0, length).toLong()
        health.diagnostic(SOURCE, DiscoveryHealth.Diagnostic.MDNS_BYTES, length.coerceIn(0, MdnsPacket.MAX_PACKET + 1))
        // Cheap receive work and full response decoding have independent finite budgets.
        if (packets > MAX_DATAGRAMS) return stop("4096-datagram receive limit reached")
        if (bytes > MAX_BYTES) return stop("8 MiB receive byte limit reached")
        if (port != 5353 || !eligible) { count(DiscoveryHealth.Diagnostic.MDNS_IGNORED_SOURCE); return null }
        if (length > MdnsPacket.MAX_PACKET) { count(DiscoveryHealth.Diagnostic.MDNS_OVERSIZED); return null }
        // This describes the header bit, not the validity of the entire query packet.
        if (length in 12..data.size && data[2].toInt() and 0x80 == 0) {
            count(DiscoveryHealth.Diagnostic.MDNS_NON_RESPONSE)
            if (length <= 1200 && queries++ < 256) {
                count(DiscoveryHealth.Diagnostic.MDNS_QUERY_DECODED)
                val questions = MdnsPacket.multicastQuestions(data, length)
                if (questions.isNotEmpty()) onQuestions(questions)
            } else count(DiscoveryHealth.Diagnostic.MDNS_QUERY_SKIPPED)
            return null
        }
        if (length < 12 || length > data.size) { count(DiscoveryHealth.Diagnostic.MDNS_REJECTED); return null }
        if (++responses > MAX_RESPONSES) return stop("1024-response processing limit reached")
        count(DiscoveryHealth.Diagnostic.MDNS_DECODED)
        val result = MdnsPacket.parse(data, length)
        count(when {
            !result.parsedResponse -> DiscoveryHealth.Diagnostic.MDNS_REJECTED
            result.freshResponse -> DiscoveryHealth.Diagnostic.MDNS_LIVE
            else -> DiscoveryHealth.Diagnostic.MDNS_NO_LIVE_DETAILS
        })
        return result
    }

    private fun stop(reason: String): MdnsPacket.Result? {
        limited = true; count(DiscoveryHealth.Diagnostic.MDNS_LIMIT)
        health.truncated(SOURCE, reason); return null
    }

    private fun count(event: DiscoveryHealth.Diagnostic) = health.diagnostic(SOURCE, event)
    companion object {
        const val SOURCE = "mDNS type and hostname queries"
        const val MAX_DATAGRAMS = 4096
        const val MAX_RESPONSES = 1024
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
