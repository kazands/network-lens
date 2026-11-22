package io.github.kazands.networklens

/** A complete inventory of retained evidence, not a packet capture or proof of recognition accuracy. */
internal object TroubleshootingReport {
    enum class ValueKind { TEXT, IDENTITY, VERSION, CATALOGUE }
    data class Fact(val field: String, val value: String, val source: String = "", val decision: String = "", val publicValue: Boolean = false, val kind: ValueKind = ValueKind.TEXT)
    data class Endpoint(val address: String, val privateValues: List<String>, val facts: List<Fact>, val omitted: Long, val names: List<String> = emptyList())
    data class Group(val summary: String, val endpoints: List<Endpoint>, val summaryFacts: List<Fact> = emptyList())
    data class Document(val pages: List<String>) { fun text() = pages.joinToString("\n\n") }

    fun create(summary: String, groups: List<Group>, context: List<Fact>, includeNames: Boolean = false,
               tcpProbes: TcpProbeJournal.Snapshot = TcpProbeJournal.Snapshot.EMPTY): Document {
        val endpoints = groups.flatMap { it.endpoints }
        val redaction = Redaction(endpoints.flatMap { it.privateValues + it.address } + tcpProbes.entries.map { it.address }, endpoints.flatMap { it.names }, includeNames)
        fun fact(f: Fact): String {
            val value = if (f.publicValue || includeNames && (f.field in setOf("IP address", "Network name", "Hostname", "MAC address", "Router-reported name") || selectedPublicLabel(f.field)))
                redaction.text(f.value, f.kind) else if (f.value.isEmpty()) "No retained value" else redaction.label(f.value)
            return "${redaction.text(f.field, schema = true)}: ${value.ifEmpty { "No retained value" }}" +
                (if (f.source.isEmpty()) "" else " | source: ${redaction.text(f.source)}") +
                (if (f.decision.isEmpty()) "" else " | ${redaction.text(f.decision)}")
        }
        val sections = arrayListOf(buildString {
            append("Network Lens troubleshooting report · format 1\n")
            append("Includes all ${groups.size} device groups / ${groups.sumOf { it.endpoints.size }} address endpoints, including this phone. Search filters do not affect this report.\n")
            append(if (includeNames) "Names and local addresses included by request. " else "Names, addresses and unique identifiers replaced with report-local labels. ")
            append("Review before sharing: device-supplied model, firmware and service text can contain custom information. Credentials and unreviewed raw field values are not included. Selected public HTTP/SSH/certificate labels can contain private names and are included only with the names option.\n\n")
            context.forEach { append(fact(it)).append('\n') }
            append("\nAggregate summary (excludes this phone)\n").append(summary.substringBefore("\nPrivacy:"))
            append("\nEvidence coverage and limitations\n")
            append("This is retained scan evidence, not every byte received and not a complete inventory of silent devices.\n")
            append("Additional capture: mDNS TXT fields; JSON identity responses for Cast, Samsung, Shelly, WLED and Spotify; UPnP/eSCL XML; Apple QueryType/GetValue fields. Unknown values, arrays, XML attributes and sensitive identifiers may be withheld, with notices. Invalid or oversized bodies are not saved.\n")
            append("Other protocol readers retain their decoded observations and available outcomes, not unused raw payload fields. IPP requests only make/model. Selected HTTP server/title/authentication-realm labels, SSH software labels and presented certificate CN/organization/DNS/IP names are recorded separately as unverified clues. Cookies, authentication nonces, raw packets/certificates, keys, certificate serials and raw audio/vendor responses are not retained. No claim that an absent field was never returned.\n")
            append("HTTP metadata history lists each candidate attempt and available queue wait, start budget and request timing. Public readers have a separate bounded journal; a reused equipment root request is identified. Legacy and audio readers list recorded state transitions. Retries internal to a transport and discarded packets may have only scan-level counters. No record means not recorded, not necessarily not attempted.\n")
            append("Each endpoint has a bounded diagnostic journal; exact duplicates are coalesced. Overflow counts are rejected record events, not unique fields. Existing UI/catalogue/parser caps remain in effect. Ports are observed/checked ports, not an exhaustive port scan.\n")
            append("Identity observations list selected versus alternate values. Returned payload fields are listed separately; they are not proof that recognition used them. Child/scanner/playback-group identity is not physical host identity.\n")
        })
        if (tcpProbes.entries.isNotEmpty() || tcpProbes.omitted > 0) sections.add(buildString {
            append("TCP presence checks with at least one successful connection\n")
            append("Diagnostic targets, not additional discovered devices. A failed reconfirmation does not establish absence; another method may have confirmed the address. Checks with no successful connection are not listed here. No exception messages or packet payloads are retained.\n")
            append("Retained checks: ${tcpProbes.entries.size}; check records omitted by capacity limit: ${tcpProbes.omitted}\n")
            val admitted = endpoints.mapTo(hashSetOf()) { it.address }
            tcpProbes.entries.forEachIndexed { index, entry ->
                append("\nTCP check ${index + 1}\n")
                append(fact(Fact("IP address", entry.address))).append('\n')
                append(fact(Fact("TCP port", entry.port.toString(), entry.source, publicValue = true))).append('\n')
                append("Reconfirmation: ${if (entry.confirmed) "Two connections succeeded" else "First connection was not reconfirmed"}\n")
                append("Included in device list: ${if (entry.address in admitted) "Yes" else "No"}\n")
                entry.attempts.forEachIndexed { i, attempt ->
                    append("Connection ${i + 1}: ${if (attempt.connected) "Connected" else attempt.failure.text}; elapsed ${attempt.elapsedMillis} ms; connect timeout ${attempt.timeoutMillis} ms\n")
                }
                append("Total check elapsed: ${entry.elapsedMillis} ms (includes the reconfirmation gap)\n")
            }
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
    private val PUBLIC_CERT_FIELD=Regex("""Public response: TLS (?:DNS|IP) name [1-8] \(unverified\)""")
    /** Exact reviewed field names only; arbitrary device text is not a reason to bypass masking. */
    private fun selectedPublicLabel(field: String): Boolean = field in setOf(
        "Public response: HTTP server label","Public response: HTTP page title","Public response: HTTP authentication realm",
        "Public response: SSH software label","Public response: TLS common name (unverified)","Public response: TLS organization (unverified)") ||
        PUBLIC_CERT_FIELD.matches(field)

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
        private val identifierMatcher = Literals(identifiers)
        private val nameMatcher = Literals(this.names)
        private val factoryNames = this.names.mapTo(hashSetOf(), ::fold)
        private data class TextKey(val raw: String, val kind: ValueKind, val schema: Boolean)
        // Schema, reader names and decisions repeat across endpoints. This cache belongs only
        // to this report build, is bounded, and never outlives create().
        private val rendered = HashMap<TextKey, String>()
        init { (identifiers + this.names).forEach { label(it) } }
        fun label(value: String): String = labels.getOrPut(value.lowercase(java.util.Locale.ROOT)) { "[private value ${labels.size + 1}]" }
        private data class Mask(val start: Int, val end: Int, val replacement: String)
        fun text(raw: String, kind: ValueKind = ValueKind.TEXT, schema: Boolean = false): String {
            val key = TextKey(raw, kind, schema)
            rendered[key]?.let { return it }
            val result = render(raw, kind, schema)
            if (rendered.size < 2048 && raw.length <= 2048) rendered[key] = result
            return result
        }
        private fun render(raw: String, kind: ValueKind, schema: Boolean): String {
            val text = SafeText.label(raw, maxOf(1, raw.length))
            val masks = arrayListOf<Mask>()
            fun pattern(regex: Regex, replacement: (String) -> String) {
                regex.findAll(text).forEach { masks.add(Mask(it.range.first, it.range.last + 1, replacement(it.value))) }
            }
            fun literals(matcher: Literals) {
                matcher.find(text) { start, end, value -> masks.add(Mask(start, end, label(value))) }
            }
            if (!includeNames) {
                literals(identifierMatcher)
                // A factory name may equal its reported model. Retain that whole identity value,
                // but still mask names embedded in other custom model text and all known identifiers.
                val factoryIdentity = kind == ValueKind.IDENTITY && fold(text) in factoryNames
                val version = kind == ValueKind.VERSION && VERSION.matches(text)
                if (!schema && kind != ValueKind.CATALOGUE && !factoryIdentity && !version) literals(nameMatcher)
                if (!version) pattern(IPV4, ::label)
                pattern(MAC, ::label)
                ipv6(text) { start, end -> masks.add(Mask(start, end, label(text.substring(start, end)))) }
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
        /** Prefix lookup avoids rescanning each fact once for every identifier in the inventory.
         * Case folding uses code points, including supplementary letters; match offsets still
         * refer to the original UTF-16 text, never a length-changing lowercase string.
         * Every overlapping match is retained for the original-text merge below. */
        private class Literals(values: List<String>) {
            private class Node(var value: String? = null) { val next = HashMap<Int, Edge>() }
            private class Edge(val text: String, val node: Node)
            private val root = Node()
            init {
                for (value in values) {
                    val folded = fold(value)
                    var node = root
                    var offset = 0
                    while (offset < folded.length) {
                        val key = folded.codePointAt(offset)
                        val edge = node.next[key]
                        if (edge == null) {
                            node.next[key] = Edge(folded.substring(offset), Node(value))
                            break
                        }
                        var common = 0
                        while (common < edge.text.length && offset + common < folded.length) {
                            val c = edge.text.codePointAt(common)
                            if (c != folded.codePointAt(offset + common)) break
                            common += Character.charCount(c)
                        }
                        if (common == edge.text.length) { offset += common; node = edge.node; continue }
                        // Compressed edges keep the node count proportional to identifiers,
                        // rather than allocating a map for every character of every hostname.
                        val branch = Node()
                        node.next[key] = Edge(edge.text.substring(0, common), branch)
                        branch.next[edge.text.codePointAt(common)] = Edge(edge.text.substring(common), edge.node)
                        offset += common
                        if (offset < folded.length) branch.next[folded.codePointAt(offset)] = Edge(folded.substring(offset), Node(value))
                        node = branch
                        break
                    }
                    if (offset == folded.length && node.value == null) node.value = value
                }
            }
            fun find(text: String, found: (Int, Int, String) -> Unit) {
                var start = 0
                while (start < text.length) {
                    var node = root
                    var end = start
                    while (end < text.length) {
                        val edge = node.next[foldCodePoint(text.codePointAt(end))] ?: break
                        var offset = 0
                        while (offset < edge.text.length && end < text.length) {
                            val c = text.codePointAt(end)
                            if (foldCodePoint(c) != edge.text.codePointAt(offset)) break
                            offset += Character.charCount(edge.text.codePointAt(offset))
                            end += Character.charCount(c)
                        }
                        if (offset != edge.text.length) break
                        node = edge.node
                        val value = node.value ?: continue
                        fun word(c: Char) = c.isLetterOrDigit() || c == '_'
                        if ((start == 0 || !word(text[start - 1]) || !word(value.first())) &&
                            (end == text.length || !word(text[end]) || !word(value.last()))) found(start, end, value)
                    }
                    start += Character.charCount(text.codePointAt(start))
                }
            }
        }
        /** Numeric-only validation never resolves a hostname. The lexer bounds candidates to
         * IPv6's maximum dotted form, with an optional interface scope. No backtracking regex. */
        private fun ipv6(text: String, found: (Int, Int) -> Unit) {
            fun numeric(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' || c == ':' || c == '.'
            var i = 0
            while (i < text.length) {
                if (!numeric(text[i])) { i++; continue }
                val runStart = i
                while (i < text.length && numeric(text[i])) i++
                var end = i
                while (end > runStart && text[end - 1] == '.') end--
                // A field label can end in hex letters (e.g. code:2001:db8:...). Check
                // group boundaries in only the last 45 chars so it cannot hide a valid
                // address or turn a long malformed run into quadratic parsing work.
                var start = maxOf(runStart, end - 45)
                var valid = false
                while (start <= end - 2) {
                    if (start == runStart || text[start - 1] == ':' || text[start] == ':' && text[start + 1] == ':') {
                        val candidate = text.substring(start, end)
                        if (candidate.count { it == ':' } >= 2 && Address.bytes(candidate) != null) { valid = true; break }
                    }
                    start++
                }
                if (!valid) continue
                if (i == end && i < text.length && text[i] == '%') {
                    var scoped = i + 1
                    while (scoped < text.length && (text[scoped].isLetterOrDigit() || text[scoped] in "_.~-")) scoped++
                    if (scoped > i + 1) { end = scoped; i = scoped }
                }
                found(start, end)
            }
        }
        companion object {
            private fun foldCodePoint(c: Int) = Character.toLowerCase(Character.toUpperCase(c))
            private fun fold(value: String): String = buildString {
                var offset = 0
                while (offset < value.length) {
                    val c = value.codePointAt(offset)
                    appendCodePoint(foldCodePoint(c)); offset += Character.charCount(c)
                }
            }
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
