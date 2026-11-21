package io.github.kazands.networklens

import java.util.EnumMap

/** At most one lookup per protocol on this endpoint, for this scan only. No paths or peer text. */
internal class MetadataLookups {
    enum class State { QUEUED, SUCCESS, FAILED, WORK_LIMIT, STOPPED, INCOMPLETE }
    data class Entry(val kind: LocalMetadata.Kind, val port: Int, val secure: Boolean,
                     val state: State = State.QUEUED, val reason: MetadataHttp.Reason? = null,
                     val httpStatus: Int? = null, val model: Boolean = false, val manufacturer: Boolean = false,
                     val startedNanos: Long = 0, val elapsedMs: Long? = null,
                     val queuedNanos: Long = 0, val queueWaitMs: Long? = null, val budgetAtStartMs: Long? = null) {
        fun timing(): String = listOfNotNull(queueWaitMs?.let { "queue wait ${it}ms" },
            budgetAtStartMs?.let { "budget at start ${it}ms" }, elapsedMs?.let { "request elapsed ${it}ms" }).joinToString("; ")
        fun description(): String = when (state) {
            State.QUEUED -> "Waiting or checking"
            State.SUCCESS -> if (kind == LocalMetadata.Kind.ESCL) "Succeeded; scanner information is shown separately" else "Succeeded" + (if (!model) "; model not reported" else "") +
                (if (!manufacturer) "; manufacturer not reported" else "")
            State.FAILED -> (reason?.detail ?: "Metadata request could not complete") +
                (if (reason == MetadataHttp.Reason.HTTP_STATUS && httpStatus != null) " ($httpStatus)" else "")
            State.WORK_LIMIT -> "Not attempted: work limit reached"
            State.STOPPED -> "Stopped before lookup finished"
            State.INCOMPLETE -> "Lookup did not finish"
        }
    }
    private val entries = EnumMap<LocalMetadata.Kind, Entry>(LocalMetadata.Kind::class.java)
    private var closed = false
    private val attempts = ArrayList<Entry>()
    private var omittedAttempts = 0
    private fun archive(entry: Entry) {
        if (attempts.size < 64) attempts.add(entry) else if (omittedAttempts < Int.MAX_VALUE) omittedAttempts++
    }
    @Synchronized fun history(): List<Entry> = attempts.toList() + entries.values.toList()
    @Synchronized fun omissions(): Int = omittedAttempts

    @Synchronized fun queue(request: LocalMetadata.Request, now: Long = System.nanoTime()): Boolean {
        if (closed || entries.containsKey(request.kind)) return false
        entries[request.kind] = Entry(request.kind, request.port, request.secure, queuedNanos = now)
        return true
    }
    /** Called by the single admitted worker, after the previous candidate failed. */
    @Synchronized fun beginCandidate(request: LocalMetadata.Request, now: Long = System.nanoTime(),
                                    deadline: Long = Long.MAX_VALUE, queuedAt: Long? = null): Boolean {
        if (closed) return false
        val prior = entries[request.kind] ?: run { queue(request, queuedAt ?: now); entries.getValue(request.kind) }
        fun started(entry: Entry): Entry = entry.copy(startedNanos = now,
            queueWaitMs = ((now - (queuedAt ?: entry.queuedNanos)) / 1_000_000).coerceAtLeast(0),
            budgetAtStartMs = if (deadline == Long.MAX_VALUE) null else ((deadline - now) / 1_000_000).coerceAtLeast(0))
        if (prior.state == State.QUEUED) {
            if (prior.port != request.port || prior.secure != request.secure) return false
            if (prior.startedNanos == 0L) entries[request.kind] = started(prior)
            return true
        }
        if (prior.state != State.FAILED) return false
        archive(prior)
        entries[request.kind] = started(Entry(request.kind, request.port, request.secure, queuedNanos = queuedAt ?: now))
        return true
    }
    private fun update(kind: LocalMetadata.Kind, transform: (Entry) -> Entry) {
        val entry = entries[kind] ?: return
        if (!closed && entry.state == State.QUEUED) entries[kind] = transform(entry).copy(elapsedMs = elapsed(entry))
    }
    private fun elapsed(entry: Entry): Long? = if (entry.startedNanos == 0L) null else ((System.nanoTime() - entry.startedNanos) / 1000000).coerceAtLeast(0)
    @Synchronized fun success(kind: LocalMetadata.Kind, report: LocalMetadata.Report) = update(kind) {
        it.copy(state = State.SUCCESS, model = IdentityHints.specific(report.hints["Model"]),
            manufacturer = IdentityHints.specific(report.hints["Manufacturer"]))
    }
    @Synchronized fun failure(kind: LocalMetadata.Kind, error: MetadataHttp.Failure) = update(kind) {
        it.copy(state = State.FAILED, reason = error.reason,
            httpStatus = error.httpStatus?.takeIf { code -> code in 100..599 })
    }
    @Synchronized fun limit(kind: LocalMetadata.Kind) = update(kind) { it.copy(state = State.WORK_LIMIT) }
    @Synchronized fun incomplete(kind: LocalMetadata.Kind) = update(kind) { it.copy(state = State.INCOMPLETE) }
    /** Sealing before closing sockets prevents late workers replacing the terminal outcome. */
    @Synchronized fun finish(stopped: Boolean) {
        if (closed) return
        for ((kind, entry) in entries) if (entry.state == State.QUEUED)
            entries[kind] = entry.copy(state = if (stopped) State.STOPPED else State.INCOMPLETE, elapsedMs = elapsed(entry))
        closed = true
    }
    @Synchronized fun snapshot(): List<Entry> = entries.values.toList()
    @Synchronized fun copy(): MetadataLookups = MetadataLookups().also {
        it.entries.putAll(entries); it.closed = closed
        it.attempts.addAll(attempts); it.omittedAttempts = omittedAttempts
    }
}
