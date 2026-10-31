package io.github.kazands.networklens

import java.util.TreeMap

/** Track attempted work separately from IPv4 coverage. */
class DiscoveryHealth {
    /** Finite diagnostic vocabulary; never accept peer text, URLs or exception messages. */
    enum class Diagnostic(val label: String) {
        CONNECTION_TIMEOUT("connection timeouts"), CONNECTION_FAILED("connection failures"),
        TLS_FAILED("TLS failures"), WRITE_FAILED("request write failures"),
        READ_TIMEOUT("read timeouts"), READ_FAILED("response read failures"),
        HTTP_STATUS("HTTP errors"), INVALID_HTTP("invalid HTTP responses"),
        INVALID_METADATA("invalid metadata responses"), DEADLINE("metadata deadlines"),
        REQUEST_FAILED("other request failures"),
        MDNS_RECEIVED("packets received"), MDNS_IPV4("IPv4 packets"), MDNS_IPV6("IPv6 packets"),
        MDNS_IGNORED_SOURCE("ignored source packets"), MDNS_OVERSIZED("oversized packets"),
        MDNS_NON_RESPONSE("non-response packets"), MDNS_REJECTED("rejected response packets"),
        MDNS_NO_LIVE_DETAILS("responses without live details"), MDNS_LIVE("live-record responses"),
        MDNS_LIMIT("receive budget stops"), MDNS_BYTES("bytes received"),
        MDNS_DECODED("response decodes"), MDNS_QUERY_DECODED("query decodes"),
        MDNS_QUERY_SKIPPED("queries beyond suppression budget"),
        MDNS_COALESCED("questions coalesced"), MDNS_SENT("query packets sent"),
        MDNS_KNOWN_ANSWERS("known answers sent")
    }
    private val starting = HashSet<String>()
    private val browsing = HashSet<String>()
    private val work = HashMap<String, Int>()
    private val pending = HashMap<String, String>()
    private val states = TreeMap<String, String>()
    private val reasons = HashMap<String, LinkedHashSet<String>>()
    private val counters = TreeMap<String, MutableMap<String, Int>>()
    private val diagnostics = TreeMap<String, MutableMap<Diagnostic, Int>>()

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
    @Synchronized fun diagnostic(source: String, event: Diagnostic, amount: Int = 1) {
        require(amount in 0..33554432)
        val values = diagnostics.getOrPut(source) { java.util.EnumMap(Diagnostic::class.java) }
        val limit = if (event == Diagnostic.MDNS_BYTES) 33554432 else 1000000
        values[event] = minOf(limit.toLong(), values.getOrDefault(event, 0).toLong() + amount).toInt()
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
        for ((source, values) in diagnostics) {
            val counts = values.entries.joinToString(", ") { (event, count) -> event.label + " " + count }
            result[source] = result.getOrDefault(source, "Not used") + " · " + counts
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

