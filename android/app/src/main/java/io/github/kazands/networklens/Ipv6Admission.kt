package io.github.kazands.networklens

/** Per-scan probe fairness, not an identity or grouping key. Never merges endpoints. */
internal class Ipv6Admission(private val maximum: Int = 64, private val perOwner: Int = 8) {
    private val addresses = HashSet<String>()
    private val owners = HashMap<String, Int>()
    @Synchronized fun admit(address: String, owner: String): ServiceAdmission.Result {
        if (address.length !in 1..128 || owner.length !in 1..512) return ServiceAdmission.Result.INVALID
        if (address in addresses) return ServiceAdmission.Result.DUPLICATE
        val key = owner.lowercase(java.util.Locale.ROOT)
        if (addresses.size >= maximum || owners.getOrDefault(key, 0) >= perOwner) return ServiceAdmission.Result.LIMIT
        addresses.add(address); owners[key] = owners.getOrDefault(key, 0) + 1
        return ServiceAdmission.Result.ADMITTED
    }
}
