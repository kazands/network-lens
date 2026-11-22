package io.github.kazands.networklens.test

import android.app.Instrumentation
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.kazands.networklens.*
import kotlin.math.abs

/** Checks real row positions, without scrolling the list after returning from details. */
internal object ListAnchorChecks {
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { d ->
            Store(test.targetContext).clear();d.start();d.configure(411,1f)
            val store=Store(test.targetContext);val network=NetworkState.current(d.activity)
            fun retain(devices: List<Device>) {store.retainIfCurrent(store.token(),network.key,devices,true)}
            val original=(40..89).map { device(it) }
            for((label,recreate,changed) in listOf(
                Triple("Earlier responses",false,(2..4).map(::device)+original),
                Triple("Earlier responses with recreation",true,(2..4).map(::device)+original),
                Triple("Earlier rows removed",false,original.drop(5)))) {
                d.main {d.set("demo",false);d.set("network",network);retain(original)}
                d.fixture(original)
                d.main {d.set("scanning",true);d.render()};d.settle()
                d.scrollToIndex(20);d.scroll("devices-list",37f)
                var tag="";var before=0f
                d.main {
                    val top=d.tagged("devices-list")!!.boundsInRoot.top
                    val row=d.nodes().filter {it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("device:")==true && it.boundsInRoot.top>top+1 && it.boundsInRoot.height>0}.minBy {it.boundsInRoot.top}
                    tag=row.config[SemanticsProperties.TestTag];before=row.boundsInRoot.top
                }
                d.clickTag(tag)
                d.main {check(d.tagged("devices-list")==null,"$label actually disposes the handset list while showing details")}
                if(recreate) d.recreate()
                d.main {retain(changed);d.set("devices",changed);d.set("groupedSource",null);d.render()};d.settle()
                d.clickTag("back")
                d.main {
                    val row=d.tagged(tag)
                    check(row!=null && abs(row.boundsInRoot.top-before)<=1f,"$label restores the same device at the same viewport offset")
                }
            }
            // The anchor is a convenience, not an identity/history cache. Its
            // disappearance must fall back safely, and a new list clears it.
            d.main {retain(original)};d.fixture(original);d.scrollToIndex(40)
            d.clickTag("options");d.click("Settings")
            d.main {retain(original.take(2));d.set("devices",original.take(2));d.set("groupedSource",null);d.render()};d.settle()
            d.clickTag("back")
            d.main {check(d.tagged("devices-list")!=null && d.activity.uiMemory.listIndex<10,"A removed anchor falls back to a valid current list position")}
            d.search("missing-fixture")
            d.main {check(d.tagged("search")?.boundsInRoot?.height?.let {it>0}==true,"Changing the query discards the old device anchor and shows the search field")}
        }
        Store(test.targetContext).clear()
    }
    private fun device(last: Int)=Device().apply {ip="192.0.2.$last";seen=true;name="Fixture device $last"}
}
