import io.github.kazands.networklens.*
import java.util.concurrent.*

/** Public model identifiers and synthetic addresses only. */
object ReportRegressionTest {
    @JvmStatic fun main(args: Array<String>) {
        var checks = 0
        fun verify(value: Boolean, why: String) { checks++; check(value) { why } }
        for (include in listOf(false, true)) {
            val id = "uuid:FIXTURE-PLAYER-MODEL-0123456789ABCDEF0123456789ABCDEF"
            val report = TroubleshootingReport.create("", listOf(TroubleshootingReport.Group("", listOf(
                TroubleshootingReport.Endpoint("192.0.2.5", emptyList(), listOf(
                    TroubleshootingReport.Fact("Services", "$id; urn:schemas-upnp-org:device:MediaRenderer:1", publicValue=true),
                    TroubleshootingReport.Fact("Model", "Fixture receiver", source=id, publicValue=true)), 0)))), emptyList(), include).text()
            verify(!report.contains("FIXTURE-PLAYER") && !report.contains("0123456789ABCDEF"), "Opaque UDN removed in value and source in both privacy modes")
            verify(report.contains("urn:schemas-upnp-org:device:MediaRenderer:1"), "Useful service type survives opaque identifier redaction")
        }
        for (code in listOf("Mac15,3", "Mac15,6", "Mac15,8", "Mac15,10", "Mac15,7", "Mac15,9", "Mac15,11")) {
            val evidence = Recog.Evidence()
            evidence.offerService("_airplay._tcp.", mapOf("model" to code.toByteArray()))
            verify(evidence.match()?.category == Recog.Category.LAPTOP, "Reviewed Mac identified from current model")
            evidence.offerService("_device-info._tcp.", mapOf("model" to "Xserve".toByteArray()))
            verify(evidence.match()?.code == code, "Generic Finder presentation does not conflict with actual hardware")
        }
        verify(Recog.lookup("Mac15,99") == null, "No guessed Mac identifiers")
        val unknown = Recog.Evidence().apply { offerService("_device-info._tcp.", mapOf("model" to "Xserve".toByteArray())) }
        verify(unknown.match() == null && unknown.categories().isEmpty(), "Presentation model is not an Apple hardware claim")
        verify(UpnpDescription.softwareIdentity(mapOf("manufacturer" to "jminim.org", "modelName" to "MonitorService2")), "Known software publisher and model scoped to service")
        verify(!UpnpDescription.softwareIdentity(mapOf("manufacturer" to "Synology", "modelName" to "DS220j")), "NAS description remains physical evidence")
        verify(!UpnpDescription.softwareIdentity(mapOf("manufacturer" to "Unreviewed", "modelName" to "MinimServer")), "Model name alone cannot discard contradictory maker")
        val products = listOf(Triple("TP-Link", "KP200(US)", DeviceCatalog.Category.OUTLET),
            Triple("Sony", "BRAVIA 4K GB", DeviceCatalog.Category.TV),
            Triple("HP", "HP ColorLaserJet MFP M182-M185", DeviceCatalog.Category.PRINTER),
            Triple("Synology Inc", "DS220j", DeviceCatalog.Category.NAS),
            Triple("Synology", "DS1513+", DeviceCatalog.Category.NAS),
            Triple("Fosi Audio", "S3", DeviceCatalog.Category.AUDIO_RECEIVER))
        for ((maker, model, category) in products) {
            val evidence = DeviceCatalog.Evidence().apply { offer("Manufacturer", maker, "Fixture"); offer("Model", model, "Fixture") }
            verify(evidence.match()?.product?.category == category, "Qualified public model selects hardware type: $model")
            verify(DeviceCatalog.lookup(model,"Unreviewed maker") == null, "Foreign manufacturer cannot borrow model $model")
        }
        val small = ScanPlan.create("198.18.0.5",24,null)
        val big = ScanPlan.create("198.18.0.5",22,null)
        verify(small.budgetSeconds == 55L && big.budgetSeconds == 100L, "Budget scales with actual sweep, remains bounded")
        verify(ScanPlan.create("198.18.0.5",21,null).budgetSeconds == 55L, "Omitted sweep does not extend discovery")
        val fullRange = big.hosts.map { FutureTask<Unit> { }.apply { run() } }
        verify(fullRange.size == 1022 && DiscoveryWork.awaitBatch(fullRange, System.nanoTime()+1_000_000_000){false}, "All /22 address jobs can settle without truncation")
        val pending = FutureTask<Unit> { }
        verify(!DiscoveryWork.awaitBatch(listOf(pending),System.nanoTime()-1){false} && pending.isCancelled, "Expired sweep cancels only batch and returns for metadata settlement")
        val finished = FutureTask<Unit> { }.apply { run() }
        verify(DiscoveryWork.awaitBatch(listOf(finished),System.nanoTime()-1){false}, "Already finished work is not incorrectly reported truncated")
        val failed = FutureTask<Unit> { error("fixture failure") }.apply { run() }
        var surfaced=false
        try { DiscoveryWork.awaitBatch(listOf(failed),Long.MAX_VALUE){false} } catch (_: ExecutionException) { surfaced=true }
        verify(surfaced, "Unexpected task failures remain visible")
        val journal = DiagnosticEvidence()
        repeat(DiagnosticEvidence.LIMIT) { journal.record("Busy XML reader", "field$it", "value$it") }
        journal.record("Late identity reader", "Model", "Fixture model")
        verify(journal.snapshot().any { it.source == "Late identity reader" }, "Equal-priority noisy source yields space to another reader")
        verify(journal.snapshot().size == DiagnosticEvidence.LIMIT && journal.losses().values.sum() == journal.omissions(), "Fair retention stays bounded with exact loss accounting")
        val snapshot = journal.copy()
        journal.record("Other reader", "Outcome", "Done", retention=DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
        verify(snapshot.omissions()==1L && journal.omissions()==2L, "Copies preserve independent diagnostic loss accounting")
        println("PASS · ReportRegressionTest · $checks assertions")
    }
}
