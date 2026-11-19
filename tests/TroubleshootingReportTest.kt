import io.github.kazands.networklens.*

object TroubleshootingReportTest {
    private var checks = 0
    private fun verify(ok: Boolean, message: String) { checks++; if (!ok) error(message) }
    @JvmStatic fun main(args: Array<String>) {
        val journal = DiagnosticEvidence()
        journal.txt("mDNS _fixture._tcp.", mapOf("model" to "Fixture lamp".toByteArray(), "features" to "123".toByteArray(), "token" to "TOKEN_SECRET".toByteArray(), "unknown" to "UNKNOWN_SECRET".toByteArray()))
        journal.json("Samsung TV device information", """{"device":{"type":"Samsung SmartTV","firmwareVersion":"fixture-1","name":"PRIVATE_ROOM","TokenAuthSupport":true},"unexpected":"SECRET","token":"TOKEN_SECRET"}""".toByteArray(), DiagnosticEvidence.paths(LocalMetadata.Kind.SAMSUNG))
        val json = journal.snapshot().joinToString()
        verify(json.contains("fixture-1") && json.contains("Samsung SmartTV") && json.contains("TokenAuthSupport"), "Useful partial Samsung fields and capability retained without a model")
        verify(!json.contains("TOKEN_SECRET") && !json.contains("UNKNOWN_SECRET") && !json.contains("PRIVATE_ROOM"), "Unknown raw values not retained in diagnostic RAM")
        verify(json.contains("unexpected") && json.contains("value withheld"), "Unsupported fields explicitly listed")
        verify(SamsungInfo.parse("""{"device":{"type":"Samsung SmartTV","firmwareVersion":"fixture-1"}}""".toByteArray()) == null, "Diagnostic evidence does not change recognition")
        journal.json("Spotify", """{"status":402,"spotifyError":7,"activeUser":"PRIVATE_ACCOUNT"}""".toByteArray(), DiagnosticEvidence.paths(LocalMetadata.Kind.SPOTIFY))
        verify(journal.snapshot().any { it.field == "spotifyError" && it.value == "7" }, "Spotify failure code retained")
        journal.xml("UPnP", """<root><device><modelName>Fixture Hub</modelName><serialNumber>PRIVATE_SERIAL</serialNumber><serviceList><service><serviceType>urn:fixture:service:1</serviceType><controlURL>http://secret/</controlURL></service></serviceList></device></root>""".toByteArray())
        verify(journal.snapshot().any { it.value == "urn:fixture:service:1" }, "Nested service types retained separately from host identity")
        verify(!journal.snapshot().joinToString().contains("PRIVATE_SERIAL") && !journal.snapshot().joinToString().contains("http://secret"), "XML identifiers and URLs withheld")
        journal.json("Invalid", "not json".toByteArray(), emptySet())
        verify(journal.snapshot().any { it.field == "Response capture" && it.value.contains("not retained") }, "Parser failure explicitly disclosed")
        val copy = journal.copy()
        repeat(200) { journal.record("fixture", "field$it", "value$it") }
        verify(journal.snapshot().size == DiagnosticEvidence.LIMIT && journal.omissions() > 0, "Diagnostic capacity loss counted")
        verify(copy.omissions() == 0L && copy.snapshot().size < journal.snapshot().size, "Detached journal snapshot")
        val long = DiagnosticEvidence(); long.record("fixture", "model", "X".repeat(300))
        verify(long.snapshot().single().disposition.contains("shortened"), "Text truncation explicit")
        val busy = DiagnosticEvidence()
        repeat(DiagnosticEvidence.LIMIT) { busy.txt("Fixture service", mapOf("private$it" to "PRIVATE".toByteArray())) }
        busy.record("Fixture model", "Model", "Synthetic product", "Offered to identity selection", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
        busy.record("Fixture reader", "Lookup outcome", "Response read failed", retention = DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
        busy.txt("Fixture service", mapOf("fwversion" to "fixture-version".toByteArray()))
        verify(busy.snapshot().size == DiagnosticEvidence.LIMIT && busy.omissions() == 3L,
            "Priority eviction preserves the total bound and counts every displaced event")
        verify(busy.snapshot().any { it.value == "Synthetic product" } && busy.snapshot().any { it.value == "Response read failed" },
            "Early withheld fields cannot crowd out late identity or lookup outcomes")
        val busyCopy = busy.copy()
        repeat(5) { busy.record("Fixture reader", "Lookup outcome", "Response read failed", retention = DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME) }
        verify(busy.omissions() == 3L && busyCopy.snapshot() == busy.snapshot(), "Retained duplicate outcomes coalesce without manufacturing loss")
        repeat(200) { busy.record("Fixture reader", "Lookup outcome", "Synthetic result $it", retention = DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME) }
        verify(busy.snapshot().size == DiagnosticEvidence.LIMIT && busy.omissions() == 203L, "Even protected evidence stays bounded with explicit overflow")
        verify(busyCopy.omissions() == 3L, "Priority and loss accounting are detached on snapshot")
        val lookups = MetadataLookups()
        val first = LocalMetadata.Request(LocalMetadata.Kind.CAST, 8008, "/setup/eureka_info")
        lookups.queue(first); lookups.failure(first.kind, MetadataHttp.Failure(MetadataHttp.Reason.READ_TIMEOUT))
        lookups.beginCandidate(first); lookups.success(first.kind, LocalMetadata.Report(mapOf("Model" to "Fixture speaker")))
        verify(lookups.history().map { it.state } == listOf(MetadataLookups.State.FAILED, MetadataLookups.State.SUCCESS), "Prior candidate failure survives success")
        verify(lookups.snapshot().single().state == MetadataLookups.State.SUCCESS, "UI retains final outcome contract")
        val historyCopy = lookups.copy(); lookups.finish(false)
        verify(historyCopy.history() == lookups.history(), "History survives copies and seal")
        val many = MetadataLookups(); many.queue(first)
        repeat(80) { many.failure(first.kind, MetadataHttp.Failure(MetadataHttp.Reason.READ_FAILED)); many.beginCandidate(first) }
        verify(many.history().size == 65 && many.omissions() == 16, "Lookup overflow explicit and bounded")
        many.finish(true); verify(many.history().last().state == MetadataLookups.State.STOPPED, "Interrupted final attempt retained")
        val facts = listOf(
            TroubleshootingReport.Fact("IP address", "192.0.2.20"),
            TroubleshootingReport.Fact("Network name", "Fixture private room"),
            TroubleshootingReport.Fact("Model", "Fixture Model", publicValue = true),
            TroubleshootingReport.Fact("Serial number", "SERIAL_PRIVATE"),
            TroubleshootingReport.Fact("Firmware", "fixture-1", publicValue = true))
        val group = TroubleshootingReport.Group("No product match\nPrimary type: Not identified", listOf(TroubleshootingReport.Endpoint("192.0.2.20", listOf("Fixture private room"), facts, 4)))
        val document = TroubleshootingReport.create("aggregate", listOf(group), emptyList())
        val report = document.text()
        verify(report.contains("No product match\nPrimary type: Not identified"), "Generated summary preserves line breaks")
        verify(report.contains("Fixture Model") && report.contains("fixture-1") && report.contains("Device 1 / Address 1"), "Unnamed device has useful evidence")
        verify(!report.contains("192.0.2.20") && !report.contains("Fixture private room") && !report.contains("SERIAL_PRIVATE"), "Default report masks identifying values")
        verify(report.contains("capacity limit: 4") && report.contains("not every byte"), "Coverage and actual omissions disclosed")
        val named = TroubleshootingReport.create("aggregate", listOf(group), emptyList(), true).text()
        verify(named.contains("192.0.2.20") && named.contains("Fixture private room") && !named.contains("SERIAL_PRIVATE"), "Explicit names mode does not reveal serials")
        val large = TroubleshootingReport.create("aggregate", (1..1200).map { n -> group.copy(summary="Synthetic device $n") }, emptyList())
        verify(large.text().contains("Device 1200 / Address 1"), "No inventory cap or silent tail loss")
        verify(large.pages.all { it.length <= 12000 }, "Preview/clipboard pages bounded")
        val emoji = "🛜".repeat(15000)
        val unicode = TroubleshootingReport.create("", listOf(group.copy(summary=emoji)), emptyList())
        verify(unicode.pages.none { it.isNotEmpty() && Character.isHighSurrogate(it.last()) }, "Page boundary preserves Unicode pairs")
        fun render(values: List<TroubleshootingReport.Fact>, names: List<String>, include: Boolean = false): String {
            val endpoint = TroubleshootingReport.Endpoint("192.0.2.20", listOf("private-host.local"), values, 0, names)
            return TroubleshootingReport.create("", listOf(TroubleshootingReport.Group("", listOf(endpoint))), emptyList(), include).text()
        }
        fun publicFact(field: String, value: String) = TroubleshootingReport.Fact(field, value, publicValue = true, kind = TroubleshootingReport.valueKind(field))
        val identity = render(listOf(publicFact("Model", "Google Home Mini"), publicFact("Firmware", "1.2.3.4"),
            publicFact("TXT md", "Google Home Mini"), publicFact("device/firmwareVersion", "1.2.3.4"),
            TroubleshootingReport.Fact("Network name", "Google Home Mini")), listOf("Google Home Mini", "Google", "Mini", "1.2.3.4"))
        verify(identity.contains("Model: Google Home Mini") && identity.contains("TXT md: Google Home Mini"), "Factory names do not erase reviewed identity fields")
        verify(identity.contains("Firmware: 1.2.3.4") && identity.contains("device/firmwareVersion: 1.2.3.4"), "Typed versions are not guessed to be addresses")
        verify(identity.contains("Network name: [private value"), "Factory network name remains private as a name field")
        val cautious = render(listOf(publicFact("Model", "Private Room speaker"), publicFact("Model", "private-host.local"),
            publicFact("Firmware", "192.0.2.20"), publicFact("Notes", "1.2.3.4")), listOf("Private Room"))
        verify(!cautious.contains("Private Room") && !cautious.contains("private-host.local"), "Private names embedded in model text and host identifiers remain masked")
        verify(!cautious.contains("192.0.2.20") && !cautious.contains("1.2.3.4"), "Known endpoint address stays private even in a version field; untyped address is masked")
        val letters = listOf("a", "e", "i", "l", "p", "r", "t", "v")
        val short = render(listOf(publicFact("Manufacturer", "Example maker"), publicFact("Notes", letters.joinToString(" "))), letters)
        verify(short.length < 5000 && short.contains("Manufacturer: Example maker"), "Short names do not rewrite schema or letters inside ordinary words")
        verify(!short.contains("[priv[") && Regex("\\[private value [0-9]+\\]").findAll(short).count() == letters.size, "Generated labels are never matched again")
        val overlaps = render(listOf(publicFact("Notes", "ROOM ALPHA / room / Room Alpha")), listOf("room", "Room Alpha"))
        verify(!overlaps.contains("ALPHA") && !overlaps.contains("Room Alpha") && !overlaps.contains("Notes: ROOM"), "Longest overlapping name matched case-insensitively")
        val labels = Regex("\\[private value [0-9]+\\]").findAll(overlaps).map { it.value }.toList()
        verify(labels.size == 3 && labels[0] == labels[2] && labels[0] != labels[1], "Stable case-insensitive labels for overlapping names")
        for (include in listOf(false, true)) {
            val protected = render(listOf(publicFact("Model", "https://private.invalid/a password=SECRET_123 user@example.invalid 00112233-4455-6677-8899-aabbccddeeff")), listOf("a", "password"), include)
            verify(!protected.contains("SECRET_123") && !protected.contains("private.invalid") && !protected.contains("user@example") && !protected.contains("00112233-4455"), "Secret rules run against original input in both privacy modes")
        }
        println("PASS · TroubleshootingReportTest · $checks assertions")
    }
}
