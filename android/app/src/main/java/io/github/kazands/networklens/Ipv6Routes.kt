package io.github.kazands.networklens

import java.net.Inet6Address
import java.net.InetAddress

/** Immutable, explicit Wi-Fi routes; the default route never admits discovery targets. */
class Ipv6Routes(rules: List<Rule>, private val iface: String) {
    data class Rule(@JvmField val destination: String, @JvmField val prefix: Int,
        @JvmField val gateway: String, @JvmField val iface: String, @JvmField val unicast: Boolean)
    private val rules = rules.filter { Address.bytes(it.destination)?.size == 16 && it.prefix in 1..128 }.distinct().toList()
    private fun unicast(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 255
        return bytes.size == 16 && (first and 0xfe == 0xfc || first and 0xe0 == 0x20)
    }
    private fun eligible(rule: Rule): Boolean = rule.unicast && rule.prefix in 64..128 &&
        rule.iface == iface && iface.isNotEmpty() &&
        Address.bytes(rule.destination)?.let(::unicast) == true &&
        Address.bytes(rule.gateway)?.let { InetAddress.getByAddress(it).isLinkLocalAddress && it.size == 16 } == true

    fun gateway(address: InetAddress): String? {
        if (address !is Inet6Address) return null
        // Thread OMR addresses can be ULA or global unicast. Never admit mapped,
        // multicast, link-local, deprecated site-local or NAT64 well-known space.
        if (!unicast(address.address)) return null
        val matches = rules.filter { Address.inPrefix(address.hostAddress!!, it.destination, it.prefix) }
        val longest = matches.maxOfOrNull { it.prefix } ?: return null
        val best = matches.filter { it.prefix == longest }
        if (best.any { !eligible(it) }) return null
        return best.map { it.gateway }.sorted().firstOrNull()
    }
    private fun effectiveRules(): Set<Rule> = rules.filter { rule -> eligible(rule) || rules.any { admitted ->
        eligible(admitted) && rule.prefix >= admitted.prefix && Address.inPrefix(rule.destination, admitted.destination, admitted.prefix)
    } }.toSet()
    fun same(other: Ipv6Routes): Boolean = iface == other.iface && effectiveRules() == other.effectiveRules()

    /** A new overlapping route can change the selected next hop, including a reject route. */
    fun retains(other: Ipv6Routes): Boolean {
        if (iface != other.iface) return false
        for (old in rules.filter(::eligible)) {
            if (old !in other.rules) return false
            if (other.rules.any { added -> added !in rules &&
                added.prefix >= old.prefix && Address.inPrefix(added.destination, old.destination, old.prefix) }) return false
        }
        return true
    }
}
