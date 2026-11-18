package io.github.kazands.networklens

/** Scope is independent of address-sweep capacity. Never silently substitute a smaller subnet. */
internal class ScanPlan private constructor(val range: IpRange?, val hosts: List<String>, val sweepOmitted: Boolean) {
    val scope: String = if (range == null) "IPv6 advertised services + this phone's Wi-Fi addresses"
        else IpRange.address(if (range.prefix >= 31) range.first else range.first - 1) + "/" + range.prefix +
            if (sweepOmitted) " · Service discovery only (IPv4 + IPv6); IPv4 address sweep not run" else " · IPv4 + IPv6 advertised services"
    companion object {
        const val MAX_HOSTS = 1024
        fun create(ip: String, prefix: Int, cidr: String?): ScanPlan {
            val requested = cidr?.javaTrim().orEmpty()
            if (ip.isEmpty()) {
                require(requested.isEmpty()) { "This network has no IPv4 range. Use automatic discovery." }
                return ScanPlan(null, emptyList(), false)
            }
            val local = IpRange(ip, prefix)
            val selected = if (requested.isEmpty()) local else {
                val parts = requested.javaSplit("/", -1)
                require(parts.size == 2) { "Enter an IPv4 range such as 192.168.1.0/24." }
                val range = IpRange(parts[0], parts[1].toInt())
                require(range.first >= local.first && range.last <= local.last) { "Choose a range inside your connected Wi-Fi network." }
                require(range.size() <= MAX_HOSTS) { "Choose a smaller range, with no more than 1,024 addresses." }
                range
            }
            val omitted = selected.size() > MAX_HOSTS
            return ScanPlan(selected, if (omitted) emptyList() else selected.hosts(MAX_HOSTS).toList(), omitted)
        }
    }
}
