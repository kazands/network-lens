package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*

/** Synthetic identities only; invoked by the emulator-guarded device-identities suite. */
internal object RecognitionEvidenceChecks {
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        fun endpoint(ip: String, maker: String = "", model: String = "") = Device().apply {
            this.ip = ip
            hint("Manufacturer", maker, "Synthetic current manufacturer", 80)
            hint("Model", model, "Synthetic current model", 80)
        }
        for (reverse in listOf(false, true)) {
            val board = endpoint("192.0.2.70", model = "J271AP").apply {
                recognizeServiceModel("_device-info._tcp.", mapOf("model" to "J271AP".toByteArray()))
                link("synthetic:apple-alias")
            }
            val product = endpoint("2001:db8::70", model = "iPad13,18").apply {
                recognizeAppleModel("iPad13,18"); link("synthetic:apple-alias")
            }
            val ipad = Device.grouped(if (reverse) listOf(product, board) else listOf(board, product)).single()
            check(ipad.groupProduct()?.product?.title == "Apple iPad (10th generation) Wi-Fi" && !ipad.recognitionReportGroup().conflict,
                "Board and ProductType agree across addresses in either grouping order")
            check(ipad.groupRecognition()?.canonicalCode == "iPad13,18" && ipad.copy().groupProduct()?.product?.id == "recog:iPad13,18",
                "Apple grouping and snapshots use canonical identity without replacing raw codes")
            val upnp = endpoint("192.0.2.71", "Google").apply {
                hint("Model", "Eureka Dongle", "UPnP device description", 90)
                hint("Model", "Chromecast", "mDNS current _googlecast._tcp. TXT", 80)
                link("synthetic:cast-alias")
            }
            val cast = endpoint("2001:db8::71", model = "Chromecast").apply { link("synthetic:cast-alias") }
            val group = Device.grouped(if (reverse) listOf(cast, upnp) else listOf(upnp, cast)).single()
            check(group.groupProduct()?.product?.id == "google:chromecast" && !group.recognitionReportGroup().conflict,
                "Selected generic UPnP model cannot suppress compatible dual-stack Cast recognition")
            check(upnp.model == "Eureka Dongle" && group.copy().groupProduct()?.product?.title == "Google Chromecast",
                "Compatibility preserves raw fields and works in snapshots")
            upnp.model = "Synthetic manual contradiction"
            check(upnp.identifiedProduct() == null, "Legacy directly populated fields still constrain recognition")
        }
        val noisy = Device().apply {
            repeat(DiagnosticEvidence.LIMIT) { hint("Name", "Synthetic service $it", "Synthetic current service", 50) }
            hint("Model", "Synthetic host model", "Synthetic current model", 80)
            detail(AppleDeviceInfo.SOURCE, "Response read failed")
        }
        check(noisy.diagnostics.snapshot().any { it.field == "Model" } &&
            noisy.diagnostics.snapshot().any { it.field == AppleDeviceInfo.SOURCE } && noisy.diagnostics.omissions() == 2L,
            "Many names cannot evict late hardware models or fixed-reader outcomes")
        val printerAlias = endpoint("192.0.2.72", "Brother", "Brother MFC-L2720DW series")
        check(printerAlias.groupProduct()?.product?.title == "Brother MFC-L2720DW" &&
            printerAlias.classification().category == DeviceCatalog.Category.PRINTER && DeviceIcon.forGroup(printerAlias).label == "Printer",
            "Recognised printer model supplies its hardware category and icon")
        val printServer = endpoint("192.0.2.73").apply { service("_ipp._tcp.") }
        check(printServer.classification().category == null && DeviceIcon.forGroup(printServer).label == "Printer service",
            "A print service alone cannot classify its host as printer hardware")
        val ecoAirPlay = endpoint("192.0.2.74", "ecobee", "ECB601").apply {
            recognizeServiceModel("_airplay._tcp.", mapOf("model" to "ECB601".toByteArray()))
        }
        check(!ecoAirPlay.recognitionReportGroup().conflict && ecoAirPlay.classification().category == DeviceCatalog.Category.THERMOSTAT,
            "Third-party AirPlay model cannot manufacture an Apple identity conflict")
        for (reverse in listOf(false, true)) {
            val first = endpoint("192.0.2.110", "ecobee", "Smart_Thermostat_Premium").apply {
                hint("Manufacturer", "ecobee Inc.", "mDNS current _airplay._tcp. TXT", 80)
                hint("Model", "ECB601", "mDNS current _airplay._tcp. TXT", 80)
                service("_spotify-connect._tcp."); service("_airplay._tcp."); link("synthetic:climate")
            }
            val second = endpoint("2001:db8::110", "ecobee Inc.", "ECB601").apply {
                service("_raop._tcp."); link("synthetic:climate")
            }
            val group = Device.grouped(if (reverse) listOf(second, first) else listOf(first, second)).single()
            check(group.presentation().title == "ecobee Smart Thermostat Premium" && group.classification().category == DeviceCatalog.Category.THERMOSTAT,
                "Mixed protocol and dual-stack Ecobee aliases yield one thermostat, reverse=$reverse")
            check(DeviceIcon.forGroup(group).drawable == R.drawable.ic_device_thermostat && group.capabilities().size == 3,
                "One thermostat retains three separate media capabilities")
            check(second.manufacturer == "ecobee Inc." && second.model == "ECB601" && !group.seen && group.mac.isEmpty(),
                "Interpretation preserves raw endpoint fields and never creates presence or a MAC")
            check(group.copy().classification() == group.classification(), "Group progress snapshots preserve type evidence")
        }
        val maker = endpoint("192.0.2.111", "ecobee Inc.").apply { link("synthetic:split") }
        val model = endpoint("2001:db8::111", model = "ECB601").apply { link("synthetic:split") }
        val split = Device.grouped(listOf(maker, model)).single()
        check(split.groupProduct()?.product?.title == "ecobee Smart Thermostat Premium" && maker.identifiedProduct() == null && model.identifiedProduct() == null,
            "Complementary manufacturer/model reports combine only for group presentation")
        val global = endpoint("192.0.2.112", "HP Inc.", "LaserJet P4015").apply { link("synthetic:printer") }
        val globalAlias = endpoint("2001:db8::112", "HP", "HP LaserJet P4015").apply { link("synthetic:printer") }
        check(Device.grouped(listOf(global, globalAlias)).single().groupProduct()?.product?.title == "HP LaserJet P4015",
            "Canonical global product IDs do not depend on per-address model spelling")
        for (ci in 2..36) {
            val d = endpoint("192.0.2.113").apply {
                hint("Device type", HomeKitInfo.category(ci), "mDNS current _hap._tcp. category", 80)
                service("_raop._tcp."); service("_spotify-connect._tcp.")
            }
            val expected = HardwareClassification.reported(HomeKitInfo.category(ci))
            check(expected != null && d.classification().category == expected && DeviceIcon.forGroup(d).label != "Audio service",
                "Explicit HomeKit category $ci leads media services")
        }
        val thermostat = endpoint("192.0.2.114", "ecobee", "ECB601").apply { service("_spotify-connect._tcp.") }
        thermostat.hint("Model", "Synthetic unreviewed platform token", "Synthetic secondary protocol", 90)
        check(thermostat.identifiedProduct() == null && DeviceIcon.forGroup(thermostat).label == "Thermostat",
            "An unresolved retail identity retains independently supported physical type")
        val tv = endpoint("2001:db8::114").apply {
            hint("Device type", "HomeKit television", "Synthetic HAP category", 80); link("synthetic:conflict")
        }
        thermostat.link("synthetic:conflict")
        val conflict = Device.grouped(listOf(tv, thermostat)).single()
        check(conflict.classification().conflicted && DeviceIcon.forGroup(conflict).label == "Conflicting device types" && conflict.presentation().subtitle == "Conflicting device types",
            "Contradictory grouped types remain explicit instead of choosing an audio or arbitrary physical icon")
        val eviction = endpoint("192.0.2.115").apply {
            hint("Device type", "HomeKit thermostat", "Z old type", 10)
            hint("Device type", "HomeKit television", "Z other old type", 10)
        }
        repeat(12) { eviction.hint("Device type", "AirPlay device", "A newer capability $it", 90) }
        check(eviction.observations().none { it.value.startsWith("HomeKit") } && eviction.copy().classification().conflicted,
            "Physical type conflicts survive bounded display-observation eviction")
        check(Device().apply { ip = eviction.ip }.classification().facts.isEmpty(), "The next scan inherits no physical type")
        val appleDiscovery = Discovery(test.targetContext, NetworkState())
        Discovery::class.java.getDeclaredField("accepting").apply { isAccessible = true }.set(appleDiscovery, true)
        val current = Discovery::class.java.getDeclaredMethod("applyCurrentService", Device::class.java, MdnsPacket.Service::class.java).apply { isAccessible = true }
        try {
            for ((code, type) in listOf("iPad14,1" to DeviceCatalog.Category.TABLET, "iPhone14,3" to DeviceCatalog.Category.PHONE,
                "iPad13,18" to DeviceCatalog.Category.TABLET, "iPad13,19" to DeviceCatalog.Category.TABLET, "iPhone17,3" to DeviceCatalog.Category.PHONE)) {
                val apple = endpoint("192.0.2.120")
                val service = MdnsPacket.Service("Synthetic Apple device", "_companion-link._tcp.").apply {
                    port = 60000; host = "synthetic-apple.local"; attributes["rpmd"] = code.toByteArray()
                }
                current.invoke(appleDiscovery, apple, service)
                check(apple.identifiedProduct()?.product?.brand == "Apple" && apple.classification().category == type && apple.manufacturer.isEmpty(),
                    "Current Companion model $code identifies Apple hardware without inventing a reported manufacturer")
                check(apple.identifiedProduct()!!.input.contains("_companion-link") && apple.model == code && !apple.seen,
                    "Apple interpretation retains the actual service source, code and separate presence state")
                if (code == "iPad13,18") check(apple.presentation().title == "Apple iPad (10th generation) Wi-Fi" && apple.identifiedProduct()!!.source.contains("AppleDB"),
                    "An exact current model report gives the tenth-generation Wi-Fi title with AppleDB provenance")
            }
            val privateApple = endpoint("192.0.2.121")
            current.invoke(appleDiscovery, privateApple, MdnsPacket.Service("Synthetic tablet name", "_companion-link._tcp.").apply { port = 60000 })
            check(privateApple.identifiedProduct() == null && privateApple.model.isEmpty() && privateApple.manufacturer.isEmpty() &&
                DeviceIcon.forGroup(privateApple).label == "Apple companion service", "Model-less Apple service stays visible without guessing a hardware model from its name")
        } finally { appleDiscovery.cancel() }
        val discovery = Discovery(test.targetContext, NetworkState())
        val apply = Discovery::class.java.getDeclaredMethod("applyDescription", Device::class.java, Map::class.java).apply { isAccessible = true }
        try {
            val samsung = endpoint("192.0.2.116").apply { service("_raop._tcp."); service("_spotify-connect._tcp.") }
            val xml = "<root><device><manufacturer>Samsung Electronics</manufacturer><modelName>PN59D550</modelName><modelDescription>Samsung DTV DMR</modelDescription><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType></device></root>"
            apply.invoke(discovery, samsung, UpnpDescription.parse(xml.toByteArray()))
            check(samsung.model == "PN59D550" && samsung.presentation().title == "Samsung PN59D550" && samsung.presentation().subtitle == "Television",
                "Parsed Samsung description reaches canonical title and physical type through production admission helper")
            check(DeviceIcon.forGroup(samsung).drawable == R.drawable.ic_device_monitor && samsung.details["UPnP model description"] == "Samsung DTV DMR",
                "Samsung television icon leads audio services and retains the raw model description")
            val unknownTv = endpoint("192.0.2.117")
            apply.invoke(discovery, unknownTv, UpnpDescription.parse(xml.replace("PN59D550", "Synthetic uncatalogued television").toByteArray()))
            check(unknownTv.identifiedProduct() == null && unknownTv.classification().category == DeviceCatalog.Category.TV,
                "A television outside the product catalogue still gets its explicitly reported physical type")
        } finally { discovery.cancel() }
    }
}
