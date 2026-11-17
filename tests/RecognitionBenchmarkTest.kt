import io.github.kazands.networklens.*

/** Small, labelled synthetic corpus. Metrics apply to these cases, never to household accuracy. */
object RecognitionBenchmarkTest {
    private data class Case(val name: String, val model: String, val maker: String, val title: String?, val category: DeviceCatalog.Category?,
                    val service: String = "", val source: String = "Synthetic current metadata response")
    @JvmStatic fun main(args: Array<String>) {
        val cases = listOf(
            Case("console", "PS5", "", "Sony PlayStation 5", DeviceCatalog.Category.CONSOLE, source = "PlayStation discovery host-type"),
            Case("streamer", "SHIELD Android TV", "NVIDIA", "NVIDIA SHIELD TV", DeviceCatalog.Category.TV_PLAYER),
            Case("thermostat-with-audio", "ECB601", "ecobee", "ecobee Smart Thermostat Premium", DeviceCatalog.Category.THERMOSTAT, "_spotify-connect._tcp."),
            Case("thermostat-with-airplay", "Smart_Thermostat_Premium", "ecobee Inc.", "ecobee Smart Thermostat Premium", DeviceCatalog.Category.THERMOSTAT, "_airplay._tcp."),
            Case("television-with-audio", "PN59D550", "Samsung", "Samsung PN59D550", DeviceCatalog.Category.TV, "_raop._tcp."),
            Case("regional-television", "PN59D550C1FXZC", "Samsung", "Samsung PN59D550", DeviceCatalog.Category.TV),
            Case("recorder", "DS-7608NI-I2/8P", "Hikvision", "Hikvision DS-7608NI-I2/8P", DeviceCatalog.Category.RECORDER),
            Case("amplifier", "A.6x", "VSSL", "VSSL A.6x", DeviceCatalog.Category.AMPLIFIER, "_airplay._tcp."),
            Case("missing-thermostat-maker", "ECB601", "", null, null, "_spotify-connect._tcp."),
            Case("conflicting-thermostat-maker", "ECB601", "Synthetic rival", null, null, "_airplay._tcp."),
            Case("unknown-model", "Synthetic Unknown 700", "Synthetic maker", null, null),
            Case("service-only", "", "", null, null, "_googlecast._tcp."),
            Case("model-suffix-near-miss", "PN59D550-unverified", "Samsung", null, null),
            Case("thermostat-name-near-miss", "Smart Thermostat Premium Plus Unverified", "ecobee", null, null)
        )
        var correct = 0; var falsePositive = 0; var missed = 0; var wrongCategory = 0
        for ((i, c) in cases.withIndex()) {
            val evidence = DeviceCatalog.Evidence()
            if (c.model.isNotEmpty()) evidence.offer("Model", c.model, c.source)
            if (c.maker.isNotEmpty()) evidence.offer("Manufacturer", c.maker, "Synthetic current manufacturer response")
            val match = evidence.match()
            val capabilities = HardwareClassification.capabilities(listOf(c.service))
            val facts = match?.product?.let { listOf(HardwareClassification.Fact(it.category, "Synthetic catalogue interpretation")) }.orEmpty()
            val classification = HardwareClassification.resolve(facts)
            check(c.service.isEmpty() || capabilities.isNotEmpty()) { "Corpus service case is unsupported" }
            if (c.title == null) { if (match != null) falsePositive++ }
            else if (match?.product?.title == c.title) correct++ else missed++
            if (c.category != null && classification.category != c.category) wrongCategory++
        }
        println("SYNTHETIC_RECOGNITION_BENCHMARK cases=${cases.size} expectedIdentified=${cases.count{it.title!=null}} correct=$correct missed=$missed falsePositive=$falsePositive wrongPrimaryCategory=$wrongCategory")
        check(missed == 0 && falsePositive == 0 && wrongCategory == 0) { "Synthetic recognition benchmark regressed" }
    }
}
