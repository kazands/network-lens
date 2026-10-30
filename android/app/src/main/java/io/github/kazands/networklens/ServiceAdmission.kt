package io.github.kazands.networklens

/** Check limits before retaining untrusted service names. */
class ServiceAdmission @JvmOverloads constructor(
    private val maximum: Int,
    private val perType: Int = maximum
) {
    enum class Result { ADMITTED, DUPLICATE, INVALID, LIMIT }

    private val counts = HashMap<String, Int>()
    private val keys = HashSet<String>()

    init { require(maximum > 0 && perType > 0) { "Positive capacity required" } }

    @Synchronized fun reserve(name: String?, type: String?): Boolean =
        admit(name, type) == Result.ADMITTED

    @Synchronized fun admit(name: String?, type: String?): Result {
        if (name == null || type == null || name.length > 128 || type.length > 128) return Result.INVALID
        val key = name + "\u0000" + type
        if (keys.contains(key)) return Result.DUPLICATE
        if (keys.size >= maximum || counts.getOrDefault(type, 0) >= perType) return Result.LIMIT
        keys.add(key)
        counts[type] = counts.getOrDefault(type, 0) + 1
        return Result.ADMITTED
    }

    @Synchronized fun size(): Int = keys.size
}

