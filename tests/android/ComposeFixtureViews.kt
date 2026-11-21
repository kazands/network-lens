package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import io.github.kazands.networklens.*

/** Catalogue fixtures are checked through the actual Compose list, details and copy actions. */
internal object ComposeFixtureViews {
    fun run(test: Instrumentation,check: (Boolean,String)->Unit,fixtures: List<Device>) {
        ComposeDriver(test,check).use { d ->
            d.start();d.fixture(fixtures)
            for(group in Device.grouped(fixtures)) {
                d.search(group.ip)
                // IP search is a substring filter: .2 can also match .20 and .21.
                // Narrow viewports compose only visible rows. Bring this actual
                // row into view before asserting its title, as clickTag does.
                d.revealTag("device:${group.ip}")
                d.main {check(d.words().contains(group.presentation().title),"Compose search retains the supported product/family title for ${group.ip}")}
                d.clickTag("device:${group.ip}")
                d.main {
                    check(d.words().contains(group.presentation().title),"Compose details retain the supported identity")
                    if(group.groupProduct()?.product?.familyOnly==true) check(d.words().contains("Device family"),"Ambiguous generation stays visibly family-only")
                }
                d.reveal("Show technical details");d.click("Show technical details")
                d.main {
                    for(endpoint in group.endpoints()) {
                        if(endpoint.model.isNotEmpty()) check(d.words().contains(endpoint.model),"Expanded Compose details preserve each original model")
                        if(endpoint.manufacturer.isNotEmpty()) check(d.words().contains(endpoint.manufacturer),"Expanded Compose details preserve each reported maker")
                    }
                }
                d.reveal("Copy device details");d.click("Copy device details")
                d.main {
                    val text=(d.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
                    for(endpoint in group.endpoints()) check(text.contains("IP: ${endpoint.ip}") && (endpoint.model.isEmpty() || text.contains("Model: ${endpoint.model}")),"Device copy preserves all endpoints and raw model codes")
                }
                d.clickTag("back")
                d.main {check(d.activity.uiState().selected==null && d.activity.uiState().query==group.ip,"Back keeps the real search context")}
            }
        }
        Store(test.targetContext).clear()
    }
    fun runAudio(test: Instrumentation,check: (Boolean,String)->Unit,fixtures: List<Device>,expected: List<String>,privateId: String) {
        ComposeDriver(test,check).use {d ->
            d.start();d.fixture(fixtures)
            val group=Device.grouped(fixtures).first()
            d.search(group.ip);d.clickTag("device:${group.ip}")
            var audio=""
            d.main {audio=d.words().first {it.startsWith("Audio system")}}
            d.reveal(audio);d.click(audio)
            d.main {check(expected.any {value -> d.words().any {it.contains(value)}},"Compose audio disclosure retains source-labelled port or zone information")}
            d.reveal("Copy device details");d.click("Copy device details")
            d.main {
                val text=(d.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
                check(expected.all {text.contains(it)} && !text.contains(privateId),"Compose audio copy preserves port/zone sources and excludes internal hardware/player identifiers")
            }
        }
        Store(test.targetContext).clear()
    }

}
