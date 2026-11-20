package io.github.kazands.networklens

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections

/** An observed endpoint; metadata and addresses do not establish ownership. */
class Device : Grouping.Endpoint {
    @JvmField var ip = ""
    @JvmField var name = ""
    @JvmField var hostname = ""
    @JvmField var manufacturer = ""
    @JvmField var model = ""
    @JvmField var kind = ""
    @JvmField var mac = ""
    @JvmField var macSource = ""
    @JvmField var identity = ""
    @JvmField var firstSeen = 0L
    @JvmField var lastSeen = 0L
    @JvmField var latency = -1
    @JvmField var seen = false
    @JvmField var self = false
    @JvmField var gateway = false
    private var identityPriority = 0
    private val groupLinks = ArrayList<String>()
    private val members = ArrayList<Device>()
    @JvmField val ports: MutableList<Int> = ArrayList()
    @JvmField val services: MutableList<String> = ArrayList()
    @JvmField val evidence: MutableList<String> = ArrayList()
    @JvmField val details: MutableMap<String, String> = LinkedHashMap()
    internal val audioPlayers = linkedMapOf<String, AudioPlayers.Player>()
    internal val audioPlayerStatuses: MutableMap<String, String> = object : LinkedHashMap<String, String>() {
        override fun put(key: String, value: String): String? {
            diagnostics.record("Audio lookup", "Lookup outcome", value, "Recorded audio lookup transition", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
            return super.put(key, value)
        }
    }
    internal var audioReport: AudioZones.Report? = null
    internal var audioStatus = ""
        set(value) {
            diagnostics.record("Audio lookup", "Lookup outcome", value, "Recorded audio lookup transition", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
            field = value
        }
    internal var audioIdentity = ""
    internal var audioRows: List<Pair<String, String>> = emptyList()
    internal var diagnostics = DiagnosticEvidence()
        private set
    @Synchronized internal fun finishAudioLookups(stopped: Boolean) {
        for ((key, value) in audioPlayerStatuses.toMap()) if (value.endsWith(": Waiting or checking"))
            audioPlayerStatuses[key] = value.removeSuffix("Waiting or checking") +
                if (stopped) "Stopped before lookup finished" else "Lookup did not finish"
        if (audioStatus.endsWith(": Waiting or checking")) audioStatus = audioStatus.removeSuffix("Waiting or checking") +
            if (stopped) "Stopped before lookup finished" else "Lookup did not finish"
    }
    private var hints = IdentityHints()
    private var localNetworkName: LocalNetworkNames.Result? = null
    @Synchronized internal fun networkName(result: LocalNetworkNames.Result) {
        localNetworkName = result
        diagnostics.record(LocalNetworkNames.SOURCE, "Lookup outcome", result.status.description,
            "Optional indirect name lookup; never presence proof", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
        for (name in result.returnedNames) diagnostics.record(LocalNetworkNames.SOURCE, "Router-reported name", name,
            "Private network name; not device-reported identity")
        if (result.omittedNames > 0) diagnostics.record(LocalNetworkNames.SOURCE, "Router names omitted by capacity", result.omittedNames.toString(),
            "Up to four returned names retained; no suggestion when ambiguous", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
    }
    @Synchronized internal fun networkNameRows(): List<Pair<String, String>> = localNetworkName?.let {
        listOf(LocalNetworkNames.SOURCE to it.status.description) +
            it.returnedNames.map { name -> "Router-reported name" to name } +
            if (it.omittedNames > 0) listOf("Router names omitted by capacity" to it.omittedNames.toString()) else emptyList()
    } ?: emptyList()
    @Synchronized internal fun finishNetworkName(stopped: Boolean = true) {
        if (localNetworkName?.status == LocalNetworkNames.Status.CHECKING)
            networkName(LocalNetworkNames.Result(if (stopped) LocalNetworkNames.Status.STOPPED else LocalNetworkNames.Status.UNFINISHED))
    }
    internal fun networkFamily(): LocalNetworkNames.Family? {
        val peers = endpoints()
        // Indirect suggestions yield to reported identity/type evidence, including conflicts.
        if (groupProduct() != null || classification().let { it.category != null || it.conflicted } ||
            peers.any { d -> synchronized(d) { d.self || d.gateway || IdentityHints.specific(d.model) ||
                IdentityHints.specific(d.manufacturer) || IdentityHints.specific(d.kind) || d.matchedFamilies.isNotEmpty() ||
                d.observations().any { it.field in setOf("Model", "Manufacturer", "Device type") && IdentityHints.specific(it.value) } } }) return null
        val names = peers.mapNotNull { d -> synchronized(d) {
            d.localNetworkName?.takeIf { it.status == LocalNetworkNames.Status.CONFIRMED }?.let { result ->
                result.name to LocalNetworkNames.family(result.name, if (d.hardwareAddressMayBeOtherInterface()) "" else d.mac)
            }
        } }
        if (names.any { it.second == null }) return null
        return names.mapNotNull { it.second }.distinct().singleOrNull()
    }
    private var recognition = Recog.Evidence()
    private var catalogue = DeviceCatalog.Evidence()
    private val matchedFamilies = sortedSetOf<String>()
    private var familiesOverflow = false
    @Synchronized internal fun discoveryFamilies(domains: Set<String>, source: String) {
        for (domain in domains.sorted()) {
            if (matchedFamilies.size < 32 || domain in matchedFamilies) matchedFamilies.add(domain) else familiesOverflow = true
            val family = DiscoveryMatchers.family(domain)
            if (family != null && family.hardware.isNotEmpty()) hint("Device type", family.hardware, DiscoveryMatchers.SOURCE + " · " + source, 60)
        }
        detail("Discovery families", matchedFamilies.joinToString(", "))
    }
    @Synchronized internal fun recognizeMatter(report: MatterInfo.Report, source: String) {
        hint("Name", report.name, source + " DN", 65)
        report.vendor?.let { detail("Matter vendor ID", it.toString()) }
        report.product?.let { detail("Matter product ID", it.toString()) }
        report.deviceType?.let {
            detail("Matter device type ID", it.toString())
            hardwareType.offerCategory(MatterInfo.category(it), source + " DT")
        }
        if (report.vendor != null && report.product != null) {
            catalogue.offerRegistryIdentity("matter:${report.vendor}:${report.product}")
        }
    }
    private var hardwareType = HardwareClassification.Evidence()
    internal var metadataLookups = MetadataLookups()
        private set

    /** Evaluated from this endpoint's current observations, never a grouped neighbour's identity. */
    @Synchronized internal fun metadataLookupRows(): List<Pair<String, String>> = metadataLookups.snapshot().map { lookup ->
        val label = lookup.kind.source + " · " + (if (ip.contains(':')) "IPv6" else "IPv4") +
            " · port " + lookup.port + if (lookup.secure) " · TLS" else ""
        val notes = ArrayList<String>()
        for ((field, value) in if (lookup.kind == LocalMetadata.Kind.ESCL) emptyList() else listOf("Model" to model, "Manufacturer" to manufacturer)) {
            if (!IdentityHints.specific(value)) continue
            val sources = hints.observations().filter {
                it.field == field && it.value == value && it.source != lookup.kind.source + " (reported)" &&
                    !it.source.startsWith("Observed ")
            }.map { it.source }.distinct().take(3)
            if (sources.isNotEmpty()) notes.add(field + " available from " + sources.joinToString(", "))
        }
        label to (listOf(lookup.description()) + notes).joinToString("\n")
    }

    @Synchronized fun observations(): MutableList<IdentityHints.Hint> = hints.observations()
    /** Called only by current-wire device-info admission, never native/cached callbacks. */
    @Synchronized internal fun recognizeModel(attributes: Map<String, ByteArray>) = recognition.offer(attributes)
    @Synchronized internal fun recognizeServiceModel(type: String, attributes: Map<String, ByteArray>) = recognition.offerService(type, attributes)
    @Synchronized internal fun recognizeAppleModel(code: String) = recognition.offerAppleModel(code)
    @Synchronized internal fun recognizeKidde(source: String) = catalogue.offerKidde(source)
    @Synchronized internal fun recognizeSmartThings(source: String) = catalogue.offerSmartThings(source)
    @Synchronized internal fun recognizeEnvisalink() = catalogue.offerEnvisalink()
    @Synchronized internal fun recognizeFireTvType(code: String, source: String) = catalogue.offerFireTvType(code, source)

    @Synchronized private fun recognitionCompatible(candidate: Recog.Model): Boolean =
        !catalogue.hasFireTvHardwareIdentity() && !catalogue.hasEnvisalinkIdentity() && !catalogue.hasSmartThingsIdentity() && !catalogue.hasKiddeIdentity() && recognition.compatible(candidate) &&
            (manufacturer.isEmpty() || !IdentityHints.specific(manufacturer) || Recog.appleMaker(manufacturer)) &&
            (model.isEmpty() || !IdentityHints.specific(model) || Recog.accepts(candidate, model)) &&
            hints.observations().none { hint -> IdentityHints.specific(hint.value) && when (hint.field) {
                "Model" -> !Recog.accepts(candidate, hint.value)
                "Manufacturer" -> !Recog.appleMaker(hint.value)
                else -> false
            } }

    @Synchronized internal fun locallyRecognized(): Recog.Model? =
        recognition.match()?.takeIf { recognitionCompatible(it) }

    internal fun groupRecognition(): Recog.Model? {
        val endpoints = endpoints()
        val matches = endpoints.mapNotNull { it.locallyRecognized() }
        val candidate = matches.firstOrNull() ?: return null
        if (matches.any { !Recog.sameModel(it.code, candidate.code) }) return null
        return candidate.takeIf { endpoints.all { it.recognitionCompatible(candidate) } }
    }
    @Synchronized internal fun identifiedProduct(): DeviceCatalog.Match? =
        catalogue.match()?.takeIf { productCompatible(it) } ?: locallyRecognized()?.let(DeviceCatalog::fromRecog)

    @Synchronized private fun productCompatible(match: DeviceCatalog.Match): Boolean {
        match.recog?.let { return recognitionCompatible(it) }
        if (locallyRecognized() != null || !catalogue.compatible(match.product)) return false
        // Include manually populated/legacy fields without promoting them into catalogue evidence.
        val selected = DeviceCatalog.Evidence()
        selected.offer("Manufacturer", manufacturer, "Selected reported field")
        // A selected observation already in catalogue evidence has its original source
        // and compatibility checks there. Only legacy/direct fields need this fallback.
        if (!catalogue.hasModel(model)) selected.offer("Model", model, "Selected reported field")
        return selected.compatible(match.product)
    }
    internal fun groupProduct(): DeviceCatalog.Match? {
        val endpoints = endpoints()
        val matches = endpoints.mapNotNull { it.identifiedProduct() }
        val combined = DeviceCatalog.Evidence()
        endpoints.forEach { endpoint -> synchronized(endpoint) { combined.include(endpoint.catalogue) } }
        val candidate = combined.match() ?: matches.firstOrNull() ?: return null
        if (matches.any { it.product.id != candidate.product.id }) return null
        return candidate.takeIf { endpoints.all { it.productCompatible(candidate) } }
    }
    /** Input is a detached scan snapshot. Never promote diagnostic-only data into identity. */
    internal fun troubleshootingGroup(finishedAt: Long = 0): TroubleshootingReport.Group {
        val product = groupProduct()
        val category = classification()
        val state = recognitionReportGroup()
        fun titleKind(p: DeviceCatalog.Product?) = if (p?.titleSource == DeviceCatalog.TitleSource.CATALOGUE)
            TroubleshootingReport.ValueKind.CATALOGUE else TroubleshootingReport.ValueKind.TEXT
        val summaryFacts = listOf(
            TroubleshootingReport.Fact("Identification", product?.product?.title ?: "No compatible product match", publicValue = true, kind = titleKind(product?.product)),
            TroubleshootingReport.Fact("Primary type", category.category?.label ?: "Not identified", publicValue = true),
            TroubleshootingReport.Fact("Recognition level", state.level.label, publicValue = true),
            TroubleshootingReport.Fact("Recorded conflict", state.conflict.toString(), publicValue = true),
            TroubleshootingReport.Fact("Explanation", recognitionSummary(), publicValue = true),
            TroubleshootingReport.Fact("Classification evidence", category.facts.joinToString("; ") { it.category.label + " from " + it.source }, publicValue = true)
        ) + listOfNotNull(product?.let { TroubleshootingReport.Fact("Product match source", it.source, decision = "Input: ${it.input}", publicValue = true) })
        return TroubleshootingReport.Group("", endpoints().map { d -> synchronized(d) {
            val facts = ArrayList<TroubleshootingReport.Fact>()
            fun add(field: String, value: String, source: String = "", decision: String = "", public: Boolean = true, kind: TroubleshootingReport.ValueKind = TroubleshootingReport.valueKind(field)) {
                facts.add(TroubleshootingReport.Fact(field, value, source, decision, public, kind))
            }
            add("IP address", d.ip, public = false)
            add("Address family", if (d.ip.contains(':')) "IPv6" else "IPv4")
            add("Network name", d.name, public = false); add("Hostname", d.hostname, public = false)
            for ((label, value) in d.networkNameRows()) add(label, value, LocalNetworkNames.SOURCE,
                "Indirect evidence; excluded from reported identity and grouping", public = label != "Router-reported name")
            add("MAC address", d.mac, d.macSource, public = false)
            add("IEEE interface vendor", MacVendors.lookup(d.mac), decision = "Interface vendor only; not reported manufacturer")
            add("Manufacturer", d.manufacturer); add("Model", d.model); add("Reported device type", d.kind)
            add("This phone", d.self.toString()); add("Gateway", d.gateway.toString())
            add("Response time ms", if (d.latency < 0) "Not available" else d.latency.toString())
            add("Presence evidence", d.evidence.joinToString("; ").ifEmpty { "None retained" })
            add("Services", d.services.joinToString("; ").ifEmpty { "None retained" })
            add("Observed / advertised ports", d.ports.sorted().joinToString(", ").ifEmpty { "None retained" })
            d.links().forEach { add("Grouping link", it, it.substringBefore(':'), "Opaque association, not proof of physical ownership", false) }
            add("Last response age at scan end ms", if (finishedAt > 0 && d.lastSeen > 0) (finishedAt - d.lastSeen).coerceAtLeast(0).toString() else "Not recorded")
            d.identifiedProduct()?.let { add("Endpoint product match", it.product.title, it.source, "Input: " + it.input, kind = titleKind(it.product)) }
            for (h in d.observations()) {
                val chosen = when(h.field) { "Name" -> d.name; "Hostname" -> d.hostname; "Manufacturer" -> d.manufacturer; "Model" -> d.model; "Device type" -> d.kind; else -> "" }
                add(h.field, h.value, h.source, if (h.value == chosen) "Selected reported value; priority ${h.priority}" else "Alternate/supporting observation; priority ${h.priority}", h.field !in setOf("Name", "Hostname"))
            }
            val history = d.metadataLookups.history()
            if (history.isEmpty()) add("HTTP identity lookup history", "No attempts recorded; does not prove none were attempted")
            history.forEachIndexed { n, it -> add("Lookup ${n+1}", it.description(), it.kind.source,
                "port ${it.port}; ${if(it.secure) "TLS" else "plain HTTP"}; state ${it.state}; elapsed ${it.elapsedMs?.let { ms -> "${ms}ms" } ?: "not recorded"}") }
            add("Lookup history overflow", d.metadataLookups.omissions().toString())
            for ((key, value) in d.details) add(key, value, "Retained device detail", public = diagnosticPublic(key))
            if (d.audioStatus.isNotEmpty()) add("Audio lookup", d.audioStatus)
            d.audioPlayerStatuses.values.forEach { add("Audio lookup", it) }
            for ((key, value) in d.audioRows) add(key, value, "Audio grouping", public = key in setOf("Audio lookup", "Player source", "Player role", "Group membership", "Source") || diagnosticPublic(key))
            for (player in d.audioPlayers.values) {
                add("Audio player ID", player.id, player.protocol.source, public = false)
                for (group in player.groups) {
                    add("Audio group ID", group.id, player.protocol.source, "Relation: ${group.relation}; role: ${group.role}", false)
                    add("Audio group leader", group.leader, player.protocol.source, public = false)
                    group.peers.sorted().forEach { add("Audio group member", it, player.protocol.source, public = false) }
                }
            }
            d.audioReport?.let { audio ->
                add("Zone hardware ID", audio.hardware, audio.kind.source, public = false)
                add("Zone number", audio.zone.toString(), audio.kind.source)
                add("Zone model", audio.model, audio.kind.source)
                add("Zone group ID", audio.group, audio.kind.source, public = false)
                add("Zone coordinator", audio.coordinator, audio.kind.source, public = false)
                for (member in audio.members) {
                    add("Zone member", member.id, audio.kind.source, public = false)
                    add("Zone member parent", member.parent, audio.kind.source, public = false)
                }
            }
            for (entry in d.diagnostics.snapshot()) {
                val returned = entry.disposition.startsWith("Returned") || entry.disposition.contains("withheld") || entry.disposition == "Array content omitted"
                add(entry.field, entry.value, entry.source, entry.disposition,
                    returned || diagnosticPublic(entry.field) || entry.source in setOf("Presence evidence", "Service observation", "Port observation", "Port check", "Audio lookup"))
            }
            d.diagnostics.losses().forEach { (kind, count) -> add("Diagnostic omitted events: " + kind.name.lowercase().replace('_', ' '), count.toString()) }
            add("Display retention limits", "8 observations per identity field; 16 detail entries; 24 services; 64 ports; 16 presence entries. Diagnostic journal retains up to ${DiagnosticEvidence.LIMIT} entries, prioritising identity observations and lookup outcomes over other returned fields, then withheld values. Capacity counts include displaced or rejected events; repeated omitted events can be counted again.")
            TroubleshootingReport.Endpoint(d.ip, listOf(d.hostname, d.mac, d.identity) + d.observations().filter { it.field == "Hostname" }.map { it.value }, facts, d.diagnostics.omissions(),
                listOf(d.name) + d.localNetworkName?.returnedNames.orEmpty() + d.observations().filter { it.field == "Name" }.map { it.value })
        } }, summaryFacts)
    }
    private fun diagnosticPublic(field: String): Boolean = field in setOf("Model", "Manufacturer", "Device type", "OS", "Firmware", "Port", "Response capture", "Lookup outcome", AppleDeviceInfo.SOURCE, EapInfo.SOURCE, EnvisalinkInfo.SOURCE, "UPnP description status", "Scanning identity", "Discovery families", "Network equipment identity request", "Kidde discovery") ||
        field.lowercase().let { it.contains("model") || it.contains("manufacturer") || it.contains("firmware") || it.contains("platform") || it.contains("protocol version") || it.startsWith("matter ") }

    internal fun recognitionSummary(): String {
        networkFamily()?.let { return "Likely ${it.title}, suggested by a router-reported name. Local DNS agrees in both directions, but router names can be stale. Exact product and model are unverified." }
        val endpoints=endpoints()
        return RecognitionStatus.explain(groupProduct()!=null, classification().conflicted,
            endpoints.any { synchronized(it) { IdentityHints.specific(it.model) } },
            endpoints.flatMap { synchronized(it) { it.matchedFamilies.toList() } }.mapNotNull { DiscoveryMatchers.family(it)?.label }.distinct().sorted(),
            endpoints.flatMap { it.metadataLookups.snapshot() },
            endpoints.any { synchronized(it) { IdentityHints.specificName(it.name) } })
    }
    /** Export facts rather than text, so device-controlled names/models never reach the clipboard report. */
    internal fun recognitionReportGroup(): RecognitionReport.Group {
        val endpoints = endpoints()
        val product = groupProduct()?.product
        val classification = classification()
        val combined = DeviceCatalog.Evidence()
        endpoints.forEach { synchronized(it) { combined.include(it.catalogue) } }
        val conflict = classification.conflicted || combined.hasRecordedConflict() ||
            endpoints.any { synchronized(it) { it.recognition.hasRecordedConflict() } } ||
            product == null && endpoints.mapNotNull { it.identifiedProduct() }.any { candidate ->
                endpoints.any { !it.productCompatible(candidate) } || endpoints.any { other ->
                    other.identifiedProduct()?.product?.id?.let { it != candidate.product.id } == true
                }
            }
        val family = endpoints.any { synchronized(it) {
            !it.familiesOverflow && it.matchedFamilies.any { domain -> DiscoveryMatchers.family(domain) != null }
        } }
        val level = when {
            product != null && !product.familyOnly -> RecognitionReport.Level.PRODUCT
            product != null || family -> RecognitionReport.Level.FAMILY
            endpoints.any { it.services.isNotEmpty() } -> RecognitionReport.Level.SERVICES
            else -> RecognitionReport.Level.UNKNOWN
        }
        val lookups = endpoints.flatMap { endpoint -> synchronized(endpoint) {
            endpoint.metadataLookups.snapshot().map(RecognitionReport::metadata) + listOfNotNull(
                endpoint.details[AppleDeviceInfo.SOURCE]?.let { RecognitionReport.legacy(RecognitionReport.Protocol.APPLE, it) },
                endpoint.details[EapInfo.SOURCE]?.let { RecognitionReport.legacy(RecognitionReport.Protocol.EAP, it) },
                endpoint.details[EnvisalinkInfo.SOURCE]?.let { RecognitionReport.legacy(RecognitionReport.Protocol.ENVISALINK, it) })
        } }
        return RecognitionReport.Group(level, product == null && endpoints.any { IdentityHints.specific(it.model) },
            conflict, classification.category != null && !classification.conflicted, endpoints.size, lookups)
    }
    internal fun presentation(): DevicePresentation.Labels {
        val product = groupProduct()?.product
        val classification = classification()
        val displayProduct = product?.copy(category = classification.category ?: DeviceCatalog.Category.UNSPECIFIED)
        val labels = DevicePresentation.labels(name, hostname, model, DeviceIcon.categoryLabel(this), displayProduct, self, gateway)
        val families = endpoints().flatMap { synchronized(it) { it.matchedFamilies.toList() } }.distinct()
        val family = DiscoveryMatchers.preferredFamily(families)
        networkFamily()?.let {
            return if (labels.unnamed) labels.copy(title = "Likely " + it.title,
                subtitle = it.role + " · Router name suggestion", unnamed = false)
            else labels.copy(subtitle = "Likely " + it.title + " · Router name suggestion")
        }
        return if (labels.unnamed && !classification.conflicted && family != null && endpoints().none { it.familiesOverflow })
            labels.copy(title = family.label, subtitle = labels.subtitle.ifEmpty { "Device family; model not available" }, unnamed = false) else labels
    }
    @Synchronized private fun hardwareFacts(): List<HardwareClassification.Fact> =
        hardwareType.snapshot() + catalogue.categories() + recognition.categories() + listOfNotNull(
            HardwareClassification.reported(kind)?.let { HardwareClassification.Fact(it, "Selected reported device type") })

    internal fun classification(): HardwareClassification.Result {
        val combined = DeviceCatalog.Evidence()
        val endpoints = endpoints()
        endpoints.forEach { endpoint -> synchronized(endpoint) { combined.include(endpoint.catalogue) } }
        val catalogueFacts = combined.categories()
        val result = HardwareClassification.resolve(endpoints.flatMap { it.hardwareFacts() } + catalogueFacts)
        // Apply the identity constraint after combining addresses. Otherwise a
        // neighbor's category can restore a hardware interpretation rejected here.
        return if ((combined.hasFireTvHardwareIdentity() || combined.hasEnvisalinkIdentity() || combined.hasSmartThingsIdentity() || combined.hasKiddeIdentity()) && catalogueFacts.isEmpty())
            result.copy(category = null, conflicted = true) else result
    }
    internal fun capabilities(): List<String> {
        val peers = endpoints()
        val kidde = peers.any { synchronized(it) { it.catalogue.hasKiddeIdentity() } }
        return HardwareClassification.capabilities(peers.flatMap { synchronized(it) { it.services.toList() } }).map {
            if (it == "HomeKit" && kidde) "HomeKit advertisement" else it
        }.distinct().sorted()
    }
    @Synchronized fun hint(field: String?, value: String?, source: String?, priority: Int) {
        if (field == "Model" && UpnpDescription.presentationModel(value.orEmpty(), source.orEmpty())) {
            diagnostics.record(source.orEmpty(), "Bonjour presentation model", value, "Service presentation label; not physical host identity")
            detail("Bonjour presentation model", value)
            return
        }
        diagnostics.record(source.orEmpty(), field.orEmpty(), value, "Offered to identity selection; priority $priority",
            if (field in setOf("Model", "Manufacturer", "Device type", "OS", "Firmware")) DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME
            else DiagnosticEvidence.Retention.OBSERVATION)
        if (field == "Device type") hardwareType.offer(SafeText.label(value, 160), SafeText.label(source, 128))
        if ((field == "Model" || field == "Manufacturer") && SafeText.label(source, 128).isNotEmpty()) {
            val cleanValue = SafeText.label(value, 160)
            recognition.observeReport(field, cleanValue)
            catalogue.offer(field, cleanValue, SafeText.label(source, 128))
        }
        val hint = hints.offer(field, value, source, priority) ?: return
        when (field) {
            "Name" -> name = hint.value
            "Hostname" -> hostname = hint.value
            "Manufacturer" -> manufacturer = hint.value
            "Model" -> model = hint.value
            "Device type" -> kind = hint.value
            "OS" -> detail("OS (advertised)", hint.value)
            "Firmware" -> detail("Firmware (advertised)", hint.value)
            else -> return
        }
    }

    @Synchronized internal fun upnpIdentity(values: Map<String, String>) {
        val source = "UPnP device description"
        if (UpnpDescription.softwareIdentity(values)) {
            for ((field, key) in listOf("Software manufacturer" to "manufacturer", "Software model" to "modelName", "Software name" to "friendlyName"))
                diagnostics.record(source, field, values[key], "Service identity; not physical host identity", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
        } else {
            hint("Name", values["friendlyName"], source + " friendly name", 80)
            hint("Manufacturer", values["manufacturer"], source, 70)
            hint("Model", values["modelName"], source, 70)
        }
    }

    @Synchronized fun identify(candidate: String?, priority: Int) {
        val clean = SafeText.label(candidate, 200)
        if (clean.isNotEmpty() && (identity.isEmpty() || priority > identityPriority ||
            (priority == identityPriority && clean.compareTo(identity) < 0))) {
            identity = clean
            identityPriority = priority
        }
    }
    @Synchronized override fun isSelf(): Boolean = self
    @Synchronized override fun hardwareAddress(): String = mac
    @Synchronized override fun hardwareAddressMayBeOtherInterface(): Boolean =
        macSource == "RAOP advertised hardware ID; may be another interface"
    @Synchronized override fun links(): Collection<String> = ArrayList(groupLinks)
    @Synchronized fun link(key: String?) {
        if (key != null && key.length <= 200 && groupLinks.size < 32 && !groupLinks.contains(key)) groupLinks.add(key)
    }
    @Synchronized fun endpoints(): MutableList<Device> =
        if (members.isEmpty()) Collections.singletonList(this) else ArrayList(members)

    @Synchronized fun title(): String = when {
        name.isNotEmpty() -> name
        self -> "This Android device"
        gateway -> "Router"
        else -> "Device at " + ip
    }
    @Synchronized fun evidence(value: String?) {
        diagnostics.record("Presence evidence", "Presence evidence", value)
        val clean = SafeText.label(value, 160)
        if (clean.isNotEmpty() && evidence.size < 16 && !evidence.contains(clean)) evidence.add(clean)
    }
    @Synchronized fun service(value: String?) {
        diagnostics.record("Service observation", "Service observation", value)
        val clean = SafeText.label(value, 128)
        if (clean.isNotEmpty() && services.size < 24 && !services.contains(clean)) services.add(clean)
    }
    @Synchronized fun port(port: Int) {
        if (port in 1..65535) diagnostics.record("Port observation", "Port", port.toString())
        if (port in 1..65535 && ports.size < 64 && !ports.contains(port)) ports.add(port)
    }

    @Synchronized fun hardware(address: String?, source: String?) {
        val valid = Address.mac(address)
        if (valid.isNotEmpty()) {
            val cleanSource = SafeText.label(source, 128)
            // Repeating an address must not downgrade its provenance: grouping uses
            // the source to distinguish an adapter address from a RAOP identifier.
            if (mac == valid && hardwareSourceRank(cleanSource) <= hardwareSourceRank(macSource)) return
            if (mac.isNotEmpty() && mac != valid) {
                // A RAOP identifier may name another interface. A later adapter
                // report must constrain grouping even when its address differs.
                val prefer = cleanSource.contains("ARP cache") || (cleanSource.contains("NetBIOS") && !macSource.contains("ARP cache")) ||
                    (hardwareAddressMayBeOtherInterface() && hardwareSourceRank(cleanSource) > 0)
                if (prefer) detail("Other advertised MAC", mac + " · " + macSource)
                else { detail("Other reported MAC", valid + " · " + cleanSource); return }
            }
            mac = valid
            macSource = cleanSource
        }
    }

    private fun hardwareSourceRank(source: String): Int = when {
        source.contains("ARP cache") -> 3
        source.contains("NetBIOS") -> 2
        source == "RAOP advertised hardware ID; may be another interface" -> 0
        source.isEmpty() -> -1
        else -> 1
    }

    @Synchronized fun detail(key: String?, value: String?) {
        diagnostics.record("Device detail", key.orEmpty(), value, "Diagnostic detail; may not affect identity",
            if (key in setOf(AppleDeviceInfo.SOURCE, EapInfo.SOURCE, EnvisalinkInfo.SOURCE, "UPnP description status"))
                DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME else DiagnosticEvidence.Retention.OBSERVATION)
        val cleanKey = SafeText.label(key, 64)
        val cleanValue = SafeText.label(value, 160)
        if (cleanKey.isEmpty() || cleanValue.isEmpty() || legacySource(cleanKey)) return
        details[cleanKey] = cleanValue
        if (details.size > 16) {
            val last = Collections.max(details.keys, compareBy<String> { detailRank(it) }.thenBy { it })
            details.remove(last)
        }
    }

    @Synchronized fun json(): JSONObject {
        val out = JSONObject()
        try {
            out.put("ip", ip).put("name", name).put("hostname", hostname).put("manufacturer", manufacturer)
                .put("model", model).put("kind", kind).put("mac", mac).put("macSource", macSource)
                .put("identity", identity).put("identityPriority", identityPriority).put("firstSeen", firstSeen)
                .put("lastSeen", lastSeen).put("latency", latency).put("self", self).put("gateway", gateway)
                .put("ports", JSONArray(ports)).put("services", JSONArray(services)).put("evidence", JSONArray(evidence))
                .put("details", JSONObject(details)).put("groupLinks", JSONArray(groupLinks))
            out.put("localNetworkName", JSONArray(networkNameRows().map { (label, value) -> JSONObject().put("label", label).put("value", value) }))
            val observations = JSONArray()
            for (h in hints.observations()) observations.put(JSONObject().put("field", h.field).put("value", h.value)
                .put("source", h.source).put("priority", h.priority))
            out.put("observations", observations)
            out.put("audioMapping", JSONArray(audioRows.map { (label, value) -> JSONObject().put("label", label).put("value", value) }))
            if (audioStatus.isNotEmpty()) out.put("audioLookup", audioStatus)
            identifiedProduct()?.let { match -> out.put("localRecognition", JSONObject()
                .put("description", match.product.title).put("deviceType", match.product.category.label)
                .put("recognizedBrand", match.product.brand).put("familyOnly", match.product.familyOnly)
                .put("source", match.source).put("input", match.input)) }
        } catch (_: JSONException) {}
        return out
    }

    @Synchronized fun copy(): Device {
        val copy = Device()
        copy.ip = ip; copy.name = name; copy.hostname = hostname; copy.manufacturer = manufacturer
        copy.model = model; copy.kind = kind; copy.mac = mac; copy.macSource = macSource
        copy.identity = identity; copy.identityPriority = identityPriority
        copy.firstSeen = firstSeen; copy.lastSeen = lastSeen; copy.latency = latency
        copy.seen = seen; copy.self = self; copy.gateway = gateway
        copy.groupLinks.addAll(groupLinks); copy.ports.addAll(ports); copy.services.addAll(services)
        copy.evidence.addAll(evidence); copy.details.putAll(details); copy.hints = hints.copy()
        copy.localNetworkName = localNetworkName
        copy.recognition = recognition.copy()
        copy.catalogue = catalogue.copy()
        copy.hardwareType = hardwareType.copy()
        copy.matchedFamilies.addAll(matchedFamilies); copy.familiesOverflow = familiesOverflow
        copy.metadataLookups = metadataLookups.copy()
        copy.audioReport = audioReport?.copy(members = audioReport!!.members.toList())
        copy.audioPlayers.putAll(audioPlayers)
        copy.audioPlayerStatuses.putAll(audioPlayerStatuses)
        copy.audioIdentity = audioIdentity; copy.audioStatus = audioStatus; copy.audioRows = audioRows.toList()
        for (member in members) copy.members.add(member.copy())
        // Recording setters above rebuild state, not scan events. Restore the exact journal last,
        // including its overflow count, so taking a snapshot cannot manufacture omissions.
        copy.diagnostics = diagnostics.copy()
        return copy
    }

    companion object {
        internal fun audioRows(devices: List<Device>) {
            val zones = devices.mapNotNull { d -> d.audioReport?.let { AudioZones.Endpoint(d.ip, it) } }
            val players = devices.flatMap { d -> d.audioPlayers.values.map { AudioPlayers.Current(d.ip, it) } }
            for (d in devices) d.audioRows =
                (d.audioReport?.let { AudioZones.rows(AudioZones.Endpoint(d.ip, it), zones) } ?: emptyList()) +
                d.audioPlayerStatuses.values.sorted().map { "Audio lookup" to it } +
                d.audioPlayers.toSortedMap().values.flatMap { AudioPlayers.rows(AudioPlayers.Current(d.ip, it), players) }
        }

        @JvmStatic fun grouped(raw: List<Device>): MutableList<Device> {
            val sorted = ArrayList(raw.map { it.copy() })
            audioRows(sorted)
            sorted.sortWith { a, b -> Address.compare(a.ip, b.ip) }
            val out = ArrayList<Device>()
            for (group in Grouping.groups(sorted)) {
                if (group.size == 1) { out.add(group[0].copy()); continue }
                val representative = group[0].copy()
                for (d in group) {
                    for (h in d.observations()) representative.hint(h.field, h.value, h.source, h.priority)
                    representative.hint("Name", d.name, "Observed name", 0)
                    representative.hint("Model", d.model, "Observed model", 0)
                    representative.hint("Manufacturer", d.manufacturer, "Observed manufacturer", 0)
                    representative.hint("Hostname", d.hostname, "Observed hostname", 0)
                    representative.hint("Device type", d.kind, "Observed type", 0)
                    if (representative.mac.isEmpty()) { representative.mac = d.mac; representative.macSource = d.macSource }
                    representative.gateway = representative.gateway || d.gateway
                }
                if (group.size > 1) for (d in group) representative.members.add(d.copy())
                out.add(representative)
            }
            return out
        }

        @JvmStatic fun currentGroup(raw: List<Device>, previous: Device): Device? {
            val addresses = previous.endpoints().mapTo(HashSet()) { it.ip }
            for (group in grouped(raw)) for (endpoint in group.endpoints()) if (endpoint.ip in addresses) return group
            return null
        }

        private fun legacySource(key: String): Boolean =
            key in arrayOf("Name source", "Hostname source", "Manufacturer source", "Model source",
                "Device type source", "OS source", "Firmware source")

        private fun detailRank(key: String): Int = when {
            key == "ONVIF hardware (advertised)" -> 0
            key == "OS (advertised)" || key == "Firmware (advertised)" -> 1
            key.contains("model") || key == "UPnP description status" -> 2
            else -> 3
        }

        @JvmStatic fun from(out: JSONObject): Device {
            val d = Device()
            d.ip = out.optString("ip"); d.name = out.optString("name"); d.hostname = out.optString("hostname")
            d.manufacturer = out.optString("manufacturer"); d.model = out.optString("model"); d.kind = out.optString("kind")
            d.mac = Address.mac(out.optString("mac")); d.macSource = out.optString("macSource")
            d.identity = out.optString("identity"); d.identityPriority = maxOf(0, minOf(100, out.optInt("identityPriority")))
            d.firstSeen = out.optLong("firstSeen"); d.lastSeen = out.optLong("lastSeen")
            d.latency = out.optInt("latency", -1); d.self = out.optBoolean("self"); d.gateway = out.optBoolean("gateway")
            out.optJSONArray("groupLinks")?.let { a -> repeat(minOf(32, a.length())) { d.link(a.optString(it)) } }
            out.optJSONArray("ports")?.let { a -> repeat(minOf(64, a.length())) { d.port(a.optInt(it)) } }
            val services = out.optJSONArray("services")
            if (services != null) repeat(minOf(24, services.length())) { d.service(services.optString(it)) }
            else d.service(out.optString("service"))
            val evidence = out.optJSONArray("evidence")
            if (evidence != null) repeat(minOf(16, evidence.length())) { d.evidence(evidence.optString(it)) }
            else d.evidence(out.optString("evidence"))
            out.optJSONObject("details")?.let { meta ->
                val keys = meta.keys()
                var read = 0
                while (keys.hasNext() && read++ < 64) { val key = keys.next(); d.detail(key, meta.optString(key)) }
            }
            out.optJSONArray("observations")?.let { observations ->
                repeat(minOf(56, observations.length())) { i ->
                    observations.optJSONObject(i)?.let { h ->
                        d.hint(h.optString("field"), h.optString("value"), h.optString("source"),
                            maxOf(0, minOf(100, h.optInt("priority"))))
                    }
                }
            }
            return d
        }
    }
}

