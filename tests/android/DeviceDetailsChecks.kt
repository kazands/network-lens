@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.*
import io.github.kazands.networklens.*

/** Actual Compose controls, documentation addresses and synthetic current reports. */
internal object DeviceDetailsChecks {
    private const val MAC="02:11:22:33:44:55"
    private fun words(d: ComposeDriver): List<String> {
        val result=mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            result+=node.config.getOrNull(SemanticsProperties.Text).orEmpty().map {it.text}
            result+=node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            node.children.forEach(::visit)
        }
        visit(d.tagged("device-details") ?: throw AssertionError("Missing device details"))
        return result
    }
    private fun device(ip: String="192.0.2.71",mac: String="",source: String="Synthetic primary interface")=Device().apply {
        this.ip=ip;seen=true;name="SHIELD Android TV"
        hint("Model","SHIELD Android TV","Synthetic current model report",80)
        service("_androidtvremote2._tcp.");service("_googlecast._tcp.");port(6466);port(8009)
        evidence("Synthetic current response at $ip");detail("ICMP response time",if(ip.endsWith("71")) "12 ms" else "14 ms")
        if(mac.isNotEmpty()) hardware(mac,source)
    }
    private fun clipboard(d: ComposeDriver)=
        (d.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()

    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { d ->
            Store(test.targetContext).clear();DeviceCatalogLoader.load(test.targetContext);d.start()
            try {
                val known=device(mac=MAC)
                for((width,scale) in listOf(375 to 1f,320 to 2f,900 to 1f)) {
                    d.configure(width,scale)
                    for(dark in listOf(false,true)) {
                        d.main {Store(d.activity).dark(dark);d.set("demo",true)}
                        d.fixture(listOf(known),known)
                        d.main {
                            val words=words(d)
                            val order=listOf("IPv4",known.ip,"MAC",MAC,"Services & ports","Show technical details").map {words.indexOf(it)}
                            check(order.all {it>=0} && order.zipWithNext().all {(a,b)->a<b},"Identity identifiers precede services and collapsed technical details")
                            check(listOf("Addresses","How this was identified","Discovery evidence","Response time","Copy MAC").none {it in words},"Main page omits redundant address, explanation and scan summary blocks")
                            check(words.containsAll(listOf("Android TV remote service","Google Cast","6466","8009")),"Services and ports retain their existing contents")
                        }
                        d.assertTextLayouts("Device details ${width}dp ${scale}x dark=$dark")
                        ComposeUiChecks.capture(d,"details-identity-${width}-${scale}x-$dark")
                        d.reveal("Show technical details")
                        ComposeUiChecks.capture(d,"details-controls-${width}-${scale}x-$dark")
                        d.clickTag("disclosure:technical")
                        d.main {
                            val words=words(d)
                            check(words.containsAll(listOf("Identification evidence","Synthetic primary interface","12 ms","Synthetic current response at ${known.ip}")),"Technical details retain identification, MAC source, timing and response evidence")
                        }
                        d.assertTextLayouts("Technical details ${width}dp ${scale}x dark=$dark")
                    }
                }

                d.configure(375,1f)
                val missing=device().apply {detail("HomeKit pairing identifier",MAC)}
                d.fixture(listOf(missing),missing)
                d.main {check(d.tagged("copy-mac")==null && "MAC" !in words(d) && "Not available" !in words(d),"An absent hardware MAC hides the whole identity row, even with MAC-shaped metadata")}
                ComposeUiChecks.capture(d,"details-identity-without-mac")

                val raopSource="RAOP advertised hardware ID; may be another interface"
                val advertisedOnly=device(mac=MAC,source=raopSource)
                d.fixture(listOf(advertisedOnly),advertisedOnly)
                d.main {check(d.tagged("copy-mac")==null && "MAC" !in words(d),"An advertised identifier that may name another interface stays out of the identity card")}
                d.reveal("Show technical details");d.clickTag("disclosure:technical")
                d.main {check(MAC in words(d) && raopSource in words(d),"Technical details retain the advertised MAC and its interface qualification")}

                val secondary=device("192.0.2.72",MAC,"Synthetic secondary interface").apply {link("fixture:detail-addresses")}
                val primary=device().apply {link("fixture:detail-addresses")}
                d.fixture(listOf(primary,secondary));d.search(primary.ip);d.clickTag("device:${primary.ip}")
                d.main {
                    check(d.activity.uiState().selected?.endpoints()?.size==2,"Grouped fixture exercises two real endpoint snapshots")
                    check(d.tagged("copy-mac")==null && secondary.ip !in words(d),"Header never borrows a sibling MAC or repeats the grouped endpoints")
                }
                d.reveal("Show technical details");d.clickTag("disclosure:technical")
                d.main {
                    check(words(d).containsAll(listOf("2 IP addresses grouped together",secondary.ip,"Synthetic secondary interface","12 ms","14 ms")),"Grouped technical details retain each address, MAC source and independent timing")
                    check(d.tagged("copy-ip:${primary.ip}")!=null && d.tagged("copy-ip:${secondary.ip}")!=null && d.tagged("copy-mac:${secondary.ip}")!=null,"Each grouped endpoint retains its copy actions")
                }
                d.assertTextLayouts("Grouped technical details")
                ComposeUiChecks.capture(d,"details-grouped-technical")

                // Copy checks follow the visual captures so the system clipboard
                // overlay cannot obscure the screenshots.
                d.clickTag("copy-ip:${secondary.ip}")
                d.main {check(clipboard(d)==secondary.ip,"Secondary IPv4 copy preserves the exact endpoint")}
                d.clickTag("copy-mac:${secondary.ip}")
                d.main {check(clipboard(d)==MAC,"Secondary MAC copy preserves the exact reported value")}
                // Recreation reloads actual sample data in demo mode. Retain
                // these synthetic results through the real RAM-only scan store.
                val network=NetworkState.current(d.activity)
                val store=Store(d.activity)
                d.main {
                    store.retainIfCurrent(store.token(),network.key,listOf(primary,secondary),true)
                    d.set("network",network);d.set("demo",false)
                }
                d.recreate()
                d.main {check(d.activity.uiMemory.expanded["technical"]==true && "Synthetic secondary interface" in words(d),"Activity recreation retains the technical disclosure and current endpoint evidence in RAM")}

                val own=device(mac="02:11:22:33:44:71",source="Synthetic primary interface").apply {link("fixture:detail-addresses")}
                val advertisedPeer=device(secondary.ip,MAC,raopSource).apply {link("fixture:detail-addresses")}
                d.main {store.retainIfCurrent(store.token(),network.key,listOf(own,advertisedPeer),true)}
                d.fixture(listOf(own,advertisedPeer));d.search(own.ip);d.clickTag("device:${own.ip}")
                d.main {
                    check(d.activity.uiState().selected?.endpoints()?.size==2,"Interface-qualified advertisement remains a valid grouped fixture")
                    check(own.mac in words(d) && MAC !in words(d),"Header pairs the primary IPv4 with its own MAC when an advertisement names another interface")
                }
                d.clickTag("copy-address")
                d.main {check(clipboard(d)==own.ip,"Header IPv4 copy is exact")}
                d.clickTag("copy-mac")
                d.main {check(clipboard(d)==own.mac,"Header MAC copy is exact and belongs to its IPv4")}
                d.reveal("Show technical details");d.clickTag("disclosure:technical")
                d.main {check(d.tagged("copy-mac:${own.ip}")!=null && d.tagged("copy-mac:${secondary.ip}")!=null,"Distinct interface MACs remain independently copyable")}
                d.reveal("Copy device details");d.click("Copy device details")
                d.main {check(clipboard(d).orEmpty().let {own.ip in it && secondary.ip in it && own.mac in it && MAC in it},"Full device copy retains all grouped identifiers")}
            } catch(t: Throwable) {runCatching {ComposeUiChecks.capture(d,"details-identity-failure")};throw t}
        }
        Store(test.targetContext).clear()
    }
}
