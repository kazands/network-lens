import io.github.kazands.networklens.*
import java.net.InetAddress

object FireTvInfoTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) error(why) }
    private fun service() = MdnsPacket.Service("amzn.dmgr:00000000000000000000000000000001:FixtureToken:123456", FireTvInfo.TYPE).apply {
        port = 45678
        attributes["n"] = "Synthetic den".toByteArray()
        attributes["ad"] = "ASYNTHETIC123".toByteArray()
    }
    @JvmStatic fun main(args: Array<String>) {
        check(FireTvInfo.TYPE in LocalServices.INITIAL, "WhisperPlay queried without relying on service enumeration")
        check(FireTvInfo.TYPE in MdnsTypePlan.ordered(emptySet(), emptyMap()).take(8), "Fire TV query is in the first batch")
        val s = service()
        val report = FireTvInfo.parse(s)!!
        check(report.name == "Synthetic den" && report.typeCode == "ASYNTHETIC123", "Name and type code decoded separately")
        check(FireTvInfo.parse(service().apply { attributes.clear() }) == FireTvInfo.Report("", ""), "Missing identity remains missing")
        check(FireTvInfo.parse(service().apply { attributes["N"] = "Conflicting name".toByteArray() }) == null, "Conflicting case variants rejected")
        check(FireTvInfo.parse(service().apply { attributes["n"] = byteArrayOf(-61, 40) }) == null, "Malformed UTF-8 rejected")
        for (bad in listOf("x".repeat(161), "Fixture\nname", "Fixture\u202Ename", s.name, "Android.local", "amazon-0123456789.local", "unknown"))
            check(FireTvInfo.parse(service().apply { attributes["n"] = bad.toByteArray() })!!.name.isEmpty(), "Invalid or technical friendly names ignored")
        for (bad in listOf("ASYNTHETIC123\n", "A", "aa123456", "00:11:22:33:44:55"))
            check(FireTvInfo.parse(service().apply { attributes["ad"] = bad.toByteArray() })!!.typeCode.isEmpty(), "Type code strictly bounded; never a MAC")
        for (type in listOf("_amzn-alexa._tcp.", "_googlecast._tcp.", "_amzn-wplay._udp."))
            check(FireTvInfo.parse(MdnsPacket.Service(s.name, type).apply { port = 45678 }) == null, "Other services cannot become Fire TV")
        for (name in listOf("Synthetic room", "amzn.dmgr:", "amzn.dmgr:a:b:x", "prefix-" + s.name, s.name + ":suffix"))
            check(FireTvInfo.parse(MdnsPacket.Service(name, FireTvInfo.TYPE).apply { port = 45678 }) == null, "Only the manager advertisement supplies family evidence")
        for (port in listOf(-1, 0, 65536)) check(FireTvInfo.parse(service().apply { this.port = port }) == null, "Invalid advertised port rejected")
        val hints = IdentityHints()
        hints.offer("Name", s.name, "Synthetic instance", 65)
        hints.offer("Name", "Android.local", "Synthetic reverse name", 35)
        val before = hints.observations().first().value
        check(DevicePresentation.labels(before, "", "", "", null, false, false).unnamed, "Internal IDs and generic reverse names are not titles")
        check(hints.offer("Name", report.name, "Synthetic TXT n", 90)!!.value == report.name, "Friendly name outranks manager ID")
        for (name in listOf("Android studio", "Synthetic room", "thinkstation-fixture.local", "Amazon Fire TV", "amazon-den.local"))
            check(IdentityHints.specificName(name), "Useful names remain visible")
        check(DiscoveryMatchers.family(FireTvInfo.FAMILY)!!.hardware.isEmpty(), "Family does not invent a stick, television or soundbar category")
        check(HardwareClassification.capabilities(listOf(FireTvInfo.TYPE)) == listOf("Amazon WhisperPlay"), "WhisperPlay is a separate capability")
        val known = "AKPGW064GI9HE"
        val product = FireTvInfo.product(known)!!
        check(product.title == "Amazon Fire TV Stick 4K" && product.familyOnly && product.category == DeviceCatalog.Category.TV_PLAYER,
            "Reviewed type code identifies 4K Stick without asserting an inconsistent generation label")
        for (code in listOf("", "ASYNTHETIC123", known.lowercase(), "$known-extra", " $known"))
            check(FireTvInfo.product(code) == null, "No guessing unknown or near-matching Amazon codes")
        fun evidence(code: String = known) = DeviceCatalog.Evidence().apply { offerFireTvType(code, "Synthetic current WhisperPlay TXT ad") }
        val identified = evidence()
        check(identified.match()?.product == product && identified.categories().single().category == DeviceCatalog.Category.TV_PLAYER,
            "Protocol-qualified catalogue inference works without inventing reported fields")
        check(identified.match()!!.source.contains("TXT ad") && identified.match()!!.input == "TXT ad=$known", "Catalogue retains field provenance")
        for (maker in listOf("Amazon", "Amazon.com", "Amazon.com, Inc."))
            check(evidence().apply { offer("Manufacturer", maker, "Synthetic UPnP") }.match()?.product == product, "Compatible reported Amazon brand aliases")
        for (model in listOf("Fire TV", "Fire TV Stick 4K", "AFTMM"))
            check(evidence().apply { offer("Model", model, "Synthetic UPnP") }.match()?.product == product, "Corroborated model aliases retain protocol identity")
        check(evidence().apply { offer("Manufacturer", "Synthetic other maker", "Synthetic conflicting maker") }.match() == null,
            "Contradictory manufacturer suppresses the Amazon product")
        check(evidence().apply { offer("Model", "AFTKA", "Synthetic conflicting model") }.match() == null, "Contradictory model suppresses 4K Stick interpretation")
        val conflict=evidence();val snapshot=conflict.copy()
        conflict.offerFireTvType("ASYNTHETIC123", "Synthetic other ad")
        repeat(12) { conflict.offerFireTvType(known, "Synthetic repeated ad") }
        check(conflict.match() == null && conflict.categories().isEmpty() && snapshot.match()?.product == product, "Type-code conflicts stay sticky; copies retain original evidence")
        for (reverse in listOf(false, true)) {
            val combined=DeviceCatalog.Evidence()
            (if (reverse) listOf(evidence("ASYNTHETIC123"), evidence()) else listOf(evidence(), evidence("ASYNTHETIC123"))).forEach(combined::include)
            check(combined.match() == null && !combined.compatible(product), "Conflicting grouped Amazon codes cannot borrow a neighbour's known identity")
        }
        check(!evidence("ASYNTHETIC123").compatible(product), "An unknown Amazon code is still contradictory identity evidence")
        for (amazonFirst in listOf(false, true)) {
            val mixed = DeviceCatalog.Evidence()
            if (amazonFirst) mixed.offerFireTvType(known, "Synthetic current TXT ad")
            mixed.offer("Model", "Google Nest Mini", "Synthetic current Cast model")
            if (!amazonFirst) mixed.offerFireTvType(known, "Synthetic current TXT ad")
            check(mixed.match() == null && mixed.categories().isEmpty(), "A rejected model cannot replace known Amazon hardware with a speaker category")
            check(mixed.copy().categories().isEmpty(), "A copied catalogue retains physical category suppression")
            val combined = DeviceCatalog.Evidence().apply { include(mixed.copy()) }
            check(combined.categories().isEmpty(), "Combined catalogue evidence cannot restore a rejected model category")
        }
        check(evidence("ASYNTHETIC123").apply { offer("Model", "Google Nest Mini", "Synthetic current Cast model") }
            .categories().single().category == DeviceCatalog.Category.SPEAKER,
            "An unknown Amazon code does not override independently supported Google hardware")
        check(!evidence("ASYNTHETIC123").hasFireTvHardwareIdentity() && !DeviceCatalog.Evidence().hasFireTvHardwareIdentity(),
            "Unknown codes and service-only evidence do not establish Amazon hardware")
        check(identified.hasFireTvHardwareIdentity() && snapshot.hasFireTvHardwareIdentity(), "Known hardware evidence survives copying")
        for (reverse in listOf(false, true)) {
            val retained = DeviceCatalog.Evidence()
            (if (reverse) listOf("ASYNTHETIC123", known) else listOf(known, "ASYNTHETIC123")).forEach {
                retained.offerFireTvType(it, "Synthetic current TXT ad")
            }
            check(retained.match() == null && retained.hasFireTvHardwareIdentity(), "Conflicting codes cannot erase known hardware in either arrival order")
            val merged = DeviceCatalog.Evidence().apply { include(retained.copy()) }
            check(merged.match() == null && merged.hasFireTvHardwareIdentity(), "Copies and combined evidence retain the cross-engine conflict guard")
        }

        val peer = InetAddress.getByName("192.0.2.40")
        val path = "/upnp/dev/00000000-0000-0000-0000-000000000001/desc"
        val root = "http://192.0.2.40:60000"
        val target = UpnpDescription.target(root + path, peer)
        check(target != null && String(UpnpDescription.request(target)).startsWith("GET $path HTTP/1.1\r\n"), "Advertised Fire TV description admits only GET")
        for (url in listOf(
            "http://192.0.2.41:60000$path", "http://example.invalid$path", "http://user@192.0.2.40$path", "https://192.0.2.40$path",
            root + path + "?action=launch", root + path + "#fragment", root + path + "/extra", root + path + "/../control",
            root + path.replace("/dev/", "/dev/../"), root + path.replace("/desc", "/%64esc"),
            root + path.replace("00000000-", "not-uuid-"), root + "/upnp/dev/fixture/desc", root + "/apps/YouTube"))
            check(UpnpDescription.target(url, peer) == null, "Amazon compatibility cannot admit off-peer, control, encoded or malformed paths")
        val xml = """<root xmlns="urn:schemas-upnp-org:device-1-0"><device><friendlyName>Synthetic den</friendlyName><manufacturer>Amazon</manufacturer><modelName>Fire TV</modelName></device></root>""".toByteArray()
        val values = UpnpDescription.parse(xml)
        check(values["modelName"] == "Fire TV" && values["manufacturer"] == "Amazon", "Reported DIAL fields remain verbatim")
        check(UpnpDescription.deviceKind(values).isEmpty(), "Generic Fire TV model does not establish exact physical hardware")
        println("PASS: $checks Amazon discovery and description checks")
    }
}
