import io.github.kazands.networklens.*
import java.net.ConnectException
import java.net.SocketTimeoutException

object AdditionalTcpChecksTest {
    private var checks = 0
    private fun check(value: Boolean, message: String) { checks++; if (!value) error(message) }
    private class Clock : TcpCheck.Clock {
        var time = 0L
        override fun nanos() = time
        override fun sleep(millis: Int) { time += millis * 1_000_000L }
    }
    @JvmStatic fun main(args: Array<String>) {
        for (openPort in listOf(8012, 10101, 11095)) {
            val clock = Clock(); val observed = mutableListOf<Int>(); val calls = mutableListOf<Int>()
            val end = AdditionalTcpChecks.run(Long.MAX_VALUE, { true }, { port, timeout, deadline ->
                TcpCheck.confirm({
                    calls.add(port); check(timeout in 1..160, "Connect waits remain bounded")
                    if (port != openPort) throw ConnectException("PRIVATE_FAULT")
                    1
                }, { clock.time >= deadline }, clock)
            }, { port, result -> if (result?.status == TcpCheck.Status.CONFIRMED) observed.add(port) }, clock::nanos)
            check(end == AdditionalTcpChecks.End.FINISHED && observed == listOf(openPort), "Each additional-only responder is reachable")
            check(calls.count { it == openPort } == 2 && calls.size == 4, "Only successful connections are reconfirmed")
        }
        run {
            val clock = Clock(); val records = mutableListOf<TcpCheck.Result?>()
            AdditionalTcpChecks.run(Long.MAX_VALUE, { true }, { _, timeout, deadline ->
                TcpCheck.confirm({ clock.time += timeout * 1_000_000L; throw SocketTimeoutException() }, { clock.time >= deadline }, clock)
            }, { _, result -> records.add(result) }, clock::nanos)
            check(records.size == 3 && records.all { it?.connections == 0 }, "Silent peers never become present")
            check(clock.time == 480_000_000L, "Silent additional pass costs at most three 160ms connections")
        }
        run {
            val clock = Clock(); val records = mutableListOf<TcpCheck.Result?>()
            AdditionalTcpChecks.run(Long.MAX_VALUE, { true }, { _, _, deadline ->
                var attempt = 0
                TcpCheck.confirm({ if (++attempt == 2) throw ConnectException(); 1 }, { clock.time >= deadline }, clock)
            }, { _, result -> records.add(result) }, clock::nanos)
            check(records.all { it?.status == TcpCheck.Status.UNCONFIRMED && it.connections == 1 }, "Transient accepts never establish presence")
        }
        for (deadline in listOf(0L, 150_000_000L)) {
            val clock = Clock(); var calls = 0; val skipped = mutableListOf<Int>()
            val end = AdditionalTcpChecks.run(deadline, { true }, { _, _, stopAt ->
                calls++; clock.time = stopAt
                TcpCheck.confirm({ 1 }, { true }, clock)
            }, { port, result -> if (result == null) skipped.add(port) }, clock::nanos)
            check(end == AdditionalTcpChecks.End.DEADLINE && skipped == listOf(8012, 10101, 11095), "Deadline omissions remain explicit")
            check(calls == if (deadline == 0L) 0 else 1, "No later probe starts at the deadline")
        }
        run {
            val clock = Clock(); var active = true; var calls = 0; var records = 0
            val end = AdditionalTcpChecks.run(Long.MAX_VALUE, { active }, { _, _, _ ->
                calls++; active = false; TcpCheck.confirm({ 1 }, { false }, clock)
            }, { _, _ -> records++ }, clock::nanos)
            check(end == AdditionalTcpChecks.End.CANCELLED && calls == 1 && records == 0, "Cancellation drops late replies and further work")
        }
        run {
            val clock = Clock(); var suppliedDeadline = 0L
            AdditionalTcpChecks.run(Long.MAX_VALUE, { true }, { _, _, deadline ->
                suppliedDeadline = deadline; clock.time = deadline
                TcpCheck.confirm({ 1 }, { true }, clock)
            }, { _, _ -> }, clock::nanos)
            check(suppliedDeadline == 1_800_000_000L, "Per-address work has an independent 1800ms ceiling")
        }
        check(ScanPlan.create("198.18.0.1", 24, null).budgetSeconds == 55L, "Normal /24 scan deadline remains 55 seconds")
        println("AdditionalTcpChecksTest: $checks checks passed")
    }
}
