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
    sources: Map<String, String> = emptyMap()
) {
    @JvmField val scope = SafeText.label(scope, 200)
    @JvmField val reason = SafeText.label(reason, 300)
    @JvmField val checked = maxOf(0, checked)
    @JvmField val total = maxOf(0, total)
    @JvmField val sources: Map<String, String>

    init {
        val clean = LinkedHashMap<String, String>()
        for ((key, value) in sources) {
            if (clean.size >= 64) break
            // Two bounded failure reasons plus six counters can exceed 200 characters.
            // Preserve that diagnostic context while keeping each RAM-only entry bounded.
            clean[SafeText.label(key, 160)] = SafeText.label(value, 512)
        }
        this.sources = Collections.unmodifiableMap(clean)
    }

    fun json(): JSONObject {
        val out = JSONObject()
        try {
            out.put("scope", scope).put("complete", complete).put("reason", reason)
                .put("checked", checked).put("total", total).put("finished", finished)
                .put("sources", JSONObject(sources))
        } catch (_: JSONException) {}
        return out
    }

    companion object {
        @JvmStatic fun from(out: JSONObject): ScanReport {
            val sources = LinkedHashMap<String, String>()
            val s = out.optJSONObject("sources")
            if (s != null) {
                val keys = s.keys()
                while (keys.hasNext() && sources.size < 64) {
                    val key = keys.next()
                    sources[key] = s.optString(key)
                }
            }
            return ScanReport(out.optString("scope"), out.optBoolean("complete"), out.optString("reason"),
                out.optInt("checked"), out.optInt("total"), out.optLong("finished"), sources)
        }
    }
}

