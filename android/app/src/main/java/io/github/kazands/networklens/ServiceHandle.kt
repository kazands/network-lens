package io.github.kazands.networklens

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.BooleanSupplier

/** Bounds callers of an OS service constructor that may ignore interruption. */
class ServiceHandle<T> {
    private var pending: FutureTask<T?>? = null

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun acquire(factory: Callable<T?>, cancelled: BooleanSupplier, timeoutMillis: Int): T? {
        require(timeoutMillis in 1..5000)
        if (cancelled.asBoolean) throw InterruptedException("Scan stopped")
        val task = synchronized(this) {
            pending ?: FutureTask(factory).also { next ->
                pending = next
                try { Thread(next, "android-discovery-service").also { it.isDaemon = true; it.start() } }
                catch (failure: RuntimeException) { pending = null; throw failure }
            }
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
        while (true) {
            if (cancelled.asBoolean) throw InterruptedException("Scan stopped")
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw TimeoutException("Android discovery service did not initialize")
            try {
                val result = task.get(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS)
                if (result == null) synchronized(this) { if (pending === task) pending = null }
                if (cancelled.asBoolean) throw InterruptedException("Scan stopped")
                return result
            } catch (_: TimeoutException) {
                // Leave the one pending constructor available for later scans;
                // interruption cannot reliably stop Android's internal latch.
            } catch (failure: ExecutionException) {
                synchronized(this) { if (pending === task) pending = null }
                throw failure
            }
        }
    }
}
