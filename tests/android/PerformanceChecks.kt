package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.os.Debug
import android.os.Bundle
import android.os.SystemClock
import io.github.kazands.networklens.*

/** Synthetic, emulator-only measurements. Never evidence of household accuracy or physical speed. */
internal object PerformanceChecks {
    fun run(test: Instrumentation, check: (Boolean,String)->Unit) {
        val runtime=Runtime.getRuntime()
        val heapBefore=runtime.totalMemory()-runtime.freeMemory()
        val pssBefore=Debug.getPss()
        val begin=SystemClock.elapsedRealtimeNanos()
        MacVendors.load(test.targetContext);DeviceCatalogLoader.load(test.targetContext)
        val catalogueNanos=SystemClock.elapsedRealtimeNanos()-begin
        val heapAfter=runtime.totalMemory()-runtime.freeMemory()
        val adversarial="<!--".repeat(16384).toByteArray()
        val eapStart=SystemClock.elapsedRealtimeNanos()
        check(!EapInfo.eligiblePage(adversarial),"Maximum-size unterminated EAP comments cannot authorize an identity request")
        val eapNanos=SystemClock.elapsedRealtimeNanos()-eapStart
        val peers=(0 until 256).map {i->Device().apply {ip="198.18.${i/254}.${i%254+1}";name="Synthetic endpoint $i";seen=true}}
        val samples=mutableListOf<Long>()
        repeat(12) {
            val start=SystemClock.elapsedRealtimeNanos()
            val prepared=ScanPresentation.capture(peers)
            check(prepared.devices().size==256 && prepared.groups().size==256,"Snapshot benchmark preserves distinct unnamed hardware")
            samples.add(SystemClock.elapsedRealtimeNanos()-start)
        }
        val launchStart=SystemClock.elapsedRealtimeNanos()
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo",true))
        test.waitForIdleSync()
        val launchNanos=SystemClock.elapsedRealtimeNanos()-launchStart
        test.runOnMainSync {activity.finish()}
        val sorted=samples.sorted()
        test.sendStatus(0, Bundle().apply {putString("stream", "SYNTHETIC_PERFORMANCE catalogueLoadMs=${catalogueNanos/1_000_000} heapDeltaBytes=${heapAfter-heapBefore} pssBeforeKiB=$pssBefore pssAfterKiB=${Debug.getPss()} threads=${Thread.getAllStackTraces().size} snapshot256MedianMs=${sorted[sorted.size/2]/1_000_000} snapshot256MaxMs=${sorted.last()/1_000_000} eap64KiBMs=${eapNanos/1_000_000} activityLaunchToIdleMs=${launchNanos/1_000_000}\n")})
        check(catalogueNanos>0 && launchNanos>0,"Performance measurements are recorded without flaky speed thresholds")
    }
}
