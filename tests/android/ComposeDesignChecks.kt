@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)
package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import io.github.kazands.networklens.*

/** Synthetic UI only. Exercises the real settings observer and scan lifecycle, not live discovery. */
internal object ComposeDesignChecks {
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        palette(check)
        ComposeDriver(test,check).use { d ->
            val prior=d.shell("settings get global animator_duration_scale")
            try {
                d.shell("settings put global animator_duration_scale 1")
                Store(test.targetContext).clear();DeviceCatalogLoader.load(test.targetContext)
                d.start();d.configure(411,1f)
                val fixtures=ComposeUiChecks.fixtures()
                d.fixture(fixtures)
                check(lensGroup(fixtures[1])==LensGroup.HOME,"Thermostat's category colour stays smart home despite AirPlay")
                check(lensGroup(fixtures[4])==LensGroup.COMPUTERS,"Laptop colour follows its physical category")
                check(lensGroup(fixtures[0])==LensGroup.BRAND,"Gateway retains brand emphasis")
                check(lensGroup(fixtures[3])==LensGroup.UNNAMED,"Unnamed responder retains a neutral treatment")
                val network=NetworkState.current(d.activity)
                val store=Store(d.activity)
                fun stage(scanning: Boolean,report: ScanReport?=null,done: Int=100,total: Int=254) {
                    d.main {
                        if(report==null) store.clear() else store.retainIfCurrent(store.token(),network.key,fixtures,report)
                        d.set("network",network);d.set("demo",false);d.set("scanning",scanning)
                        d.set("done",done);d.set("total",total);d.render()
                    }
                }
                fun flag(key: SemanticsPropertyKey<Boolean>): Boolean {
                    var value=false;d.main {value=d.tagged("scan-radar")?.config?.getOrNull(key)==true};return value
                }
                fun awaitFlag(key: SemanticsPropertyKey<Boolean>,expected: Boolean,label: String) {
                    val until=SystemClock.uptimeMillis()+2500
                    while(flag(key)!=expected && SystemClock.uptimeMillis()<until) SystemClock.sleep(20)
                    check(flag(key)==expected,label)
                }
                fun noFinish(label: String) {
                    var seen=false
                    val until=SystemClock.uptimeMillis()+450
                    while(SystemClock.uptimeMillis()<until) {seen=seen || flag(RadarFinishing);SystemClock.sleep(20)}
                    check(!seen,label)
                }
                stage(true);awaitFlag(RadarRunning,true,"Visible active scan starts the radar")
                d.main {check(d.words().any {it.contains("39%") && it.contains("100 of 254")},"Radar retains explicit address-pass progress text")}
                ComposeUiChecks.capture(d,"design-scanning-light")
                stage(true,done=254);d.settle()
                d.main {check(d.words().any {it.startsWith("Identifying devices")} && d.words().none {it.contains("100%")},"Identification does not pretend overall progress reached 100 percent")}
                ComposeUiChecks.capture(d,"design-identifying-light")
                val complete=ScanReport("Synthetic",true,"",254,254,System.currentTimeMillis())
                stage(false,complete)
                awaitFlag(RadarFinishing,true,"New successful scan gets one closing ring")
                awaitFlag(RadarFinishing,false,"Completion motion ends")
                check(!flag(RadarRunning),"Finished scan has no infinite animation")
                d.main {repeat(8) {d.render()}};noFinish("Ordinary result refresh never replays completion")
                d.main {d.set("selected",fixtures[1]);d.render()};d.settle();d.clickTag("back")
                noFinish("Returning from device details never replays completion")
                for((label,reason) in listOf("Stop" to "Scan stopped. Results collected so far are shown.","Failed check" to "One check failed.","Network loss" to "Wi-Fi connection changed.")) {
                    stage(true);awaitFlag(RadarRunning,true,"$label fixture begins active")
                    stage(false,ScanReport("Synthetic",false,reason,254,254,System.currentTimeMillis()))
                    noFinish("$label stops without a success ring")
                    check(!flag(RadarRunning),"$label leaves the radar static")
                }
                check(!radarMayFinish(complete,0),"Empty completed scan does not celebrate a device count")
                check(!radarMayFinish(ScanReport("Synthetic",true,"Scan stopped.",1,1,1),1),"Stop suppresses completion even if a report is inconsistent")
                stage(true);awaitFlag(RadarRunning,true,"Motion begins before a live settings change")
                d.shell("settings put global animator_duration_scale 0")
                awaitFlag(RadarRunning,false,"Remove animations takes effect without recreating the screen")
                noFinish("Reduced motion has no closing ring")
                ComposeUiChecks.capture(d,"design-reduced-motion")
                d.shell("settings put global animator_duration_scale 1")
                awaitFlag(RadarRunning,true,"Re-enabling animations resumes only the active radar")
                d.scrollToIndex(5);awaitFlag(RadarRunning,false,"Off-screen radar has no infinite animation")
                d.scrollToIndex(0);awaitFlag(RadarRunning,true,"Visible active radar resumes after scrolling back")
                stage(false,complete);d.settle();awaitFlag(RadarFinishing,false,"Completion settles before static captures")
                for(dark in listOf(false,true)) {
                    d.main {Store(d.activity).dark(dark);d.render()};d.settle()
                    ComposeUiChecks.capture(d,"design-list-$dark")
                    d.search("192.0.2.41");d.clickTag("device:192.0.2.41")
                    ComposeUiChecks.capture(d,"design-details-$dark")
                    d.clickTag("back");d.search("");d.scrollToIndex(0)
                    d.clickTag("scan-details-action")
                    d.main {check(d.words().contains("Device groups") && d.words().contains("Address endpoints"),"Technical scan sheet retains separate grouping and endpoint counts")}
                    ComposeUiChecks.capture(d,"design-scan-sheet-$dark")
                    d.click("Done")
                }
                // The narrow / enlarged-text boundary uses the stacked scan-card layout.
                d.configure(320,2f)
                d.main {d.set("scanning",false);d.set("demo",true);d.set("devices",fixtures);d.set("groupedSource",null);d.render()};d.settle()
                d.assertTextLayouts("Adaptive scan card at 320dp and 200% text")
                d.main {
                    val status=d.tagged("scan-status")!!.boundsInRoot
                    val action=d.tagged("scan-details-action")!!.boundsInRoot
                    check(kotlin.math.abs(status.width-action.width)<1f,"Enlarged scan status uses the full card width instead of competing with Details")
                    val details=d.text("Details")!!.boundsInRoot
                    check(details.top>=status.bottom,"Enlarged Details action sits below the status text")
                }
                ComposeUiChecks.capture(d,"design-320dp-200pct")
            } finally {
                if(prior=="null") d.shell("settings delete global animator_duration_scale")
                else d.shell("settings put global animator_duration_scale $prior")
            }
        }
        Store(test.targetContext).clear()
    }
    private fun palette(check: (Boolean,String)->Unit) {
        fun contrast(a: Color,b: Color): Float {
            val x=a.luminance();val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        for((name,c) in listOf("light" to LensDay,"dark" to LensNight)) {
            val text=listOf(c.onBackground to c.background,c.onSurface to c.surface,c.onPrimary to c.primary,
                c.onSecondary to c.secondary,c.onTertiary to c.tertiary,c.onPrimaryContainer to c.primaryContainer,
                c.onSecondaryContainer to c.secondaryContainer,c.onTertiaryContainer to c.tertiaryContainer,
                c.onError to c.error,c.onErrorContainer to c.errorContainer,c.inverseOnSurface to c.inverseSurface,c.inversePrimary to c.inverseSurface,
                c.onPrimaryFixed to c.primaryFixedDim,c.onPrimaryFixedVariant to c.primaryFixedDim,
                c.onSecondaryFixed to c.secondaryFixedDim,c.onSecondaryFixedVariant to c.secondaryFixedDim,
                c.onTertiaryFixed to c.tertiaryFixedDim,c.onTertiaryFixedVariant to c.tertiaryFixedDim)
            for((i,pair) in text.withIndex()) check(contrast(pair.first,pair.second)>=4.5f,"$name semantic text pair $i meets AA")
            for(surface in listOf(c.background,c.surface,c.secondaryContainer,c.surfaceContainer,c.surfaceContainerHigh)) {
                check(contrast(c.onSurface,surface)>=7f,"$name primary text on each used surface retains strong contrast")
                check(contrast(c.onSurfaceVariant,surface)>=4.5f,"$name secondary text on each used surface meets AA")
                check(contrast(c.primary,surface)>=4.5f,"$name action text on each used surface meets AA")
                check(contrast(c.outline,surface)>=3f,"$name input/focus boundaries remain visible")
            }
            for(tone in (if(name=="light") LensDayTones else LensNightTones).values) {
                check(contrast(tone.ink,tone.container)>=7f,"$name category text remains high contrast")
                check(contrast(tone.icon,tone.container)>=3f,"$name category icons remain visible")
            }
        }
    }
}
