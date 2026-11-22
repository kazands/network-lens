package io.github.kazands.networklens

import java.util.Collections

/** Bounded, scan-local diagnostics, including peers that never entered the inventory. */
class TcpProbeJournal {
    data class Attempt(val connected: Boolean, val elapsedMillis: Long, val timeoutMillis: Int,
                       val failure: TransportDiagnostic.Failure = TransportDiagnostic.Failure.NONE)
    data class Entry(val address: String, val port: Int, val source: String, val confirmed: Boolean,
                     val attempts: List<Attempt>, val elapsedMillis: Long)
    class Snapshot internal constructor(entries: List<Entry>, val omitted: Long) {
        val entries: List<Entry> = Collections.unmodifiableList(ArrayList(entries))
        companion object { val EMPTY = Snapshot(emptyList(), 0) }
    }
    private val entries = ArrayList<Entry>()
    private var omitted = 0L
    private var sealed = false

    @Synchronized fun record(address: String, port: Int, source: String, result: TcpCheck.Result,
                             attempts: List<Attempt>, elapsedMillis: Long) {
        if (sealed || result.connections == 0 || result.status == TcpCheck.Status.CANCELLED ||
            Address.bytes(address)?.size != 4 || port !in 1..65535) return
        val confirmed = result.status == TcpCheck.Status.CONFIRMED
        if (entries.size == LIMIT) {
            omitted = (omitted + 1).coerceAtMost(Long.MAX_VALUE - 1)
            // Preserve the failed-reconfirmation evidence even after many successful checks.
            val victim = if (!confirmed) entries.indexOfFirst { it.confirmed } else -1
            if (victim < 0) return
            entries.removeAt(victim)
        }
        entries.add(Entry(address, port, SafeText.label(source, 128), confirmed,
            Collections.unmodifiableList(attempts.take(2).map { it.copy(elapsedMillis = maxOf(0, it.elapsedMillis)) }),
            maxOf(0, elapsedMillis)))
    }
    @Synchronized fun seal() { sealed = true }
    @Synchronized fun snapshot() = Snapshot(entries, omitted)
    companion object { const val LIMIT = 128 }
}
