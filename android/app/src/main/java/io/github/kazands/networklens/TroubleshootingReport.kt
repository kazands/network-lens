package io.github.kazands.networklens

/** A complete inventory of retained evidence, not a packet capture or proof of recognition accuracy. */
internal object TroubleshootingReport {
    enum class ValueKind { TEXT, IDENTITY, VERSION, CATALOGUE }
    data class Fact(val field: String, val value: String, val source: String = "", val decision: String = "", val publicValue: Boolean = false, val kind: ValueKind = ValueKind.TEXT)
    data class Endpoint(val address: String, val privateValues: List<String>, val facts: List<Fact>, val omitted: Long, val names: List<String> = emptyList())
    data class Group(val summary: String, val endpoints: List<Endpoint>, val summaryFacts: List<Fact> = emptyList())
    data class Document(val pages: List<String>) { fun text() = pages.joinToString("\n\n") }

    fun create(summary: String, groups: List<Group>, context: List<Fact>, includeNames: Boolean = false): Document {
        val endpoints = groups.flatMap { it.endpoints }
        val redaction = Redaction(endpoints.flatMap { it.privateValues + it.address }, endpoints.flatMap { it.names }, includeNames)
        fun fact(f: Fact): String {
            val value = if (f.publicValue || includeNames && f.field in setOf("IP address", "Network name", "Hostname", "MAC address", "Router-reported name"))
                redaction.text(f.value, f.kind) else if (f.value.isEmpty()) "No retained value" else redaction.label(f.value)
            return "${redaction.text(f.field, schema = true)}: ${value.ifEmpty { "No retained value" }}" +
                (if (f.source.isEmpty()) "" else " | source: ${redaction.text(f.source)}") +
                (if (f.decision.isEmpty()) "" else " | ${redaction.text(f.decision)}")
        }
        val sections = arrayListOf(buildString {
            append("Network Lens troubleshooting report · format 1\n")
            append("Includes all ${groups.size} device groups / ${groups.sumOf { it.endpoints.size }} address endpoints, including this phone. Search filters do not affect this report.\n")
            append(if (includeNames) "Names and local addresses included by request. " else "Names, addresses and unique identifiers replaced with report-local labels. ")
            append("Review before sharing: device-supplied model, firmware and service text can contain custom information. Credentials and unreviewed raw field values are not included.\n\n")
            context.forEach { append(fact(it)).append('\n') }
            append("\nAggregate summary (excludes this phone)\n").append(summary.substringBefore("\nPrivacy:"))
            append("\nEvidence coverage and limitations\n")
            append("This is retained scan evidence, not every byte received and not a complete inventory of silent devices.\n")
            append("Additional capture: mDNS TXT fields; JSON identity responses for Cast, Samsung, Shelly, WLED and Spotify; UPnP/eSCL XML; Apple QueryType/GetValue fields. Unknown values, arrays, XML attributes and sensitive identifiers may be withheld, with notices. Invalid or oversized bodies are not saved.\n")
            append("Other protocol readers retain their decoded observations and available outcomes, not unused raw payload fields. IPP requests only make/model. HTTP headers, packet contents, TLS certificates and raw audio/vendor responses are not retained. No claim that an absent field was never returned.\n")
            append("HTTP metadata history lists each candidate attempt; legacy and audio readers list recorded state transitions. Retries internal to a transport and discarded packets may have only scan-level counters. No record means not recorded, not necessarily not attempted.\n")
            append("Each endpoint has a bounded diagnostic journal; exact duplicates are coalesced. Overflow counts are rejected record events, not unique fields. Existing UI/catalogue/parser caps remain in effect. Ports are observed/checked ports, not an exhaustive port scan.\n")
            append("Identity observations list selected versus alternate values. Returned payload fields are listed separately; they are not proof that recognition used them. Child/scanner/playback-group identity is not physical host identity.\n")
        })
        groups.forEachIndexed { index, group ->
            sections.add(buildString {
                append("Device ${index + 1}\n")
                if (group.summaryFacts.isEmpty()) append(group.summary.lineSequence().joinToString("\n") { redaction.text(it) }).append('\n')
                else group.summaryFacts.forEach { append(fact(it)).append('\n') }
                group.endpoints.forEachIndexed { addressIndex, endpoint ->
                    append("\nDevice ${index + 1} / Address ${addressIndex + 1}\n")
                    endpoint.facts.forEach { append(fact(it)).append('\n') }
                    append("Diagnostic record events omitted by capacity limit: ${endpoint.omitted}\n")
                }
            })
        }
        // Pages are display/clipboard chunks only. Sharing concatenates EVERY page; no device cap.
        val pages = arrayListOf<String>()
        for (section in sections) {
            var start = 0
            while (start < section.length) {
                var end = minOf(start + 12000, section.length)
                if (end < section.length && Character.isHighSurrogate(section[end - 1])) end--
                pages.add(section.substring(start, end)); start = end
            }
        }
        return Document(pages)
    }
    /** Exact reviewed field names only; arbitrary device text is not a reason to bypass masking. */
    fun valueKind(field: String): ValueKind {
        val key = field.substringAfterLast('/').removePrefix("TXT ").removeSuffix(" (advertised)").lowercase(java.util.Locale.ROOT)
        return when (key) {
            "model", "modelname", "model_name", "modelnumber", "model_number", "modeldescription", "modeldisplayname",
            "manufacturer", "branddisplayname", "makeandmodel", "zone model", "md", "ty", "product", "usb_mdl", "mdl",
            "usb_mfg", "mfg", "am", "rpmd", "mn", "vn" -> ValueKind.IDENTITY
            "firmware", "firmwareversion", "firmware version", "version", "ver", "fw", "fv", "fwversion", "osvers",
            "libraryversion", "srcvers", "protovers" -> ValueKind.VERSION
            else -> ValueKind.TEXT
        }
    }
    private class Redaction(values: List<String>, names: List<String>, private val includeNames: Boolean) {
        private val labels = linkedMapOf<String, String>()
        private val identifiers = values.filter { it.isNotBlank() }.distinct()
        private val names = names.filter { it.isNotBlank() }.distinct()
        init { (identifiers + this.names).forEach { label(it) } }
        fun label(value: String): String = labels.getOrPut(value.lowercase(java.util.Locale.ROOT)) { "[private value ${labels.size + 1}]" }
        private data class Mask(val start: Int, val end: Int, val replacement: String)
        fun text(raw: String, kind: ValueKind = ValueKind.TEXT, schema: Boolean = false): String {
            val text = SafeText.label(raw, maxOf(1, raw.length))
            val masks = arrayListOf<Mask>()
            fun pattern(regex: Regex, replacement: (String) -> String) {
                regex.findAll(text).forEach { masks.add(Mask(it.range.first, it.range.last + 1, replacement(it.value))) }
            }
            fun literal(value: String) {
                var start = text.indexOf(value, ignoreCase = true)
                while (start >= 0) {
                    val end = start + value.length
                    // Names must be complete tokens, not letters inside schema labels or product words.
                    fun word(c: Char) = c.isLetterOrDigit() || c == '_'
                    if ((start == 0 || !word(text[start - 1]) || !word(value.first())) &&
                        (end == text.length || !word(text[end]) || !word(value.last())))
                        masks.add(Mask(start, end, label(value)))
                    start = text.indexOf(value, start + 1, ignoreCase = true)
                }
            }
            if (!includeNames) {
                identifiers.forEach(::literal)
                // A factory name may equal its reported model. Retain that whole identity value,
                // but still mask names embedded in other custom model text and all known identifiers.
                val factoryIdentity = kind == ValueKind.IDENTITY && names.any { text.equals(it, ignoreCase = true) }
                val version = kind == ValueKind.VERSION && VERSION.matches(text)
                if (!schema && kind != ValueKind.CATALOGUE && !factoryIdentity && !version) names.forEach(::literal)
                if (!version) pattern(IPV4, ::label)
                pattern(MAC, ::label)
            }
            // Match every rule against the ORIGINAL text. Neither private labels nor other generated
            // replacements can become input to another rule. Merge overlapping masks without leaks.
            pattern(URL) { "[URL withheld]" }; pattern(SECRET) { "[credential field withheld]" }
            pattern(UDN, ::label); pattern(UUID, ::label); pattern(EMAIL) { "[email withheld]" }
            val ordered = masks.sortedWith(compareBy<Mask> { it.start }.thenByDescending { it.end })
            return buildString {
                var cursor = 0
                var i = 0
                while (i < ordered.size) {
                    val mask = ordered[i++]
                    var end = mask.end
                    while (i < ordered.size && ordered[i].start < end) end = maxOf(end, ordered[i++].end)
                    append(text, cursor, mask.start); append(mask.replacement); cursor = end
                }
                append(text, cursor, text.length)
            }
        }
        companion object {
            private val VERSION = Regex("[vV]?[0-9]+(?:\\.[0-9]+){1,7}(?:[-+_][A-Za-z0-9._-]+)?")
            private val URL = Regex("(?i)\\b(?:https?|ftp)://[^\\s]+")
            private val SECRET = Regex("(?i)\\b(?:password|passwd|access_token|refresh_token|authorization|cookie|private_key)\\s*[:=]\\s*[^\\s,;]+")
            private val IPV4 = Regex("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")
            private val MAC = Regex("(?i)(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}")
            // UPnP UDNs are opaque: many vendors do not use RFC UUID formatting.
            private val UDN = Regex("(?i)\\buuid:[^\\s;|]+")
            private val UUID = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
            private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        }
    }
}
