package dev.netlens

/** Serializes in-memory session changes and fences replaced scan work. */
class DataGuard {
    private var generation = 0L

    @Synchronized fun token(): Long = generation
    @Synchronized fun isCurrent(token: Long): Boolean = token == generation

    @Synchronized fun writeIfCurrent(token: Long, write: Runnable): Boolean {
        if (token != generation) return false
        write.run()
        return true
    }

    @Synchronized fun invalidateAndClear(clear: Runnable) {
        generation++
        clear.run()
    }
}

