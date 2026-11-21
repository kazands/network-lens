package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

/** Synthetic state and loopback transports only. Never contacts another LAN endpoint. */
internal object PublicIdentityChecks {
    fun run(test: Instrumentation, check: (Boolean,String)->Unit) {
        val qemu=Runtime.getRuntime().exec(arrayOf("/system/bin/getprop","ro.kernel.qemu"))
        check(qemu.inputStream.bufferedReader().use {it.readText().trim()}=="1","Public-reader checks reject physical devices")
        val peer=Device().apply {ip="192.0.2.34";seen=true;port(8080);hostname="fixture"}
        val tasks=ArrayList<Runnable>();val health=DiscoveryHealth();var current=true;var reads=0
        val reader=PublicIdentityDiscovery(health,{current && it===peer},{System.nanoTime()+5_000_000_000},
            {_,_,task->tasks.add(task);true},{}, {_,_,_->reads++;PublicIdentity.Result("Synthetic public clues",mapOf("HTTP page title" to "Private fixture room"),PublicHttpInfo.Software.HOME_ASSISTANT)})
        current=false;reader.offer(peer)
        check(tasks.isEmpty() && peer.publicIdentity.snapshot().isEmpty(),"Candidates without current ownership never enter public work")
        current=true;reader.offer(peer);reader.offer(peer)
        check(tasks.size==1,"Repeated observations schedule a single per-peer worker")
        tasks.removeAt(0).run()
        check(reads==1 && peer.publicIdentity.snapshot().single().state==PublicIdentity.State.OBSERVED,"Current public response recorded once")
        check(peer.model.isEmpty() && peer.manufacturer.isEmpty() && peer.kind.isEmpty() && peer.links().isEmpty(),"Software clues cannot supply hardware fields or grouping links")
        check(peer.details["Discovery families"].orEmpty().contains("public_web_homeassistant"),"Corroborated web software remains a separate service family")
        check(peer.publicIdentityRows().any {it.second=="Private fixture room"},"Technical observations retain the selected clue")
        val copied=peer.copy()
        check(copied.publicIdentity.snapshot()==peer.publicIdentity.snapshot(),"RAM snapshots preserve public observations")
        val hidden=TroubleshootingReport.create("Synthetic aggregate",listOf(peer.troubleshootingGroup()),emptyList()).text()
        val explicit=TroubleshootingReport.create("Synthetic aggregate",listOf(peer.troubleshootingGroup()),emptyList(),true).text()
        check(!hidden.contains("Private fixture room") && explicit.contains("Private fixture room"),"Selected public labels require the explicit names option when sharing")
        check(hidden.contains("queue wait") && hidden.contains("budget at start") && hidden.contains("request elapsed"),"Report explains queue and request timings separately")
        check(peer.recognitionReportGroup().lookups.any {it.protocol==RecognitionReport.Protocol.HTTP},"Counts report includes public-reader outcomes without raw labels")
        check(Device().apply {ip=peer.ip}.publicIdentity.snapshot().isEmpty(),"Reused address cannot inherit prior public clues")
        ComposeDriver(test,check).use { driver ->
            driver.start();driver.fixture(listOf(peer));driver.clickTag("device:${peer.ip}")
            driver.main {check(driver.words().none {it=="Private fixture room"},"Public page title stays behind technical disclosure")}
            driver.reveal("Show technical details");driver.click("Show technical details")
            driver.main {check(driver.words().contains("Private fixture room"),"Actual Compose technical details show the retained public clue")}
            driver.reveal("Copy device details");driver.click("Copy device details")
            driver.main {
                val clipboard=driver.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val text=clipboard.primaryClip?.getItemAt(0)?.coerceToText(driver.activity).toString()
                check(text.contains("Private fixture room") && text.contains("queue wait"),"Complete device copy includes public clues and lookup timing")
            }
        }
        reader.close()
        val req=PublicIdentity.Request(PublicIdentity.Kind.HTTP,8080)
        reader.observe(peer,req,PublicIdentity.Result("Late",software=PublicHttpInfo.Software.PI_HOLE))
        check(!peer.details["Discovery families"].orEmpty().contains("public_web_pihole"),"Terminal journal prevents late duplicate interpretation")

        fun scenario(before: (Device)->Unit,read: (Device)->PublicIdentity.Result): Pair<Device,DiscoveryHealth> {
            val d=Device().apply {ip="192.0.2.35";seen=true;port(8080);hostname="fixture"};val jobs=ArrayList<Runnable>();val h=DiscoveryHealth()
            val r=PublicIdentityDiscovery(h,{true},{System.nanoTime()+5_000_000_000},{_,_,job->jobs.add(job);true},{}, {item,_,_->read(item)})
            r.offer(d);before(d);jobs.forEach {it.run()};r.close();d.publicIdentity.close(false);return d to h
        }
        val stronger=scenario({it.hint("Model","Synthetic model 34","Current device response",90)},{throw AssertionError("Unneeded public read")}).first
        check(stronger.publicIdentity.snapshot().single().state==PublicIdentity.State.NOT_NEEDED,"Stronger late model suppresses queued public work")
        val loss=Device().apply {ip="192.0.2.36";seen=true;port(8080);hostname="fixture"};val jobs=ArrayList<Runnable>();var owned=true
        val lost=PublicIdentityDiscovery(DiscoveryHealth(),{owned},{System.nanoTime()+5_000_000_000},{_,_,job->jobs.add(job);true},{}, {_,_,_->owned=false;PublicIdentity.Result("Stale",software=PublicHttpInfo.Software.PI_HOLE)})
        lost.offer(loss);jobs.single().run();lost.close();loss.publicIdentity.close(true)
        check(loss.details["Discovery families"].isNullOrEmpty() && loss.publicIdentity.snapshot().single().state==PublicIdentity.State.STOPPED,"Network or endpoint ownership loss discards late response")
        val failed=scenario({}, {throw MetadataHttp.Failure(MetadataHttp.Reason.READ_TIMEOUT)} )
        check(failed.first.publicIdentity.snapshot().single().reason==MetadataHttp.Reason.READ_TIMEOUT && failed.second.summary().isNotEmpty(),"Read failures have typed outcomes and scan health")
        val capped=PublicIdentityDiscovery(DiscoveryHealth(),{true},{Long.MAX_VALUE},{_,_,_->false},{}, {_,_,_->throw AssertionError("Rejected work ran")})
        val rejected=Device().apply {ip="192.0.2.37";seen=true;port(8080);hostname="fixture"};capped.offer(rejected)
        check(rejected.publicIdentity.snapshot().single().state==PublicIdentity.State.LIMITED,"Rejected worker records a terminal work limit")
        capped.close()
        val state=NetworkState().apply {ip="198.18.0.1";prefix=24;key="synthetic-public-ownership";addresses.add(ip);prefixes[ip]=prefix}
        val op=Discovery(test.targetContext,state)
        try {
            fun field(name:String)=Discovery::class.java.getDeclaredField(name).apply {isAccessible=true}
            field("accepting").set(op,true);field("enriching").set(op,true)
            val observe=Discovery::class.java.getDeclaredMethod("observe",String::class.java,String::class.java,Boolean::class.javaPrimitiveType).apply {isAccessible=true}
            val candidate=observe.invoke(op,"198.18.0.34","Synthetic cached candidate",false) as Device
            val valid=Discovery::class.java.getDeclaredMethod("currentPublicPeer",Device::class.java).apply {isAccessible=true}
            check(valid.invoke(op,candidate)==false,"Production gate rejects cached candidates")
            observe.invoke(op,candidate.ip,"Synthetic current response",true)
            check(valid.invoke(op,candidate)==true,"Production gate admits a current in-scope responder")
            candidate.self=true;check(valid.invoke(op,candidate)==false,"Production gate excludes this phone")
            candidate.self=false;check(valid.invoke(op,candidate.copy())==false,"Production gate rejects stale endpoint objects")
        } finally {op.cancel()}
        sharedPort80(test,check)
        transports(check)
    }
    /** Real schedulers, synthetic scope, and blocked lanes: no fixture can open a LAN socket. */
    private fun sharedPort80(test:Instrumentation,check:(Boolean,String)->Unit) {
        val net=NetworkState().apply {ip="198.18.0.1";prefix=24;key="synthetic-shared-root";addresses.add(ip);prefixes[ip]=prefix}
        val op=Discovery(test.targetContext,net)
        fun field(name:String)=Discovery::class.java.getDeclaredField(name).apply {isAccessible=true}
        field("accepting").set(op,true);field("enriching").set(op,true)
        val work=field("enrichment").get(op) as DiscoveryWork
        val pools=DiscoveryWork::class.java.getDeclaredField("pools").apply {isAccessible=true}.get(work) as Map<*,*>
        val metadata=pools[DiscoveryWork.Lane.METADATA] as ThreadPoolExecutor
        val publicPool=pools[DiscoveryWork.Lane.PUBLIC] as ThreadPoolExecutor
        val entered=CountDownLatch(4);val release=CountDownLatch(1)
        val observe=Discovery::class.java.getDeclaredMethod("observe",String::class.java,String::class.java,Boolean::class.javaPrimitiveType).apply {isAccessible=true}
        val equipment=Discovery::class.java.getDeclaredMethod("requestEapModel",Device::class.java).apply {isAccessible=true}
        val current=Discovery::class.java.getDeclaredMethod("applyCurrentService",Device::class.java,MdnsPacket.Service::class.java).apply {isAccessible=true}
        val root=PublicIdentity.Request(PublicIdentity.Kind.HTTP,80)
        fun peer(n:Int)=(observe.invoke(op,"198.18.0.$n","Synthetic WS-Discovery current responder",true) as Device).apply {hostname="fixture.local";port(80)}
        fun generic(d:Device) {current.invoke(op,d,MdnsPacket.Service("Fixture workstation","_workstation._tcp.").apply {host="fixture.local";port=9})}
        try {
            for(lane in listOf(DiscoveryWork.Lane.METADATA,DiscoveryWork.Lane.PUBLIC)) repeat(2) {
                check(work.submit(lane,Runnable {entered.countDown();try {release.await()} catch(_:InterruptedException) {}}),"Shared-root fixture blocks a real worker before any LAN operation")
            }
            check(entered.await(3,TimeUnit.SECONDS),"Shared-root fixture owns both network-reader lanes")
            for(equipmentFirst in listOf(false,true)) {
                val d=peer(if(equipmentFirst) 41 else 40);val before=metadata.taskCount
                if(equipmentFirst) equipment.invoke(op,d) else generic(d)
                val queued=d.publicIdentity.snapshot().single()
                if(equipmentFirst) generic(d) else equipment.invoke(op,d)
                check(metadata.taskCount==before+1 && publicPool.taskCount==2L,"Both arrival orders schedule exactly one equipment root owner, equipment first=$equipmentFirst")
                check(d.publicIdentity.snapshot()==listOf(queued) && queued.request==root,"The shared root keeps one unchanged queue/timing record, equipment first=$equipmentFirst")
                check(d.details[EapInfo.SOURCE]=="Waiting or checking","Generic-first admission preserves the equipment model lookup")
            }
            // Defensive coverage for a root already owned by a standalone generic reader.
            val retained=peer(42);retained.publicIdentity.queue(root,1_000_000_000);retained.publicIdentity.begin(root,1_100_000_000,2_000_000_000)
            val previous=retained.publicIdentity.snapshot();val before=metadata.taskCount
            equipment.invoke(op,retained)
            check(metadata.taskCount==before && retained.publicIdentity.snapshot()==previous,"An already-started generic root cannot gain a second equipment request or reset its timings")
            check(retained.troubleshootingGroup().endpoints.any {e->e.facts.any {it.value=="Not attempted: existing root request retained"}},"Suppressed duplicate is explained in troubleshooting evidence")
            repeat(8) {n->
                val d=peer(50+n);val count=metadata.taskCount;val start=CountDownLatch(1);val failures=java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
                val actors=listOf(Thread {try {start.await();equipment.invoke(op,d)} catch(t:Throwable) {failures.add(t)}},
                    Thread {try {start.await();generic(d)} catch(t:Throwable) {failures.add(t)}})
                actors.forEach {it.start()};start.countDown();actors.forEach {it.join(2000)}
                check(actors.none {it.isAlive} && failures.isEmpty(),"Concurrent root admission has no deadlock or failed actor")
                check(metadata.taskCount==count+1 && publicPool.taskCount==2L && d.publicIdentity.snapshot().size==1,"Concurrent equipment/public observations retain one root owner")
            }
            while(metadata.queue.remainingCapacity()>0) check(work.submit(DiscoveryWork.Lane.METADATA,Runnable {}),"Shared-root fixture fills only its isolated metadata queue")
            val rejected=peer(70);generic(rejected)
            check(rejected.publicIdentity.snapshot().single().state==PublicIdentity.State.LIMITED && publicPool.taskCount==2L,"Rejected equipment work stays an explicit limit without a duplicate public fetch")
            val admission=field("eapRequests").get(op) as ServiceAdmission
            repeat(32) {admission.admit("198.18.0.${100+it}",EapInfo.SOURCE)}
            val fallback=peer(71);generic(fallback);val pending=fallback.publicIdentity.snapshot();val submitted=metadata.taskCount
            check(pending.single().state==PublicIdentity.State.QUEUED && publicPool.taskCount==3L,"Public root fallback remains available when equipment admission is exhausted")
            equipment.invoke(op,fallback)
            check(metadata.taskCount==submitted && fallback.publicIdentity.snapshot()==pending && publicPool.taskCount==3L,"Later equipment offers cannot duplicate the capacity fallback")
            op.cancel()
            check(fallback.publicIdentity.snapshot().single().state==PublicIdentity.State.STOPPED,"Cancellation seals an admitted but unstarted fallback")
        } finally {op.cancel();release.countDown();work.awaitTermination(3,TimeUnit.SECONDS)}
    }
    private fun transports(check:(Boolean,String)->Unit) {
        val timer=ScheduledThreadPoolExecutor(1).apply {removeOnCancelPolicy=true}
        val pool=Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0,1,InetAddress.getLoopbackAddress()).use {server ->
                val job=pool.submit {server.accept().use {s->s.getOutputStream().write("SSH-2.0-Synthetic_1.0 private-comment\r\n".toByteArray())}}
                val socket=redirect(server.localPort)
                val result=socket.use {PublicIdentityReader.exchange(it,InetAddress.getLoopbackAddress(),PublicIdentity.Request(PublicIdentity.Kind.SSH,22),System.nanoTime()+2_000_000_000,timer){true}}
                job.get(3,TimeUnit.SECONDS)
                check(result.fields==mapOf("SSH software label" to "Synthetic_1.0"),"Android greeting reader receives software without comments or authentication")
            }
            val context=certificate().first;val completed=AtomicBoolean()
            (context.serverSocketFactory.createServerSocket(0,1,InetAddress.getLoopbackAddress()) as SSLServerSocket).use {server ->
                val job=pool.submit {server.accept().use {s->s.soTimeout=2000;try {(s as SSLSocket).startHandshake();completed.set(true)} catch(_:IOException) {}}}
                val result=redirect(server.localPort).use {PublicIdentityReader.exchange(it,InetAddress.getLoopbackAddress(),PublicIdentity.Request(PublicIdentity.Kind.TLS,443),System.nanoTime()+2_000_000_000,timer){true}}
                job.get(3,TimeUnit.SECONDS)
                check(result.fields["TLS common name (unverified)"]=="fixture.invalid" && !completed.get(),"Android TLS observer extracts a self-signed name and rejects the handshake")
            }
            (context.serverSocketFactory.createServerSocket(0,1,InetAddress.getLoopbackAddress()) as SSLServerSocket).use {server ->
                val job=pool.submit {server.accept().use {s->s.soTimeout=2000;try {(s as SSLSocket).startHandshake()} catch(_:IOException) {}}}
                try {
                    redirect(server.localPort).use {PublicIdentityReader.exchange(it,InetAddress.getLoopbackAddress(),PublicIdentity.Request(PublicIdentity.Kind.HTTP,443),System.nanoTime()+2_000_000_000,timer){true}}
                    check(false,"Untrusted HTTPS accepted")
                } catch(e:MetadataHttp.Failure) {check(e.reason==MetadataHttp.Reason.TLS_FAILED,"Ordinary HTTPS retains platform certificate and endpoint verification")}
                job.get(3,TimeUnit.SECONDS)
            }
            // Direct fixture extraction only: no hostile certificate is served over the network.
            val escaped="\\\\".repeat(161)
            val cert=certificate("CN=$escaped,O=$escaped").second
            val parser=Executors.newSingleThreadExecutor {r->Thread(r,"certificate-subject-check").apply {isDaemon=true}}
            try {
                val fields=parser.submit<Map<String,String>> {PublicIdentityReader.certificateFields(cert)}.get(2,TimeUnit.SECONDS)
                check("TLS common name (unverified)" !in fields && "TLS organization (unverified)" !in fields &&
                    fields["TLS selected fields"]?.contains("omitted")==true,"Android certificate subject extraction omits overlong escaped values within its deadline")
            } finally {parser.shutdownNow()}
        } finally {pool.shutdownNow();timer.shutdownNow()}
    }
    private fun redirect(port:Int)=object:Socket() {override fun connect(endpoint:SocketAddress,timeout:Int) {super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),port),timeout)}}
    /** Minimal synthetic X.509 fixture, generated in RAM; no signing credential or key file. */
    private fun certificate(subject:String="CN=fixture.invalid,O=Synthetic fixture"):Pair<SSLContext,X509Certificate> {
        fun tag(t:Int,v:ByteArray):ByteArray {val n=if(v.size<128) byteArrayOf(v.size.toByte()) else if(v.size<256) byteArrayOf(0x81.toByte(),v.size.toByte()) else byteArrayOf(0x82.toByte(),(v.size ushr 8).toByte(),v.size.toByte());return byteArrayOf(t.toByte())+n+v}
        fun seq(vararg v:ByteArray)=tag(0x30,v.fold(ByteArray(0)){a,b->a+b})
        val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val alg=seq(byteArrayOf(6,9,0x2a,0x86.toByte(),0x48,0x86.toByte(),0xf7.toByte(),0x0d,1,1,11),byteArrayOf(5,0))
        val name=X500Principal(subject).encoded
        val tbs=seq(tag(0xa0,tag(2,byteArrayOf(2))),tag(2,byteArrayOf(1)),alg,name,seq(tag(0x17,"250101000000Z".toByteArray()),tag(0x17,"350101000000Z".toByteArray())),name,keys.public.encoded)
        val sig=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(tbs)}.sign()
        val cert=CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(seq(tbs,alg,tag(3,byteArrayOf(0)+sig)))) as X509Certificate
        val pw="synthetic-fixture-only".toCharArray();val store=KeyStore.getInstance("PKCS12").apply {load(null,null);setKeyEntry("fixture",keys.private,pw,arrayOf(cert))}
        val km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {init(store,pw)}
        return SSLContext.getInstance("TLS").apply {init(km.keyManagers,null,null)} to cert
    }
}
