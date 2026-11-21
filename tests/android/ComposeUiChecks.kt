@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.semantics.*
import io.github.kazands.networklens.*
import java.io.File

/** Real Compose screens, synthetic observations and no live discovery or update requests. */
internal object ComposeUiChecks {
    fun fixtures(): List<Device> {
        val thermostat=Device().apply {
            ip="192.0.2.41";seen=true;name="Fixture hallway";link("compose:thermostat")
            hint("Manufacturer","ecobee","Synthetic current report",80);hint("Model","ECB601","Synthetic current report",80)
            hint("Device type","AirPlay device","Synthetic current service",50);service("_airplay._tcp.")
            evidence("Synthetic current IPv4 response");detail("ICMP response time","12 ms")
        }
        val v6=Device().apply {
            ip="fe80:0000:0000:0000:1234:5678:9abc:def0%wlan_fixture0";seen=true;link("compose:thermostat")
            hint("Manufacturer","ecobee","Synthetic current IPv6 report",80);hint("Model","ECB601","Synthetic current IPv6 report",80)
            evidence("Synthetic current IPv6 response");detail("ICMPv6 response time","14 ms")
        }
        val laptop=Device().apply {
            ip="192.0.2.43";seen=true;name="Fixture workstation";recognizeAppleModel("MacBookPro18,3")
            hint("Model","MacBookPro18,3","Synthetic Apple model report",80);hint("Manufacturer","Apple","Synthetic Apple maker",80)
        }
        val plug=Device().apply {ip="192.0.2.44";seen=true;hint("Model","HS103(US)",TpLinkDiscovery.MODEL_SOURCE,80);hardware("02:11:22:33:44:55","Synthetic current response")}
        val router=Device().apply {ip="192.0.2.1";seen=true;gateway=true}
        val onlyV6=Device().apply {ip="2001:db8:1234:5678:90ab:cdef:1234:5678";seen=true;name="Fixture IPv6 sensor";hint("Device type","HomeKit sensor","Synthetic current service",80)}
        val unknown=Device().apply {ip="192.0.2.42";seen=true;evidence("Synthetic current unnamed response")}
        return listOf(router,thermostat,v6,unknown,laptop,plug,onlyV6)
    }
    internal fun capture(d: ComposeDriver,name: String) {
        // Test artifacts show the app rather than the transient system clipboard overlay.
        d.main {
            d.activity.uiMemory.notice=null
            if(Build.VERSION.SDK_INT>=28) (d.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).clearPrimaryClip()
        }
        d.settle()
        val folder=File(d.activity.getExternalFilesDir(null),"compose-ui-api${Build.VERSION.SDK_INT}").apply {mkdirs()}
        val image=d.test.uiAutomation.takeScreenshot() ?: run {
            // Some Android 8 emulator images cannot capture their display through
            // UiAutomation. Render the actual current app/dialog view as a fallback.
            var rendered: Bitmap?=null
            d.main {
                val view=d.root().view.rootView
                rendered=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also {view.draw(android.graphics.Canvas(it))}
            }
            rendered!!
        }
        File(folder,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    private fun clip(d: ComposeDriver)=(d.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    fun run(test: Instrumentation,check: (Boolean,String)->Unit,mode: String="compose-ui") {
        ComposeDriver(test,check).use { d ->
            Store(test.targetContext).clear();DeviceCatalogLoader.load(test.targetContext);d.start()
            try { when(mode) {
                "compose-typography","typography" -> typography(d,check)
                "compose-updates","app-updates" -> updates(d,check)
                "compose-reports","recognition-report" -> reports(d,check)
                "compose-rtl" -> rightToLeft(d,check)
                else -> basic(d,check)
            } } catch(t: Throwable) { runCatching {capture(d,"failure-$mode")};throw t }
        }
        Store(test.targetContext).clear()
    }
    private fun rightToLeft(d: ComposeDriver,check: (Boolean,String)->Unit) {
        d.configure(411,1f)
        val endpoint=fixtures().last {it.ip.startsWith("2001:")}
        d.fixture(listOf(endpoint));d.clickTag("device:${endpoint.ip}")
        d.main {d.root().view.layoutDirection=android.view.View.LAYOUT_DIRECTION_RTL};d.settle()
        d.main {
            val layouts=d.nodes().flatMap {node -> mutableListOf<androidx.compose.ui.text.TextLayoutResult>().also {node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(it)}}
            check(layouts.any {it.layoutInput.text.text=="Fixture IPv6 sensor" && it.layoutInput.layoutDirection==androidx.compose.ui.unit.LayoutDirection.Rtl},"Actual Compose screen applies right-to-left layout")
            val address=layouts.filter {it.layoutInput.text.text in setOf("2001:","db8:","1234:","5678:","90ab:","cdef:","5678")}
            check(address.size>=7 && address.all {it.layoutInput.layoutDirection==androidx.compose.ui.unit.LayoutDirection.Ltr},"IPv6 groups retain left-to-right layout inside a right-to-left screen")
        }
        d.clickTag("copy-address")
        d.main {check(clip(d)==endpoint.ip,"Right-to-left address copy preserves the exact original IPv6 value")}
        capture(d,"details-rtl")
    }
    private fun basic(d: ComposeDriver,check: (Boolean,String)->Unit) {
        // The functional scenario has a fixed baseline viewport; the typography
        // mode separately exercises 320dp/200% and the wide two-pane layout.
        d.configure(411,1f)
        val fixtures=fixtures();d.fixture(fixtures)
        d.revealTag("result-count")
        d.main {
            check(d.words().contains("6 devices"),"Compose list uses plain device wording for grouped results")
            check(d.words().none {it.contains("02:11:22:33:44:55")},"MAC lines stay out of the list")
            val options=d.tagged("options")!!
            check(options.config.getOrNull(SemanticsActions.OnClick)?.action!=null && options.touchBoundsInRoot.height>=48*d.root().density.density,"Toolbar has a real accessible action and 48dp target")
        }
        capture(d,"list-light");d.assertTextLayouts("Default list")
        d.search("02:11:22:33:44:55")
        d.privateKeyboard()
        d.main {check(d.words().contains("1 matching device"),"Hidden MAC remains searchable")}
        d.search("no-fixture-match");d.main {check(d.words().contains("No matching devices"),"No-match state is neutral and recoverable")};capture(d,"search-no-match")
        d.click("Clear filters");d.clickTag("filter-UNNAMED")
        d.main {check(d.words().contains("1 matching device") && d.words().contains("Unnamed device"),"Unnamed filter keeps the useful unresolved endpoint")}
        d.clickTag("device:192.0.2.42")
        d.main {check(d.words().contains(RecognitionCopy.UNNAMED),"Unknown details explain available evidence without inventing identity");check(d.words().count {it=="Not available"}==1,"Unknown MAC is summarised once before disclosure")}
        d.reveal("Show technical details");d.click("Show technical details")
        d.main {check(d.words().contains("Synthetic current unnamed response"),"Technical disclosure retains unnamed endpoint evidence");check(d.words().none {it.contains("Check for Envisalink")},"No manual alarm-check action remains")}
        d.reveal("Copy device details");d.click("Copy device details")
        d.main {check(clip(d).contains("IP: 192.0.2.42") && clip(d).contains("MAC: Not available"),"Copy preserves unknown values and original address")}
        d.clickTag("back")
        d.main {check(d.activity.uiMemory.filter==DeviceFilter.UNNAMED,"Back preserves the active list filter")}
        d.clickTag("filter-ALL");d.search("192.0.2.41");d.clickTag("device:192.0.2.41")
        d.main {
            check(d.words().contains("Thermostat") && d.words().contains("Fixture hallway"),"Physical thermostat identity and room lead the details")
            check(d.words().none {it=="ECB601"},"Raw model starts collapsed")
            check(d.words().indexOf("192.0.2.41")<d.words().indexOf(fixtures[2].ip),"Unified address section orders IPv4 before IPv6")
        }
        d.assertTextLayouts("Default details");capture(d,"details-light")
        d.reveal("How this was identified");d.click("How this was identified")
        d.main {check(d.words().any {it.contains("offline product catalogue")},"Identification explanation preserves the actual evidence basis")}
        d.reveal("Show technical details");d.click("Show technical details")
        d.main {check(d.words().contains("Synthetic current IPv4 response") && d.words().contains("Synthetic current IPv6 response"),"One disclosure contains independent evidence for both endpoints")}
        d.scroll("device-details",500f)
        var root: Any?=null;var nodeId=0;var offset=0
        d.main {root=d.root();nodeId=d.tagged("device-details")!!.id;offset=d.activity.uiMemory.detailOffset}
        repeat(12) {d.main {d.render()}}
        d.settle()
        d.main {check(d.root()===root && d.tagged("device-details")!!.id==nodeId && d.activity.uiMemory.detailOffset==offset,"Repeated refresh keeps the Compose root, details node and scroll offset (same root=${d.root()===root}, node=$nodeId/${d.tagged("device-details")!!.id}, offset=$offset/${d.activity.uiMemory.detailOffset})")}
        d.reveal("Copy device details");d.click("Copy device details")
        d.main {val copied=clip(d);check(copied.contains(fixtures[2].ip) && copied.contains("12 ms") && copied.contains("14 ms") && copied.contains("Model: ECB601"),"Copy retains exact scoped IPv6, model and separate timings")}
        d.clickTag("back");d.clickTag("options");d.click("Settings")
        d.awaitTag("settings")
        d.main {check(d.tagged("options")!=null && d.words().contains("Scanning"),"Settings retains the same overflow control")}
        d.click("Scan range");d.main {check(d.words().contains("Custom IPv4 range (optional)"),"Range dialog uses a real Compose text field")}
        d.search("not-a-range");d.click("Save range")
        d.main {check(d.words().any {it.contains("Enter an IPv4 address") || it.contains("Connect to an IPv4")},"Range errors stay visible without dismissing the dialog")}
        d.click("Cancel");d.clickTag("back")
        val network=NetworkState.current(d.activity);val store=Store(d.activity)
        d.main {store.retainIfCurrent(store.token(),network.key,fixtures,true);d.set("network",network);d.set("demo",false);d.set("query","");d.set("scanning",true);d.set("done",100);d.set("total",254);d.render()};d.settle()
        d.main {check(d.words().any {it.contains("Checking addresses · 39%") && it.contains("100 of 254")},"Scan percentage describes only the address pass")};capture(d,"scan-addresses")
        d.main {d.set("done",254);d.render()};d.settle()
        d.main {check(d.words().any {it.startsWith("Identifying devices…")} && d.words().none {it.contains("100%")},"Identification continues without a false overall 100 percent")};capture(d,"scan-identifying")
        d.main {d.set("scanning",false);store.retainIfCurrent(store.token(),network.key,fixtures,ScanReport("Synthetic",false,"Some checks need review.",254,254,System.currentTimeMillis(),mapOf("Fixture lookup" to "Failed · synthetic timeout")));d.render()};d.settle()
        d.main {check(d.words().any {it.contains("1 check to review")} && store.report(network.key)?.complete==false,"Finished status preserves incomplete method outcomes")}
        d.clickTag("scan-details-action");d.click("Show method outcomes")
        d.main {check(d.words().contains("Failed · synthetic timeout"),"Bottom sheet retains the actual method failure")};capture(d,"scan-details")
        d.click("Done")
        d.search("192.0.2.41");d.clickTag("device:192.0.2.41")
        d.reveal("How this was identified");d.click("How this was identified")
        d.recreate()
        d.main {check(d.activity.uiState().selected?.ip=="192.0.2.41" && d.activity.uiState().query=="192.0.2.41" && d.words().any {it.contains("offline product catalogue")},"Activity recreation retains selection, query and disclosure only in RAM")}
        d.main {store.clear();d.set("demo",true);d.set("devices",emptyList<Device>());d.set("groupedSource",null);d.set("selected",null);d.set("query","");d.render()};d.settle()
        d.main {check(d.words().contains("Your devices will appear here"),"First-scan state has clear next-step copy")};capture(d,"before-scan")
        d.main {d.set("demo",false);d.set("network",null);d.render()};d.settle()
        d.main {check(d.words().contains("Connect to Wi-Fi") && d.words().contains("Open Wi-Fi settings"),"Disconnected state exposes the correct recovery");check(d.tagged("scan")!!.config.contains(SemanticsProperties.Disabled),"Disconnected scan action is disabled")}
        capture(d,"wifi-disconnected")
        d.main {d.set("demo",true);d.render()};d.settle();d.clickTag("exit-sample")
        d.main {check(!d.activity.uiState().demo && !d.activity.uiState().scanning && d.activity.uiState().groups.isEmpty(),"Leaving the sample restores an empty real network without starting a scan")}
        val late=Device().apply {ip="192.0.2.99";seen=true}
        d.main {d.set("demo",true)};d.fixture(listOf(late))
        d.main {
            late.hint("Model","HS103(US)",TpLinkDiscovery.MODEL_SOURCE,80)
            MainActivity::class.java.getDeclaredMethod("refreshCataloguePresentation").apply {isAccessible=true}.invoke(d.activity)
        };d.settle();d.revealTag("device:${late.ip}")
        d.main {check(d.words().contains(late.presentation().title) && !late.presentation().unnamed,"Catalogue completion refreshes cached rows and filters without a new scan")}
        d.main {
            val saved=Bundle();MainActivity::class.java.getDeclaredMethod("onSaveInstanceState",Bundle::class.java).apply {isAccessible=true}.invoke(d.activity,saved)
            check(saved.isEmpty,"Compose state is not written into Android saved state")
        }
    }
    private fun typography(d: ComposeDriver,check: (Boolean,String)->Unit) {
        val fixtures=fixtures()
        for((width,scale) in listOf(411 to 1f,320 to 2f,900 to 1f)) {
            d.configure(width,scale)
            for(dark in listOf(false,true)) {
                d.main {Store(d.activity).dark(dark);d.set("demo",true);d.set("selected",null)}
                d.fixture(fixtures);d.assertTextLayouts("List ${width}dp ${scale}x dark=$dark");capture(d,"list-${width}-${scale}x-$dark")
                d.search("192.0.2.41");d.clickTag("device:192.0.2.41")
                d.assertTextLayouts("Details ${width}dp ${scale}x dark=$dark")
                d.main {check(d.words().contains(fixtures[2].ip),"Scoped IPv6 remains exact in the real $width dp/$scale x layout")}
                capture(d,"details-${width}-${scale}x-$dark")
                d.reveal("Show technical details");d.click("Show technical details");d.assertTextLayouts("Technical evidence ${width}dp ${scale}x dark=$dark")
                d.reveal("Copy device details");d.click("Copy device details")
                d.main {check(clip(d).contains(fixtures[2].ip) && !clip(d).contains('\u200b'),"Copy never contains display-only wrapping characters")}
                d.clickTag("back");d.search("2001:db8:");d.revealTag("device:${fixtures[6].ip}")
                d.main {check(d.words().contains(fixtures[6].ip),"An IPv6-only device stays visible in the list")}
                d.assertTextLayouts("IPv6-only list $width/$scale/$dark");capture(d,"ipv6-only-${width}-${scale}x-$dark")
            }
        }
    }
    private fun updates(d: ComposeDriver,check: (Boolean,String)->Unit) {
        val installedVersion=d.test.targetContext.packageManager.getPackageInfo(d.test.targetContext.packageName,0).versionName.orEmpty()
        check(installedVersion.isNotEmpty(),"Updater fixture reads the actual installed package version")
        var flow=d.field("updater") as UpdateFlow
        val release=AppUpdates.Release(AppUpdates.Version(0,99,0),true,"https://github.com/kazands/network-lens/releases/download/v0.99.0/Network-Lens-0.99.0.apk",1024,"a".repeat(64))
        fun field(name: String,value: Any?)=flow.javaClass.getDeclaredField(name).apply {isAccessible=true}.set(flow,value)
        d.main {check(flow.view.step==UpdateFlow.Step.IDLE && flow.javaClass.getDeclaredField("transfer").apply {isAccessible=true}.get(flow)==null,"Opening Compose never requests an update")}
        for(scale in listOf(1f,2f)) {
        d.configure(411,scale)
        flow=d.field("updater") as UpdateFlow
        for(dark in listOf(false,true)) for(step in UpdateFlow.Step.entries) {
            d.main {Store(d.activity).dark(dark);field("release",release);field("view",UpdateFlow.View(step,"Synthetic update status",47));d.set("modal",UiModal.UPDATES);d.set("updatesOpen",true);d.render()};d.settle()
            d.main {check(d.words().contains("Installed · $installedVersion") && d.words().none {it.contains("null")},"Updater $step retains installed version and valid text")}
            d.assertTextLayouts("Updater $step dark=$dark font=$scale");capture(d,"update-${step.name}-$dark-$scale")
            if(step==UpdateFlow.Step.BLOCKED) d.main {check(d.words().none {it in setOf("Install update","Continue installation","Check again")},"Blocked updater offers no bypass or immediate retry")}
        }
        }
        d.main {field("view",UpdateFlow.View(UpdateFlow.Step.DOWNLOADING,"Synthetic update status",48));d.render()};d.settle()
        var root: Any?=null;var cancel=0
        d.main {root=d.root();cancel=d.text("Cancel")!!.id;for(percent in listOf(49,50,51)) {field("view",UpdateFlow.View(UpdateFlow.Step.DOWNLOADING,"Synthetic update status",percent));d.render()}}
        d.settle();d.main {check(d.root()===root && d.text("Cancel")!!.id==cancel && d.words().contains("Downloading · 51%"),"Download updates preserve the dialog root and action node")}
        d.click("Cancel");d.main {check(flow.view.step==UpdateFlow.Step.AVAILABLE && d.field("updatesOpen")==false,"Cancel keeps the updater retryable without contacting GitHub")}
    }
    private fun reports(d: ComposeDriver,check: (Boolean,String)->Unit) {
        d.configure(411,2f)
        val fixtures=fixtures();val network=NetworkState.current(d.activity);val store=Store(d.activity)
        d.main {store.retainIfCurrent(store.token(),network.key,fixtures,ScanReport("Synthetic",false,"Review fixture",254,254,System.currentTimeMillis(),mapOf("Fixture lookup" to "Failed · synthetic timeout")));d.set("demo",false);d.set("network",network)}
        d.fixture(fixtures);d.search("192.0.2.42")
        d.main {d.activity.handle(UiAction.ReportOptions)};d.settle()
        d.main {check(d.nodes().filter {it.config.contains(SemanticsProperties.ToggleableState)}.let {it.size==1 && it.all {node->node.config[SemanticsProperties.ToggleableState]==androidx.compose.ui.state.ToggleableState.Off}},"Detailed report defaults to masking names and addresses")}
        d.assertTextLayouts("Report privacy choices at 200% text")
        d.click("Preview report")
        repeat(40) {var ready=false;d.main {ready=d.activity.uiState().modal==UiModal.REPORT};if(!ready) {android.os.SystemClock.sleep(100);d.settle()}}
        d.main {
            val document=d.activity.uiState().document ?: throw AssertionError("Report was not prepared")
            val text=document.text()
            check(text.contains("6 device groups / 7 address endpoints"),"Report includes the full inventory despite the active search")
            check(!text.contains("Fixture hallway") && !text.contains("192.0.2.41"),"Default report masks synthetic private names and addresses")
        }
        d.click("Copy this page")
        d.main {check(clip(d).startsWith("Network Lens report · page 1/"),"Report copy uses the actual preview page");check((d.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.description.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE)==true,"Report clipboard remains marked sensitive")}
        var pageCount=0;d.main {pageCount=d.activity.uiState().document!!.pages.size}
        if(pageCount>1) {
            d.click("Next");d.click("Copy this page")
            d.main {check(clip(d).startsWith("Network Lens report · page 2/$pageCount"),"Fixed report controls copy the newly selected page")}
            d.click("Previous")
        }
        d.assertTextLayouts("Report preview and fixed controls at 200% text")
        capture(d,"report-preview")
        d.main {
            val clipboard=d.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Synthetic sentinel","unchanged-synthetic-sentinel"))
            store.clear();d.activity.handle(UiAction.CopyReportPage(0))
            check(clip(d)=="unchanged-synthetic-sentinel","A cleared session cannot copy its obsolete report")
        }
        TroubleshootingChecks.run(d.test,d.activity,check)
    }
}
