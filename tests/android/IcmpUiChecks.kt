package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*

/** Shared by real injected/OS-denial scans and a small synthetic UI regression. */
internal object IcmpUiChecks {
    fun fixture(test: Instrumentation,check: (Boolean,String)->Unit) {
        // Reject physical targets before changing fixture state.
        ComposeDriver(test,check).use {
            val store=Store(test.targetContext);val network=NetworkState.current(test.targetContext)
            store.clear()
            try {
                val report=ScanReport("Synthetic single-address scope",false,"Some checks could not finish.",1,1,System.currentTimeMillis(),
                    mapOf("ICMP echo" to "Failed · Android or a VPN blocked ICMP echo requests"))
                store.retainIfCurrent(store.token(),network.key,emptyList(),report)
                run(test,check)
            } finally {store.clear()}
        }
    }
    fun run(test: Instrumentation,check: (Boolean,String)->Unit) {
        ComposeDriver(test,check).use { d ->
            val network=NetworkState.current(test.targetContext)
            val report=Store(test.targetContext).report(network.key) ?: throw AssertionError("ICMP scan report is missing")
            val failure=report.sources["ICMP echo"] ?: throw AssertionError("ICMP outcome is missing")
            check(!report.complete && failure.startsWith("Failed"),"ICMP error UI uses an actual failed scan outcome")
            d.start(false)
            for(dark in listOf(false,true)) {
                d.main {Store(d.activity).dark(dark);d.render()};d.settle()
                d.clickTag("scan-details-action")
                d.main {check(d.words().contains("Scan details") && d.words().contains(report.statusLabel),"ICMP failure retains the real scan status in the Compose sheet")}
                var expanded=false
                d.main {expanded=d.words().contains("Hide method outcomes")}
                if(!expanded) {d.reveal("Show method outcomes","scan-details-scroll");d.click("Show method outcomes")}
                d.reveal("ICMP echo","scan-details-scroll")
                d.main {check(d.words().contains("ICMP echo") && d.words().contains(failure),"Compose method outcomes expose the retained ICMP failure in both themes")}
                d.click("Done")
                d.main {check(d.activity.uiState().modal==null,"Done dismisses the actual ICMP details sheet")}
            }
        }
    }
}
