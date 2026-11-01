package io.github.kazands.networklens

import java.util.EnumMap
import java.util.concurrent.*

/** Independent bounded lanes keep slow name lookups out of other workers. */
class DiscoveryWork {
    enum class Lane { DETAILS, NAMES, DESCRIPTIONS, CONFIRM, CAST, IPV6, METADATA }

    private val pools = EnumMap<Lane, ThreadPoolExecutor>(Lane::class.java)
    init {
        add(Lane.DETAILS, 4, 128)
        add(Lane.NAMES, 4, 144)
        add(Lane.DESCRIPTIONS, 2, 32)
        add(Lane.CONFIRM, 4, 128)
        add(Lane.CAST, 2, 32)
        add(Lane.IPV6, 4, 32)
        add(Lane.METADATA, 2, 32)
    }
    private fun add(lane: Lane, threads: Int, queued: Int) {
        pools[lane] = ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(queued))
    }
    fun submit(lane: Lane, task: Runnable): Boolean = try {
        pools.getValue(lane).execute(task)
        true
    } catch (_: RejectedExecutionException) { false }
    fun shutdown() { pools.values.forEach { it.shutdown() } }
    fun shutdownNow() { pools.values.forEach { it.shutdownNow() } }
    fun isTerminated(): Boolean = pools.values.all { it.isTerminated }

    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        val end = System.nanoTime() + unit.toNanos(timeout)
        for (pool in pools.values) {
            val left = end - System.nanoTime()
            if (left <= 0) return isTerminated()
            pool.awaitTermination(left, TimeUnit.NANOSECONDS)
        }
        return isTerminated()
    }
}

