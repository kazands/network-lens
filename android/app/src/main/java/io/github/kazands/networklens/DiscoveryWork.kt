package io.github.kazands.networklens

import java.util.EnumMap
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Independent bounded lanes keep slow name lookups out of other workers. */
class DiscoveryWork {
    enum class Lane { DETAILS, NAMES, DESCRIPTIONS, CONFIRM, CAST, IPV6, METADATA }

    private val pools = EnumMap<Lane, ThreadPoolExecutor>(Lane::class.java)
    private val upstreamLanes = AtomicInteger(Lane.entries.size - 1)
    @Volatile private var stopped = false
    init {
        add(Lane.DETAILS, 4, 128)
        add(Lane.NAMES, 4, 144)
        add(Lane.DESCRIPTIONS, 2, 32)
        add(Lane.CONFIRM, 4, 128)
        add(Lane.CAST, 2, 32)
        add(Lane.IPV6, 4, 64)
        add(Lane.METADATA, 2, 32)
    }
    private fun add(lane: Lane, threads: Int, queued: Int) {
        pools[lane] = object : ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, if (lane == Lane.METADATA || lane == Lane.CAST || lane == Lane.IPV6) FairWorkQueue(queued) else ArrayBlockingQueue(queued)) {
            override fun terminated() {
                // Already-admitted descriptions/probes may produce a final metadata lookup.
                // Close that lane only when every producer has finished, without a polling task.
                if (lane != Lane.METADATA && upstreamLanes.decrementAndGet() == 0) pools.getValue(Lane.METADATA).shutdown()
            }
        }
    }
    fun submit(lane: Lane, task: Runnable): Boolean = submit(lane, "", task)
    fun submit(lane: Lane, owner: String, task: Runnable): Boolean = try {
        if (stopped) false else { pools.getValue(lane).execute(if (lane == Lane.METADATA || lane == Lane.CAST || lane == Lane.IPV6) FairWorkQueue.Task(owner, task) else task); true }
    } catch (_: RejectedExecutionException) { false }
    fun shutdown() { pools.filterKeys { it != Lane.METADATA }.values.forEach { it.shutdown() } }
    fun shutdownNow() { stopped = true; pools.values.forEach { it.shutdownNow() } }
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

