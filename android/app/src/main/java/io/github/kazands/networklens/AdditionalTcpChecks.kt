package io.github.kazands.networklens

import java.util.Collections
import java.util.concurrent.TimeUnit

/** Reachability only: these ports are shared by several products, not a camera fingerprint. */
internal object AdditionalTcpChecks {
    const val SOURCE = "Additional TCP checks"
    const val CONNECT_TIMEOUT_MS = 160
    const val PEER_BUDGET_MS = 1800L
    val PORTS: List<Int> = Collections.unmodifiableList(listOf(8012, 10101, 11095))
    enum class End { FINISHED, DEADLINE, CANCELLED }
    fun source(port: Int) = "$SOURCE · port $port"

    /** One bounded pass. A null result identifies a port omitted at the deadline. */
    fun run(scanDeadline: Long, active: () -> Boolean,
            probe: (Int, Int, Long) -> TcpCheck.Result,
            record: (Int, TcpCheck.Result?) -> Unit,
            now: () -> Long = System::nanoTime): End {
        val deadline = minOf(scanDeadline, now() + TimeUnit.MILLISECONDS.toNanos(PEER_BUDGET_MS))
        for ((index, port) in PORTS.withIndex()) {
            if (!active() || Thread.currentThread().isInterrupted) return End.CANCELLED
            if (now() >= deadline) {
                PORTS.drop(index).forEach { record(it, null) }
                return End.DEADLINE
            }
            val result = probe(port, CONNECT_TIMEOUT_MS, deadline)
            if (!active() || Thread.currentThread().isInterrupted) return End.CANCELLED
            if (now() >= deadline) {
                PORTS.drop(index).forEach { record(it, null) }
                return End.DEADLINE
            }
            if (result.status == TcpCheck.Status.CANCELLED) return End.CANCELLED
            record(port, result)
        }
        return End.FINISHED
    }
}
