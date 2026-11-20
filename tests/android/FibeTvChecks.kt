package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*

/** Synthetic manufacturer/model responses; no network or household identifiers. */
internal object FibeTvChecks {
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val description = UpnpDescription.parse("""<root xmlns="urn:schemas-upnp-org:device-1-0"><device>
            <deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType><friendlyName>Fixture lounge</friendlyName>
            <manufacturer>ARRIS</manufacturer><modelName>FibeTV</modelName>
            </device></root>""".toByteArray())
        check(description["manufacturer"] == "ARRIS" && description["modelName"] == "FibeTV", "UPnP retains the actual receiver manufacturer and model")
        val operation = Discovery(test.targetContext, NetworkState())
        val apply = Discovery::class.java.getDeclaredMethod("applyDescription", Device::class.java, Map::class.java).apply { isAccessible = true }
        try {
            for (modelFirst in listOf(false, true)) {
                val receiver = Device().apply { ip = "198.18.0.30" }
                fun castModel() {
                    receiver.services.add("_googlecast._tcp.")
                    receiver.hint("Model", "FibeTV", "mDNS current _googlecast._tcp. TXT", 80)
                }
                if (modelFirst) castModel()
                apply.invoke(operation, receiver, description)
                if (!modelFirst) castModel()
                check(receiver.presentation().title == "ARRIS FibeTV" && receiver.presentation().subtitle == "TV receiver · Fixture lounge", "Reported receiver leads the list and retains its friendly name in either arrival order")
                check(receiver.manufacturer == "ARRIS" && receiver.model == "FibeTV", "Recognition preserves the raw reported manufacturer and model")
                check(receiver.classification().category == DeviceCatalog.Category.TV_PLAYER && DeviceIcon.forGroup(receiver).drawable == R.drawable.ic_device_tv_player, "Receiver has its physical category and streaming-device icon")
                check(receiver.groupProduct()?.product?.familyOnly == true && receiver.recognitionReportGroup().level == RecognitionReport.Level.FAMILY, "FibeTV is a receiver family without an invented model generation")
                check(!receiver.seen && receiver.mac.isEmpty() && receiver.evidence.isEmpty(), "Identity interpretation creates neither presence nor a MAC")
                val v6 = Device().apply {
                    ip = "2001:db8::30"; link("synthetic:fibetv")
                    hint("Model", "FibeTV", "mDNS current _googlecast._tcp. TXT", 80)
                }
                receiver.link("synthetic:fibetv")
                val grouped = Device.grouped(listOf(v6, receiver)).single()
                check(grouped.presentation().title == "ARRIS FibeTV" && grouped.classification().category == DeviceCatalog.Category.TV_PLAYER, "Compatible dual-stack observations use the reported ARRIS identity")
                check(v6.manufacturer.isEmpty() && v6.identifiedProduct() == null, "Grouping does not invent an IPv6 manufacturer or independent product match")
                val report = TroubleshootingReport.create("Synthetic scan", listOf(grouped.troubleshootingGroup()), emptyList()).text()
                check(report.contains("Identification: ARRIS FibeTV") && report.contains("Manufacturer: ARRIS") && report.contains("Model: FibeTV") && report.contains("UPnP device description"), "Troubleshooting report uses the receiver identity and preserves reported sources")
                check(!report.contains(receiver.ip) && !report.contains(v6.ip) && !report.contains("Fixture lounge"), "Default report still masks local addresses and friendly names")
                val snapshot = grouped.copy()
                receiver.hint("Manufacturer", "Fixture conflicting maker", "Synthetic conflicting response", 90)
                check(Device.grouped(listOf(v6, receiver)).single().groupProduct() == null && snapshot.presentation().title == "ARRIS FibeTV", "A conflicting endpoint suppresses recognition without changing the earlier snapshot")
            }
            val nameOnly = Device().apply {
                hint("Name", "FibeTV", "Synthetic friendly name", 80)
                hint("Manufacturer", "ARRIS", "Synthetic manufacturer", 80)
            }
            check(nameOnly.groupProduct() == null, "A FibeTV friendly name cannot substitute for a reported model")
            val unknown = Device().apply { hint("Model", "FibeTV", "Synthetic current model", 80) }
            check(unknown.groupProduct() == null, "FibeTV without a manufacturer does not acquire the ARRIS profile")
            val fresh = Device().apply { ip = "198.18.0.30" }
            check(fresh.groupProduct() == null && fresh.manufacturer.isEmpty() && fresh.model.isEmpty(), "A fresh scan does not inherit the prior address occupant's identity")
        } finally { operation.cancel() }
    }
}
