package io.github.kazands.networklens

import java.io.Closeable
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** One terminal owner. Registration racing with cancellation closes instead of leaking a resource. */
internal class ScanResources : Closeable {
    private val lock = Any()
    private val resources = Collections.newSetFromMap(IdentityHashMap<Closeable, Boolean>())
    private val threads = Collections.newSetFromMap(IdentityHashMap<Thread, Boolean>())
    @Volatile private var closed = false
    @Volatile var closeFailures = 0
        private set
    fun active(): Boolean = !closed

    fun <T : Closeable> own(resource: T): T {
        synchronized(lock) { if (!closed) { resources.add(resource); return resource } }
        dispose(resource)
        throw CancellationException("Scan has ended")
    }
    fun release(resource: Closeable) { synchronized(lock) { resources.remove(resource) } }
    fun start(thread: Thread): Boolean = synchronized(lock) {
        if (closed) false else {
            threads.add(thread)
            try { thread.start(); true } catch (e: RuntimeException) { threads.remove(thread); throw e }
        }
    }
    fun <T : Closeable> registry(): MutableSet<T> = object : AbstractMutableSet<T>() {
        private val values = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
        override val size: Int get() = synchronized(lock) { values.size }
        override fun add(element: T): Boolean {
            synchronized(lock) {
                if (!closed) { resources.add(element); return values.add(element) }
            }
            dispose(element)
            throw CancellationException("Scan has ended")
        }
        override fun remove(element: T): Boolean = synchronized(lock) {
            release(element); values.remove(element)
        }
        override fun iterator(): MutableIterator<T> {
            val copy = synchronized(lock) { values.toList() }.iterator()
            val registry = this
            return object : MutableIterator<T> {
                private var last: T? = null
                override fun hasNext() = copy.hasNext()
                override fun next(): T = copy.next().also { last = it }
                override fun remove() { val value = checkNotNull(last); registry.remove(value); last = null }
            }
        }
    }
    override fun close() {
        val owned: List<Closeable>
        val running: List<Thread>
        synchronized(lock) {
            if (closed) return
            closed = true; owned = resources.toList(); resources.clear(); running = threads.toList()
        }
        running.filter { it !== Thread.currentThread() }.forEach { it.interrupt() }
        owned.forEach(::dispose)
    }
    private fun dispose(resource: Closeable) {
        try { resource.close() } catch (_: Exception) { synchronized(lock) { closeFailures++ } }
    }
    /** One shared wait budget, rather than a full timeout for every protocol thread. */
    fun awaitThreads(timeout: Long, unit: TimeUnit): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        val running = synchronized(lock) { threads.toList() }
        for (thread in running) {
            if (thread === Thread.currentThread()) continue
            val left = deadline - System.nanoTime()
            if (left <= 0) return running.none { it !== Thread.currentThread() && it.isAlive }
            TimeUnit.NANOSECONDS.timedJoin(thread, left)
        }
        return running.none { it !== Thread.currentThread() && it.isAlive }
    }
}
