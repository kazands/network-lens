package io.github.kazands.networklens

import java.util.EnumMap
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Independent bounded lanes keep slow name lookups out of other workers. */
class DiscoveryWork {
    enum class Lane { DETAILS, NAMES, DESCRIPTIONS, CONFIRM, CAST, METADATA, PUBLIC }

    private val pools = EnumMap<Lane, ThreadPoolExecutor>(Lane::class.java)
    private val upstreamLanes = AtomicInteger(Lane.entries.size - 2)
    @Volatile private var stopped = false
    init {
        add(Lane.DETAILS, 4, 128)
        add(Lane.NAMES, 4, 144)
        add(Lane.DESCRIPTIONS, 2, 32)
        add(Lane.CONFIRM, 4, 128)
        add(Lane.CAST, 2, 32)
        add(Lane.METADATA, 2, 32)
        add(Lane.PUBLIC, 2, 32)
    }
    private fun add(lane: Lane, threads: Int, queued: Int) {
        pools[lane] = object : ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, if (lane in setOf(Lane.METADATA,Lane.PUBLIC,Lane.CAST)) FairWorkQueue(queued) else ArrayBlockingQueue(queued)) {
            override fun terminated() {
                // Already-admitted descriptions/probes may produce a final metadata lookup.
                // Close that lane only when every producer has finished, without a polling task.
                if (lane != Lane.METADATA && lane != Lane.PUBLIC && upstreamLanes.decrementAndGet() == 0) {
                    pools.getValue(Lane.METADATA).shutdown();pools.getValue(Lane.PUBLIC).shutdown()
                }
            }
        }
    }
    companion object {
        internal fun awaitBatch(jobs: List<Future<*>>, deadline: Long, cancelled: () -> Boolean): Boolean {
            for (job in jobs) {
                if (cancelled()) return false
                if (job.isDone) { job.get(); continue }
                val left = deadline - System.nanoTime()
                try {
                    if (left <= 0) throw TimeoutException()
                    job.get(left, TimeUnit.NANOSECONDS)
                } catch (_: TimeoutException) {
                    jobs.filter { !it.isDone }.forEach { it.cancel(true) }
                    return false
                }
            }
            return true
        }
    }
    fun submit(lane: Lane, task: Runnable): Boolean = submit(lane, "", task)
    fun submit(lane: Lane, owner: String, task: Runnable): Boolean = try {
        if (stopped) false else { pools.getValue(lane).execute(if (lane in setOf(Lane.METADATA,Lane.PUBLIC,Lane.CAST)) FairWorkQueue.Task(owner, task) else task); true }
    } catch (_: RejectedExecutionException) { false }
    fun shutdown() { pools.filterKeys { it != Lane.METADATA && it != Lane.PUBLIC }.values.forEach { it.shutdown() } }
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

