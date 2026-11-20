import io.github.kazands.networklens.*
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

object ScanLifecycleTest {
    private var checks = 0
    private fun check(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
    private fun reject(action: () -> Unit) { var rejected=false; try { action() } catch (_: IllegalArgumentException) { rejected=true }; check(rejected,"Invalid scan range rejected") }
    @JvmStatic fun main(args: Array<String>) {
        val normal = ScanPlan.create("198.18.0.40",24,null)
        check(normal.hosts.size==254 && !normal.sweepOmitted,"Normal address sweep retained")
        val large = ScanPlan.create("198.18.0.40",21,null)
        check(large.hosts.isEmpty() && large.sweepOmitted && large.range!!.prefix==21,"Large scope retained without allocating a sweep")
        check(large.scope.contains("Service discovery only") && large.scope.contains("/21"),"Omitted sweep is explicit, not a hidden /24")
        check(ScanPlan.create("198.18.0.40",22,null).hosts.size==1022,"Largest supported attached sweep")
        check(ScanPlan.create("198.18.0.40",0,null).sweepOmitted,"A /0 cannot allocate billions of addresses")
        check(ScanPlan.create("198.18.0.40",21,"198.18.0.0/24").hosts.size==254,"Explicit smaller range remains available")
        reject { ScanPlan.create("198.18.0.40",21,"198.18.0.0/21") }
        reject { ScanPlan.create("198.18.0.40",24,"198.19.0.0/24") }
        reject { ScanPlan.create("",0,"198.18.0.0/24") }
        check(ScanPlan.create("",0,null).range==null,"IPv6-only plan needs no IPv4 sweep")
        check(ScanPlan.create("198.18.0.41",31,null).hosts.size==2,"Point-to-point range retained")

        repeat(30) {
            val resources=ScanResources(); val closed=AtomicInteger(); val ready=CountDownLatch(1)
            val fake=Closeable {closed.incrementAndGet()}
            resources.own(fake)
            val late=Thread { ready.await(); try { resources.own(Closeable {closed.incrementAndGet()}) } catch (_:CancellationException) {} }
            late.start(); ready.countDown(); resources.close(); late.join(2000); resources.close()
            check(!late.isAlive && closed.get()==2,"Concurrent registration and repeated cancellation close every resource")
            check(!resources.start(Thread {error("Closed session must never launch work")}),"Late protocol startup is rejected")
        }
        val resources=ScanResources();val registry=resources.registry<Closeable>();val closed=AtomicInteger()
        val removed=Closeable {closed.incrementAndGet()};registry.add(removed);registry.remove(removed)
        resources.own(Closeable {throw IOException("synthetic close failure")})
        resources.own(Closeable {closed.incrementAndGet()});resources.close()
        check(closed.get()==1 && resources.closeFailures==1,"One close failure cannot strand another resource")
        try {registry.add(Closeable {closed.incrementAndGet()});error("Late socket admitted")}catch(_:CancellationException){}
        check(closed.get()==2,"Late socket registration closes immediately")
        val acquisition = ScanResources()
        val acquired = CountDownLatch(1)
        val register = CountDownLatch(1)
        val held = AtomicInteger()
        val lateOwner = Thread {
            held.incrementAndGet()
            val lease = Closeable { held.decrementAndGet() }
            acquired.countDown();register.await()
            try { acquisition.own(lease) } catch (_:CancellationException) {}
        }
        lateOwner.start()
        try {
            check(acquired.await(2,TimeUnit.SECONDS),"Synthetic reception acquired before registration")
            acquisition.close();register.countDown();lateOwner.join(2000)
            check(!lateOwner.isAlive && held.get()==0,"Cancellation between acquisition and registration releases a late lease")
        } finally {register.countDown();lateOwner.join(2000);acquisition.close()}
        val running=ScanResources();val entered=CountDownLatch(1);val done=CountDownLatch(1)
        val thread=Thread {entered.countDown();try {done.await()}catch(_:InterruptedException){}}
        check(running.start(thread) && entered.await(2,TimeUnit.SECONDS),"Owned worker starts")
        running.close();check(running.awaitThreads(2,TimeUnit.SECONDS),"Cancellation interrupts and joins owned workers")
        val slow=ScanResources();val stalled=CountDownLatch(1);val release=CountDownLatch(1)
        slow.start(Thread { stalled.countDown();while(release.count>0) try{release.await()}catch(_:InterruptedException){} })
        check(stalled.await(2,TimeUnit.SECONDS),"Synthetic non-cooperative worker started")
        slow.close()
        try { check(!slow.awaitThreads(20,TimeUnit.MILLISECONDS),"Cleanup has a shared bounded wait even for a non-cooperative worker") }
        finally {release.countDown();slow.awaitThreads(2,TimeUnit.SECONDS)}

        val plans=MetadataPlans(2,1)
        val first=LocalMetadata.Request(LocalMetadata.Kind.WLED,80,"/json/info")
        val decision=plans.offer("198.18.0.2",first,1)
        check(decision.offer==MetadataCandidates.Offer.START,"Metadata scheduling admits one worker")
        check(plans.offer("198.18.0.2",first.copy(port=8080),2).offer==MetadataCandidates.Offer.WAIT,"Alternate target shares the original worker")
        check(plans.offer("198.18.0.3",first,2).admission==ServiceAdmission.Result.LIMIT,"Per-method budgets retained")
        plans.close()
        check(decision.plan!!.next(3,Long.MAX_VALUE)==null,"Closing scheduler discards pending targets")
        check(plans.offer("198.18.0.4",first,4).offer==MetadataCandidates.Offer.CLOSED,"Late metadata producers cannot reopen a scan")
        val peer=java.net.InetAddress.getByName("198.18.0.2")
        val bytes="HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\nUSN: uuid:synthetic-ssdp\r\n\r\n".toByteArray()
        var now=0L;var sends=0;var emissions=0;var receives=0
        val searches=mutableListOf<String>()
        val channel=object:SsdpDiscovery.Channel {
            override fun send(bytes:ByteArray){sends++;searches.add(String(bytes))}
            override fun receive(timeoutMillis:Int):SsdpDiscovery.Packet? {
                now+=TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong());receives++
                return if(receives<=2)SsdpDiscovery.Packet(peer,bytes,bytes.size) else null
            }
        }
        val ssdp=SsdpDiscovery.discover(channel,TimeUnit.SECONDS.toNanos(55),{true},{it==peer},{now}){_,_->emissions++}
        check(sends==10 && ssdp.sent==10 && emissions==1 && ssdp.unique==1 && !ssdp.interrupted,"SSDP retries and deduplication retain the bounded recovery window")
        check(searches.count { "ST: ssdp:all\r\n" in it }==2 && searches.count { "ST: urn:dial-multiscreen-org:service:dial:1\r\n" in it }==2,
            "Broad and DIAL searches each receive two attempts without launching applications")
        now=0;sends=0;receives=0;emissions=0
        SsdpDiscovery.discover(channel,TimeUnit.SECONDS.toNanos(55),{true},{false},{now}){_,_->emissions++}
        check(emissions==0,"Out-of-scope SSDP responses cannot publish evidence")
        now=0;sends=0
        check(SsdpDiscovery.discover(channel,1,{false},{true},{now}){_,_->error("Late reply")}.interrupted && sends==0,"Cancelled SSDP sends no query")
        now=0;sends=0;receives=0;emissions=0
        val short=SsdpDiscovery.discover(channel,1,{true},{true},{now}){_,_->emissions++}
        check(short.interrupted && emissions==0,"SSDP cannot publish a response received beyond its deadline")
        now=0;var dialRequested=false;emissions=0
        val dialBytes="HTTP/1.1 200 OK\r\nST: urn:dial-multiscreen-org:service:dial:1\r\nLOCATION: http://198.18.0.2:60000/upnp/dev/00000000-0000-0000-0000-000000000001/desc\r\n\r\n".toByteArray()
        val dialOnly=object:SsdpDiscovery.Channel {
            override fun send(bytes:ByteArray) {if("ST: urn:dial-multiscreen-org:service:dial:1\r\n" in String(bytes))dialRequested=true}
            override fun receive(timeoutMillis:Int):SsdpDiscovery.Packet? {
                now+=TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
                return if(dialRequested)SsdpDiscovery.Packet(peer,dialBytes,dialBytes.size) else null
            }
        }
        val dialResult=SsdpDiscovery.discover(dialOnly,TimeUnit.SECONDS.toNanos(55),{true},{it==peer},{now}){sender,headers->
            emissions++;check(UpnpDescription.target(headers["location"],sender)!=null,"DIAL-only receiver can reach the guarded description reader")
        }
        check(dialRequested && emissions==1 && dialResult.sent==10 && now<=TimeUnit.SECONDS.toNanos(12),
            "A DIAL-only receiver is discovered and deduplicated within the bounded recovery window")
        val quiet = java.net.InetAddress.getByName("198.18.0.3")
        fun burst(count: Int, unique: Boolean = false, eligible: (java.net.InetAddress) -> Boolean = { true }): Pair<SsdpDiscovery.Result, Set<java.net.InetAddress>> {
            now=0;var n=0;val accepted=linkedSetOf<java.net.InetAddress>()
            val noisy=object:SsdpDiscovery.Channel {
                override fun send(bytes:ByteArray) {}
                override fun receive(timeoutMillis:Int):SsdpDiscovery.Packet? {
                    n++
                    now+=TimeUnit.MILLISECONDS.toNanos(if(n<=count+1) 1 else timeoutMillis.toLong())
                    if(n>count+1)return null
                    val payload=if(unique && n<=count) String(bytes).replace("synthetic-ssdp", "synthetic-$n").toByteArray() else bytes
                    return SsdpDiscovery.Packet(if(n<=count)peer else quiet,payload,payload.size)
                }
            }
            return SsdpDiscovery.discover(noisy,TimeUnit.SECONDS.toNanos(55),{true},eligible,{now}){sender,_->accepted.add(sender)} to accepted
        }
        val duplicates=burst(500)
        check(quiet in duplicates.second && duplicates.first.unique==2 && duplicates.first.sent==10,
            "One duplicate burst cannot hide another sender or prevent the second search wave")
        check(duplicates.first.limitedPeers==1 && duplicates.first.limited && duplicates.first.duplicates==SsdpDiscovery.MAX_PEER_PACKETS-1,
            "Per-peer parse loss is bounded and disclosed separately from duplicate replies")
        val records=burst(90,unique=true)
        check(records.first.unique==SsdpDiscovery.MAX_PEER_RECORDS+1 && quiet in records.second && records.first.limitedPeers==1,
            "A sender with many distinct records cannot consume every peer's admission capacity")
        val outside=burst(500,eligible={it==quiet})
        check(outside.second==setOf(quiet) && outside.first.unique==1 && !outside.first.limited,
            "Out-of-scope traffic does not consume per-peer or useful-response budgets")
        val flood=burst(SsdpDiscovery.MAX_PACKETS+1)
        check(flood.first.packets==SsdpDiscovery.MAX_PACKETS && flood.first.limited && quiet !in flood.second,
            "Sustained floods still stop at the independent raw receive ceiling")
        fun inventoryTraffic(recordsPerPeer: Int, malformed: Boolean = false): SsdpDiscovery.Result {
            now=0;var n=0
            val many=object:SsdpDiscovery.Channel {
                override fun send(bytes:ByteArray) {}
                override fun receive(timeoutMillis:Int):SsdpDiscovery.Packet? {
                    now+=TimeUnit.MILLISECONDS.toNanos(1);n++
                    val owner=(n-1)/recordsPerPeer
                    val sender=java.net.InetAddress.getByAddress(byteArrayOf(198.toByte(),18,(owner/256).toByte(),(owner%256).toByte()))
                    val payload=if(malformed) "malformed synthetic response".toByteArray()
                        else String(bytes).replace("synthetic-ssdp", "synthetic-$n").toByteArray()
                    return SsdpDiscovery.Packet(sender,payload,payload.size)
                }
            }
            return SsdpDiscovery.discover(many,TimeUnit.SECONDS.toNanos(55),{true},{true},{now}){_,_->}
        }
        val aggregate=inventoryTraffic(4)
        check(aggregate.unique==SsdpDiscovery.MAX_RECORDS && aggregate.packets==SsdpDiscovery.MAX_RECORDS && aggregate.limited,
            "Many different senders still obey the global retained-record ceiling")
        val manyPeers=inventoryTraffic(1,malformed=true)
        check(manyPeers.peers==SsdpDiscovery.MAX_PEERS && manyPeers.unique==0 && manyPeers.packets==SsdpDiscovery.MAX_PACKETS && manyPeers.limited,
            "Peer tracking and out-of-cap sender traffic remain independently bounded")
        check(aggregate.peers==100 && inventoryTraffic(1).peers==SsdpDiscovery.MAX_RECORDS,
            "Peer fairness does not reduce the previous capacity of 400 distinct useful senders")
        println("PASS · $checks scope, resource ownership, cancellation and metadata scheduling checks")
    }
}
