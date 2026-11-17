package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic current callbacks only; the enclosing runner rejects physical devices. */
internal object OmadaChecks {
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        fun set(owner: Any, key: String, value: Any?) = owner.javaClass.getDeclaredField(key).let {it.isAccessible=true;it.set(owner,value)}
        fun scan(prefix: Int = 25) = Discovery(test.targetContext, NetworkState().apply {
            ip="198.18.0.1";this.prefix=24;key="synthetic-omada";prefixes[ip]=24;addresses.add(ip)
        }).also {set(it,"accepting",true);set(it,"enriching",true);set(it,"range",IpRange("198.18.0.1",prefix))}
        val self=scan(32)
        try {
            Discovery::class.java.getDeclaredMethod("startOmada").apply{isAccessible=true}.invoke(self)
            check(self.sources()[OmadaDiscovery.SOURCE]?.startsWith("Not used")==true,"Self-only scan skips passive Omada listener")
        }finally{self.cancel()}
        val fake=scan()
        val receive=Discovery::class.java.getDeclaredMethod("omadaResolved",InetAddress::class.java,OmadaDiscovery.Response::class.java).apply{isAccessible=true}
        fun response(model:String,family:String,mac:String) = OmadaDiscovery.Response(model,family,"Fixture equipment","1.0","1.0",mac,"2.3.0")
        try {
            receive.invoke(fake,InetAddress.getByName("198.18.0.20"),response("EAP245","ap","02:00:00:00:00:20"))
            val ap=fake.snapshot().single()
            check(ap.seen && ap.presentation().title=="TP-Link EAP245" && ap.classification().category==DeviceCatalog.Category.ACCESS_POINT,"Current AP identity and presence")
            check(ap.manufacturer.isEmpty() && ap.ports.isEmpty(),"Inferred brand not reported manufacturer or TCP port")
            check(ap.observations().any {it.field=="Model" && it.source==OmadaDiscovery.MODEL_SOURCE},"Announcement model source retained")
            ap.service("_spotify-connect._tcp.")
            check(DeviceIcon.forGroup(ap).drawable==R.drawable.ic_device_router && DeviceIcon.forGroup(ap).label=="Wi-Fi access point","AP hardware wins over service capability")
            check(ap.copy().identifiedProduct()==ap.identifiedProduct(),"Snapshot retains Omada identity")
            receive.invoke(fake,InetAddress.getByName("198.18.0.21"),response("SG2008","switch","02:00:00:00:00:21"))
            val switch=fake.snapshot().single{it.ip.endsWith(".21")}
            check(switch.classification().category==DeviceCatalog.Category.NETWORK_SWITCH && DeviceIcon.forGroup(switch).drawable==R.drawable.ic_device_network,"Network switch not light-switch icon")
            receive.invoke(fake,InetAddress.getByName("198.18.0.200"),response("EAP245","ap","02:00:00:00:00:22"))
            check(fake.snapshot().size==2,"Out-of-range announcement rejected")
            receive.invoke(fake,InetAddress.getByName("198.18.0.20"),response("ER605","gateway","02:00:00:00:00:20"))
            val conflicting=fake.snapshot().single{it.ip.endsWith(".20")}
            check(conflicting.classification().conflicted && conflicting.identifiedProduct()==null,"Conflicting AP/router identity explicit")
            // Public HTTP enrichment requires an already-confirmed current endpoint and port.
            val observe=Discovery::class.java.getDeclaredMethod("observe",String::class.java,String::class.java).apply{isAccessible=true}
            val d=observe.invoke(fake,"198.18.0.30","Synthetic current response") as Device
            val apply=Discovery::class.java.getDeclaredMethod("applyEapReport",Device::class.java,String::class.java).apply{isAccessible=true}
            d.detail(EapInfo.SOURCE,"Waiting or checking")
            check(apply.invoke(fake,d,"EAP265 HD")==false,"HTTP identity requires observed port")
            d.port(80)
            check(apply.invoke(fake,d,"EAP265 HD")==true && d.presentation().title=="TP-Link EAP265 HD","Public EAP model reaches display")
            check(d.classification().category==DeviceCatalog.Category.ACCESS_POINT && d.manufacturer.isEmpty() && d.mac.isEmpty(),"Public model does not invent manufacturer or MAC")
            check(d.observations().any {it.field=="Model" && it.source==EapInfo.MODEL_SOURCE},"Public HTTP model source retained")
            val stale=Device().apply{ip=d.ip;port(80);detail(EapInfo.SOURCE,"Waiting or checking")}
            check(apply.invoke(fake,stale,"EAP245")==false,"Reject replaced endpoint")
            check(Device().apply{ip=d.ip}.identifiedProduct()==null,"No identity inherited across fresh scan")
            d.detail(EapInfo.SOURCE,"Waiting or checking");fake.cancel()
            check(apply.invoke(fake,d,"EAP245")==false,"Reject HTTP identity after Stop")
            receive.invoke(fake,InetAddress.getByName("198.18.0.23"),response("EAP245","ap","02:00:00:00:00:23"))
            check(fake.snapshot().size==3,"Reject late announcement after Stop")
        }finally{fake.cancel()}
        // Accepted EAP work may never run: both metadata workers can still be busy at Stop/deadline.
        for (stopped in listOf(true, false)) {
            val queued = scan()
            val work = Discovery::class.java.getDeclaredField("enrichment").let {
                it.isAccessible = true; it.get(queued) as DiscoveryWork
            }
            val started = CountDownLatch(2)
            val release = CountDownLatch(1)
            try {
                repeat(2) {
                    check(work.submit(DiscoveryWork.Lane.METADATA, Runnable {
                        started.countDown()
                        try { release.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                    }), "Metadata blocker accepted")
                }
                check(started.await(3, TimeUnit.SECONDS), "Both metadata workers are occupied before queuing EAP")
                val observe = Discovery::class.java.getDeclaredMethod("observe", String::class.java, String::class.java).apply { isAccessible = true }
                val pending = (observe.invoke(queued, "198.18.0.31", "Synthetic current response") as Device).apply { port(80) }
                val finished = (observe.invoke(queued, "198.18.0.32", "Synthetic current response") as Device).apply {
                    detail(EapInfo.SOURCE, "Public model received")
                }
                Discovery::class.java.getDeclaredMethod("requestEapModel", Device::class.java).apply { isAccessible = true }.invoke(queued, pending)
                check(pending.details[EapInfo.SOURCE] == "Waiting or checking", "EAP request is admitted behind occupied workers")
                if (stopped) queued.cancel() else {
                    set(queued, "accepting", false); set(queued, "enriching", false)
                    Discovery::class.java.getDeclaredMethod("finishMetadataLookups", Boolean::class.javaPrimitiveType).apply {
                        isAccessible = true
                    }.invoke(queued, false)
                    work.shutdownNow()
                }
                val expected = if (stopped) "Stopped before lookup finished" else "Lookup did not finish"
                check(pending.details[EapInfo.SOURCE] == expected, "Queued EAP has terminal status after Stop/deadline")
                check(queued.snapshot().single { it.ip == pending.ip }.details[EapInfo.SOURCE] == expected,
                    "Retained endpoint snapshot cannot keep a waiting EAP status")
                check(finished.details[EapInfo.SOURCE] == "Public model received", "Completed EAP outcome is preserved")
                check(pending.model.isEmpty() && queued.sources()[EapInfo.SOURCE]?.contains("requests") != true,
                    "Discarded EAP task performs no network request or identity mutation")
            } finally {
                queued.cancel(); release.countDown()
                check(work.awaitTermination(3, TimeUnit.SECONDS), "Blocked metadata workers are released")
            }
        }
    }
}
