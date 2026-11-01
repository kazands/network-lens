package io.github.kazands.networklens

import java.util.concurrent.TimeUnit

/** Current-scan multicast questions can replace an identical scheduled question, not a response. */
internal class MdnsQuestionSuppression {
    private val observed = HashMap<String, Long>()
    private var closed = false
    private fun key(ipv6: Boolean, q: MdnsPacket.Question) = "$ipv6|${q.kind}|${MdnsPacket.key(q.labels)}"

    @Synchronized fun observe(ipv6: Boolean, questions: List<MdnsPacket.Question>, now: Long) {
        if (closed) return
        for (q in questions.take(8)) {
            val key = key(ipv6, q)
            if (key in observed || observed.size < 256) observed[key] = now
        }
    }
    @Synchronized fun suppress(ipv6: Boolean, q: MdnsPacket.Question, now: Long): Boolean {
        if (closed) return false
        val received = observed[key(ipv6, q)] ?: return false
        return now >= received && now - received < TimeUnit.SECONDS.toNanos(1)
    }
    @Synchronized fun close() { closed = true; observed.clear() }
}
