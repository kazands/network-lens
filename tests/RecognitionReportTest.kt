import io.github.kazands.networklens.*

object RecognitionReportTest {
    private var checks = 0
    private fun verify(ok: Boolean, message: String) { checks++; check(ok) { message } }
    @JvmStatic fun main(args: Array<String>) {
        val groups = listOf(
            RecognitionReport.Group(RecognitionReport.Level.PRODUCT, false, false, true, 2,
                listOf(RecognitionReport.Lookup(RecognitionReport.Protocol.CAST, RecognitionReport.Outcome.MODEL))),
            RecognitionReport.Group(RecognitionReport.Level.FAMILY, false, false, true, 1, emptyList()),
            RecognitionReport.Group(RecognitionReport.Level.SERVICES, true, false, false, 1,
                listOf(RecognitionReport.Lookup(RecognitionReport.Protocol.APPLE, RecognitionReport.Outcome.FAILED, MetadataHttp.Reason.READ_FAILED))),
            RecognitionReport.Group(RecognitionReport.Level.UNKNOWN, true, true, false, 1, emptyList()))
        val private = "SYNTHETIC_PRIVATE_NAME_9387"
        val sources = mapOf("mDNS _$private._tcp." to "Failed · $private at 192.0.2.45 · read timeouts 3",
            private to "Truncated · secret=$private", EapInfo.SOURCE to "OK · models received 2")
        val report = RecognitionReport.text(groups, 1, RecognitionReport.ScanState.NOTES, sources, 2, "0.3.26", 54, 37)
        for (expected in listOf("Device groups: 4 · address endpoints: 5", "Product matches: 1",
            "Family or integration matches only: 1", "Services only: 1", "This phone's groups excluded: 1",
            "Groups with recorded identity or type conflicts: 1", "Reported model without a compatible product/family match: 2",
            "Groups with no recorded lookup from the readers below: 2", "read timeouts: 3", "Method records omitted: 2")) {
            verify(report.contains(expected), "Report includes $expected")
        }
        verify(!report.contains(private) && !report.contains("192.0.2.45") && !report.contains("secret="), "No free-text source, reason or address escapes")
        verify(report.contains("not verified accuracy"), "Interpretation is not an accuracy claim")
        for (kind in LocalMetadata.Kind.values()) for (state in MetadataLookups.State.values()) {
            val entry = MetadataLookups.Entry(kind, 12345, true, state, MetadataHttp.Reason.READ_TIMEOUT, 403, true, true)
            val parsed = RecognitionReport.metadata(entry)
            verify(parsed.protocol.name == kind.name, "Typed protocol retained")
            verify(parsed.reason == MetadataHttp.Reason.READ_TIMEOUT, "Typed reason retained")
            if (state == MetadataLookups.State.SUCCESS) verify(parsed.outcome ==
                if (kind == LocalMetadata.Kind.ESCL) RecognitionReport.Outcome.SUCCESS else RecognitionReport.Outcome.MODEL,
                "Scanner model is not a host model")
        }
        for (protocol in listOf(RecognitionReport.Protocol.APPLE, RecognitionReport.Protocol.EAP, RecognitionReport.Protocol.ENVISALINK)) {
            verify(RecognitionReport.legacy(protocol, private).outcome == RecognitionReport.Outcome.OTHER, "Unknown detail stays unclassified")
            verify(RecognitionReport.legacy(protocol, "Not attempted: work queue limit reached").outcome == RecognitionReport.Outcome.LIMITED, "Queue limit retained")
            verify(RecognitionReport.legacy(protocol, "Stopped before lookup finished").outcome == RecognitionReport.Outcome.STOPPED, "Stop preserved")
            verify(RecognitionReport.legacy(protocol, "Lookup did not finish").outcome == RecognitionReport.Outcome.INCOMPLETE, "Unfinished preserved")
        }
        val withheld = RecognitionReport.legacy(RecognitionReport.Protocol.APPLE, AppleDeviceInfo.Status.UNAVAILABLE.text + "; " + MetadataHttp.Reason.READ_FAILED.detail)
        verify(withheld.outcome == RecognitionReport.Outcome.FAILED && withheld.reason == MetadataHttp.Reason.READ_FAILED, "Apple model lookup failure counted")
        verify(RecognitionReport.legacy(RecognitionReport.Protocol.EAP, "Public model received").outcome == RecognitionReport.Outcome.MODEL, "EAP success classified")
        verify(RecognitionReport.legacy(RecognitionReport.Protocol.ENVISALINK, EnvisalinkInfo.Report.NO_GREETING.text).outcome == RecognitionReport.Outcome.NO_IDENTITY, "Connected service without greeting is not an identity response")
        verify(RecognitionReport.legacy(RecognitionReport.Protocol.EAP, "No supported EAP identity reported").outcome == RecognitionReport.Outcome.NO_IDENTITY, "Other web pages do not count as EAP identity responses")
        val empty = RecognitionReport.text(emptyList(), 0, RecognitionReport.ScanState.STOPPED, emptyMap(), 0, private, 0, 26)
        verify(empty.contains("Scan: Stopped") && empty.contains("Device groups: 0"), "Stopped empty scan is explicit")
        verify(!empty.contains(private) && empty.contains("App: not available"), "Invalid version cannot carry text")
        val catalogue = DeviceCatalog.Evidence()
        catalogue.offer("Manufacturer", "Example one", "Synthetic")
        catalogue.offer("Manufacturer", "Example two", "Synthetic")
        verify(catalogue.hasRecordedConflict(), "Existing catalogue conflict flag exposed without changing matching")
        val recog = Recog.Evidence()
        recog.observeReport("Manufacturer", "Samsung")
        verify(!recog.hasRecordedConflict(), "Non-Apple products do not acquire Apple conflicts")
        conflictDiagnostics()
        println("PASS: $checks recognition report privacy, accounting and outcome checks")
    }

    private fun conflictDiagnostics() {
        val different = listOf("Google Home", "Google Home Mini")
        val aliases = listOf("Google Home Mini", "Google Home Mini (1st gen)")
        for (models in listOf(different, different.reversed(), aliases, aliases.reversed())) {
            val evidence = DeviceCatalog.Evidence()
            evidence.offer("Model", models.first(), "Synthetic current response one")
            val earlier = evidence.copy()
            evidence.offer("Model", models.last(), "Synthetic current response two")
            val expectedConflict = "Google Home" in models
            for (snapshot in listOf(evidence, evidence.copy(), DeviceCatalog.Evidence().apply { include(evidence) })) {
                verify(snapshot.hasRecordedConflict() == expectedConflict, "Same-category models and aliases distinguished: $models")
                verify((snapshot.match() == null) == expectedConflict, "Diagnostics agree with product compatibility: $models")
                verify(snapshot.categories().all { it.category == DeviceCatalog.Category.SPEAKER }, "Model disagreement need not be a physical-type conflict")
            }
            verify(!earlier.hasRecordedConflict() && earlier.match() != null, "Later contradictions do not alter earlier copies")
        }
        for (models in listOf(listOf("ECB601", "Smart_Thermostat_Premium"), listOf("Smart_Thermostat_Premium", "ECB601"))) {
            val evidence = DeviceCatalog.Evidence()
            models.forEach { evidence.offer("Model", it, "Synthetic current response") }
            verify(evidence.match() == null && !evidence.hasRecordedConflict(), "Missing manufacturer is not a conflict")
            evidence.offer("Manufacturer", "ecobee", "Synthetic later manufacturer")
            verify(evidence.match() != null && !evidence.hasRecordedConflict(), "Late manufacturer resolves aliases without sticky diagnostics")
        }
        val unknown = DeviceCatalog.Evidence().apply { offer("Model", "Synthetic unlisted model", "Synthetic response") }
        verify(unknown.match() == null && !unknown.hasRecordedConflict(), "Unlisted model alone is not contradictory identity")
        val registry = DeviceCatalog.Evidence().apply {
            offerSmartThings("Synthetic current service")
            offer("Model", "Google Home", "Synthetic conflicting model")
        }
        verify(registry.match() == null && registry.hasRecordedConflict(), "Registry candidate rejection is included")
        for (makerFirst in listOf(false, true)) {
            val evidence = Recog.Evidence()
            if (makerFirst) evidence.observeReport("Manufacturer", "Samsung")
            evidence.offerAppleModel("iPhone14,5")
            if (!makerFirst) evidence.observeReport("Manufacturer", "Samsung")
            verify(evidence.match() == null && evidence.hasRecordedConflict(), "Apple conflict is independent of observation order")
            verify(evidence.copy().hasRecordedConflict(), "Apple conflict diagnostic survives copying")
        }
        val apple = Recog.Evidence().apply {
            observeReport("Manufacturer", "Apple")
            offerAppleModel("iPhone14,5")
        }
        verify(apple.match() != null && !apple.hasRecordedConflict(), "Compatible Apple evidence stays conflict-free")
    }
}
