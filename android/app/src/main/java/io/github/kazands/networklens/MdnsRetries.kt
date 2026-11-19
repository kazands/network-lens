package io.github.kazands.networklens

import java.util.TreeSet
import java.util.concurrent.TimeUnit

/** Coalesce failed resolutions into two fresh PTR questions per type and family. */
class MdnsRetries {
    private class Attempt { var count = 0; var next: Long? = null }
    private val requested = TreeSet<String>()
    private val ipv4 = HashMap<String, Attempt>()
    private val ipv6 = HashMap<String, Attempt>()
    private val recovery = HashSet<String>()
    /** One late opportunity for service types on current, insufficiently identified endpoints. */
    @Synchronized fun recover(types: Collection<String>): Int {
        var added=0
        for(type in types.distinct().sorted()) {
            if(recovery.size>=32)break
            if(request(type) && recovery.add(type)) {
                // A type already receives its normal discovery questions elsewhere. If
                // resolution has not needed this retry queue, recovery gets only one query.
                for (family in listOf(ipv4,ipv6)) family.getOrPut(type) { Attempt().apply { count=2 } }
                added++
            }
        }
        return added
    }

    @Synchronized fun request(type: String?): Boolean {
        if (type == null || type != MdnsPacket.serviceType(type + "local")) return false
        if (!requested.contains(type) && requested.size >= MAX_TYPES) return false
        requested.add(type)
        return true
    }

    /** Includes unsent queries and one reply window after the last opportunity.
     * The caller checks only receiving families; failed/absent sockets cannot drain work. */
    @Synchronized fun pending(v6: Boolean, now: Long): Boolean {
        val family = if (v6) ipv6 else ipv4
        return requested.any { type ->
            val attempt = family[type]
            attempt == null || attempt.count < budget(type) || attempt.next?.let { now - it < 0 } == true
        }
    }

    private fun budget(type: String) = if (type in recovery) 3 else 2

    @Synchronized fun due(v6: Boolean, now: Long, limit: Int): MutableList<String> {
        require(limit in 0..8) { "At most eight local questions" }
        val out = ArrayList<String>()
        val family = if (v6) ipv6 else ipv4
        for (type in requested) {
            if (out.size == limit) break
            var attempt = family[type]
            if (attempt != null && (attempt.count >= budget(type) || attempt.next?.let { now - it < 0 } == true)) continue
            if (attempt == null) { attempt = Attempt(); family[type] = attempt }
            attempt.count++
            attempt.next = now + GAP
            out.add(type)
        }
        return out
    }
    companion object {
        private const val MAX_TYPES = MdnsTypePlan.MAX_TYPES
        private val GAP = TimeUnit.MILLISECONDS.toNanos(1200)
    }
}

