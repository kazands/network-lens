import io.github.kazands.networklens.*
import java.net.InetAddress

object HardwareClassificationTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
    private fun permutations(values: List<Triple<String, String, String>>): List<List<Triple<String, String, String>>> =
        if (values.isEmpty()) listOf(emptyList()) else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
    @JvmStatic fun main(args: Array<String>) {
        val reports = listOf(Triple("Manufacturer", "ecobee", "Spotify Connect device information (reported)"),
            Triple("Manufacturer", "ecobee Inc.", "mDNS current _airplay._tcp. TXT"),
            Triple("Model", "Smart_Thermostat_Premium", "Spotify Connect device information (reported)"),
            Triple("Model", "ECB601", "mDNS current _airplay._tcp. TXT"))
        for (order in permutations(reports)) {
            val evidence = DeviceCatalog.Evidence()
            order.forEach { (field, value, source) -> evidence.offer(field, value, source) }
            check(evidence.match()?.product?.title == "ecobee Smart Thermostat Premium", "All manufacturer/model arrival orders resolve the exact thermostat")
            val result = HardwareClassification.resolve(evidence.categories())
            check(result.category == DeviceCatalog.Category.THERMOSTAT && !result.conflicted, "Service aliases agree on physical category")
            check(result.facts.any { it.source.contains("_airplay") } && result.facts.any { it.source.contains("Spotify") }, "Category evidence preserves each model's source")
            val copy = evidence.copy()
            evidence.offer("Model", "Unreviewed secondary identifier", "Synthetic secondary protocol")
            check(evidence.match() == null && HardwareClassification.resolve(evidence.categories()).category == DeviceCatalog.Category.THERMOSTAT,
                "Unreviewed model suppresses exact retail identity, not independently supported physical type")
            check(copy.match() != null, "Progress copies remain independent")
            evidence.offer("Manufacturer", "Synthetic rival maker", "Synthetic conflicting response")
            check(evidence.categories().isEmpty(), "Contradictory manufacturer cannot qualify catalogue hardware")
        }
        val split = DeviceCatalog.Evidence().apply { offer("Model", "ECB601", "Synthetic IPv6 model") }
        val maker = DeviceCatalog.Evidence().apply { offer("Manufacturer", "ecobee Inc.", "Synthetic IPv4 maker") }
        split.include(maker)
        check(split.match()?.product?.category == DeviceCatalog.Category.THERMOSTAT, "Complementary grouped reports qualify the same product")
        val known = DeviceCatalog.Evidence().apply {
            offer("Manufacturer", "Samsung Electronics", "Synthetic description")
            offer("Model", "PN59D550", "Synthetic description")
        }
        check(known.match()?.product?.title == "Samsung PN59D550", "Exact manufacturer-qualified Samsung model names the TV family")
        check(DeviceCatalog.lookup("PN59D550", "Synthetic rival") == null, "A TV-like code under a different maker does not match")
        for (ci in listOf(3, 5, 6, 7, 8, 9, 10, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 31, 34, 35, 36)) {
            val typed = HardwareClassification.Evidence().apply { offer(HomeKitInfo.category(ci), "Synthetic current HomeKit category") }
            check(HardwareClassification.resolve(typed.snapshot()).category != null, "Physical HomeKit category $ci is independent of media capabilities")
        }
        check(HardwareClassification.reported(HomeKitInfo.category(31)) == DeviceCatalog.Category.TV, "HomeKit television is a television")
        for (ci in listOf(24, 35, 36)) check(HardwareClassification.reported(HomeKitInfo.category(ci)) == DeviceCatalog.Category.TV_PLAYER, "HomeKit player category $ci is not a television")
        for (kind in listOf("AirPlay device", "Audio service", "Audio device", "Cast device", "Matter device", "HomeKit accessory", "Samsung TV in a custom name"))
            check(HardwareClassification.reported(kind) == null, "Capability or arbitrary label cannot establish physical hardware: $kind")
        val facts = HardwareClassification.Evidence().apply { offer("HomeKit thermostat", "Synthetic HAP") }
        val copy = facts.copy()
        facts.offer("HomeKit television", "Synthetic contradiction")
        repeat(40) { facts.offer("HomeKit thermostat", "Synthetic later report $it") }
        check(HardwareClassification.resolve(facts.snapshot()).conflicted && HardwareClassification.resolve(facts.copy().snapshot()).conflicted, "Type conflicts survive display eviction and copies")
        check(HardwareClassification.resolve(copy.snapshot()).category == DeviceCatalog.Category.THERMOSTAT, "Earlier type snapshots remain independent")
        check(HardwareClassification.Evidence().snapshot().isEmpty(), "Fresh scan has no previous type evidence")
        val capabilityList = listOf("_spotify-connect._tcp.", "_raop._tcp.", "_airplay._tcp.", "_spotify-connect._tcp.")
        check(HardwareClassification.capabilities(capabilityList) == HardwareClassification.capabilities(capabilityList.reversed()) && HardwareClassification.capabilities(capabilityList).size == 3,
            "Capabilities are distinct and deterministic across address/service ordering")
        val peer = InetAddress.getByName("192.0.2.40")
        val target = UpnpDescription.target("http://192.0.2.40:7676/smp_2_", peer)
        check(target != null && String(UpnpDescription.request(target)).startsWith("GET /smp_2_ HTTP/1.1"), "Advertised Samsung extensionless description gets a read-only request")
        for (url in listOf("http://192.0.2.41:7676/smp_2_", "http://example.invalid/smp_2_", "http://192.0.2.40/smp_2_?action=control", "http://192.0.2.40/smp_2_#fragment", "http://user@192.0.2.40/smp_2_", "http://192.0.2.40/smp_2_/../control", "http://192.0.2.40/smp_%32_", "http://192.0.2.40/smp_123456_"))
            check(UpnpDescription.target(url, peer) == null, "Description compatibility preserves peer/path/query constraints")
        val xml = "<root xmlns=\"urn:schemas-upnp-org:device-1-0\"><device><manufacturer>Samsung Electronics</manufacturer><modelName>PN59D550</modelName><modelDescription>Samsung DTV DMR</modelDescription><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType></device></root>"
        val parsed = UpnpDescription.parse(xml.toByteArray())
        check(parsed["modelDescription"] == "Samsung DTV DMR" && UpnpDescription.deviceKind(parsed) == "Samsung television", "Samsung TV description supplies explicit physical type without replacing model")
        for (replacement in listOf("Generic renderer", "Samsung PC DMS", "Samsung Soundbar"))
            check(UpnpDescription.deviceKind(parsed + ("modelDescription" to replacement)).isEmpty(), "Generic media playback or software is not a television")
        check(UpnpDescription.deviceKind(parsed + ("manufacturer" to "Synthetic maker")).isEmpty(), "Samsung TV descriptions require matching manufacturer")
        check(UpnpDescription.deviceKind(parsed + ("deviceType" to "urn:schemas-upnp-org:device:MediaServer:1")).isEmpty(), "A media server cannot borrow the TV renderer classification")
        println("PASS · $checks hardware classification and description compatibility checks")
    }
}
