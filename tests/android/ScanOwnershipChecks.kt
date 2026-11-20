package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.net.wifi.WifiManager
import io.github.kazands.networklens.*
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal object ScanOwnershipChecks {
    private fun field(op: Any, name: String) = op.javaClass.getDeclaredField(name).apply { isAccessible=true }
    fun run(test: Instrumentation, check: (Boolean,String)->Unit) {
        val original=Device().apply {ip="198.18.0.2";name="Synthetic endpoint";service("_http._tcp.")}
        val input=mutableListOf(original)
        val snapshot=ScanPresentation.capture(input)
        original.name="Changed producer";original.services.clear();input.clear()
        val first=snapshot.devices()
        check(first.single().name=="Synthetic endpoint" && first.single().services.contains("_http._tcp."),"Snapshot owns endpoint fields and collections")
        first.single().name="Changed consumer";first.single().services.clear()
        snapshot.groups().single().name="Changed grouped consumer"
        check(snapshot.devices().single().name=="Synthetic endpoint" && snapshot.groups().single().name=="Synthetic endpoint","Neither UI consumer nor grouping can mutate a retained snapshot")
        val before=NetworkState().apply {ip="198.18.0.1";prefix=24;key="synthetic-session";addresses.add(ip);prefixes[ip]=prefix}
        val after=NetworkState().apply {ip="198.19.0.1";prefix=24;key=before.key;addresses.add(ip);prefixes[ip]=prefix}
        val op=Discovery(test.targetContext,before)
        field(op,"accepting").set(op,true);field(op,"enriching").set(op,true)
        val peer=Device().apply {ip="198.18.0.2";name="Original current endpoint"}
        (field(op,"found").get(op) as MutableMap<String,Device>)[peer.ip]=peer
        (field(op,"freshResponders").get(op) as MutableSet<String>).add(peer.ip)
        check(!op.refreshNetwork(after),"A withdrawn network scope cannot refresh an active scan")
        val release=CountDownLatch(1)
        val late=Thread {
            release.await()
            op.javaClass.getDeclaredMethod("tpLinkResolved",InetAddress::class.java,Int::class.javaPrimitiveType,TpLinkDiscovery.Response::class.java).apply {isAccessible=true}
                .invoke(op,InetAddress.getByName(peer.ip),9999,TpLinkDiscovery.Response("HS103","plug","Late name","","",""))
        }
        late.start();op.cancel();release.countDown();late.join(2000)
        check(!late.isAlive && peer.name=="Original current endpoint" && peer.model.isEmpty(),"Late protocol reply after cancellation cannot change identity")
        val socket=Socket()
        try {
            (field(op,"sockets").get(op) as MutableSet<Socket>).add(socket)
            check(false,"Ended scan must reject a new socket")
        } catch (_:CancellationException) {check(socket.isClosed,"A socket registered after scan cancellation is closed immediately")}
        op.cancel()
        cancelledStartup(test, check)
        cancelledEnrichment(test, check)
        multicastOwnership(test, check)
    }
    private fun cancelledEnrichment(test: Instrumentation,check: (Boolean,String)->Unit) {
        val op=Discovery(test.targetContext,NetworkState())
        field(op,"enriching").set(op,true)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val finished=CountDownLatch(1)
        val worker=AtomicReference<Thread>();val failure=AtomicReference<Throwable>()
        val socket=AtomicReference<Socket>();val continued=AtomicInteger()
        val submit=Discovery::class.java.getDeclaredMethod("submitEnrichment",String::class.java,Runnable::class.java).apply {isAccessible=true}
        try {
            check(submit.invoke(op,"Synthetic cancellation",Runnable {
                // Capture only this fixture worker's uncaught failure so the
                // negative control would fail an assertion rather than kill Android.
                worker.set(Thread.currentThread())
                Thread.currentThread().uncaughtExceptionHandler=Thread.UncaughtExceptionHandler {_,e->failure.set(e)}
                entered.countDown()
                try {
                    while(release.count>0) {try {release.await()} catch (_:InterruptedException) {}}
                    val value=Socket();socket.set(value)
                    (field(op,"sockets").get(op) as MutableSet<Socket>).add(value)
                    continued.incrementAndGet()
                } finally {finished.countDown()}
            })==true,"Synthetic enrichment is admitted before cancellation")
            check(entered.await(2,TimeUnit.SECONDS),"Actual enrichment worker pauses before resource registration")
            op.cancel();release.countDown()
            check(finished.await(2,TimeUnit.SECONDS),"Cancelled enrichment exits its action promptly")
            worker.get()?.join(2000)
            check(worker.get()?.isAlive==false && failure.get()==null,"Rejected late registration does not escape the enrichment worker")
            check(socket.get()?.isClosed==true && continued.get()==0,"Cancellation closes the rejected socket and prevents continuation")
        } finally {
            op.cancel();release.countDown();worker.get()?.join(2000)
        }
    }
    private fun cancelledStartup(test: Instrumentation, check: (Boolean,String)->Unit) {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val progress = AtomicInteger()
        val outcome = AtomicReference<Pair<Boolean,String>>()
        val net = NetworkState().apply { ip="198.18.0.1";prefix=24;key="synthetic-cancelled-startup" }
        val op = Discovery(test.targetContext, net,
            Discovery.EchoFactory { _, _ -> throw AssertionError("Cancelled startup must not probe") },
            Discovery.NetworkCheck { _, _ ->
                entered.countDown()
                if (!resume.await(5, TimeUnit.SECONDS)) throw AssertionError("Startup fixture was not resumed")
            })
        try {
            op.scan(null, object : Discovery.Listener {
                override fun progress(done: Int, total: Int, devices: List<Device>) { progress.incrementAndGet() }
                override fun finished(devices: List<Device>, complete: Boolean, message: String) {
                    outcome.set(complete to message);finished.countDown()
                }
            })
            check(entered.await(5, TimeUnit.SECONDS), "Actual scan pauses after its initial cancellation check")
            op.cancel()
            resume.countDown()
            check(finished.await(5, TimeUnit.SECONDS), "Cancelled startup finishes within the cleanup budget")
            check(outcome.get()?.let { !it.first && it.second.contains("stopped") } == true,
                "Startup cancelled during network validation reports Stop")
            check(progress.get()==0 && !(field(op,"accepting").get(op) as Boolean) &&
                !(field(op,"enriching").get(op) as Boolean), "Cancelled startup cannot reopen admissions or publish progress")
            check(field(op,"multicast").get(op)==null && field(op,"nsdStarted").getLong(op)==0L,
                "Cancelled startup never acquires multicast reception or starts native discovery")
        } finally {
            op.cancel();resume.countDown();finished.await(5, TimeUnit.SECONDS)
        }
    }
    private fun multicastOwnership(test: Instrumentation, check: (Boolean,String)->Unit) {
        val op = Discovery(test.targetContext, NetworkState())
        val enable = Discovery::class.java.getDeclaredMethod("enableMulticast").apply { isAccessible=true }
        val start = Discovery::class.java.getDeclaredMethod("startNsd").apply { isAccessible=true }
        val cleanup = Discovery::class.java.getDeclaredMethod("cleanupNsd").apply { isAccessible=true }
        try {
            field(op,"accepting").set(op,true)
            val lock = enable.invoke(op) as WifiManager.MulticastLock
            check(lock.isHeld, "Synthetic emulator fixture acquires actual Android multicast reception")
            op.cancel()
            check(!lock.isHeld, "Cancellation releases the actual Android multicast lock")
            cleanup.invoke(op);op.cancel()
            check(!lock.isHeld, "Repeated terminal and native cleanup cannot retain or under-release the lock")
            // Defense in depth: even a stale admission flag cannot bypass terminal ownership.
            field(op,"accepting").set(op,true)
            start.invoke(op)
            check(enable.invoke(op)==null && field(op,"nsdStarted").getLong(op)==0L,
                "Late native startup rejects a cancelled owner even with a stale accepting flag")
        } finally { op.cancel();cleanup.invoke(op) }
    }
}
