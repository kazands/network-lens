package io.github.kazands.networklens

import java.util.EnumMap

/** At most one lookup per protocol on this endpoint, for this scan only. No paths or peer text. */
internal class MetadataLookups {
    enum class State { QUEUED, SUCCESS, FAILED, WORK_LIMIT, STOPPED, INCOMPLETE }
    data class Entry(val kind: LocalMetadata.Kind, val port: Int, val secure: Boolean,
                     val state: State = State.QUEUED, val reason: MetadataHttp.Reason? = null,
                     val httpStatus: Int? = null, val model: Boolean = false, val manufacturer: Boolean = false) {
        fun description(): String = when (state) {
            State.QUEUED -> "Waiting or checking"
            State.SUCCESS -> "Succeeded" + (if (!model) "; model not reported" else "") +
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

    @Synchronized fun queue(request: LocalMetadata.Request): Boolean {
        if (closed || entries.containsKey(request.kind)) return false
        entries[request.kind] = Entry(request.kind, request.port, request.secure)
        return true
    }
    private fun update(kind: LocalMetadata.Kind, transform: (Entry) -> Entry) {
        val entry = entries[kind] ?: return
        if (!closed && entry.state == State.QUEUED) entries[kind] = transform(entry)
    }
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
            entries[kind] = entry.copy(state = if (stopped) State.STOPPED else State.INCOMPLETE)
        closed = true
    }
    @Synchronized fun snapshot(): List<Entry> = entries.values.toList()
    @Synchronized fun copy(): MetadataLookups = MetadataLookups().also {
        it.entries.putAll(entries); it.closed = closed
    }
}
