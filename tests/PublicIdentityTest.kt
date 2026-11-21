import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.math.BigInteger
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

/** Entirely synthetic/loopback; certificates and keys are generated in RAM, never saved. */
object PublicIdentityTest {
    private val checks=AtomicInteger()
    private fun check(ok: Boolean,message: String) {checks.incrementAndGet();if(!ok) throw AssertionError(message)}
    private fun http(body: String="",headers: String="Content-Type: text/html\r\n",status: String="200 OK"): PublicHttpInfo.Report =
        PublicHttpInfo.read(ByteArrayInputStream(("HTTP/1.1 $status\r\n$headers"+"Content-Length: ${body.toByteArray().size}\r\n\r\n"+body).toByteArray()))
    @JvmStatic fun main(args: Array<String>) {
        if(args.contentEquals(arrayOf("certificate-subject-worker"))) {
            certificateSubjectStress();println("PASS · bounded certificate subject worker");return
        }
        certificateSubjects()
        val auth=http("ignored-private-body","Server: FixtureHTTP/1.0\r\nWWW-Authenticate: Digest realm=\"Fixture public console\",nonce=\"SECRET_NONCE\"\r\nSet-Cookie: session=SECRET_COOKIE\r\n","401 Unauthorized")
        check(auth.status==401 && auth.fields["HTTP authentication realm"]=="Fixture public console","Authentication-required response retains the selected realm")
        check(!auth.toString().contains("SECRET") && !auth.toString().contains("ignored-private-body"),"Cookies, challenge nonces and login bodies are not retained")
        check(http("", "Location: http://192.0.2.9/control\r\n", "302 Found").fields.isEmpty(),"Redirect destinations are never used or retained")
        check(http("<script>var fake=\"<title>Home Assistant</title><home-assistant>\";</script>").software==null,"Script literals cannot identify software")
        check(http("<html data-octoprint-version=\"1.11.0\"><title data-bind=\"text: title\">OctoPrint</title>").software==PublicHttpInfo.Software.OCTOPRINT,"OctoPrint title and version attribute identify software without a printer type")
        check(http("<title>Pi-hole fixture</title><a href=\"https://pi-hole.net\">Pi-hole</a>").software==PublicHttpInfo.Software.PI_HOLE,"Pi-hole title needs a separate software marker")
        check(http("<script>var fake=\"</scripture><title>Home Assistant</title><home-assistant>\";</script>").software==null,"Script close prefixes cannot expose fake page titles")
        val cookies=http("<title>Selected clue</title>","Content-Type: text/html\r\nServer: FixtureServer\r\nSet-Cookie: first=PRIVATE1\r\nSet-Cookie: second=PRIVATE2\r\n")
        check(cookies.fields["HTTP page title"]=="Selected clue" && cookies.fields["HTTP server label"]=="FixtureServer" && !cookies.toString().contains("PRIVATE"),"Repeated ignored cookies preserve selected clues without retaining cookies")
        val start="<title>Home Assistant</title><home-assistant></home-assistant>"
        val split=start+" ".repeat(8191-start.toByteArray().size)+"é"+"x".repeat(50)
        check(http(split).software==PublicHttpInfo.Software.HOME_ASSISTANT,"UTF-8 split at bounded prefix preserves earlier valid software clues")
        val invalid=("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 9000\r\n\r\n"+start).toByteArray()+byteArrayOf(0xff.toByte())+ByteArray(9000){32}
        check(PublicHttpInfo.read(ByteArrayInputStream(invalid)).software==null,"Malformed interior UTF-8 remains rejected")
        val title=http("<title>Fixture &amp; console</title>")
        check(title.fields["HTTP page title"]=="Fixture & console","Complete bounded title decoded")
        check(http("<!-- <title>Fake</title> --><title>Real</title>").fields["HTTP page title"]=="Real","Commented fake titles are ignored")
        check(http("<title>One</title><title>Two</title>").fields["HTTP page title"]==null,"Ambiguous titles rejected")
        check(http("<title>"+"x".repeat(600)+"</title>").fields["HTTP page title"]==null,"Overlong title is not a name")
        check(http("<title>Home Assistant</title><home-assistant></home-assistant>").software==PublicHttpInfo.Software.HOME_ASSISTANT,"Corroborated Home Assistant page identifies software")
        check(http("<title>Home Assistant</title>").software==null,"Title alone cannot identify a service")
        check(http("<title>Tasmota</title><a href=\"https://tasmota.github.io\">Documentation</a>").software==PublicHttpInfo.Software.TASMOTA,"Corroborated Tasmota firmware service")
        check(http("<title>My router</title><p>Tasmota and Pi-hole</p>").software==null,"Mentioned brands do not become identity")
        check(http("<title>Fixture</title>"+"x".repeat(20000)).prefixLimited,"Large public pages read only the bounded prefix")
        check(http("<title>Private</title>","Content-Type: application/json\r\n").fields.isEmpty(),"Unsupported media type not interpreted")
        for(wire in listOf("HTTP/1.1 200 OK\r\nServer: a\r\nServer: b\r\n\r\n","HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\n","HTTP/1.1 200 OK\r\nContent-Length: -1\r\n\r\n")) {
            try {PublicHttpInfo.read(ByteArrayInputStream(wire.toByteArray()));check(false,"Invalid framing accepted")}
            catch(_:IOException) {check(true,"Ambiguous framing rejected")}
        }
        check(PublicHttpInfo.read(ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nTransfer-Encoding: chunked\r\n\r\n10\r\n<title>x</title>\r\n0\r\n\r\n".toByteArray())).fields["HTTP page title"]=="x","Chunked root page decoded")
        val r=PublicIdentity.Request(PublicIdentity.Kind.HTTP,80);val journal=PublicIdentity.Journal()
        check(journal.queue(r,1_000_000_000) && journal.begin(r,1_500_000_000,2_000_000_000),"Lookup has an explicit queue/start boundary")
        check(journal.snapshot().single().queueWaitMs==500L && journal.snapshot().single().budgetAtStartMs==500L,"Queue wait and start budget measured independently")
        journal.finish(r,PublicIdentity.State.OBSERVED,PublicIdentity.Result("Synthetic outcome",mapOf("HTTP page title" to "Fixture")),now=1_600_000_000)
        check(journal.snapshot().single().elapsedMs==100L,"Request time excludes queued time")
        val copy=journal.copy();journal.close(true);journal.finish(r,PublicIdentity.State.FAILED)
        check(journal.snapshot()==copy.snapshot(),"Terminal observation and snapshot survive late failures")
        val request=LocalMetadata.Request(LocalMetadata.Kind.WLED,80,"/json/info")
        val metadata=MetadataLookups();metadata.queue(request,1_000_000_000);metadata.beginCandidate(request,1_500_000_000,2_000_000_000)
        check(metadata.snapshot().single().queueWaitMs==500L && metadata.snapshot().single().budgetAtStartMs==500L,"Existing metadata readers retain queue timings")
        val plan=PublicIdentity.Plan();check(plan.offer(r)==PublicIdentity.Plan.Offer.START,"One public worker starts per peer")
        check(plan.offer(r)==PublicIdentity.Plan.Offer.DUPLICATE,"Repeated advertisements do not restart a lookup")
        check(plan.next(100,1000)==r && plan.deadline()==1000L,"Public window is capped by scan deadline")
        check(plan.next(1000,5000)==null && plan.offer(PublicIdentity.Request(PublicIdentity.Kind.SSH,22))==PublicIdentity.Plan.Offer.CLOSED,"Late advertisements cannot extend the original window")
        check(PublicIdentity.candidates(listOf(4025,9999),false).isEmpty(),"No unadvertised web/SSH ports or retired alarm service probed")
        check(PublicIdentity.candidates(listOf(443,8009,22),true).size==4,"Independent public protocol opportunities retained")
        llmnr();transports();drain()
        val random=java.util.Random(34)
        repeat(2000) {
            val bytes=ByteArray(random.nextInt(512));random.nextBytes(bytes)
            try {PublicHttpInfo.read(ByteArrayInputStream(bytes))} catch(_:IOException) {}
            check(LlmnrInfo.response(bytes,"192.0.2.7",1234)==null,"Malformed response cannot name a host")
        }
        println("PASS · $checks public identity, timing and bounded transport checks")
    }
    private fun certificateSubjects() {
        fun selected(subject:String)=CertificateSubject.select(subject)
        for((subject,expected) in listOf(
            "CN=fixture.invalid,O=Synthetic fixture" to mapOf("CN" to "fixture.invalid","O" to "Synthetic fixture"),
            "CN=Fixture\\, console+O=Fixture\\+ Lab" to mapOf("CN" to "Fixture, console","O" to "Fixture+ Lab"),
            "CN=Caf\\C3\\A9,O=Fixture\\=Lab" to mapOf("CN" to "Café","O" to "Fixture=Lab"),
            "OU=private\\,CN\\=Fake,CN=fixture.invalid" to mapOf("CN" to "fixture.invalid"),
            "CN=Fixture\\\\console,O=Fixture\\20Lab" to mapOf("CN" to "Fixture\\console","O" to "Fixture Lab")
        )) check(selected(subject).let {it.values==expected && it.notice.isEmpty()},"Canonical certificate attributes and escaped separators are decoded without inventing attributes")
        check(selected("CN="+"\\\\".repeat(160)).values["CN"]=="\\".repeat(160),"Decoded subject value at the exact limit is retained")
        check(selected("CN="+"😀".repeat(80)).values["CN"]=="😀".repeat(80),"Supplementary characters respect the UTF-16 value limit")
        check(selected("CN="+"😀".repeat(81)).let {it.values.isEmpty() && it.notice.isNotEmpty()},"Supplementary characters cannot exceed the decoded value limit")
        for(subject in listOf("CN="+"\\\\".repeat(161),"O="+"x".repeat(161),"CN=#040161","CN=\\FF","CN=","CN=\uD800")) {
            check(selected(subject).let {it.values.isEmpty() && it.notice.isNotEmpty()},"Unsupported, invalid or overlong selected values are omitted explicitly")
        }
        for(subject in listOf("CN=one,CN=two,O=Fixture","CN=one+CN=one,O=Fixture","CN=one,CN=two,CN=three,O=Fixture")) {
            check(selected(subject).let {it.values==mapOf("O" to "Fixture") && it.notice.isNotEmpty()},"Duplicate selected attributes stay suppressed")
        }
        for(subject in listOf("CN=fixture\\","CN=fixture\\q","CN=fixture,","CN=fixture+","CN=fixture,O","CN=fixture;O=bad","CN=\"fixture\"")) {
            check(selected(subject).let {it.values.isEmpty() && it.notice.isNotEmpty()},"Malformed canonical subjects fail closed")
        }
        val prefix="CN=fixture.invalid,OU="
        val boundary=prefix+"x".repeat(CertificateSubject.MAX_SUBJECT-prefix.length)
        check(selected(boundary).let {it.values==mapOf("CN" to "fixture.invalid") && it.notice.isEmpty()},"Exact subject length limit is accepted")
        check(selected(boundary+"x").let {it.values.isEmpty() && it.notice=="Subject name exceeded capture limit"},"Overlong subjects are rejected whole rather than truncated into a label")
        // A separate bounded process fails safely if certificate-subject parsing ever stalls again.
        val process=ProcessBuilder(File(System.getProperty("java.home"),"bin/java").path,"-cp",System.getProperty("java.class.path"),
            "PublicIdentityTest","certificate-subject-worker").redirectErrorStream(true).start()
        try {
            check(process.waitFor(8,TimeUnit.SECONDS),"Certificate parsing finishes within the isolated process deadline")
            check(process.exitValue()==0 && process.inputStream.bufferedReader().readText().contains("PASS"),"Actual X.509 subject extraction completes with explicit omissions")
        } finally {if(process.isAlive) {process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS)}}
    }
    private fun certificateSubjectStress() {
        val escaped="\\\\".repeat(161)
        val (_,cert)=certificate("CN=$escaped,O=$escaped")
        val start=System.nanoTime()
        val fields=PublicIdentityReader.certificateFields(cert)
        check(System.nanoTime()-start<1_000_000_000,"Certificate subject selection remains bounded")
        check("TLS common name (unverified)" !in fields && "TLS organization (unverified)" !in fields &&
            fields["TLS selected fields"]?.contains("omitted")==true,"Overlong escaped CN and organization are omitted with a fixed notice")
    }
    private fun llmnr() {
        val query=LlmnrInfo.query("192.0.2.7",1234)
        check(query.size==(((query[0].toInt() and 255) shl 8) or (query[1].toInt() and 255))+2 && query[4].toInt()==0,"LLMNR has TCP framing and no DNS recursion flag")
        val dns=query.copyOfRange(2,query.size)
        dns[2]=0x80.toByte();dns[7]=1
        val name=byteArrayOf(7)+"fixture".toByteArray()+byteArrayOf(0)
        val tail=byteArrayOf(0xc0.toByte(),12,0,12,0,1,0,0,0,60,0,name.size.toByte())+name
        val response=dns+tail
        check(LlmnrInfo.response(response,"192.0.2.7",1234)=="fixture","Direct correlated reverse response accepted")
        check(LlmnrInfo.response(response,"192.0.2.8",1234)==null && LlmnrInfo.response(response,"192.0.2.7",1235)==null,"Wrong address question or transaction is rejected")
        response[2]=0x84.toByte();check(LlmnrInfo.response(response,"192.0.2.7",1234)==null,"LLMNR conflicting response rejected")
    }
    private fun transports() {
        val timer=ScheduledThreadPoolExecutor(1).apply {removeOnCancelPolicy=true}
        fun run(kind: PublicIdentity.Kind,port: Int,response: ByteArray): Pair<PublicIdentity.Result,String> {
            ServerSocket(0,1,InetAddress.getLoopbackAddress()).use {server ->
                val request=AtomicReference("");val pool=Executors.newSingleThreadExecutor()
                try {
                    val task=pool.submit {server.accept().use {peer ->peer.soTimeout=1500;if(kind==PublicIdentity.Kind.HTTP) {val bytes=ByteArrayOutputStream();while(!bytes.toString("US-ASCII").endsWith("\r\n\r\n")) {val b=peer.getInputStream().read();if(b<0)break;bytes.write(b)};request.set(bytes.toString("US-ASCII"))};peer.getOutputStream().write(response)}}
                    val socket=object:Socket() {override fun connect(endpoint:SocketAddress,timeout:Int) {check((endpoint as InetSocketAddress).port==port,"Reader uses only admitted protocol port");super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),server.localPort),timeout)}}
                    val result=socket.use {PublicIdentityReader.exchange(it,InetAddress.getLoopbackAddress(),PublicIdentity.Request(kind,port),System.nanoTime()+2_000_000_000,timer){true}}
                    task.get(3,TimeUnit.SECONDS);return result to request.get()
                } finally {pool.shutdownNow()}
            }
        }
        try {
            val (report,wire)=run(PublicIdentity.Kind.HTTP,80,"HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Fixture\"\r\nContent-Length: 99999\r\n\r\n".toByteArray())
            check(report.fields["HTTP authentication realm"]=="Fixture" && wire.startsWith("GET / HTTP/1.1\r\n"),"Actual bounded root request retains a realm without reading login body")
            check(!wire.contains("Authorization") && !wire.contains("Cookie"),"No credentials or session state transmitted")
            val ssh=run(PublicIdentity.Kind.SSH,22,"Synthetic preamble\r\nSSH-2.0-OpenSSH_9.9 comment-not-retained\r\n".toByteArray()).first
            check(ssh.fields==mapOf("SSH software label" to "OpenSSH_9.9"),"SSH keeps the software token, not comments or a guessed OS")
            ServerSocket(0,1,InetAddress.getLoopbackAddress()).use {server ->
                val pool=Executors.newSingleThreadExecutor();val roots=AtomicInteger();val observed=AtomicReference<PublicHttpInfo.Report>()
                try {
                    val task=pool.submit {server.accept().use {peer ->
                        peer.soTimeout=1500;val bytes=ByteArrayOutputStream()
                        while(!bytes.toString("US-ASCII").endsWith("\r\n\r\n")) {val b=peer.getInputStream().read();if(b<0)break;bytes.write(b)}
                        roots.incrementAndGet();peer.getOutputStream().write("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Fixture root\"\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    }}
                    val result=EapInfo.read(InetAddress.getLoopbackAddress(),System.nanoTime()+2_000_000_000,timer,{true},
                        {object:Socket() {override fun connect(endpoint:SocketAddress,timeout:Int) {super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),server.localPort),timeout)}}},{},{observed.set(it)})
                    task.get(3,TimeUnit.SECONDS)
                    check(result==null && roots.get()==1 && observed.get().fields["HTTP authentication realm"]=="Fixture root","Existing equipment root request supplies public clues without a duplicate GET or identity POST")
                } finally {pool.shutdownNow()}
            }
            val (context,cert)=certificate()
            val fields=PublicIdentityReader.certificateFields(cert)
            check(fields["TLS common name (unverified)"]=="fixture.invalid" && fields.values.none {it.contains("SERIAL")},"Only selected certificate fields retained")
            val server=context.serverSocketFactory.createServerSocket(0,1,InetAddress.getLoopbackAddress()) as SSLServerSocket
            server.use {
                val pool=Executors.newSingleThreadExecutor();val completed=AtomicBoolean()
                try {
                    val task=pool.submit {server.accept().use {peer ->try {(peer as SSLSocket).startHandshake();completed.set(true)} catch(_:IOException) {}}}
                    val socket=object:Socket() {override fun connect(endpoint:SocketAddress,timeout:Int) {super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),server.localPort),timeout)}}
                    val result=socket.use {PublicIdentityReader.exchange(it,InetAddress.getLoopbackAddress(),PublicIdentity.Request(PublicIdentity.Kind.TLS,443),System.nanoTime()+2_000_000_000,timer){true}}
                    task.get(3,TimeUnit.SECONDS)
                    check(result.fields["TLS common name (unverified)"]=="fixture.invalid" && result.outcome.contains("unverified"),"Self-signed TLS certificate can be observed without declaring trust")
                    check(!completed.get(),"Certificate observer aborts the handshake before application traffic")
                } finally {pool.shutdownNow()}
            }
            val socket=Socket()
            try {PublicIdentityReader.exchange(socket,InetAddress.getLoopbackAddress(),PublicIdentity.Request(PublicIdentity.Kind.SSH,22),System.nanoTime()+1_000_000_000,timer){false};check(false,"Cancelled request started")}
            catch(e:MetadataHttp.Failure) {check(e.reason==MetadataHttp.Reason.CANCELLED && !socket.isConnected,"Cancelled reader never connects")}
            finally {socket.close()}
        } finally {timer.shutdownNow()}
    }
    private fun drain() {
        val work=DiscoveryWork();val enter=CountDownLatch(1);val release=CountDownLatch(1);val complete=CountDownLatch(1)
        try {
            work.submit(DiscoveryWork.Lane.DETAILS,Runnable {enter.countDown();release.await();check(work.submit(DiscoveryWork.Lane.PUBLIC,"fixture",Runnable {complete.countDown()}),"Public work can be produced while upstream drains")})
            check(enter.await(2,TimeUnit.SECONDS),"Producer starts")
            work.shutdown();release.countDown()
            check(complete.await(2,TimeUnit.SECONDS) && work.awaitTermination(2,TimeUnit.SECONDS),"Public lane drains and terminates without starving metadata")
        } finally {release.countDown();work.shutdownNow()}
    }
    private fun certificate(subject:String="CN=fixture.invalid,O=Synthetic fixture"): Pair<SSLContext,X509Certificate> {
        fun tagged(tag:Int,value:ByteArray):ByteArray {
            val length=if(value.size<128) byteArrayOf(value.size.toByte()) else if(value.size<256) byteArrayOf(0x81.toByte(),value.size.toByte()) else byteArrayOf(0x82.toByte(),(value.size ushr 8).toByte(),value.size.toByte())
            return byteArrayOf(tag.toByte())+length+value
        }
        fun seq(vararg values:ByteArray)=tagged(0x30,values.fold(ByteArray(0)) {a,b->a+b})
        val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val algorithm=seq(byteArrayOf(6,9,0x2a,0x86.toByte(),0x48,0x86.toByte(),0xf7.toByte(),0x0d,1,1,11),byteArrayOf(5,0))
        val name=X500Principal(subject).encoded
        val validity=seq(tagged(0x17,"250101000000Z".toByteArray()),tagged(0x17,"350101000000Z".toByteArray()))
        val tbs=seq(tagged(0xa0,tagged(2,byteArrayOf(2))),tagged(2,BigInteger.ONE.toByteArray()),algorithm,name,validity,name,keys.public.encoded)
        val signature=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(tbs)}.sign()
        val cert=CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(seq(tbs,algorithm,tagged(3,byteArrayOf(0)+signature)))) as X509Certificate
        val password="synthetic-fixture-only".toCharArray();val store=KeyStore.getInstance("PKCS12").apply {load(null,null);setKeyEntry("fixture",keys.private,password,arrayOf(cert))}
        val km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {init(store,password)}
        return SSLContext.getInstance("TLS").apply {init(km.keyManagers,null,null)} to cert
    }
}
