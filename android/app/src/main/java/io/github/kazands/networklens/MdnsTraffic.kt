package io.github.kazands.networklens

/** Per-socket receive accounting. No packet, peer identifier or history is retained. */
internal class MdnsTraffic(private val health: DiscoveryHealth, private val ipv6: Boolean) {
    private var packets = 0
    var limited = false
        private set

    fun receive(data: ByteArray, length: Int, port: Int, eligible: Boolean): MdnsPacket.Result? {
        if (limited) return null
        count(DiscoveryHealth.Diagnostic.MDNS_RECEIVED)
        count(if (ipv6) DiscoveryHealth.Diagnostic.MDNS_IPV6 else DiscoveryHealth.Diagnostic.MDNS_IPV4)
        // All datagrams still consume the original safety budget, even discarded traffic.
        if (++packets > 1024) {
            limited = true
            count(DiscoveryHealth.Diagnostic.MDNS_LIMIT)
            health.truncated(SOURCE, "1024-packet safety limit reached")
            return null
        }
        if (port != 5353 || !eligible) { count(DiscoveryHealth.Diagnostic.MDNS_IGNORED_SOURCE); return null }
        if (length > MdnsPacket.MAX_PACKET) { count(DiscoveryHealth.Diagnostic.MDNS_OVERSIZED); return null }
        // This describes the header bit, not the validity of the entire query packet.
        if (length in 12..data.size && data[2].toInt() and 0x80 == 0) {
            count(DiscoveryHealth.Diagnostic.MDNS_NON_RESPONSE); return null
        }
        val result = MdnsPacket.parse(data, length)
        count(when {
            !result.parsedResponse -> DiscoveryHealth.Diagnostic.MDNS_REJECTED
            result.freshResponse -> DiscoveryHealth.Diagnostic.MDNS_LIVE
            else -> DiscoveryHealth.Diagnostic.MDNS_NO_LIVE_DETAILS
        })
        return result
    }

    private fun count(event: DiscoveryHealth.Diagnostic) = health.diagnostic(SOURCE, event)
    companion object { const val SOURCE = "mDNS type and hostname queries" }
}
