import io.github.kazands.networklens.*
import io.github.kazands.networklens.OmadaDiscovery as O
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object OmadaDiscoveryTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
    private fun frame(json: String): ByteArray {
        val body = json.toByteArray()
        return ByteArray(4) { (body.size ushr (24-it*8)).toByte() } + body
    }
    private fun json(family: String = "ap", model: String = "EAP245", extra: String = "") =
        """{"header":{"version":"2.3.0","device":"$family","mac":"02-00-00-00-00-40","type":1},"body":{"deviceInfo":{"model":"$model","name":"Fixture equipment","firmwareVersion":"1.2.3","hardwareVersion":"3.0"$extra}}}"""
    private fun packet(bytes: ByteArray = frame(json()), ip: String = "192.0.2.40", port: Int = 43210) = O.Packet(InetAddress.getByName(ip),port,bytes,bytes.size)
    private class Fixture : O.Channel {
        var time = 1_000_000_000L; var active = true; var cancel = false; var flood = false
        val packets = ArrayDeque<O.Packet>()
        override fun receive(timeoutMillis: Int): O.Packet? {
            check(timeoutMillis in 1..250,"Receive deadline bounded")
            time += TimeUnit.MILLISECONDS.toNanos(if (packets.isEmpty() && !flood) timeoutMillis.toLong() else 1)
            if (cancel) active=false
            return if (flood) packet(byteArrayOf(0)) else packets.poll()
        }
        fun run(seconds: Long = 20, accept: (O.Response)->Unit = {}): O.Result = O.listen(this,time+TimeUnit.SECONDS.toNanos(seconds),{active},
            {it.hostAddress!!.startsWith("192.0.2.")},{time}) {_,r->accept(r)}
    }
    private fun eapWire(root: String, server: String = "TP-LINK HTTPD/1.0", status: Int = 200,
                        identity: String = """{"dutMode":"EAP245","error":0}""", expectedRequests: Int): String? {
        val listener=ServerSocket(0,4,InetAddress.getLoopbackAddress());listener.soTimeout=1500
        val requests=ArrayList<String>();var failure: Throwable?=null
        val worker=thread {
            try {
                repeat(expectedRequests) { n -> listener.accept().use { client ->
                    client.soTimeout=1000
                    val input=client.getInputStream();val head=StringBuilder()
                    while(!head.endsWith("\r\n\r\n")) {val c=input.read();require(c>=0 && head.length<2048);head.append(c.toChar())}
                    val length=Regex("Content-Length: ([0-9]+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    repeat(length) {head.append(input.read().toChar())};requests.add(head.toString())
                    val body=(if(n==0)root else identity).toByteArray()
                    client.getOutputStream().write("HTTP/1.1 $status OK\r\nServer: $server\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray()+body)
                } }
            } catch(t: Throwable){failure=t}
        }
        val timer=Executors.newSingleThreadScheduledExecutor();val sockets=ArrayList<Socket>();var released=0
        try {
            val result=EapInfo.read(InetAddress.getByName("192.0.2.40"),System.nanoTime()+TimeUnit.SECONDS.toNanos(3),timer,{true},{
                object:Socket(){override fun connect(endpoint:java.net.SocketAddress,timeout:Int){
                    check((endpoint as InetSocketAddress).port==80,"Fixed HTTP destination")
                    super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),listener.localPort),timeout)
                }}.also{sockets.add(it)}
            },{released++})
            worker.join(2000);failure?.let{throw AssertionError("Loopback fixture",it)}
            check(!worker.isAlive && requests.size==expectedRequests,"Bounded request sequence")
            check(sockets.all{it.isClosed} && released==sockets.size,"All sockets closed and unregistered")
            check(requests.first().startsWith("GET / HTTP/1.1"),"Public page first")
            if(expectedRequests==2) check(requests.last().startsWith("POST /data/login.json HTTP/1.1") && requests.last().endsWith("operation=read") &&
                requests.none{it.contains("Cookie:") || it.contains("Authorization:") || it.contains("username=") || it.contains("password=")},"Only public identity read; no authentication")
            return result
        } finally {listener.close();timer.shutdownNow();worker.join(2000)}
    }
    @JvmStatic fun main(args:Array<String>) {
        val ap=O.parse(packet())!!
        check(ap.model=="EAP245" && ap.kind=="Omada access point" && ap.mac=="02:00:00:00:00:40","AP announcement fields")
        check(O.parse(packet(frame(json("switch","SG2008"))))!!.kind=="Omada network switch","Network switch distinct from light switch")
        check(O.parse(packet(frame(json("gateway","ER605"))))!!.kind=="Omada gateway","Gateway role, not inferred firewall")
        check(O.parse(packet(frame(json().replace("2.3.0","1.0.0"))))!=null,"Plaintext v1 framing")
        for(bad in listOf(json("other"),json(model=""),json(model="EAP245\\n"),json().replace("\"type\":1","\"type\":2"),
            json().replace("\"type\":1","\"type\":\"1\""),json().replace("\"type\":1","\"type\":1,\"error\":-1"),
            json().replace("\"type\":1","\"type\":1,\"compress\":\"lzo-2.07\""),json().replace("2.3.0","9.0.0"),
            json().replace("02-00-00-00-00-40","not-a-mac"),json(extra=",\"mainMac\":\"02:00:00:00:00:41\""),
            json(extra=",\"model\":\"ER605\""),json().replace("\"deviceInfo\":{","\"deviceInfo\":[{").replace("}}}","}]}}")))
            check(O.parse(packet(frame(bad)))==null,"Reject unsupported or malformed announcement")
        val bytes=frame(json())
        for(n in bytes.indices) check(O.parse(packet(bytes.copyOf(n)))==null,"Truncated datagram rejected")
        check(O.parse(packet(bytes+0))==null && O.parse(packet(bytes).copy(length=bytes.size+1))==null,"Reject extra bytes or impossible length")
        check(O.parse(packet(bytes.copyOf(bytes.size+20)).copy(length=bytes.size))==ap,"Datagram length respected")
        check(O.parse(packet(ip="2001:db8::1"))==null,"IPv4 protocol only")
        check(O.parse(packet(port=0))==null && O.parse(packet(ByteArray(O.MAX_PACKET+1)))==null,"Port/size bounds")
        val private=O.parse(packet(frame(json(extra=",\"ip\":\"203.0.113.1\",\"serial\":\"fixture-secret\",\"controllerSetting\":{\"password\":\"fixture-secret\"}"))))!!
        check(!private.toString().contains("fixture-secret") && !private.toString().contains("203.0.113"),"No controller/serial/address retention")
        val f=Fixture(); f.packets.add(packet());f.packets.add(packet());f.packets.add(packet(frame(json(model="EAP225"))));f.packets.add(packet(ip="203.0.113.40"))
        var accepted=0;val result=f.run{accepted++}
        check(accepted==2 && result.devices==1 && !result.limited && !result.interrupted,"Dedupe identical replies but retain contradictory identities")
        check(Fixture().apply{active=false}.run{error("late")}.interrupted,"Cancellation before listen")
        check(Fixture().apply{cancel=true;packets.add(packet())}.run{error("late")}.interrupted,"Cancellation during receive")
        check(Fixture().run(0).interrupted && Fixture().run(1).interrupted,"Deadline truncation")
        check(Fixture().apply{flood=true}.run().limited,"Packet flood bounded")
        val cap=Fixture();repeat(129){cap.packets.add(packet(ip="192.0.2.${it+1}"))};check(cap.run().let{it.limited && it.devices==128},"Device bound")
        val changes=Fixture();repeat(9){changes.packets.add(packet(frame(json(model="EAP${200+it}"))))};check(changes.run().limited,"Per-device variant bound")
        val evidence=DeviceCatalog.Evidence().apply{offer("Model","EAP245",O.MODEL_SOURCE)}
        check(evidence.match()?.product?.title=="TP-Link EAP245","Protocol-qualified model title")
        check(evidence.copy().match()==evidence.match(),"Copies retain protocol corroboration")
        check(DeviceCatalog.Evidence().apply{include(evidence)}.match()==evidence.match(),"Grouping retains corroboration")
        val corroborated=DeviceCatalog.Evidence().apply {
            offer("Model","EAP245","Other current model source")
            offer("Model","EAP245",EapInfo.MODEL_SOURCE)
        }
        check(corroborated.match()!!.source.contains(EapInfo.MODEL_SOURCE),"Qualifying protocol source remains visible after earlier model")
        check(DeviceCatalog.Evidence().apply{include(corroborated)}.match()==corroborated.match(),"Grouping retains secondary protocol source")
        evidence.offer("Model","EAP225",O.MODEL_SOURCE);check(evidence.match()==null,"Conflicting models not hidden")
        check(DeviceCatalog.Evidence().apply{offer("Model","EAP245","arbitrary hostname")}.match()==null,"No model-prefix recognition without qualifying source")
        check(DeviceCatalog.Evidence().apply{offer("Model","EAP245",O.MODEL_SOURCE);offer("Manufacturer","Other","reported")}.match()==null,"Contradictory manufacturer rejected")
        check(HardwareClassification.reported("Omada network switch")==DeviceCatalog.Category.NETWORK_SWITCH,"Hardware family mapping")
        check(HardwareClassification.reported("TP-Link EAP access point")==DeviceCatalog.Category.ACCESS_POINT,"Public identity mapping")
        val page="""<span id="devname"></span><script>$.ajax({url: "./data/login.json",type:'POST',data:{"operation":"read"}});</script>"""
        check(EapInfo.eligiblePage(page.toByteArray()),"Audited public page markers")
        check(!EapInfo.eligiblePage("<!--$page-->".toByteArray()),"Comments cannot qualify a public endpoint")
        check(!EapInfo.eligiblePage(("<!--".repeat(16384)).toByteArray()),"Maximum-size repeated unclosed comments are rejected")
        check(!EapInfo.eligiblePage(("<!--$page").toByteArray()),"Unterminated comment markers cannot authorize a POST")
        check(EapInfo.eligiblePage(("<!--ignored-->$page<!--unfinished").toByteArray()),"Only visible markers before an unfinished comment qualify")
        check(EapInfo.eligiblePage(("<!--ignored-->$page<!--ignored-->").toByteArray()),"Closed comments preserve public page markers")
        check(!EapInfo.eligiblePage(("<!--".repeat(16385)).toByteArray()),"Oversized HTML remains rejected")
        check(!EapInfo.eligiblePage("<span>EAP245</span>".toByteArray()),"No HTML model guessing")
        check(EapInfo.parse("""{"dutMode":"EAP245","error":0,"serial":"fixture-secret"}""".toByteArray())=="EAP245","Allowlisted public model")
        for(json in listOf("{}","""{"dutMode":"EAP245","error":"0"}""","""{"dutMode":"EAP245","error":1}""",
            """{"dutMode":"EAP245","dutMode":"EAP225","error":0}""","""{"dutMode":"HS103","error":0}""","""{"dutMode":"EAP245\\n","error":0}"""))
            check(EapInfo.parse(json.toByteArray())==null,"Strict EAP metadata")
        check(eapWire(page,expectedRequests=2)=="EAP245","Two-stage public identity exchange")
        check(eapWire("<!--$page",expectedRequests=1)==null,"Unterminated comment cannot trigger the identity POST")
        check(eapWire(page,server="Other HTTPD/1.0",expectedRequests=1)==null,"Never POST to other server family")
        check(eapWire("<html>Other TP-Link product</html>",expectedRequests=1)==null,"Never POST without EAP markers")
        check(eapWire(page,status=302,expectedRequests=1)==null,"Never follow redirects")
        check(eapWire(page,identity="{}",expectedRequests=2)==null,"Missing model stays unknown")
        check(eapWire(page,identity="""{"dutMode":"EAP265 HD","error":0}""",expectedRequests=2)=="EAP265 HD","Reported multi-token model preserved")
        val timer=Executors.newSingleThreadScheduledExecutor()
        try {
            for(active in listOf(true,false)) {
                var stopped=false
                try {EapInfo.read(InetAddress.getByName("192.0.2.1"),0,timer,{active},{error("No socket after deadline/Stop")},{})}
                catch(e:MetadataHttp.Failure){stopped=true}
                check(stopped,"EAP deadline/Stop before opening socket")
            }
            for(cancel in listOf(false,true)) {
                val listener=ServerSocket(0,1,InetAddress.getLoopbackAddress());listener.soTimeout=1500
                val accepted=java.util.concurrent.CountDownLatch(1)
                val finish=java.util.concurrent.CountDownLatch(1)
                val active=java.util.concurrent.atomic.AtomicBoolean(true)
                val server=thread {
                    try {listener.accept().use {accepted.countDown();finish.await(2,TimeUnit.SECONDS)}}
                    finally {listener.close()}
                }
                var released=false
                val client=object:Socket(){override fun connect(endpoint:java.net.SocketAddress,timeout:Int){
                    super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),listener.localPort),timeout)
                }}
                val stopper=if(cancel) thread {
                    accepted.await(1,TimeUnit.SECONDS);active.set(false);client.close()
                } else null
                try {
                    EapInfo.read(InetAddress.getByName("192.0.2.40"),System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(200),timer,{active.get()},{client},{released=true})
                    error("Stalled endpoint must not complete")
                }catch(e:MetadataHttp.Failure){
                    check(e.reason==if(cancel)MetadataHttp.Reason.CANCELLED else MetadataHttp.Reason.DEADLINE,"Interrupted in-flight HTTP has precise outcome")
                }finally{finish.countDown();server.join(2000);stopper?.join(1000);client.close()}
                check(released && client.isClosed && !server.isAlive,"Stalled/cancelled sockets released")
            }
        }finally{timer.shutdownNow()}
        val random=java.util.Random(245);repeat(1000){val b=ByteArray(random.nextInt(2500));random.nextBytes(b);O.parse(packet(b));EapInfo.parse(b)}
        println("Omada/EAP discovery: $checks checks passed")
    }
}
