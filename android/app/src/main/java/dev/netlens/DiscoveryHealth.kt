package dev.netlens

import java.util.TreeMap

/** Track attempted work separately from IPv4 coverage. */
class DiscoveryHealth {
    private val starting = HashSet<String>()
    private val browsing = HashSet<String>()
    private val work = HashMap<String, Int>()
    private val pending = HashMap<String, String>()
    private val states = TreeMap<String, String>()
    private val reasons = HashMap<String, LinkedHashSet<String>>()
    private val counters = TreeMap<String, MutableMap<String, Int>>()

    @Synchronized fun begin(source: String) {
        if (!states.containsKey(source) || states.getValue(source).startsWith("Not used")) states[source] = "Running"
    }
    @Synchronized fun ok(source: String) {
        val s = states[source]
        if (s == null || s == "Running" || s.startsWith("Not used")) states[source] = "OK"
    }
    @Synchronized fun fail(source: String, reason: String) { note(source, reason, true) }
    @Synchronized fun truncated(source: String, reason: String) { note(source, reason, false) }

    private fun note(source: String, reason: String, failed: Boolean) {
        val values = reasons.getOrPut(source) { LinkedHashSet() }
        if (values.size < 2) values.add(SafeText.label(reason, 80))
        val text = StringBuilder(if (failed || states.getOrDefault(source, "").startsWith("Failed")) "Failed · " else "Truncated · ")
        var first = true
        for (value in values) {
            if (!first) text.append("; ")
            text.append(value)
            first = false
        }
        states[source] = text.toString()
    }

    @Synchronized fun count(source: String, event: String) {
        val values = counters.getOrPut(source) { TreeMap() }
        if (values.containsKey(event) || values.size < 6) values[event] = minOf(1000000, values.getOrDefault(event, 0) + 1)
    }
    @Synchronized fun skipped(source: String, reason: String) { states.putIfAbsent(source, "Not used · " + reason) }
    @Synchronized fun starting(type: String) { starting.add(type); begin("mDNS " + type) }
    @Synchronized fun started(type: String) { starting.remove(type); browsing.add(type) }
    @Synchronized fun closeBrowseWindow() { for (type in browsing) ok("mDNS " + type); browsing.clear() }
    @Synchronized fun workStarted(source: String) { begin(source); work[source] = work.getOrDefault(source, 0) + 1 }
    @Synchronized fun workFinished(source: String) {
        val left = maxOf(0, work.getOrDefault(source, 0) - 1)
        work[source] = left
        if (left == 0) ok(source)
    }
    @Synchronized fun startFailed(type: String) { starting.remove(type); fail("mDNS " + type, "Discovery could not start") }
    @Synchronized fun resolving(key: String, type: String) { pending[key] = type }
    @Synchronized fun resolved(key: String) { pending.remove(key) }
    @Synchronized fun resolveFailed(key: String) { resolveFailed(key, "Service resolution failed") }
    @Synchronized fun resolveFailed(key: String, reason: String) {
        pending.remove(key)?.let { fail("mDNS " + it, reason) }
    }
    @Synchronized fun pending(): Boolean = starting.isNotEmpty() || pending.isNotEmpty()

    @Synchronized fun finish(cancelled: Boolean) {
        for (type in starting) truncated("mDNS " + type, "Start callback did not arrive")
        for (type in pending.values) truncated("mDNS " + type, "Device details did not finish resolving")
        for ((source, count) in work) if (count > 0) truncated(source, if (cancelled) "Scan stopped" else "Lookups did not finish")
        for (source in ArrayList(states.keys)) if (states[source] == "Running") {
            truncated(source, if (cancelled) "Scan stopped" else "Completion was not confirmed")
        }
    }

    @Synchronized fun sources(): MutableMap<String, String> {
        val result = TreeMap(states)
        for ((source, values) in counters) {
            val s = StringBuilder(result.getOrDefault(source, "Not used"))
            var first = true
            for ((event, count) in values) {
                s.append(if (first) " · " else ", ")
                first = false
                s.append(event).append(' ').append(count)
            }
            result[source] = s.toString()
        }
        return result
    }

    @Synchronized fun summary(): String {
        var failures = 0
        var limits = 0
        for (state in states.values) {
            if (state.startsWith("Failed")) failures++
            else if (state.startsWith("Truncated")) limits++
        }
        return if (failures + limits == 0) "" else "Device details are partial: " + failures +
            " discovery source" + (if (failures == 1) "" else "s") + " failed, " + limits +
            " truncated. Tap scan status for details."
    }
}

