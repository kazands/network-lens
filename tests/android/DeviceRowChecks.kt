@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.kazands.networklens.test

import android.app.Instrumentation
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import io.github.kazands.networklens.*

/** Synthetic names and documentation addresses; never reads or scans the LAN. */
internal object DeviceRowChecks {
    private fun rowText(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map {it.text}+node.children.flatMap(::rowText)

    private fun fixtures(): List<Device> {
        val shield=Device().apply {
            ip="192.0.2.71";seen=true;name="SHIELD Android TV"
            hint("Model","SHIELD Android TV","Synthetic current report",80)
            service("_androidtvremote2._tcp.");service("_googlecast._tcp.")
        }
        val home=Device().apply {
            ip="192.0.2.72";seen=true;name="Synthetic lounge"
            hint("Model","Google Home","Synthetic current report",80);service("_googlecast._tcp.")
        }
        val phone=Device().apply {
            ip="192.0.2.73";seen=true;recognizeAppleModel("iPhone17,1")
            hint("Model","iPhone17,1","Synthetic current report",80);service("_companion-link._tcp.")
        }
        val plug=Device().apply {
            ip="192.0.2.74";seen=true
            hint("Model","KP400",TpLinkDiscovery.MODEL_SOURCE,80)
        }
        val cast=Device().apply {
            ip="192.0.2.75";seen=true;name="Synthetic upstairs media room with a deliberately long display name"
            hint("Model","Chromecast with Google TV","Synthetic current report",80);service("_googlecast._tcp.")
            link("fixture:long-cast")
        }
        val peer=Device().apply {
            ip="192.0.2.175";seen=true;link("fixture:long-cast")
            hint("Model","Chromecast with Google TV","Synthetic current report",80)
        }
        val long=Device().apply {
            ip="192.0.2.76";seen=true
            name="Synthetic multiroom controller with an unusually long reported device name for the layout stress check"
        }
        val unbroken=Device().apply {
            ip="192.0.2.77";seen=true;name="fixture-"+"abcdefghijk".repeat(5)
        }
        val harmony=Device().apply {
            ip="192.0.2.78";seen=true;name="Synthetic study"
            hint("Model","Harmony Hub","Synthetic current report",80)
        }
        return listOf(shield,home,phone,plug,cast,peer,long,unbroken,harmony)
    }

    fun previews(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { d ->
            Store(test.targetContext).clear();DeviceCatalogLoader.load(test.targetContext);d.start();d.configure(375,1f)
            val examples=fixtures().filter {it.ip in setOf("192.0.2.71","192.0.2.72","192.0.2.73","192.0.2.74","192.0.2.78")}
            for(dark in listOf(false,true)) {
                d.main {Store(d.activity).dark(dark);d.set("demo",true)}
                d.fixture(examples);d.scrollToIndex(5)
                d.assertTextLayouts("Device list preview dark=$dark")
                ComposeUiChecks.capture(d,"preview-device-list-$dark")
                d.clickTag("device:192.0.2.71")
                d.assertTextLayouts("Device details preview dark=$dark")
                ComposeUiChecks.capture(d,"preview-device-details-$dark")
                d.reveal("Services & ports")
                ComposeUiChecks.capture(d,"preview-device-services-$dark")
                d.clickTag("back")
            }
        }
        Store(test.targetContext).clear()
    }

    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { d ->
            Store(test.targetContext).clear();DeviceCatalogLoader.load(test.targetContext);d.start()
            try {
                val fixtures=fixtures()
                val expected=listOf("NVIDIA SHIELD TV","Google Home","Apple iPhone 16 Pro","TP-Link Kasa KP400","Logitech Harmony Hub")
                check((fixtures.take(4)+fixtures.last()).map {it.presentation().title}==expected,"Row examples exercise actual catalogue product titles")
                for((width,scale) in listOf(320 to 1f,360 to 1f,375 to 1f,411 to 1f,320 to 2f,900 to 1f)) {
                    d.configure(width,scale)
                    for(dark in listOf(false,true)) {
                        d.main {Store(d.activity).dark(dark);d.set("demo",true)}
                        d.fixture(fixtures)
                        d.scrollToIndex(5)
                        ComposeUiChecks.capture(d,"device-rows-${width}-${scale}x-$dark")
                        var groups=emptyList<Device>()
                        d.main {groups=d.activity.uiState().groups}
                        for((index,device) in groups.withIndex()) {
                            // A composed row may still be clipped at the edge of
                            // the lazy viewport. Bring the whole item into view.
                            d.scrollToIndex(5+index)
                            val title=device.presentation().title
                            d.main {
                                val node=d.text(title) ?: throw AssertionError("Missing row title: $title")
                                val layouts=mutableListOf<TextLayoutResult>()
                                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                                check(layouts.isNotEmpty(),"Device name has a measured text layout")
                                if(scale==1f && title in expected) check(layouts.single().lineCount==1,"$title stays on one line at ${width}dp in both themes")
                                check(d.tagged("device:${device.ip}")!!.touchBoundsInRoot.height>=48*d.root().density.density,"The whole row remains an accessible touch target")
                            }
                            d.assertTextLayouts("Device rows ${width}dp ${scale}x dark=$dark")
                        }
                        d.search("192.0.2.75");d.scrollToIndex(5)
                        d.main {
                            check(d.words().contains(fixtures[4].name),"Long room names remain fully available")
                            check(d.words().contains("+1 address"),"Grouped addresses keep a quiet visible count")
                        }
                        d.assertTextLayouts("Long product and room ${width}dp ${scale}x dark=$dark")
                        ComposeUiChecks.capture(d,"device-rows-long-${width}-${scale}x-$dark")
                    }
                }
                d.configure(411,1f)
                d.fixture(fixtures);d.search("Android TV remote service")
                d.revealTag("result-count")
                d.main {check(d.words().contains("1 matching device"),"Services remain searchable")}
                d.revealTag("device:192.0.2.71")
                d.main {
                    val row=d.tagged("device:192.0.2.71") ?: throw AssertionError("Service search lost the device")
                    check("Android TV remote service" !in rowText(row),"Services do not crowd the row")
                }
                d.clickTag("device:192.0.2.71")
                d.reveal("Services available")
                d.main {check(d.words().contains("Android TV remote service") && d.words().contains("Google Cast"),"Full service labels remain in device details")}
                d.clickTag("back")
                d.main {check(d.activity.uiState().query=="Android TV remote service","Returning from details preserves the service search")}
            } catch(t: Throwable) {runCatching {ComposeUiChecks.capture(d,"device-rows-failure")};throw t}
        }
        Store(test.targetContext).clear()
    }
}
