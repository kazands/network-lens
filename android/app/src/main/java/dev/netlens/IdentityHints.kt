package dev.netlens

import java.util.Locale
import java.util.TreeSet

/** Deterministic observation precedence, not an identity-verification claim. */
class IdentityHints {
    class Hint internal constructor(
        @JvmField val field: String,
        @JvmField val value: String,
        @JvmField val source: String,
        @JvmField val priority: Int
    )

    private val best = HashMap<String, Hint>()
    private val observations = HashMap<String, MutableList<Hint>>()

    @Synchronized fun offer(field: String?, value: String?, source: String?, priority: Int): Hint? {
        val cleanValue = SafeText.label(value, 160)
        val cleanSource = SafeText.label(source, 128)
        val previous = best[field]
        if (cleanValue.isEmpty() || cleanSource.isEmpty()) return previous
        if (field == null || field !in FIELDS) return null
        val next = Hint(field, cleanValue, cleanSource, priority)
        val choices = observations.getOrPut(field) { ArrayList() }
        if (choices.none { it.value == cleanValue && it.source == cleanSource && it.priority == priority }) {
            choices.add(next)
            choices.sortWith(::compare)
            if (choices.size > 8) choices.removeAt(8)
        }
        best[field] = choices[0]
        return best[field]
    }

    @Synchronized fun observations(): MutableList<Hint> {
        val out = ArrayList<Hint>()
        for (field in TreeSet(observations.keys)) out.addAll(observations.getValue(field))
        return out
    }

    @Synchronized fun copy(): IdentityHints {
        val copy = IdentityHints()
        copy.best.putAll(best)
        for ((field, choices) in observations) copy.observations[field] = ArrayList(choices)
        return copy
    }

    companion object {
        private val FIELDS = setOf("Name", "Hostname", "Manufacturer", "Model", "Device type", "OS", "Firmware")
        @JvmStatic fun specific(value: String?): Boolean =
            SafeText.label(value, 160).isNotEmpty() && quality(value!!) > 0

        @JvmStatic fun specificName(value: String?): Boolean {
            val n = SafeText.label(value, 160).lowercase(Locale.ROOT)
            val opaque = n.replace("-", "").replace("_", "").length >= 16 &&
                n.matches(Regex("[0-9a-f]+(?:[-_][0-9a-f]+)+"))
            return specific(n) && !opaque &&
                !n.matches(Regex("(?:google[ -]?)?cast(?: device)?|chromecast(?:[-_ ][a-z0-9-]{8,})?|android tv"))
        }

        private fun quality(hint: Hint): Int =
            if (hint.field == "Name") if (specificName(hint.value)) 1 else 0 else quality(hint.value)

        private fun quality(value: String): Int {
            val v = value.javaTrim().lowercase(Locale.ROOT)
            if (v.matches(Regex("unknown(?:[ _-](?:device|model|name))?|none|null|n/?a|not (?:available|reported)|unspecified|generic(?: device)?|android|device|printer|tv|esp[_-][0-9a-f]+|android[_-][0-9a-f]+"))) return 0
            if (v.matches(Regex("[0-9a-f]{12,}|[0-9]{1,3}(?:\\.[0-9]{1,3}){3}|(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}"))) return 0
            return if (v.contains(':') && v.matches(Regex("[0-9a-f:]+(?:%[a-z0-9_.-]+)?"))) 0 else 1
        }

        private fun compare(a: Hint, b: Hint): Int {
            var n = java.lang.Boolean.compare(b.priority >= 100, a.priority >= 100)
            if (n == 0) n = Integer.compare(quality(b), quality(a))
            if (n == 0) n = Integer.compare(b.priority, a.priority)
            if (n == 0) n = a.source.compareTo(b.source)
            if (n == 0) n = a.value.compareTo(b.value, ignoreCase = true)
            return if (n == 0) a.value.compareTo(b.value) else n
        }
    }
}

