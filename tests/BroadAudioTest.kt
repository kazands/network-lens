import io.github.kazands.networklens.*
import io.github.kazands.networklens.AudioPlayers.Protocol
import io.github.kazands.networklens.AudioPlayers.Relation
import io.github.kazands.networklens.AudioPlayers.Group
import io.github.kazands.networklens.AudioPlayers.Player
import io.github.kazands.networklens.AudioPlayers.Current
import io.github.kazands.networklens.AudioPlayers.Result
import java.net.*
import java.util.concurrent.*
import kotlin.concurrent.thread

/** Protocol examples use synthetic addresses, identifiers and room names only. */
object BroadAudioTest {
    private var checks = 0
    private fun check(ok: Boolean, message: String) { checks++; if (!ok) error(message) }
    private const val IP = "198.18.0.2"
    private const val OTHER = "198.18.0.3"
    private const val GROUP = "00000000-0000-4000-8000-000000000001"
    private fun read(protocol: Protocol, vararg responses: String, ip: String = IP, port: Int = when(protocol) {
        Protocol.HEOS -> 1255; Protocol.MUSICCAST -> 80; Protocol.CAST -> 8008; else -> 11000
    }): Result {
        var n=0
        return BroadAudioInfo.read(BroadAudioInfo.Plan(protocol,port),InetAddress.getByName(ip)) {
            responses.getOrElse(n++) { throw MetadataHttp.Failure(MetadataHttp.Reason.READ_TIMEOUT) }.toByteArray()
        }
    }
    private fun rejects(block: ()->Unit) { var rejected=false;try{block()}catch(_:Exception){rejected=true};check(rejected,"Unsupported response rejected") }
    private fun heosPlayers(extra: String = "", own: String = "\"ip\":\"$IP\",\"gid\":1") =
        """{"heos":{"command":"player/get_players","result":"success"},"payload":[$extra{"pid":1,$own,"name":"Synthetic lounge","model":"Synthetic receiver"}]}"""
    private fun heosGroups(gid: Int=1) = """{"heos":{"command":"group/get_groups","result":"success"},"payload":[{"gid":$gid,"name":"Synthetic group","players":[{"pid":1,"role":"leader"},{"pid":2,"role":"member"}]}]}"""
    private val info = """{"response_code":0,"model_name":"Synthetic receiver","category_code":1,"serial_number":"discard-me"}"""
    private val features = """{"response_code":0,"zone":[{"id":"main"},{"id":"zone2"}]}"""
    private fun distribution(role:String="server",clients:String="{\"ip_address\":\"$OTHER\",\"data_type\":\"base\"}") =
        """{"response_code":0,"group_id":"$GROUP","group_name":"Synthetic group","role":"$role","status":"working","client_list":[$clients]}"""
    private fun bluos(ip:String=IP,port:Int=11000,children:String="",extra:String="") =
        """<SyncStatus id="$ip:$port" name="Synthetic zone" modelName="Synthetic player" brand="Synthetic Audio" mac="020000000001" $extra>$children</SyncStatus>"""
    private fun rows(a:Player,b:Player,ipB:String=OTHER) = AudioPlayers.rows(Current(IP,a),listOf(Current(IP,a),Current(ipB,b)))
    private fun linked(a:Player,b:Player,ipB:String=OTHER) = rows(a,b,ipB).any { it.second.contains(ipB + " · port") }
    private fun service(type:String="_airplay._tcp.",port:Int=7000,vararg attrs:Pair<String,String>) = MdnsPacket.Service("Synthetic group",type).apply {
        this.port=port; attrs.forEach { (k,v)->attributes[k]=v.toByteArray() }
    }
    @JvmStatic fun main(args:Array<String>) {
        val tree=BoundedJson.document("""{"a":[{"n":1,"s":"1","b":true,"z":null}]}""".toByteArray())!!
        val item=((tree.values["a"] as BoundedJson.ArrayValue).values.single() as BoundedJson.ObjectValue).values
        check((item["n"] as BoundedJson.Scalar).number && !(item["s"] as BoundedJson.Scalar).number,"Tree preserves number versus string")
        check(item["b"] is BoundedJson.BooleanValue && item["z"] == BoundedJson.NullValue,"Tree preserves literals")
        for(bad in listOf("{\"a\":1,\"a\":2}","{\"a\":[{\"n\":1,\"n\":2}]}","[]","{} true","{\"a\":01}","{\"a\":\"\\ud800\"}"))
            check(BoundedJson.document(bad.toByteArray())==null,"Malformed JSON rejected")
        check(BoundedJson.document(byteArrayOf(123,34,-1,34,58,49,125))==null,"Invalid UTF8 rejected")
        check(BoundedJson.paths("""{"a":[{"n":"wrong"}]}""".toByteArray(),setOf(listOf("a","n")))!!.isEmpty(),"Arrays cannot impersonate paths")
        check(BoundedXml.parse("<!DOCTYPE a [<!ENTITY b SYSTEM 'file:///synthetic'>]><a>&b;</a>".toByteArray())==null,"Audio XML cannot resolve external entities")

        check(BroadAudioInfo.descriptionPlan(mapOf("deviceType" to "urn:schemas-denon-com:device:ACT-Denon:1"))?.protocol==Protocol.HEOS,"HEOS specific device type qualifies")
        check(BroadAudioInfo.descriptionPlan(mapOf("manufacturer" to "Yamaha Corporation","deviceType" to "urn:schemas-upnp-org:device:MediaRenderer:1"))?.protocol==Protocol.MUSICCAST,"Yamaha renderer qualifies")
        check(BroadAudioInfo.descriptionPlan(mapOf("manufacturer" to "Denon","deviceType" to "urn:schemas-upnp-org:device:MediaRenderer:1"))==null,"Manufacturer alone does not qualify HEOS")
        check(BroadAudioInfo.advertisementPlan(service("_musc._tcp.",11020))?.port==11020,"BluOS advertised port retained")
        check(BroadAudioInfo.advertisementPlan(service("_musp._tcp.",0))==null,"Invalid advertised port rejected")
        check(LocalServices.INITIAL.containsAll(listOf("_musc._tcp.","_musp._tcp.")),"BluOS discovery explicitly queried")

        val heos=read(Protocol.HEOS,heosPlayers("""{"pid":2,"ip":"$OTHER","model":"Wrong neighbour"},"""),heosGroups())
        check(heos.player.model=="Synthetic receiver" && heos.hints["Manufacturer"]==null,"HEOS selects self IP, not first neighbour or invented manufacturer")
        check(heos.player.groups.single().peers.size==2 && heos.note.isEmpty(),"HEOS roster decoded")
        val heosOther=read(Protocol.HEOS,heosPlayers().replace("\"pid\":1","\"pid\":2").replace(IP,OTHER),heosGroups(),ip=OTHER)
        check(linked(heos.player,heosOther.player),"HEOS fresh mutual roster associates players")
        check(!linked(heos.player,heosOther.player.copy(groups=emptyList())),"Missing reciprocal HEOS report does not associate")
        rejects{read(Protocol.HEOS,heosPlayers(own="\"gid\":1"))}
        rejects{read(Protocol.HEOS,heosPlayers("""{"pid":1,"ip":"$OTHER"},"""))}
        rejects{read(Protocol.HEOS,heosPlayers("""{"pid":2,"ip":"$IP"},"""))}
        check(read(Protocol.HEOS,heosPlayers(),heosGroups(2)).let {it.note.isNotEmpty() && it.player.groups.isEmpty() && it.player.model.isNotEmpty()},"Conflicting HEOS group keeps own model only")
        check(read(Protocol.HEOS,heosPlayers()).note.contains("unavailable"),"Missing optional response is visible")

        val yamaha=read(Protocol.MUSICCAST,info,features,distribution())
        val client=read(Protocol.MUSICCAST,info,features,distribution("client"),ip=OTHER)
        check(yamaha.player.zones==listOf("main","zone2") && yamaha.hints["Device type"]=="Yamaha audio receiver","MusicCast hardware zones and category retained")
        check(linked(yamaha.player,client.player) && linked(client.player,yamaha.player,IP),"MusicCast server list and current client agree")
        check(!linked(yamaha.player,client.player.copy(groups=client.player.groups.map {it.copy(role="Coordinator")})),"Conflicting current MusicCast coordinators suppress associations")
        check(!linked(read(Protocol.MUSICCAST,info,features,distribution(clients="")).player,client.player),"Unlisted client not associated")
        check(read(Protocol.MUSICCAST,info).let{it.player.model.isNotEmpty() && it.note.contains("Hardware zones") && it.note.contains("Distribution")},"Optional failure preserves direct MusicCast model")
        rejects{read(Protocol.MUSICCAST,info.replace("\"response_code\":0","\"response_code\":4"))}
        check(read(Protocol.MUSICCAST,info,features.replace("zone2","zone9"),distribution()).player.zones.isEmpty(),"Unsupported hardware zone rejected")
        check(yamaha.player.groups.single().zone=="main", "MusicCast documented default distribution zone")
        check(read(Protocol.MUSICCAST,info,features,distribution().replace("\"status\":", "\"server_zone\":\"zone2\",\"status\":" )).player.groups.single().zone=="zone2", "Explicit MusicCast distribution zone retained")
        check(!yamaha.toString().contains("discard-me"),"Serial numbers discarded")
        check(HardwareClassification.reported("Yamaha audio receiver")==DeviceCatalog.Category.AUDIO_RECEIVER,"MusicCast receiver class overrides audio capability")

        val primary=read(Protocol.BLUOS,bluos(children="<slave id=\"$IP\" port=\"11010\"/>"))
        val secondary=read(Protocol.BLUOS,bluos(port=11010,children="<master port=\"11000\">$IP</master>"),port=11010)
        check(primary.player.id!=secondary.player.id && linked(primary.player,secondary.player,IP),"BluOS same-IP per-port players remain distinct and related")
        check(primary.hints["Manufacturer"]=="Synthetic Audio" && primary.hints["MAC"]==null,"BluOS own maker retained without treating player MAC as interface MAC")
        rejects{read(Protocol.BLUOS,bluos(ip=OTHER))};rejects{read(Protocol.BLUOS,bluos(port=11010))}
        val ambiguous=read(Protocol.BLUOS,bluos(children="<master port=\"11000\">$OTHER</master><slave id=\"198.18.0.4\" port=\"11000\"/>"))
        check(ambiguous.note.isNotEmpty() && ambiguous.player.groups.isEmpty() && ambiguous.player.model.isNotEmpty(),"Ambiguous BluOS hierarchy keeps own identity without mapping")
        val fixed=read(Protocol.BLUOS,bluos(extra="zone=\"Synthetic stereo\" zoneMaster=\"true\""))
        check(fixed.player.groups.single().relation==Relation.HOSTED && !linked(fixed.player,fixed.player.copy(id="other")),"Fixed-group name alone cannot join hardware")
        check(!linked(primary.player,secondary.player.copy(groups=emptyList()),IP),"BluOS requires reciprocal player report")

        val ap=BroadAudioInfo.advertisement(service(attrs=arrayOf("gid" to GROUP,"igl" to "1","gpn" to "Synthetic group")),IP)!!
        val ap2=BroadAudioInfo.advertisement(service(attrs=arrayOf("gid" to GROUP,"igl" to "0")),OTHER)!!
        check(linked(ap,ap2) && rows(ap,ap2).any{it.first=="Other devices advertising this group"},"AirPlay labels advertisement evidence without claiming authenticated membership")
        check(!linked(ap,ap2.copy(groups=ap2.groups.map{it.copy(role="Coordinator")})),"Two AirPlay coordinators suppress association")
        check(BroadAudioInfo.advertisement(service(attrs=arrayOf("pi" to GROUP,"hgid" to GROUP,"psi" to GROUP)),IP)==null,"Pairing and household IDs do not establish audio groups")
        check(BroadAudioInfo.advertisement(service(attrs=arrayOf("gid" to "not-a-uuid")),IP)==null,"Unknown group identifier format stays unknown")
        check(BroadAudioInfo.advertisement(service(attrs=arrayOf("pgid" to GROUP)),IP)!!.groups.single().label=="Parent AirPlay group","Parent group remains separately labelled")
        val castAd=service("_googlecast._tcp.",32123,"ca" to "32","id" to GROUP,"fn" to "Synthetic group")
        check(BroadAudioInfo.advertisementPlan(castAd)==null,"Virtual Cast target cannot trigger physical receiver query")
        check(BroadAudioInfo.advertisement(castAd,IP)!!.groups.single().relation==Relation.HOSTED,"Cast target is descriptive only")
        val cast=read(Protocol.CAST,"""{"multizone":{"groups":[{"uuid":"$GROUP","name":"Synthetic group","elected_leader":"self","cast_port":32123}],"dynamic_groups":[]}}""")
        check(cast.hints.isEmpty() && cast.player.groups.single().role.contains("Hosted"),"Cast metadata describes hosting without supplying receiver identity")
        check(!linked(cast.player,cast.player.copy(id="other")),"Cast description does not imply physical membership")
        var calls=0
        BroadAudioInfo.read(BroadAudioInfo.Plan(Protocol.CAST,8008),InetAddress.getByName(IP)) { calls++; """{"multizone":{"groups":[{"uuid":"$GROUP","elected_leader":"198.18.0.9:9999"}]}}""".toByteArray() }
        check(calls==1,"Cast advertised leader address is never followed")
        rejects { read(Protocol.CAST,"""{"multizone":{"groups":[{"uuid":"$GROUP"},{"uuid":"$GROUP"}]}}""") }
        check(AudioWire.Query(1255,command="player/get_players").bytes(InetAddress.getByName(IP)).toString(Charsets.US_ASCII)=="heos://player/get_players\r\n","HEOS fixed read request")
        rejects {AudioWire.Query(1255,command="player/set_volume?level=1").bytes(InetAddress.getByName(IP))}
        rejects {AudioWire.Query(80,"/YamahaExtendedControl/v1/main/setPower?power=on").bytes(InetAddress.getByName(IP))}
        transport()
        println("PASS · broader audio protocols: $checks checks")
    }
    private fun transport() {
        val timer=Executors.newSingleThreadScheduledExecutor()
        try {
            for(status in listOf(200,404)) ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { server ->
                var request=""
                val worker=thread {server.accept().use { s ->
                    val input=s.getInputStream().bufferedReader();val lines=ArrayList<String>()
                    while(true){val line=input.readLine()?:break;if(line.isEmpty())break;lines.add(line)}
                    request=lines.joinToString("\n")
                    s.getOutputStream().write("HTTP/1.1 $status Test\r\nContent-Length: 2\r\n\r\n{}".toByteArray())
                }}
                Socket().use { socket ->
                    if(status==200) check(AudioWire.exchange(socket,InetAddress.getLoopbackAddress(),AudioWire.Query(server.localPort,"/SyncStatus"),System.nanoTime()+TimeUnit.SECONDS.toNanos(2),timer){true}.contentEquals("{}".toByteArray()),"Fixed HTTP response decoded")
                    else try {AudioWire.exchange(socket,InetAddress.getLoopbackAddress(),AudioWire.Query(server.localPort,"/SyncStatus"),System.nanoTime()+TimeUnit.SECONDS.toNanos(2),timer){true};error("Expected HTTP failure")}
                    catch(e:MetadataHttp.Failure){check(e.reason==MetadataHttp.Reason.HTTP_STATUS,"HTTP error classified")}
                }
                worker.join(2000)
                check(request.startsWith("GET /SyncStatus HTTP/1.1") && !request.contains("Authorization"),"Read-only request has no credentials")
            }
            ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { server ->
                val worker=thread{server.accept().use {s->s.getInputStream().read();Thread.sleep(250)}}
                Socket().use {socket->try {AudioWire.exchange(socket,InetAddress.getLoopbackAddress(),AudioWire.Query(server.localPort,"/SyncStatus"),System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(100),timer){true};error("Expected deadline")}
                    catch(e:MetadataHttp.Failure){check(e.reason==MetadataHttp.Reason.DEADLINE,"Whole lookup deadline closes blocked exchange")}}
                worker.join(2000)
            }
            Socket().use {socket->try {AudioWire.exchange(socket,InetAddress.getLoopbackAddress(),AudioWire.Query(1,"/SyncStatus"),System.nanoTime()+TimeUnit.SECONDS.toNanos(2),timer){false};error("Expected cancellation")}
                catch(e:MetadataHttp.Failure){check(e.reason==MetadataHttp.Reason.CANCELLED && !socket.isConnected,"Cancellation prevents connection")}}
        } finally {timer.shutdownNow()}
    }
}
