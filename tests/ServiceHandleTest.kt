@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.ServiceHandle
import java.util.concurrent.*
import java.util.concurrent.atomic.*

object ServiceHandleTest {
    private var checks: Int = 0

    private fun check(value: Boolean, message: String): Unit {
        if (!(value)) {
            throw AssertionError(message)
        }
        checks++
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var handle: ServiceHandle<Any> = ServiceHandle()
        var entered: CountDownLatch = CountDownLatch(1)
        var release: CountDownLatch = CountDownLatch(1)
        var calls: AtomicInteger = AtomicInteger()
        var service: Any = Any()
        var stuck: Callable<Any?> =
            Callable<Any?>(
                callback1@{
                    calls.incrementAndGet()
                    entered.countDown()
                    release.await()
                    return@callback1 service
                }
            )
        var start: Long = System.nanoTime()
        try {
            handle.acquire(stuck, callback2@{ false }, 50)
            throw AssertionError("Blocked constructor returned")
        } catch (expected: TimeoutException) {
            checks++
        }
        check(
            ((TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start))).toLong() <
                (1500).toLong()),
            "A blocked service constructor cannot block its caller indefinitely",
        )
        check(entered.await(1, TimeUnit.SECONDS), "Constructor runs in its isolated worker")
        try {
            handle.acquire(stuck, callback3@{ false }, 50)
            throw AssertionError("Second caller waited without a deadline")
        } catch (expected: TimeoutException) {
            checks++
        }
        check(
            (calls.get() == 1),
            "Repeated scans share one pending constructor instead of leaking workers",
        )
        release.countDown()
        check(
            (handle.acquire(stuck, callback4@{ false }, 1000) === service),
            "An eventually ready service is available to a later scan",
        )
        check(
            ((handle.acquire(callback5@{ Any() }, callback6@{ false }, 100) === service) &&
                (calls.get() == 1)),
            "A ready OS handle is reused without another initialization",
        )
        var cancel: AtomicBoolean = AtomicBoolean(true)
        var starts: AtomicInteger = AtomicInteger()
        var cancelled: ServiceHandle<Any> = ServiceHandle()
        try {
            cancelled.acquire(
                callback7@{
                    starts.incrementAndGet()
                    return@callback7 service
                },
                cancel::get,
                100,
            )
            throw AssertionError("Cancelled caller started acquisition")
        } catch (expected: InterruptedException) {
            checks++
        }
        check((starts.get() == 0), "A cancelled scan creates no new service worker")
        var pending: ServiceHandle<Any> = ServiceHandle()
        var waiting: CountDownLatch = CountDownLatch(1)
        var finish: CountDownLatch = CountDownLatch(1)
        var stopped: CountDownLatch = CountDownLatch(1)
        cancel.set(false)
        var error: AtomicReference<Throwable> = AtomicReference()
        var caller: Thread =
            Thread(
                callback8@{
                    try {
                        pending.acquire(
                            callback9@{
                                waiting.countDown()
                                finish.await()
                                return@callback9 service
                            },
                            cancel::get,
                            4000,
                        )
                        error.set(AssertionError("Cancelled waiter returned a service"))
                    } catch (expected: InterruptedException) {} catch (problem: Throwable) {
                        error.set(problem)
                    } finally {
                        stopped.countDown()
                    }
                }
            )
        caller.start()
        check(
            waiting.await(1, TimeUnit.SECONDS),
            "Cancellation fixture entered the platform constructor",
        )
        cancel.set(true)
        check(
            (stopped.await(1, TimeUnit.SECONDS) && (error.get() === null)),
            "Cancellation releases the caller while the platform remains blocked",
        )
        finish.countDown()
        caller.join(1000)
        var failed: ServiceHandle<String> = ServiceHandle()
        try {
            failed.acquire(
                callback10@{ throw IllegalStateException("fixture") },
                callback11@{ false },
                1000,
            )
            throw AssertionError("Service error hidden")
        } catch (expected: ExecutionException) {
            check(
                (expected.cause is IllegalStateException),
                "Initialization failure preserves its cause",
            )
        }
        check(
            "ready".equals(failed.acquire(callback12@{ "ready" }, callback13@{ false }, 1000)),
            "A failed constructor can be retried by a later scan",
        )
        var absent: ServiceHandle<String> = ServiceHandle()
        check(
            (absent.acquire(callback14@{ null }, callback15@{ false }, 1000) === null),
            "A missing OS service is returned without inventing a handle",
        )
        check(
            "ready".equals(absent.acquire(callback16@{ "ready" }, callback17@{ false }, 1000)),
            "A later available service can replace a missing result",
        )
        try {
            absent.acquire(callback18@{ "unused" }, callback19@{ false }, 0)
            throw AssertionError("Invalid budget accepted")
        } catch (expected: IllegalArgumentException) {
            checks++
        }
        System.out.println(
            (("PASS · " + checks) +
                " OS-service initialization deadline, sharing, cancellation and recovery checks")
        )
    }
}
