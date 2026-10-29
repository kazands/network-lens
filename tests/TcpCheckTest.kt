@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import dev.netlens.TcpCheck
import java.io.*
import java.util.concurrent.atomic.*

object TcpCheckTest {
    var checks: Int = 0

    fun check(yes: Boolean, message: String): Unit {
        checks++
        if (!(yes)) {
            throw AssertionError(message)
        }
    }

    class Clock : TcpCheck.Clock {
        var now: Long = 0L

        var sleeps: Int = 0

        var action: Runnable = Runnable(callback1@{})

        override fun nanos(): Long {
            return now
        }

        override fun sleep(ms: Int): Unit {
            check(((ms > 0) && (ms <= 25)), "Cancellation polling is bounded")
            now += (ms * 1000000L)
            sleeps++
            action.run()
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var calls: AtomicInteger = AtomicInteger()
        var clock: Clock = Clock()
        var r: TcpCheck.Result =
            TcpCheck.confirm(
                callback2@{
                    calls.incrementAndGet()
                    return@callback2 54
                },
                callback3@{ false },
                clock,
            )
        check(
            ((((r.status === TcpCheck.Status.CONFIRMED) && (r.connections == 2)) &&
                (r.latency == 54)) && (calls.get() == 2)),
            "A stable peer requires two separately executed connections",
        )
        check((clock.now >= 200000000L), "Reconfirmation waits at least 200 ms")
        calls.set(0)
        r =
            TcpCheck.confirm(
                callback4@{
                    if ((calls.incrementAndGet() == 2)) {
                        throw IOException("Peer left")
                    }
                    return@callback4 54
                },
                callback5@{ false },
                Clock(),
            )
        check(
            ((((r.status === TcpCheck.Status.UNCONFIRMED) && (r.connections == 1)) &&
                (r.latency == -(1))) && (r.failure is IOException)),
            "One accepted connection is insufficient when the peer disappears",
        )
        calls.set(0)
        r =
            TcpCheck.confirm(
                callback6@{
                    calls.incrementAndGet()
                    throw java.net.ConnectException("Connection refused")
                },
                callback7@{ false },
                Clock(),
            )
        check(
            (((r.status === TcpCheck.Status.UNCONFIRMED) && (r.connections == 0)) &&
                (calls.get() == 1)),
            "A refusal never counts as a successful presence check",
        )
        calls.set(0)
        r =
            TcpCheck.confirm(
                callback8@{
                    calls.incrementAndGet()
                    return@callback8 1
                },
                callback9@{ true },
                Clock(),
            )
        check(
            ((r.status === TcpCheck.Status.CANCELLED) && (calls.get() == 0)),
            "Stop before the first attempt opens no socket",
        )
        var stop: AtomicBoolean = AtomicBoolean()
        clock = Clock()
        clock.action = Runnable(callback10@{ stop.set(true) })
        calls.set(0)
        r =
            TcpCheck.confirm(
                callback11@{
                    calls.incrementAndGet()
                    return@callback11 1
                },
                stop::get,
                clock,
            )
        check(
            (((r.status === TcpCheck.Status.CANCELLED) && (calls.get() == 1)) &&
                (clock.now <= 25000000L)),
            "Stop during spacing prevents a second connection promptly",
        )
        stop.set(false)
        calls.set(0)
        r =
            TcpCheck.confirm(
                callback12@{
                    calls.incrementAndGet()
                    stop.set(true)
                    return@callback12 1
                },
                stop::get,
                Clock(),
            )
        check(
            ((r.status === TcpCheck.Status.CANCELLED) && (calls.get() == 1)),
            "Stop during the first connection rejects the result",
        )
        calls.set(0)
        r =
            TcpCheck.confirm(
                callback13@{
                    if ((calls.incrementAndGet() == 2)) {
                        throw SecurityException("Denied")
                    }
                    return@callback13 1
                },
                callback14@{ false },
                Clock(),
            )
        check(
            (((r.status === TcpCheck.Status.UNCONFIRMED) && (r.connections == 1)) &&
                (r.failure is SecurityException)),
            "Permission failure remains available to source error reporting",
        )
        r = TcpCheck.confirm(callback15@{ -(1) }, callback16@{ false }, Clock())
        check(
            ((r.status === TcpCheck.Status.UNCONFIRMED) && (r.connections == 0)),
            "Invalid transport timing cannot confirm a peer",
        )
        var interrupted: TcpCheck.Clock =
            object : TcpCheck.Clock {
                override fun nanos(): Long {
                    return 0
                }

                override fun sleep(ms: Int): Unit {
                    throw InterruptedException()
                }
            }
        r = TcpCheck.confirm(callback17@{ 1 }, callback18@{ false }, interrupted)
        check(
            ((r.status === TcpCheck.Status.CANCELLED) &&
                (Thread.currentThread())!!.isInterrupted()),
            "Interrupted wait cancels and preserves the interrupt flag",
        )
        Thread.interrupted()
        System.out.println(
            (("PASS · " + checks) + " TCP presence, spacing, cancellation and error assertions")
        )
    }
}
