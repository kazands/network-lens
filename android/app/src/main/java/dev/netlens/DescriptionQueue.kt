package dev.netlens

import java.net.URI
import java.util.ArrayDeque

/** Three distinct same-peer description opportunities, serialized per peer. */
class DescriptionQueue(private val maximumPeers: Int, private val maximumLocations: Int) {
    enum class Offer { START, QUEUED, DUPLICATE, LIMIT }

    private class Plan {
        val seen = HashSet<URI>()
        val waiting = ArrayDeque<URI>()
        var running = false
        var success = false
        var attempts = 0
    }
    private val plans = LinkedHashMap<String, Plan>()

    init { require(maximumPeers > 0 && maximumLocations > 0) }

    @Synchronized fun offer(peer: String?, target: URI?): Offer {
        require(peer != null && peer.length <= 128 && target != null && target.toString().length <= 1024)
        val plan = plans[peer] ?: run {
            if (plans.size >= maximumPeers) return Offer.LIMIT
            Plan().also { plans[peer] = it }
        }
        if (plan.seen.contains(target)) return Offer.DUPLICATE
        if (plan.seen.size >= maximumLocations) return Offer.LIMIT
        plan.seen.add(target)
        plan.waiting.add(target)
        if (plan.running) return Offer.QUEUED
        plan.running = true
        return Offer.START
    }

    @Synchronized fun next(peer: String): URI? {
        val plan = plans[peer] ?: return null
        return plan.waiting.poll().also { if (it == null) plan.running = false }
    }
    @Synchronized fun record(peer: String, success: Boolean) {
        plans[peer]?.let { it.attempts++; it.success = it.success || success }
    }
    @Synchronized fun peers(): Int = plans.size
    @Synchronized fun failedPeers(): Int =
        plans.values.count { !it.running && it.waiting.isEmpty() && it.attempts > 0 && !it.success }
    @Synchronized fun hasSuccess(peer: String): Boolean = plans[peer]?.success == true
}

