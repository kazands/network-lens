@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import dev.netlens.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Deterministic deletion ordering and adversarial resource/input regressions. */
object SecurityTest {
    private var checks: Int = 0

    private fun check(ok: Boolean, message: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(message)
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var data: DataGuard = DataGuard()
        var records: MutableList<String> = ArrayList()
        var old: Long = data.token()
        check(
            data.writeIfCurrent(old, callback1@{ records.add("scan") }),
            "Fresh scan must persist",
        )
        data.invalidateAndClear(records::clear)
        check(
            !(data.writeIfCurrent(old, callback2@{ records.add("late scan") })),
            "Late scan must not restore deleted inventory",
        )
        check(
            !(data.writeIfCurrent(old, callback3@{ records.add("late diagnostic") })),
            "Late diagnostic must not restore history",
        )
        check(records.isEmpty(), "Deletion must stay empty")
        check(
            data.writeIfCurrent(data.token(), callback4@{ records.add("new scan") }),
            "New user work may persist",
        )
        // Hold an old writer inside the shared lock while deletion is requested.
        var writing: CountDownLatch = CountDownLatch(1)
        var release: CountDownLatch = CountDownLatch(1)
        var deleting: CountDownLatch = CountDownLatch(1)
        var pool: ExecutorService = Executors.newFixedThreadPool(2)
        var token: Long = data.token()
        try {
            var writer: Future<*> =
                pool.submit(
                    callback5@{
                        data.writeIfCurrent(
                            token,
                            callback6@{
                                writing.countDown()
                                await(release)
                                records.add("in flight")
                            },
                        )
                    }
                )
            check(writing.await(3, TimeUnit.SECONDS), "Writer entered critical section")
            var clear: Future<*> =
                pool.submit(
                    callback7@{
                        deleting.countDown()
                        data.invalidateAndClear(records::clear)
                    }
                )
            check(deleting.await(3, TimeUnit.SECONDS), "Deletion requested during write")
            release.countDown()
            writer.get(3, TimeUnit.SECONDS)
            clear.get(3, TimeUnit.SECONDS)
            check(
                records.isEmpty(),
                "A write already in progress must be cleared before delete returns",
            )
            check(
                !(data.writeIfCurrent(token, callback8@{ records.add("queued old work") })),
                "Queued old work must stay rejected",
            )
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
        var services: ServiceAdmission = ServiceAdmission(100)
        var advertisers: ExecutorService = Executors.newFixedThreadPool(8)
        var jobs: MutableList<Future<*>> = ArrayList()
        try {
            run {
                var t: Int = 0
                while ((t < 8)) {
                    var n: Int = t
                    jobs.add(
                        advertisers.submit(
                            callback9@{
                                run {
                                    var i: Int = 0
                                    while ((i < 2000)) {
                                        services.reserve(
                                            ((("device-" + n) + "-") + i),
                                            "_http._tcp.",
                                        )
                                        i++
                                    }
                                }
                            }
                        )
                    )
                    t++
                }
            }
            for (job in jobs) {
                job.get(5, TimeUnit.SECONDS)
            }
        } finally {
            advertisers.shutdownNow()
        }
        check(
            (services.size() == 100),
            "Concurrent advertisements must not retain more than 100 keys",
        )
        check(
            !(services.reserve("overflow", "_http._tcp.")),
            "Full admission must reject without retaining",
        )
        var small: ServiceAdmission = ServiceAdmission(3)
        check(small.reserve("printer", "_ipp._tcp."), "Valid service admitted")
        check(
            (!(small.reserve("printer", "_ipp._tcp.")) && (small.size() == 1)),
            "Duplicates do not consume capacity",
        )
        check(!(small.reserve(null, "_http._tcp.")), "Null service rejected")
        check(
            (!(small.reserve(repeat("x", 129), "_http._tcp.")) && (small.size() == 1)),
            "Oversized name rejected without retention",
        )
        check(!(small.reserve("name", repeat("x", 129))), "Oversized type rejected")
        check(
            (SafeText.label("Printer\n\t\u202eevil\u2066\u0000", 128))!!.equals("Printerevil"),
            "Strip control and bidi label spoofing characters",
        )
        var unicode: String = "\ud83d\udda8"
        check(
            ((SafeText.label(repeat(unicode, 1000), 128))!!.codePointCount(0, 256) == 128),
            "Bound Unicode without splitting surrogate pairs",
        )
        check(
            (SafeText.label((repeat("\n", 10000) + "hidden"), 128))!!.isEmpty(),
            "Input processing must also be bounded",
        )
        check((SafeText.label(null, 128))!!.isEmpty(), "Null label safe")
        check(
            (SafeText.label(" <printer>& ", 128))!!.equals("<printer>&"),
            "Ordinary text preserved, not interpreted as markup",
        )
        System.out.println((("PASS · " + checks) + " security regression checks"))
    }

    private fun repeat(s: String, n: Int): String {
        var b: StringBuilder = StringBuilder()
        run {
            var i: Int = 0
            while ((i < n)) {
                b.append(s)
                i++
            }
        }
        return b.toString()
    }

    private fun await(latch: CountDownLatch): Unit {
        try {
            if (!(latch.await(3, TimeUnit.SECONDS))) {
                throw AssertionError("Timed out releasing writer")
            }
        } catch (e: InterruptedException) {
            (Thread.currentThread())!!.interrupt()
            throw AssertionError(e)
        }
    }
}
