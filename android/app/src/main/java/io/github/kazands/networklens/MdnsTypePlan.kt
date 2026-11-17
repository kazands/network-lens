package io.github.kazands.networklens

/** Large catalogue, small fair query batches. Limits protect against unbounded peer advertisements. */
internal object MdnsTypePlan {
    const val MAX_TYPES = 512
    const val CONCURRENT_BROWSES = 6
    const val BROWSE_MILLIS = 3000L
    fun ordered(observed: Set<String>, attempts: Map<String, Int>): List<String> =
        (LocalServices.INITIAL + observed.sorted() + HaMatchers.types).distinct().sortedWith(
            compareBy<String> { attempts.getOrDefault(it,0) }
                .thenBy { if (it in observed) 0 else if (it in LocalServices.INITIAL) 1 else 2 }
        )
}
