package io.github.kazands.networklens

/** Clipboard-only aggregates. Peer text never becomes output: only enums, fixed labels and counts. */
internal object RecognitionReport {
    enum class Level(val label: String) {
        PRODUCT("Product matches"), FAMILY("Family or integration matches only"),
        SERVICES("Services only"), UNKNOWN("No usable product, family or service identity")
    }
    enum class ScanState(val label: String) {
        FINISHED("Finished"), NOTES("Finished with checks to review"), STOPPED("Stopped")
    }
    enum class Protocol(val label: String) {
        APPLE("Apple device information"), EAP("TP-Link EAP public information"), ENVISALINK("Envisalink greeting"),
        SAMSUNG("Samsung TV"), SHELLY("Shelly"), WLED("WLED"), SPOTIFY("Spotify Connect"),
        IPP("IPP printer"), ESCL("eSCL scanner service"), CAST("Google Cast")
    }
    enum class Outcome(val label: String) {
        MODEL("Model received"), SUCCESS("Response received without a host model"),
        NO_IDENTITY("No identifying response"), UNAVAILABLE("Service unavailable"), FAILED("Failed"),
        LIMITED("Not attempted: work limit"), STOPPED("Stopped"), INCOMPLETE("Unfinished"),
        QUEUED("Still marked as queued"), OTHER("Outcome not classified")
    }
    data class Lookup(val protocol: Protocol, val outcome: Outcome, val reason: MetadataHttp.Reason? = null)
    data class Group(val level: Level, val modelWithoutMatch: Boolean, val conflict: Boolean,
                     val typed: Boolean, val endpoints: Int, val lookups: List<Lookup>)

    fun metadata(entry: MetadataLookups.Entry): Lookup = Lookup(Protocol.valueOf(entry.kind.name), when (entry.state) {
        MetadataLookups.State.QUEUED -> Outcome.QUEUED
        MetadataLookups.State.SUCCESS -> if (entry.model && entry.kind != LocalMetadata.Kind.ESCL) Outcome.MODEL else Outcome.SUCCESS
        MetadataLookups.State.FAILED -> Outcome.FAILED
        MetadataLookups.State.WORK_LIMIT -> Outcome.LIMITED
        MetadataLookups.State.STOPPED -> Outcome.STOPPED
        MetadataLookups.State.INCOMPLETE -> Outcome.INCOMPLETE
    }, entry.reason)

    /** These legacy readers record fixed app-generated status text, never arbitrary peer errors. */
    fun legacy(protocol: Protocol, value: String): Lookup {
        val reason = MetadataHttp.Reason.values().firstOrNull { value == it.detail || value.endsWith("; " + it.detail) }
        val outcome = when {
            value == "Waiting or checking" -> Outcome.QUEUED
            value == "Stopped before lookup finished" -> Outcome.STOPPED
            value == "Lookup did not finish" -> Outcome.INCOMPLETE
            value in setOf("Not attempted: 32-device work limit reached", "Not attempted: work queue limit reached") -> Outcome.LIMITED
            reason != null -> Outcome.FAILED
            protocol == Protocol.EAP && value == "Public model received" -> Outcome.MODEL
            protocol == Protocol.EAP && value == "No supported EAP identity reported" -> Outcome.NO_IDENTITY
            protocol == Protocol.APPLE && value == AppleDeviceInfo.Status.MODEL.text -> Outcome.MODEL
            protocol == Protocol.APPLE && value == AppleDeviceInfo.Status.OTHER.text -> Outcome.NO_IDENTITY
            protocol == Protocol.APPLE && AppleDeviceInfo.Status.values().any { it.text == value } -> Outcome.SUCCESS
            protocol == Protocol.ENVISALINK && value == EnvisalinkInfo.Report.UNAVAILABLE.text -> Outcome.UNAVAILABLE
            protocol == Protocol.ENVISALINK && value == EnvisalinkInfo.Report.IDENTIFIED.text -> Outcome.SUCCESS
            protocol == Protocol.ENVISALINK && EnvisalinkInfo.Report.values().any { it.text == value } -> Outcome.NO_IDENTITY
            else -> Outcome.OTHER
        }
        return Lookup(protocol, outcome, reason)
    }

    private fun method(source: String): String = when (source) {
        AppleDeviceInfo.SOURCE -> "Apple device information"
        EapInfo.SOURCE -> "TP-Link EAP public information"
        EnvisalinkInfo.SOURCE -> "Envisalink greeting"
        "SSDP / UPnP", "UPnP descriptions" -> "SSDP / UPnP"
        "mDNS type and hostname queries" -> "mDNS / Bonjour"
        "Device port and name checks" -> "Port and name checks"
        "IPv4 checks", "ICMP echo", "IPv6 service checks", "Service address checks" -> "Presence checks"
        else -> LocalMetadata.Kind.values().firstOrNull { it.source == source }?.let { Protocol.valueOf(it.name).label }
            ?: if (source.startsWith("mDNS ")) "mDNS / Bonjour" else "Other discovery methods"
    }
    private fun state(value: String): String = when (value.substringBefore(" · ")) {
        "OK" -> "finished"; "Failed" -> "failed"; "Truncated" -> "unfinished"
        "Running" -> "running"; "Not used" -> "not used"; else -> "unclassified"
    }
    private val diagnosticPatterns = DiscoveryHealth.Diagnostic.values().associateWith {
        Regex("(?:^| · |, )" + Regex.escape(it.label) + " ([0-9]{1,8})(?=$|,| · )")
    }

    fun text(groups: List<Group>, selfGroups: Int, scan: ScanState, sources: Map<String, String>,
             omittedSources: Int, version: String, build: Long, api: Int): String = buildString {
        append("Network Lens recognition report · format 1\n")
        // Build identity is application-owned; validate even that text rather than accepting arbitrary strings.
        append("App: ").append(version.takeIf { it.matches(Regex("[0-9]{1,4}(?:\\.[0-9]{1,4}){1,3}")) } ?: "not available")
        append(" · build ").append(build.coerceAtLeast(0)).append(" · Android API ").append(api.coerceIn(0, 1000)).append('\n')
        append("Scan: ").append(scan.label).append('\n')
        append("Device groups: ").append(groups.size).append(" · address endpoints: ").append(groups.sumOf { it.endpoints }).append('\n')
        append("This phone's groups excluded: ").append(selfGroups).append("\n\nRecognition\n")
        for (level in Level.values()) append(level.label).append(": ").append(groups.count { it.level == level }).append('\n')
        append("Groups with a primary device type: ").append(groups.count { it.typed }).append('\n')
        append("Reported model without a compatible product/family match: ").append(groups.count { it.modelWithoutMatch }).append('\n')
        append("Groups with recorded identity or type conflicts: ").append(groups.count { it.conflict }).append('\n')
        append("Groups with no recorded lookup from the readers below: ").append(groups.count { it.lookups.isEmpty() }).append('\n')
        append("\nIdentity lookups (per address; a group can have several)\n")
        val lookups = groups.flatMap { it.lookups }
        if (lookups.isEmpty()) append("None recorded by these readers.\n")
        for (protocol in Protocol.values()) {
            val entries = lookups.filter { it.protocol == protocol }
            if (entries.isEmpty()) continue
            append(protocol.label).append(": ")
            append(Outcome.values().mapNotNull { outcome -> entries.count { it.outcome == outcome }.takeIf { it > 0 }?.let { outcome.label + " " + it } }.joinToString("; "))
            append('\n')
            for (reason in MetadataHttp.Reason.values()) entries.count { it.reason == reason }.takeIf { it > 0 }?.let {
                append("  ").append(reason.detail).append(": ").append(it).append('\n')
            }
        }
        append("\nDiscovery method outcomes (aggregated)\n")
        val methods = sortedMapOf<String, MutableMap<String, Int>>()
        val diagnostics = sortedMapOf<String, MutableMap<DiscoveryHealth.Diagnostic, Long>>()
        for ((source, raw) in sources) {
            val label = method(source)
            val counts = methods.getOrPut(label) { sortedMapOf() }
            val status = state(raw)
            counts[status] = (counts[status] ?: 0) + 1
            // Parse only the finite diagnostic vocabulary and bounded numbers. Never copy free-text reasons.
            for ((diagnostic, pattern) in diagnosticPatterns) {
                val count = pattern.findAll(raw.take(1024)).sumOf { it.groupValues[1].toLong().coerceAtMost(33554432) }
                if (count > 0) {
                    val items = diagnostics.getOrPut(label) { linkedMapOf() }
                    items[diagnostic] = (items[diagnostic] ?: 0) + count
                }
            }
        }
        if (methods.isEmpty()) append("No method outcomes recorded.\n")
        for ((label, counts) in methods) {
            append(label).append(": ").append(counts.entries.joinToString("; ") { it.key + " " + it.value }).append('\n')
            for ((diagnostic, count) in diagnostics[label].orEmpty()) append("  ").append(diagnostic.label).append(": ").append(count).append('\n')
        }
        append("Method records omitted: ").append(omittedSources.coerceAtLeast(0)).append('\n')
        append("\nLimits: Counts describe the app's interpretation, not verified accuracy or a complete inventory. ")
        append("Family/integration matches may describe software services. A product match does not guarantee an exact generation. ")
        append("No recorded lookup does not prove none was attempted; some readers have only method-level outcomes. ")
        append("Scanner information stays separate from host identity. Grouping and conflicts may need review.\n")
        append("Privacy: Counts and fixed diagnostic labels only. No addresses, names, model strings, identifiers, credentials or raw responses. Nothing is uploaded by this action.\n")
    }
}
