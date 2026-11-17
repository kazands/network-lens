import io.github.kazands.networklens.*
import io.github.kazands.networklens.TpLinkDiscovery as T
import java.net.InetAddress
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

object TpLinkDiscoveryTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
    private const val TX = 0x12345678
    private fun legacy(model: String = "HS103(US)", extra: String = "", error: String = "0", family: String = "IOT.SMARTPLUGSWITCH"): ByteArray =
        encode("""{"system":{"get_sysinfo":{"model":"$model","mic_type":"$family","alias":"Fixture plug","hw_ver":"5.0","sw_ver":"1.0","err_code":$error$extra}}}""")
    private fun encode(json: String): ByteArray {
        var key = 171
        return json.toByteArray().map { key = key xor (it.toInt() and 255); key.toByte() }.toByteArray()
    }
    private fun modern(model: String = "HS103(US)", error: String = "0", extra: String = ""): ByteArray = frame(
        """{"error_code":$error,"result":{"device_model":"$model","device_type":"SMART.TAPOPLUG","hw_ver":"5.0"$extra}}""")
    private fun frame(json: String): ByteArray {
        val body = json.toByteArray()
        val bytes = byteArrayOf(2,0,0,1,(body.size ushr 8).toByte(),body.size.toByte(),2,0,0x12,0x34,0x56,0x78,0,0,0,0) + body
        crc(bytes); return bytes
    }
    private fun crc(bytes: ByteArray) {
        byteArrayOf(0x5a,0x6b,0x7c,0x8d.toByte()).copyInto(bytes,12)
        val value = CRC32().apply { update(bytes) }.value
        repeat(4) { bytes[12+it] = (value ushr (24-it*8)).toByte() }
    }
    private fun packet(bytes: ByteArray, port: Int = 9999, ip: String = "192.0.2.40") = T.Packet(InetAddress.getByName(ip),port,bytes,bytes.size)
    private class Fixture : T.Channel {
        var time = 1_000_000_000L
        var active = true
        var stopOnReceive = false
        var stopOnSend = false
        var flood = false
        val packets = ArrayDeque<T.Packet>()
        val sent = ArrayList<Pair<String,Int>>()
        override fun send(target: InetAddress, port: Int, bytes: ByteArray) {
            check(bytes.contentEquals(T.query(port,TX)), "Only discovery queries are transmitted")
            sent.add(target.hostAddress!! to port)
            if (stopOnSend) active = false
        }
        override fun receive(timeoutMillis: Int): T.Packet? {
            check(timeoutMillis in 1..250, "Receive bounded")
            time += TimeUnit.MILLISECONDS.toNanos(if (packets.isEmpty() && !flood) timeoutMillis.toLong() else 1)
            if (stopOnReceive) active = false
            return if (flood) packet(byteArrayOf(0)) else packets.poll()
        }
        fun run(accept: (T.Response) -> Unit = {}, duration: Int = 10, targets: List<InetAddress> = listOf(InetAddress.getByName("192.0.2.255"))): T.Result =
            T.discover(this,targets,TX,time+TimeUnit.SECONDS.toNanos(duration.toLong()),{active},
                { it.hostAddress!!.startsWith("192.0.2.") },{time}) { _,_,response -> accept(response) }
    }
    @JvmStatic fun main(args: Array<String>) {
        val old = T.parse(packet(legacy()),TX)!!
        check(old.model == "HS103(US)" && old.name == "Fixture plug" && old.hardware == "5.0", "Legacy identity fields")
        check(T.parse(packet(modern(),20002),TX)!!.model == old.model, "Both formats keep the reported model")
        check(T.query(20002,0).joinToString("") { "%02x".format(it) } == "020000010000000000000000463cb5d3", "Reference zero-transaction query")
        check(T.parse(packet(modern(),20002),TX+1) == null, "Reject stale transaction")
        for (port in listOf(0,80,9998,20004)) check(T.parse(packet(legacy(),port),TX)==null,"Reject unqueried port")
        for (body in listOf(legacy(error="-1"),legacy(error="\"0\""),legacy(family="IOT.SMARTBULB"),legacy(model=""),legacy(model="HS103\\u0000"),
            legacy(extra=",\"model\":\"HS220\""), encode("""{"system.get_sysinfo":{"model":"HS103","mic_type":"IOT.SMARTPLUGSWITCH","err_code":0}}"""),
            encode("""{"system":{"get_sysinfo":[{"model":"HS103","mic_type":"IOT.SMARTPLUGSWITCH","err_code":0}]}}"""),
            encode("{}"), byteArrayOf(0), ByteArray(T.MAX_PACKET+1))) check(T.parse(packet(body),TX)==null,"Reject malformed/error legacy packet")
        for (index in listOf(0,1,2,3,4,5,6,7,8,12,16)) {
            val body = modern(); body[index] = (body[index].toInt() xor 1).toByte()
            check(T.parse(packet(body,20002),TX)==null,"Reject changed header, body or checksum")
        }
        for (body in listOf(modern(error="-1"),modern(error="\"0\""),modern(extra=",\"device_model\":\"HS220\""),frame("{}"),modern().copyOf(15)))
            check(T.parse(packet(body,20002),TX)==null,"Reject malformed/error modern packet")
        val secret = T.parse(packet(legacy(extra=",\"mac\":\"02:00:00:00:00:40\",\"deviceId\":\"fixture-secret\",\"owner\":\"fixture-owner\",\"ip\":\"203.0.113.40\"")),TX)!!
        check(secret.mac == "02:00:00:00:00:40" && !secret.toString().contains("fixture-secret") && !secret.toString().contains("203.0.113"),"Retain only allowlisted facts; no supplied address redirect")
        check(T.parse(packet(legacy().copyOf(legacy().size+20)).copy(length=legacy().size),TX) == old,"Parse exact datagram length")
        check(T.parse(packet(legacy()).copy(length=99999),TX)==null,"Reject impossible length")
        for (model in listOf("HS100","HS103","HS105","HS110","KP100","KP105","KP115","KP125","EP10","EP25","P100","P100M","P105","P110","P110M","P115","P125","P125M")) {
            val product = DeviceCatalog.lookup("$model(US)","TP-Link")!!
            check(product.category == DeviceCatalog.Category.OUTLET && product.displayType == "Smart plug","Reviewed plug $model")
        }
        for (model in listOf("HS220","KS220","KS220M","KS225","KS230","S500D","S505D")) {
            val product = DeviceCatalog.lookup(model,"TP-Link")!!
            check(product.category == DeviceCatalog.Category.SWITCH && product.displayType == "Dimmer switch","Reviewed dimmer $model")
        }
        for (model in listOf("HS200","HS210","KS200","KS200M","KS205","S500","S505"))
            check(DeviceCatalog.lookup(model,"TP-Link")!!.displayType == "Light switch","Reviewed switch $model")
        check(DeviceCatalog.lookup("HS103") == null && DeviceCatalog.lookup("HS103","Fixture Maker") == null,"Short codes require manufacturer or protocol corroboration")
        check(DeviceCatalog.lookup("HS103X","TP-Link") == null && DeviceCatalog.lookup("P999","TP-Link") == null,"Unknown models not guessed from prefixes")
        val evidence = DeviceCatalog.Evidence()
        evidence.offer("Model","HS103(US)",T.MODEL_SOURCE)
        evidence.offer("Model","HS103","Other current service")
        val product = evidence.match()!!.product
        check(product.title == "TP-Link Kasa HS103" && evidence.copy().match() == evidence.match(),"Protocol qualifies brand without inventing reported manufacturer")
        check(evidence.categories().isNotEmpty() && evidence.categories().all { it.category == DeviceCatalog.Category.OUTLET },"Physical plug category")
        check(DevicePresentation.labels("Fixture plug","","HS103(US)","",product,false,false).subtitle == "Smart plug · Fixture plug","Clean subtitle and custom name")
        val group = DeviceCatalog.Evidence(); group.include(evidence)
        check(group.match()?.product==evidence.match()?.product,"Grouped evidence retains corroboration")
        evidence.offer("Model","HS220",T.MODEL_SOURCE)
        check(evidence.match()==null && HardwareClassification.resolve(evidence.categories()).conflicted,"Different products/types remain conflicts")
        check(DeviceCatalog.Evidence().match()==null,"Fresh scan inherits nothing")
        val f=Fixture(); f.packets.add(packet(legacy())); f.packets.add(packet(legacy())); f.packets.add(packet(modern(),20002)); f.packets.add(packet(legacy(),9999,"203.0.113.40"))
        var accepted=0; val result=f.run({accepted++})
        check(accepted==2 && result.devices==1 && result.sent==4 && !result.limited && !result.interrupted,"One device with two complementary protocol responses, bounded retries")
        val stopped=Fixture().apply { stopOnReceive=true; packets.add(packet(legacy())) }
        check(stopped.run({throw AssertionError("Late response")}).interrupted,"Cancel before accept")
        val stoppedSend=Fixture().apply {stopOnSend=true}
        check(stoppedSend.run().sent==1,"Cancel between sends")
        val cancelled=Fixture().apply {active=false}
        check(cancelled.run().sent==0,"No work after cancellation")
        check(Fixture().run(duration=0).interrupted,"Expired deadline")
        check(Fixture().run(duration=1).interrupted,"Short scan deadline reported")
        val flood=Fixture().apply {this.flood=true}
        check(flood.run().limited && flood.time<5_000_000_000L,"Packet flood bounded and reported")
        val cap=Fixture()
        repeat(T.MAX_DEVICES+1) { cap.packets.add(packet(legacy(),9999,"192.0.2.${it+1}")) }
        check(cap.run().let {it.limited && it.devices==T.MAX_DEVICES},"Device limit reported")
        check(T.targets("192.0.2.1",24,IpRange("192.0.2.1",24)).single().hostAddress=="192.0.2.255","Full subnet directed broadcast")
        val narrow=T.targets("192.0.2.1",24,IpRange("192.0.2.16",30))
        check(narrow.map {it.hostAddress}==listOf("192.0.2.17","192.0.2.18"),"Narrow range never broadcasts outside selected scope")
        val unicast=Fixture(); check(unicast.run(targets=narrow).sent==8 && unicast.sent.all {it.first in listOf("192.0.2.17","192.0.2.18")},"Both protocols retry each selected host")
        check(T.targets("192.0.2.1",32,IpRange("192.0.2.1",32)).isEmpty(),"Self-only scan sends nothing")
        var rejected=false; try {T.targets("192.0.2.1",24,IpRange("203.0.113.1",24))} catch (_:IllegalArgumentException){rejected=true}
        check(rejected,"Reject targets outside attached subnet")
        val random=java.util.Random(103)
        repeat(1000) { val bytes=ByteArray(random.nextInt(256));random.nextBytes(bytes);T.parse(packet(bytes,if(it%2==0)9999 else 20002),TX) }
        check(true,"Malformed input does not crash")
        println("TP-Link discovery: $checks checks passed")
    }
}
