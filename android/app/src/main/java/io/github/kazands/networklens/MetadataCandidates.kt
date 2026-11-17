package io.github.kazands.networklens

import java.util.concurrent.TimeUnit

/** Two validated same-peer targets, one worker and one original lookup window. Scan RAM only. */
internal class MetadataCandidates {
    enum class Offer { START, WAIT, DUPLICATE, LIMIT, CLOSED, REJECTED }
    private val seen = LinkedHashSet<LocalMetadata.Request>()
    private val pending = ArrayDeque<LocalMetadata.Request>()
    private var running = false
    private var closed = false
    private var deadline = 0L
    @Synchronized fun offer(request: LocalMetadata.Request, now: Long): Offer {
        if (closed || deadline != 0L && now >= deadline) return Offer.CLOSED
        val original = seen.firstOrNull()
        // IPPS must never fall back to an advertised plaintext IPP target (or vice versa).
        if (original != null && (request.kind != original.kind || request.secure != original.secure)) return Offer.REJECTED
        if (request in seen) return Offer.DUPLICATE
        if (seen.size >= 2) return Offer.LIMIT
        seen.add(request); pending.addLast(request)
        if (running) return Offer.WAIT
        running = true; return Offer.START
    }
    @Synchronized fun next(now: Long, scanDeadline: Long, onIdle: () -> Unit = {}): LocalMetadata.Request? {
        if (closed) return null
        if (deadline == 0L) deadline = minOf(scanDeadline, now + TimeUnit.SECONDS.toNanos(2))
        if (now >= deadline) { onIdle(); close(); return null }
        if (pending.isEmpty()) { onIdle(); running = false; return null }
        return pending.removeFirst()
    }
    @Synchronized fun deadline(): Long = deadline
    @Synchronized fun close() { closed = true; pending.clear(); running = false }
}
