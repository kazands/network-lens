package io.github.kazands.networklens.test

import io.github.kazands.networklens.*

/** Exercises the real Device pipeline, using only synthetic endpoint identifiers. */
internal object ReportRegressionChecks {
    fun run(check: (Boolean, String) -> Unit) {
        val software = mapOf("manufacturer" to "jminim.org", "modelName" to "MinimServer", "friendlyName" to "Fixture music service")
        val hardware = mapOf("manufacturer" to "Synology Inc", "modelName" to "DS220j", "friendlyName" to "Fixture NAS")
        for (softwareFirst in listOf(false,true)) {
            val device = Device().apply { ip="192.0.2.30" }
            for (description in if (softwareFirst) listOf(software,hardware) else listOf(hardware,software)) device.upnpIdentity(description)
            device.hint("Model","Xserve","mDNS current _device-info._tcp. TXT",20)
            check(device.manufacturer=="Synology Inc" && device.model=="DS220j" && device.name=="Fixture NAS", "Software identity cannot overwrite host fields in either arrival order")
            check(device.identifiedProduct()?.product?.category==DeviceCatalog.Category.NAS && !device.recognitionReportGroup().conflict, "NAS identity remains usable without software or presentation conflicts")
            check(device.diagnostics.snapshot().any { it.field=="Software model" && it.value=="MinimServer" }, "Excluded software identity is retained for diagnosis")
            check(device.details["Bonjour presentation model"]=="Xserve", "Presentation hint retained with correct scope")
            val copy=device.copy()
            device.upnpIdentity(mapOf("manufacturer" to "Fixture conflicting maker", "modelName" to "Fixture other hardware"))
            check(device.identifiedProduct()==null && copy.identifiedProduct()!=null, "Real hardware conflicts still suppress recognition and snapshots are detached")
        }
        fun mac(ipValue: String, address: String, source: String) = Device().apply {
            ip=ipValue; seen=true; link("mdns-host:fixture-mac.local")
            hint("Model","Mac15,6","mDNS current _airplay._tcp. TXT",80)
            recognizeServiceModel("_airplay._tcp.", mapOf("model" to "Mac15,6".toByteArray()))
            hardware(address,source)
        }
        val v4=mac("192.0.2.40","02:00:00:00:00:01","NetBIOS reported adapter address")
        val v6=mac("fe80::40%fixture0","02:00:00:00:00:02","RAOP advertised hardware ID; may be another interface")
        val merged=Device.grouped(listOf(v4,v6))
        check(merged.size==1 && merged.single().endpoints().size==2, "Current shared hostname groups alternate-interface RAOP identity")
        check(merged.single().classification().category==DeviceCatalog.Category.LAPTOP, "Grouped Mac gets laptop type")
        check(merged.single().presentation().title.contains("MacBook Pro"), "Grouped Mac gets reviewed product title")
        check(v4.mac!=v6.mac, "Both reported hardware addresses remain unchanged")
        val other=mac("192.0.2.41","02:00:00:00:00:03","NetBIOS reported adapter address")
        check(Device.grouped(listOf(v4,other)).size==2, "Conflicting network adapter MACs still prevent unsafe merging")
        val raopSource = "RAOP advertised hardware ID; may be another interface"
        for (adapterSource in listOf("NetBIOS reported adapter address", "Android ARP cache (Android 8–9)",
            "Omada discovery reported MAC address", "TP-Link discovery reported MAC address", "mDNS advertised MAC address")) {
            for (raopFirst in listOf(false, true)) {
                val sources = if (raopFirst) listOf(raopSource, adapterSource) else listOf(adapterSource, raopSource)
                val endpoint = mac("192.0.2.50", "02:00:00:00:00:04", sources.first())
                val before = endpoint.copy()
                endpoint.hardware(endpoint.mac, sources.last())
                check(endpoint.macSource == adapterSource && !endpoint.hardwareAddressMayBeOtherInterface(), "Repeated MAC retains adapter provenance in either response order: $adapterSource")
                for (snapshot in listOf(endpoint, endpoint.copy())) {
                    check(Device.grouped(listOf(snapshot, other)).size == 2, "Conflicting adapter addresses stay separate after repeated RAOP and snapshot copying")
                }
                check(before.macSource == sources.first(), "Later source promotion cannot alter earlier snapshots")

                val raopAddress = "02:00:00:00:00:07"
                val adapterAddress = "02:00:00:00:00:08"
                val different = mac("192.0.2.53", if (raopFirst) raopAddress else adapterAddress, sources.first())
                val earlier = different.copy()
                different.hardware(if (raopFirst) adapterAddress else raopAddress, sources.last())
                check(different.mac == adapterAddress && different.macSource == adapterSource,
                    "Adapter report supersedes a differing RAOP identifier in either response order: $adapterSource")
                check(different.diagnostics.snapshot().any { it.value.contains(raopAddress) && it.value.contains(raopSource) },
                    "Alternate RAOP address and its source remain in diagnostic evidence")
                check(earlier.mac == (if (raopFirst) raopAddress else adapterAddress) && earlier.macSource == sources.first(),
                    "Differing-address promotion leaves earlier snapshots detached")
                val matching = mac("fe80::53%fixture0", adapterAddress, adapterSource)
                for (snapshot in listOf(different, different.copy())) {
                    check(Device.grouped(listOf(snapshot, other)).size == 2 && Device.grouped(listOf(other, snapshot)).size == 2,
                        "Differing RAOP and adapter addresses cannot hide a third conflicting endpoint")
                    check(Device.grouped(listOf(snapshot, matching)).size == 1,
                        "Matching adapter and current hostname still group IPv4 and IPv6 after RAOP promotion")
                }
                different.hardware(raopAddress, raopSource)
                check(different.mac == adapterAddress && different.macSource == adapterSource,
                    "Repeated differing RAOP advertisements cannot undo adapter promotion")
            }
        }
        val unknownSource = mac("192.0.2.54", "02:00:00:00:00:09", raopSource)
        unknownSource.hardware("02:00:00:00:00:0A", null)
        check(unknownSource.mac == "02:00:00:00:00:09" && unknownSource.hardwareAddressMayBeOtherInterface(),
            "An address without source evidence cannot replace a RAOP observation")
        val chained = mac("192.0.2.51", "02:00:00:00:00:05", "NetBIOS reported adapter address")
        chained.hardware(chained.mac, "AirPlay advertised device ID; may be another interface")
        chained.hardware(chained.mac, raopSource)
        check(chained.macSource == "NetBIOS reported adapter address" && Device.grouped(listOf(chained, other)).size == 2,
            "Intermediate advertisements cannot erase adapter evidence before RAOP arrives")
        val promoted = mac("192.0.2.52", "02:00:00:00:00:06", "NetBIOS reported adapter address")
        promoted.hardware(promoted.mac, "Android ARP cache (Android 8–9)")
        promoted.hardware(promoted.mac, "NetBIOS reported adapter address")
        promoted.hardware(promoted.mac, raopSource)
        check(promoted.macSource == "Android ARP cache (Android 8–9)", "ARP provenance remains strongest after same-address repeats")
        val unrelated=Device().apply { ip="fe80::42%fixture0"; seen=true; hardware("02:00:00:00:00:02","RAOP advertised hardware ID; may be another interface") }
        check(Device.grouped(listOf(v4,unrelated)).size==2, "No merge from RAOP or model alone")
    }
}
