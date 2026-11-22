import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Synthetic audio hardware, room names and identifiers only. */
object AudioZonesTest {
    private var checks = 0
    private fun check(ok: Boolean, message: String) { checks++; if (!ok) error(message) }
    private const val A = "RINCON_02000000000101400"
    private const val B = "RINCON_02000000000201400"
    private const val C = "RINCON_02000000000301400"
    private fun frame(json: String): ByteArray = json.toByteArray().let { byteArrayOf(16, 0, (it.size+1).toByte(), 8)+it }
    private fun vssl(zone: Int = 1, model: Int = 16, chassis: String = "020000000011") = frame("""{"id":"$zone","tp":"$model","mc":"$chassis","track":"synthetic discarded"}""")
    private fun envelope(xml: String): ByteArray = ("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:GetZoneGroupStateResponse xmlns:u=\"urn:schemas-upnp-org:service:ZoneGroupTopology:1\"><ZoneGroupState>" + xml.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</ZoneGroupState></u:GetZoneGroupStateResponse></s:Body></s:Envelope>").toByteArray()
    private fun group(members: String, coordinator: String = A) = "<ZoneGroupState><ZoneGroups><ZoneGroup Coordinator=\"$coordinator\" ID=\"synthetic-group\">$members</ZoneGroup></ZoneGroups><VanishedDevices><Device UUID=\"$C\"/></VanishedDevices></ZoneGroupState>"
    private fun member(id: String, extra: String = "", child: String = "") = "<ZoneGroupMember UUID=\"$id\" ZoneName=\"Synthetic room\" Location=\"http://203.0.113.99:1400/private\" $extra>$child</ZoneGroupMember>"
    @JvmStatic fun main(args: Array<String>) {
        val report = AudioZoneInfo.vssl(ByteArrayInputStream(vssl()))!!
        check(report.model == "A.6x" && report.zone == 1 && report.kind == AudioZones.Kind.VSSL, "Direct status identifies amplifier and zone")
        check(!report.toString().contains("020000000011") && !report.toString().contains("discarded"), "No raw chassis ID or playback data retained")
        for ((code, count) in listOf(1 to 1, 3 to 3, 6 to 6, 11 to 1, 13 to 3, 16 to 6)) {
            val r = AudioZoneInfo.vssl(ByteArrayInputStream(vssl(if(count==1)7 else count, code)))!!
            check(r.zone == count && DeviceCatalog.lookup(r.model, "VSSL")?.category == DeviceCatalog.Category.AMPLIFIER, "Supported model has qualified amplifier category")
            check(AudioZoneInfo.vssl(ByteArrayInputStream(vssl(if(count==1)1 else count+1,code))) == null, "Invalid model-zone combination rejected")
        }
        for (bytes in listOf(vssl(model=99),vssl(chassis="000000000000"),vssl(chassis="synthetic"),frame("""{"id":"1","id":"2","tp":"16","mc":"020000000011"}"""), frame("""{"id":1,"tp":"16","mc":"020000000011"}"""), vssl().apply { this[3]=9 },vssl().apply { this[0]=0 }))
            check(AudioZoneInfo.vssl(ByteArrayInputStream(bytes)) == null,"Malformed or unrelated status rejected")
        var truncated=false; try { AudioZoneInfo.vssl(ByteArrayInputStream(vssl().dropLast(1).toByteArray())) } catch(_:EOFException) { truncated=true }
        check(truncated,"Truncated frame fails")
        check(DeviceCatalog.lookup("A6x", "VSSL")?.title == "VSSL A.6x" && DeviceCatalog.lookup("A6x", "Other") == null && DeviceCatalog.lookup("A6x") == null, "Aliases require actual VSSL manufacturer")
        fun advert(maker:String,type:String="_airplay._tcp.")=MdnsPacket.Service("Synthetic zone",type).apply { attributes["manufacturer"]=maker.toByteArray() }
        check(AudioZoneInfo.vsslCandidate(advert("VSSL")),"Explicit VSSL AirPlay triggers query")
        for(s in listOf(advert("VSSL impostor"),advert("VSSL","_googlecast._tcp."),advert("Other"),advert("VSSL").apply { attributes["MANUFACTURER"]="Other".toByteArray() })) check(!AudioZoneInfo.vsslCandidate(s),"Unqualified advertisements do not trigger proprietary probes")
        fun cast(model: String) = MdnsPacket.Service("Synthetic audio endpoint", "_googlecast._tcp.").apply {
            port = 8009; attributes["md"] = model.toByteArray()
        }
        for (model in listOf("A.1", "A.3", "A.6", "A.1x", "A.3x", "A.6x")) {
            for (alias in listOf("VSSL $model", "VSSL ${model.replace(".", "")}"))
                check(AudioZoneInfo.vsslCandidate(cast(alias)), "Exact brand-qualified Cast model admits a status query without inventing a manufacturer")
        }
        for (model in listOf("A6x", "A.6x", "VSSL", "VSSL A.9x", "VSSL A6x speaker", "Other VSSL A6x", "Google Cast Group", "Unknown"))
            check(!AudioZoneInfo.vsslCandidate(cast(model)), "Bare, unknown and partial Cast models cannot authorize VSSL status")
        val qualified = cast("VSSL A6x")
        check(qualified.attributes["manufacturer"] == null && DeviceCatalog.lookup("VSSL A6x") == null,
            "Candidate admission alone leaves manufacturer and catalogue identity unknown")
        for (bad in listOf(
            cast("VSSL A6x").apply { attributes["manufacturer"] = "Other".toByteArray() },
            cast("VSSL A6x").apply { attributes["manufacturer"] = "VSSL".toByteArray(); attributes["MANUFACTURER"] = "VSSL".toByteArray() },
            cast("VSSL A6x").apply { attributes["MD"] = "Other".toByteArray() },
            cast("VSSL A6x").apply { duplicateModelKey = true },
            cast("VSSL A6x").apply { attributes["ca"] = "32".toByteArray() },
            cast("VSSL A6x").apply { attributes["md"] = ("VSSL A6x" + " ".repeat(161)).toByteArray() },
            advert("VSSL", "_http._tcp.").apply { attributes["md"] = "VSSL A6x".toByteArray() },
            MdnsPacket.Service("VSSL A6x", "_googlecast._tcp.")
        )) check(!AudioZoneInfo.vsslCandidate(bad), "Conflicts, duplicate fields, groups and service names cannot qualify VSSL reads")
        val desc="<root xmlns=\"urn:schemas-upnp-org:device-1-0\"><device><manufacturer>Sonos, Inc.</manufacturer><UDN>uuid:$A</UDN></device></root>"
        val aid=AudioZoneInfo.sonosIdentity(desc.toByteArray())!!;val bid=AudioZones.token(B)
        for (bad in listOf(desc.replace("Sonos, Inc.","Other"), desc.replace("</UDN>","</UDN><UDN>uuid:$B</UDN>"), desc.replace("uuid:$A","uuid:invalid"),"<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///synthetic'>]>"+desc))
            check(AudioZoneInfo.sonosIdentity(bad.toByteArray())==null,"Ambiguous or unsafe descriptions cannot authorize topology")
        val tree=group(member(A,child="<Satellite UUID=\"$B\" ZoneName=\"Synthetic satellite\" Invisible=\"1\"/>"))
        val sonos=AudioZoneInfo.sonos(envelope(tree),aid)!!
        check(sonos.members.size==2 && sonos.members[1].parent==aid,"Bonded satellite stays a separate hardware member")
        check(!sonos.toString().contains("203.0.113") && !sonos.toString().contains(C),"Topology locations and vanished devices ignored")
        check(AudioZoneInfo.sonos(envelope(tree),AudioZones.token(C))==null,"Absent self cannot borrow another player's identity")
        for (bad in listOf(group(member(A)+member(A)), group(member(B)), tree.replace("GetZoneGroupState","Other"), "<!DOCTYPE ZoneGroupState>"+tree)) {
            if (bad==tree) continue
            check(AudioZoneInfo.sonos(envelope(bad),aid)==null,"Duplicate, missing coordinator or DTD topology rejected")
        }
        check(AudioZoneInfo.sonos(envelope(tree).toString(Charsets.UTF_8).replace("GetZoneGroupStateResponse","OtherResponse").toByteArray(),aid)==null,"Wrong SOAP action rejected")
        check(AudioZoneInfo.sonos(ByteArray(65537),aid)==null,"Oversized XML rejected")
        val v1=AudioZones.Endpoint("198.18.0.2", report); val v2=AudioZones.Endpoint("198.18.0.3",AudioZoneInfo.vssl(ByteArrayInputStream(vssl(2)))!!)
        val vOther=AudioZones.Endpoint("198.18.0.4",AudioZoneInfo.vssl(ByteArrayInputStream(vssl(3,chassis="020000000022")))!!)
        val rows=AudioZones.rows(v1,listOf(v1,v2,vOther)).toMap()
        check(rows["Other zones found on this amplifier"]=="Zone 2 · 198.18.0.3","Only same-chassis independently observed zones associated")
        check(AudioZones.rows(v1,listOf(v1,v2.copy(report=v2.report.copy(zone=1)))).toMap()["Other zones found on this amplifier"]=="None confirmed in this scan","Another address for the same zone is not a second zone")
        check(AudioZones.rows(v1,listOf(v1,v2.copy(report=v2.report.copy(model="A.3x")))).toMap().containsKey("Zone mapping"),"Conflicting chassis models suppress mapping")
        val a=AudioZones.Endpoint("198.18.0.5",sonos);val b=AudioZones.Endpoint("198.18.0.6",AudioZoneInfo.sonos(envelope(tree),bid)!!)
        check(AudioZones.rows(a,listOf(a,b)).toMap()["Other players confirmed in this group"]!!.contains(b.ip),"Mutually confirmed physical players associated")
        check(AudioZones.rows(b,listOf(a,b)).toMap()["Playback role"]=="Bonded player","Invisible satellites retain role")
        for (other in listOf(b.copy(report=b.report.copy(group="different")), b.copy(report=b.report.copy(coordinator=bid)),b.copy(report=b.report.copy(members=b.report.members.filter { it.id!=aid }))))
            check(!AudioZones.rows(a,listOf(a,other)).toString().contains(b.ip),"Disagreeing topology does not associate players")
        check(!AudioZones.rows(a,listOf(a)).toString().contains(b.ip),"Topology does not manufacture presence")
        val request=AudioZoneInfo.request(AudioZones.Kind.SONOS,InetAddress.getByName("fd00::1")).toString(Charsets.UTF_8)
        check(request.startsWith("POST /ZoneGroupTopology/Control HTTP/1.1") && request.contains("#GetZoneGroupState") && request.contains("Host: [fd00:0:0:0:0:0:0:1]:1400"),"Fixed read-only SOAP request supports IPv6")
        check(AudioZoneInfo.request(AudioZones.Kind.VSSL,InetAddress.getLoopbackAddress()).contentEquals(byteArrayOf(16,0,1,8)),"Only VSSL status command sent")
        for(kind in AudioZones.Kind.entries) {
            val bytes=if(kind==AudioZones.Kind.VSSL)vssl() else envelope(tree).let { "HTTP/1.1 200 OK\r\nContent-Length: ${it.size}\r\n\r\n".toByteArray()+it }
            ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout=3000;val error=AtomicReference<Throwable?>();val worker=thread {
                    try { server.accept().use { s ->
                        s.soTimeout=2000
                        val expected=AudioZoneInfo.request(kind,InetAddress.getLoopbackAddress());val got=ByteArray(expected.size);DataInputStream(s.getInputStream()).readFully(got)
                        check(got.contentEquals(expected),"Exchange sends only fixed query")
                        s.getOutputStream().write(bytes)
                    } } catch(e:Throwable) { error.set(e) }
                }
                val timer=ScheduledThreadPoolExecutor(1)
                try {
                    val socket=object:Socket(){override fun connect(endpoint:SocketAddress,timeout:Int){super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),server.localPort),timeout)}}
                    socket.use { val got=AudioZoneInfo.exchange(it,InetAddress.getLoopbackAddress(),kind,aid,System.nanoTime()+TimeUnit.SECONDS.toNanos(2),timer){true};check(got.kind==kind,"Loopback exchange parses current reply") }
                } finally { timer.shutdownNow();worker.join(3500) }
                check(!worker.isAlive && error.get()==null,"Exchange fixture cleans up")
            }
        }
        for(stop in listOf(false,true)) {
            val work=DiscoveryWork();val begun=CountDownLatch(1);val release=CountDownLatch(1)
            val accepted=java.util.concurrent.atomic.AtomicBoolean(false);val ran=CountDownLatch(1)
            try {
                check(work.submit(DiscoveryWork.Lane.DESCRIPTIONS) {
                    begun.countDown()
                    try { release.await() } catch(_:InterruptedException) { Thread.currentThread().interrupt() }
                    accepted.set(work.submit(DiscoveryWork.Lane.METADATA) { ran.countDown() })
                },"Admit description before closing discovery")
                check(begun.await(2,TimeUnit.SECONDS),"Description is active across drain boundary")
                if(stop) work.shutdownNow() else work.shutdown()
                release.countDown()
                check(work.awaitTermination(2,TimeUnit.SECONDS) && work.isTerminated(),"All lanes terminate after dependent work or cancellation")
                check(accepted.get()==!stop && (ran.count==0L)==!stop,"Normal drain executes dependent topology; Stop rejects it")
                check(!work.submit(DiscoveryWork.Lane.METADATA) {},"Completed work cannot accept more metadata")
            } finally {release.countDown();work.shutdownNow()}
        }
        val timer=ScheduledThreadPoolExecutor(1)
        try {
            for(active in listOf(false,true)) {
                var reason:MetadataHttp.Reason?=null
                Socket().use { socket ->
                    try { AudioZoneInfo.exchange(socket,InetAddress.getLoopbackAddress(),AudioZones.Kind.VSSL,"",System.nanoTime()-1,timer){active} }
                    catch(e:MetadataHttp.Failure){reason=e.reason}
                }
                check(reason==if(active)MetadataHttp.Reason.DEADLINE else MetadataHttp.Reason.CANCELLED,"Cancelled and expired exchanges never connect")
            }
            val socket=object:Socket(){override fun connect(endpoint:SocketAddress,timeout:Int){throw SocketTimeoutException("synthetic connection timeout")}}
            socket.use {
                var failure:MetadataHttp.Failure?=null
                try { AudioZoneInfo.exchange(it,InetAddress.getLoopbackAddress(),AudioZones.Kind.VSSL,"",System.nanoTime()+TimeUnit.SECONDS.toNanos(2),timer){true} }
                catch(e:MetadataHttp.Failure){failure=e}
                check(failure?.reason==MetadataHttp.Reason.CONNECTION_TIMEOUT && failure?.cause is SocketTimeoutException,"Connection timeout retains correct stage and original cause")
            }
        } finally { timer.shutdownNow() }
        println("PASS · $checks audio zone protocol and relationship checks")
    }
}
