package io.github.kazands.networklens.test

import io.github.kazands.networklens.*
import org.json.JSONObject
import java.util.TreeMap

/** Bounded synthetic diagnostics, including failures after the former 64-entry boundary. */
internal object ScanReportChecks {
    fun run(check: (Boolean, String) -> Unit) {
        val health = DiscoveryHealth()
        repeat(MdnsTypePlan.MAX_TYPES) { health.ok("mDNS _fixture%03d._tcp.".format(it)) }
        repeat(40) { health.ok("Fixture method $it") }
        health.fail("Fixture method 0", "Early fixture failure")
        health.fail("mDNS _fixture511._tcp.", "Late fixture failure")
        val sources = health.sources()
        val report = ScanReport("192.0.2.0/24", false, health.summary(), 254, 254, 1L, sources)
        check(sources.size > 64 && report.sources == sources && report.omittedSources == 0,
            "Every admitted mDNS type and non-mDNS method survives report retention")
        check(report.sources.values.count { it.startsWith("Failed") } == 2 && !report.complete,
            "Early and late failures retain actual completeness")
        val restored = ScanReport.from(report.json())
        check(restored.sources == sources && restored.omittedSources == 0 && restored.reason == report.reason,
            "JSON round trip retains every method and failure reason")
        val overflow = TreeMap<String, String>()
        repeat(ScanReport.MAX_SOURCES + 10) { overflow["Fixture %04d".format(it)] = "OK" }
        val bounded = ScanReport("", false, "Fixture limit", 0, 0, 1L, overflow)
        check(bounded.sources.size == ScanReport.MAX_SOURCES && bounded.omittedSources == 10,
            "Oversized input remains bounded and explicitly counts omitted methods")
        check(ScanReport.from(bounded.json()).let { it.sources == bounded.sources && it.omittedSources == 10 },
            "Omission count survives round trip without doubling")
        val external = report.json().put("sources", JSONObject(overflow)).put("omittedSources", 7)
        check(ScanReport.from(external).let { it.sources.size == ScanReport.MAX_SOURCES && it.omittedSources == 17 },
            "JSON reader uses the same limit and accounts for existing and new omissions")
        check(ScanReport.from(external.put("omittedSources", Int.MAX_VALUE)).omittedSources == Int.MAX_VALUE,
            "Omission count cannot wrap on malformed metadata")
        check(ScanReport.from(report.json().apply { remove("omittedSources") }).omittedSources == 0,
            "Older report shape defaults to no omissions")
    }
}
