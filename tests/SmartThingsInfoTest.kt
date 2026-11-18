import io.github.kazands.networklens.*

object SmartThingsInfoTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) error(why) }
    private fun service(name: String = "0000000000000001", type: String = SmartThingsInfo.TYPE) =
        MdnsPacket.Service(name, type).apply { host = "hubv2-00000000000000001.local"; port = 8081 }

    @JvmStatic fun main(args: Array<String>) {
        check(listOf(SmartThingsInfo.TYPE, SmartThingsInfo.HEDGE_TYPE).all { it in LocalServices.INITIAL }, "Both services queried explicitly")
        check(SmartThingsInfo.matches(service()), "Current hub signature with padded identifier")
        check(SmartThingsInfo.matches(service().apply { host = "HUBV2-0000000000000001.LOCAL." }), "Unpadded identifier and DNS case/trailing dot")
        for (bad in listOf("tv.local", "hubv2-00000000000000002.local", "hubv2-00000000000000001.local.evil", "hubv2-00000000000000001", "hubv3-00000000000000001.local", "x".repeat(300)))
            check(!SmartThingsInfo.matches(service().apply { host = bad }), "Other hosts and mismatched identifiers cannot identify a hub")
        for (name in listOf("Kitchen TV", "0000000000000002", "0000000000000001 ", "0".repeat(128)))
            check(!SmartThingsInfo.matches(service(name)), "Instance must match the entire hub identifier")
        for (type in listOf(SmartThingsInfo.HEDGE_TYPE, "_matter._tcp.", "_http._tcp."))
            check(!SmartThingsInfo.matches(service(type = type)), "Hostname or other service alone cannot identify a hub")
        for (port in listOf(-1, 0, 65536)) check(!SmartThingsInfo.matches(service().apply { this.port = port }), "Invalid SRV port")
        val caps = HardwareClassification.capabilities(listOf(SmartThingsInfo.TYPE, SmartThingsInfo.HEDGE_TYPE, "_matter._tcp."))
        check(caps == listOf("Matter", "SmartThings"), "No inferred Zigbee or Z-Wave capability; duplicate SmartThings services combined")
        val identity = SmartThingsInfo.identity("synthetic current service")
        check(RecognitionCopy.explanation(identity).contains("exact generation was not verified"), "Explanation describes the actual evidence and limitation")
        check(identity.product.familyOnly && identity.product.category == DeviceCatalog.Category.HUB, "Hub family without exact generation")
        for (name in listOf(service().host, service().name, "hub-" + "0".repeat(32), "hub-" + "0".repeat(32) + ".local")) {
            val labels = DevicePresentation.labels(name, name, "", "", identity.product, false, false)
            check(labels.title == "SmartThings Hub" && labels.subtitle == "Smart-home hub", "Technical identifiers stay out of product subtitle")
        }
        check(DevicePresentation.labels("Synthetic den", "", "", "", identity.product, false, false).subtitle == "Smart-home hub · Synthetic den", "Friendly names remain useful")
        check(DevicePresentation.labels("hub-personal", "", "", "", identity.product, false, false).subtitle.endsWith("hub-personal"), "Only bounded technical names suppressed")
        check(DevicePresentation.labels(service().host, "", "", "", null, false, false).title != "SmartThings Hub", "Names cannot create identity")
        fun evidence() = DeviceCatalog.Evidence().apply { offerSmartThings("synthetic current service") }
        for (maker in listOf("", "Samsung Electronics", "SmartThings", "Aeotec")) {
            val e = evidence().apply { offer("Manufacturer", maker, "synthetic TXT") }
            check(e.match()?.product?.id == SmartThingsInfo.PRODUCT_ID, "Compatible reported maker preserves family")
        }
        for (hubFirst in listOf(false, true)) {
            val e = DeviceCatalog.Evidence()
            if (hubFirst) e.offerSmartThings("synthetic")
            e.offer("Model", "Google Nest Mini", "synthetic TXT")
            if (!hubFirst) e.offerSmartThings("synthetic")
            check(e.match() == null && e.categories().isEmpty(), "Conflicting product cannot reclassify hub in either order")
            val copy = e.copy()
            check(copy.hasSmartThingsIdentity() && copy.categories().isEmpty(), "Conflict survives snapshots")
        }
        val merged = DeviceCatalog.Evidence().apply { include(evidence()); include(DeviceCatalog.Evidence().apply { offer("Manufacturer", "Apple", "synthetic TXT") }) }
        check(merged.hasSmartThingsIdentity() && merged.match() == null && merged.categories().isEmpty(), "Group conflicts preserve hub constraint")
        check(HardwareClassification.resolve(listOf(HardwareClassification.Fact(DeviceCatalog.Category.HUB, "hub"), HardwareClassification.Fact(DeviceCatalog.Category.BRIDGE, "HomeKit"))).category == DeviceCatalog.Category.HUB, "Bridge is compatible with hub")
        check(HardwareClassification.resolve(listOf(HardwareClassification.Fact(DeviceCatalog.Category.HUB, "hub"), HardwareClassification.Fact(DeviceCatalog.Category.TV, "TV"))).conflicted, "Television is conflicting hardware")
        println("SmartThingsInfoTest: $checks checks passed")
    }
}
