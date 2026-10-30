@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.*
import java.io.*
import java.util.*
import java.util.concurrent.atomic.*

object IcmpEchoTest {
    var checks: Int = 0

    fun check(ok: Boolean, message: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(message)
        }
    }

    class Clock : IcmpEcho.Clock {
        var time: Long = 0L

        var interrupt: Boolean = false

        override fun nanos(): Long {
            return time
        }

        override fun sleep(ms: Int): Unit {
            if (interrupt) {
                throw InterruptedException()
            }
            time += (ms * 1000000L)
        }

        fun advance(ms: Int): Unit {
            time += (ms * 1000000L)
        }
    }

    fun interface Read {
        fun read(channel: Fake, wait: Int): ByteArray?
    }

    class Fake : IcmpEcho.Channel {
        var clock: Clock? = null

        var sent: MutableList<ByteArray> = ArrayList()

        var times: MutableList<Long> = ArrayList()

        var closed: Boolean = false
        var closeFails: Boolean = false
        var sendFails: Boolean = false

        var reads: Int = 0

        var reader: Read? = null

        constructor(c: Clock) {
            clock = c
            reader =
                IcmpEchoTest.Read(
                    callback1@{ f, w ->
                        clock!!.advance(w)
                        return@callback1 null
                    }
                )
        }

        override fun identifier(): Int {
            return 12345
        }

        override fun send(bytes: ByteArray): Unit {
            if (sendFails) {
                throw IOException("send fixture")
            }
            sent.add(bytes.clone())
            times.add(clock!!.time)
        }

        override fun receive(wait: Int): ByteArray? {
            reads++
            return reader!!.read(this, wait)
        }

        override fun close(): Unit {
            closed = true
            if (closeFails) {
                throw IOException("close fixture")
            }
        }
    }

    fun reply(request: ByteArray): ByteArray {
        var p: ByteArray = request.clone()
        p[0] = 0
        fix(p)
        return p
    }

    fun fix(p: ByteArray): Unit {
        p[3] = 0
        p[2] = 0
        var sum: Int = IcmpEcho.checksum(p)
        p[2] = ((((sum).toInt() ushr (8).toInt()))).toByte()
        p[3] = (sum).toByte()
    }

    fun run(f: Fake): IcmpEcho.Result {
        return IcmpEcho.probe(callback2@{ f }, callback3@{ false }, 200, 2, 120, f.clock!!)
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var clock: Clock = Clock()
        var f: Fake = Fake((clock)!!)
        f.reader =
            IcmpEchoTest.Read(
                callback4@{ ch, w ->
                    ch.clock!!.advance(3)
                    return@callback4 reply(ch.sent.get(0))
                }
            )
        var r: IcmpEcho.Result = run(f)
        check(
            (((r.status === IcmpEcho.Status.REPLIED) && (r.requests == 1)) &&
                ((r.latencyMillis).toLong() == (3).toLong())),
            "First echo reply ends the probe",
        )
        check((f.closed && (f.sent.size == 1)), "Successful socket closed without redundant retry")
        clock = Clock()
        f = Fake(clock)
        r = run(f)
        check(
            (((r.status === IcmpEcho.Status.NO_REPLY) && (r.problem === null)) &&
                (r.requests == 2)),
            "Silence is not an execution error",
        )
        check(
            (f.times.equals(Arrays.asList(0L, 320000000L)) && (clock!!.time == 520000000L)),
            "Two attempts have bounded waits and a real retry gap",
        )
        check(f.closed, "Silent socket closed")
        clock = Clock()
        f = Fake(clock)
        f.reader =
            IcmpEchoTest.Read(
                callback5@{ ch, w ->
                    if ((ch.sent.size == 1)) {
                        ch.clock!!.advance(w)
                        return@callback5 null
                    }
                    ch.clock!!.advance(4)
                    return@callback5 reply(ch.sent.get(1))
                }
            )
        r = run(f)
        check(
            (((r.status === IcmpEcho.Status.REPLIED) && (r.requests == 2)) &&
                ((r.latencyMillis).toLong() == (4).toLong())),
            "A dropped first request is recovered on retry",
        )
        clock = Clock()
        f = Fake(clock)
        f.reader =
            IcmpEchoTest.Read(
                callback6@{ ch, w ->
                    if ((ch.sent.size == 1)) {
                        ch.clock!!.advance(w)
                        return@callback6 null
                    }
                    ch.clock!!.advance(5)
                    return@callback6 reply(ch.sent.get(0))
                }
            )
        r = run(f)
        check(
            (((r.status === IcmpEcho.Status.REPLIED) && (r.requests == 2)) &&
                ((r.latencyMillis).toLong() == (325).toLong())),
            "Late matching first reply remains valid after retry",
        )
        var nonce: ByteArray = ByteArray(16)
        Arrays.fill(nonce, (37).toByte())
        var request: ByteArray = IcmpEcho.request(12345, 1, nonce)
        var good: ByteArray = reply(request)
        check(
            ((((request.size == 24) && ((request[0]).toInt() == (8).toInt())) &&
                ((request[1]).toInt() == (0).toInt())) && (IcmpEcho.checksum(request) == 0)),
            "Request carries a valid IPv4 ICMP checksum",
        )
        check(
            (IcmpEcho.replySequence(good, 12345, 1, nonce) == 1),
            "Reply identifier, sequence, nonce and checksum match",
        )
        for (index in intArrayOf(0, 1, 2, 4, 5, 6, 7, 8, 23)) {
            var bad: ByteArray = good.clone()
            bad[index] = ((bad[index]).toInt() xor (1).toInt()).toByte()
            if ((index != 2)) {
                fix(bad)
            }
            check(
                (IcmpEcho.replySequence(bad, 12345, 1, nonce) == 0),
                ("Reject altered echo field " + index),
            )
        }
        check(
            (IcmpEcho.replySequence(reply(IcmpEcho.request(12345, 2, nonce)), 12345, 1, nonce) ==
                0),
            "Future unsent sequence is rejected",
        )
        run {
            var length: Int = 0
            while ((length < 24)) {
                check(
                    (IcmpEcho.replySequence(Arrays.copyOf(good, length), 12345, 1, nonce) == 0),
                    ("Truncated reply rejected " + length),
                )
                length++
            }
        }
        check(
            (IcmpEcho.replySequence(Arrays.copyOf(good, 25), 12345, 1, nonce) == 0),
            "Oversized reply rejected",
        )
        check((IcmpEcho.replySequence(null, 12345, 1, nonce) == 0), "Null reply rejected")
        var random: Random = Random(391)
        run {
            var n: Int = 0
            while ((n < 10000)) {
                var bad: ByteArray = ByteArray(random.nextInt(64))
                random.nextBytes(bad)
                if ((IcmpEcho.replySequence(bad, 12345, 2, nonce) != 0)) {
                    throw AssertionError("Uncorrelated fuzz reply accepted")
                }
                n++
            }
        }
        check(true, "10000 malformed or uncorrelated replies do not match")
        clock = Clock()
        f = Fake(clock)
        f.reader = IcmpEchoTest.Read(callback7@{ ch, w -> ByteArray(24) })
        r = run(f)
        check(
            ((((r.status === IcmpEcho.Status.ERROR) &&
                (r.problem === IcmpEcho.Problem.REPLY_LIMIT)) && (f.reads == 64)) && f.closed),
            "Reply flood is bounded and reported",
        )
        var opens: AtomicInteger = AtomicInteger()
        r =
            IcmpEcho.probe(
                callback8@{
                    opens.incrementAndGet()
                    throw IOException()
                },
                callback9@{ true },
                200,
                2,
                120,
            )
        check(
            ((r.status === IcmpEcho.Status.CANCELLED) && (opens.get() == 0)),
            "Already cancelled probe opens no socket",
        )
        var cancel: AtomicBoolean = AtomicBoolean()
        clock = Clock()
        f = Fake(clock)
        f.reader =
            IcmpEchoTest.Read(
                callback10@{ ch, w ->
                    cancel.set(true)
                    return@callback10 reply(ch.sent.get(0))
                }
            )
        var receiving: Fake = f
        r = IcmpEcho.probe(callback11@{ receiving }, cancel::get, 200, 2, 120, clock)
        check(
            ((r.status === IcmpEcho.Status.CANCELLED) && f.closed),
            "Cancellation during receive rejects even a matching reply",
        )
        clock = Clock()
        f = Fake(clock)
        var timing: Clock = clock
        var delayed: Fake = f
        r =
            IcmpEcho.probe(
                callback12@{ delayed },
                callback13@{ (timing.time >= 220000000L) },
                200,
                2,
                120,
                clock,
            )
        check(
            (((r.status === IcmpEcho.Status.CANCELLED) && (f.sent.size == 1)) && f.closed),
            "Cancellation interrupts retry gap before sending again",
        )
        for (kind in
            arrayOf(
                IcmpEcho.Problem.PERMISSION,
                IcmpEcho.Problem.UNSUPPORTED,
                IcmpEcho.Problem.SETUP,
                IcmpEcho.Problem.ROUTE,
                IcmpEcho.Problem.IO,
            )) {
            r =
                IcmpEcho.probe(
                    callback14@{ throw IcmpEcho.Failure(kind, "private fixture") },
                    callback15@{ false },
                    200,
                    2,
                    120,
                )
            check(
                ((((r.status === IcmpEcho.Status.ERROR) && (r.problem === kind)) &&
                    (r.requests == 0)) && !((r.reason())!!.isEmpty())),
                ("Execution failure retained: " + kind),
            )
            check(
                !((r.reason())!!.contains("private")),
                ("Raw diagnostic text is not exposed: " + kind),
            )
        }
        r =
            IcmpEcho.probe(
                callback16@{
                    throw IcmpEcho.Failure(IcmpEcho.Problem.PEER_UNREACHABLE, "peer negative")
                },
                callback17@{ false },
                200,
                2,
                120,
            )
        check(
            ((r.status === IcmpEcho.Status.NO_REPLY) && (r.problem === null)),
            "Ordinary peer-unreachable result is not an execution failure",
        )
        clock = Clock()
        f = Fake(clock)
        f.sendFails = true
        r = run(f)
        check(
            ((((r.status === IcmpEcho.Status.ERROR) && (r.problem === IcmpEcho.Problem.IO)) &&
                f.closed) && (r.requests == 0)),
            "Send failure reported and socket closed",
        )
        clock = Clock()
        f = Fake(clock)
        f.reader = IcmpEchoTest.Read(callback18@{ ch, w -> throw IOException("receive fixture") })
        r = run(f)
        check(
            (((r.status === IcmpEcho.Status.ERROR) && (r.requests == 1)) && f.closed),
            "Receive failure reported and socket closed",
        )
        clock = Clock()
        f = Fake(clock)
        f.closeFails = true
        f.reader = IcmpEchoTest.Read(callback19@{ ch, w -> reply(ch.sent.get(0)) })
        r = run(f)
        check(
            (((r.status === IcmpEcho.Status.REPLIED) && (r.problem === IcmpEcho.Problem.IO)) &&
                f.closed),
            "Cleanup error is reported without discarding a proven reply",
        )
        clock = Clock()
        f = Fake(clock)
        f.closeFails = true
        r = run(f)
        check(
            ((r.status === IcmpEcho.Status.ERROR) && (r.problem === IcmpEcho.Problem.IO)),
            "Cleanup failure is not silently successful",
        )
        clock = Clock()
        f = Fake(clock)
        clock!!.interrupt = true
        r = run(f)
        check(
            (((r.status === IcmpEcho.Status.CANCELLED) && f.closed) && Thread.interrupted()),
            "Interrupted retry closes socket and preserves interrupt",
        )
        clock = Clock()
        f = Fake(clock)
        f.reader =
            IcmpEchoTest.Read(
                callback20@{ ch, w -> throw IllegalStateException("unexpected fixture") }
            )
        var threw: Boolean = false
        try {
            run(f)
        } catch (expected: IllegalStateException) {
            threw = true
        }
        check((threw && f.closed), "Unexpected failure still closes socket")
        for (b in
            arrayOf(
                intArrayOf(0, 2, 120),
                intArrayOf(1001, 2, 120),
                intArrayOf(200, 0, 120),
                intArrayOf(200, 3, 120),
                intArrayOf(200, 2, -(1)),
                intArrayOf(200, 2, 501),
            )) {
            var invalid: Boolean = false
            try {
                IcmpEcho.probe(
                    callback21@{ throw AssertionError("opened") },
                    callback22@{ false },
                    b[0],
                    b[1],
                    b[2],
                )
            } catch (expected: IllegalArgumentException) {
                invalid = true
            }
            check(invalid, "Unsafe echo budget rejected")
        }
        var health: DiscoveryHealth = DiscoveryHealth()
        health.begin("ICMP echo")
        health.fail("ICMP echo", "ICMP socket unavailable")
        health.ok("ICMP echo")
        check(
            (((health.sources())!!.get("ICMP echo"))!!.startsWith("Failed") &&
                !((health.summary())!!.isEmpty())),
            "TCP success cannot hide ICMP execution failure",
        )
        health = DiscoveryHealth()
        health.begin("ICMP echo")
        health.count("ICMP echo", "no echo reply")
        health.ok("ICMP echo")
        check(
            ((health.summary())!!.isEmpty() &&
                ((health.sources())!!.get("ICMP echo"))!!.startsWith("OK")),
            "Completed unanswered checks can finish without false errors",
        )
        System.out.println(
            (("PASS · " + checks) +
                " ICMP policy, retry, correlation, cancellation and error checks; 10000 malformed inputs")
        )
    }
}
