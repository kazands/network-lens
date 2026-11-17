package io.github.kazands.networklens

import java.util.AbstractQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Bounded round-robin queue. A noisy endpoint cannot occupy every waiting slot. */
internal class FairWorkQueue(private val capacity: Int, private val perOwner: Int = 8) : AbstractQueue<Runnable>(), BlockingQueue<Runnable> {
    class Task(val owner: String, private val action: Runnable) : Runnable { override fun run() = action.run() }
    private val lock = ReentrantLock()
    private val readable = lock.newCondition()
    private val writable = lock.newCondition()
    private val buckets = LinkedHashMap<String, ArrayDeque<Runnable>>()
    private var count = 0
    private fun owner(task: Runnable) = (task as? Task)?.owner.orEmpty()
    override val size: Int get() = lock.withLock { count }
    private fun addLocked(task: Runnable): Boolean {
        val key = owner(task)
        if (count >= capacity || key.isNotEmpty() && (buckets[key]?.size ?: 0) >= perOwner) return false
        buckets.getOrPut(key) { ArrayDeque() }.addLast(task); count++; readable.signal(); return true
    }
    override fun offer(task: Runnable) = lock.withLock { addLocked(task) }
    private fun next(): Runnable? {
        val key = buckets.keys.firstOrNull() ?: return null
        val bucket = buckets.remove(key)!!; val result = bucket.removeFirst()
        if (bucket.isNotEmpty()) buckets[key] = bucket
        count--; writable.signalAll(); return result
    }
    override fun poll(): Runnable? = lock.withLock { next() }
    override fun peek(): Runnable? = lock.withLock { buckets.values.firstOrNull()?.firstOrNull() }
    override fun take(): Runnable = lock.withLock { while (count == 0) readable.await(); next()!! }
    override fun poll(timeout: Long, unit: TimeUnit): Runnable? = lock.withLock {
        var left = unit.toNanos(timeout)
        while (count == 0) { if (left <= 0) return null; left = readable.awaitNanos(left) }
        next()
    }
    override fun put(task: Runnable) = lock.withLock { while (!addLocked(task)) writable.await() }
    override fun offer(task: Runnable, timeout: Long, unit: TimeUnit): Boolean = lock.withLock {
        var left = unit.toNanos(timeout)
        while (!addLocked(task)) { if (left <= 0) return false; left = writable.awaitNanos(left) }; true
    }
    override fun remainingCapacity() = lock.withLock { capacity - count }
    override fun iterator(): MutableIterator<Runnable> {
        val snapshot = lock.withLock { buckets.values.flatMap { it.toList() }.iterator() }
        return object : MutableIterator<Runnable> {
            private var last: Runnable? = null
            override fun hasNext() = snapshot.hasNext()
            override fun next() = snapshot.next().also { last = it }
            override fun remove() { val task = checkNotNull(last); this@FairWorkQueue.remove(task); last = null }
        }
    }
    override fun clear() = lock.withLock { buckets.clear(); count = 0; writable.signalAll() }
    override fun remove(element: Runnable): Boolean = lock.withLock {
        val key = owner(element); val bucket = buckets[key] ?: return false
        if (!bucket.remove(element)) return false
        if (bucket.isEmpty()) buckets.remove(key)
        count--; writable.signalAll(); true
    }
    override fun drainTo(target: MutableCollection<in Runnable>): Int = drainTo(target, Int.MAX_VALUE)
    override fun drainTo(target: MutableCollection<in Runnable>, maxElements: Int): Int = lock.withLock {
        require(target !== this)
        var drained = 0
        while (count > 0 && drained < maxElements) { target.add(peek()!!); next(); drained++ }
        drained
    }
}
