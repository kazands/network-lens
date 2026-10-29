package dev.netlens

import java.util.TreeSet
import java.util.concurrent.TimeUnit

/** Coalesce failed resolutions into two fresh PTR questions per type and family. */
class MdnsRetries {
    private class Attempt { var count = 0; var next = 0L }
    private val requested = TreeSet<String>()
    private val ipv4 = HashMap<String, Attempt>()
    private val ipv6 = HashMap<String, Attempt>()

    @Synchronized fun request(type: String?): Boolean {
        if (type == null || type != MdnsPacket.serviceType(type + "local")) return false
        if (!requested.contains(type) && requested.size >= MAX_TYPES) return false
        requested.add(type)
        return true
    }

    @Synchronized fun due(v6: Boolean, now: Long, limit: Int): MutableList<String> {
        require(limit in 0..8) { "At most eight local questions" }
        val out = ArrayList<String>()
        val family = if (v6) ipv6 else ipv4
        for (type in requested) {
            if (out.size == limit) break
            var attempt = family[type]
            if (attempt != null && (attempt.count >= 2 || now - attempt.next < 0)) continue
            if (attempt == null) { attempt = Attempt(); family[type] = attempt }
            attempt.count++
            attempt.next = now + GAP
            out.add(type)
        }
        return out
    }
    companion object {
        private const val MAX_TYPES = 48
        private val GAP = TimeUnit.MILLISECONDS.toNanos(1200)
    }
}

