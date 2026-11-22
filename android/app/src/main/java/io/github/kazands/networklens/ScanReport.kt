package io.github.kazands.networklens

import org.json.JSONException
import org.json.JSONObject
import java.util.Collections

/** Immutable summary retained only in memory. */
class ScanReport @JvmOverloads constructor(
    scope: String?,
    @JvmField val complete: Boolean,
    reason: String?,
    checked: Int,
    total: Int,
    @JvmField val finished: Long,
    sources: Map<String, String> = emptyMap(),
    previouslyOmittedSources: Int = 0,
    val tcpProbes: TcpProbeJournal.Snapshot = TcpProbeJournal.Snapshot.EMPTY
) {
    @JvmField val scope = SafeText.label(scope, 200)
    @JvmField val reason = SafeText.label(reason, 300)
    /** Scan lifecycle wording; [complete] separately records whether every planned check succeeded. */
    val statusLabel: String get() = if (reason.startsWith("Scan stopped.")) "Scan stopped" else "Scan finished"
    @JvmField val checked = maxOf(0, checked)
    @JvmField val total = maxOf(0, total)
    @JvmField val sources: Map<String, String>
    @JvmField val omittedSources: Int

    init {
        val clean = LinkedHashMap<String, String>()
        for ((key, value) in sources) {
            if (clean.size >= MAX_SOURCES) break
            // Bounded reasons plus the finite traffic/transport diagnostic vocabulary.
            // Preserve that diagnostic context while keeping each RAM-only entry bounded.
            clean[SafeText.label(key, 160)] = SafeText.label(value, 1024)
        }
        this.sources = Collections.unmodifiableMap(clean)
        omittedSources = omissionCount(previouslyOmittedSources, sources.size - clean.size)
    }

    fun json(): JSONObject {
        val out = JSONObject()
        try {
            out.put("scope", scope).put("complete", complete).put("reason", reason)
                .put("checked", checked).put("total", total).put("finished", finished)
                .put("sources", JSONObject(sources)).put("omittedSources", omittedSources)
        } catch (_: JSONException) {}
        return out
    }

    companion object {
        // Every admitted mDNS type plus room for the finite non-mDNS discovery/lookup methods.
        internal const val MAX_SOURCES = MdnsTypePlan.MAX_TYPES + 128
        private fun omissionCount(previous: Int, additional: Int): Int =
            (maxOf(0, previous).toLong() + maxOf(0, additional)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        @JvmStatic fun from(out: JSONObject): ScanReport {
            val sources = LinkedHashMap<String, String>()
            val s = out.optJSONObject("sources")
            if (s != null) {
                val keys = s.keys()
                while (keys.hasNext() && sources.size < MAX_SOURCES) {
                    val key = keys.next()
                    sources[key] = s.optString(key)
                }
            }
            return ScanReport(out.optString("scope"), out.optBoolean("complete"), out.optString("reason"),
                out.optInt("checked"), out.optInt("total"), out.optLong("finished"), sources,
                omissionCount(out.optInt("omittedSources"), (s?.length() ?: 0) - sources.size))
        }
    }
}

