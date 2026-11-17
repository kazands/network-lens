import io.github.kazands.networklens.*
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import java.security.MessageDigest

object LanIdentityTest {
    private var checks=0
    private fun check(value:Boolean,why:String) {checks++;if(!value)error(why)}
    private val peer=InetAddress.getByName("192.0.2.25")
    private fun packet(text:String,port:Int=38899)=LanIdentity.Packet(peer,port,text.toByteArray(),text.toByteArray().size)
    private val wiz="""{"method":"getSystemConfig","result":{"mac":"02:00:00:00:00:25","moduleName":"FixtureModule","fwVersion":"1.0"}}"""
    private val tuya="""{"ip":"192.0.2.25","gwId":"fixture_device_123","version":"3.3","productKey":"fixture_product"}"""
    private val key=SecretKeySpec(MessageDigest.getInstance("MD5").digest("yGAdlopoPVldABfn".toByteArray()),"AES")
    private fun frame(text:String,encrypted:Boolean):ByteArray {
        val payload=if(encrypted) Cipher.getInstance("AES/ECB/PKCS5Padding").apply {init(Cipher.ENCRYPT_MODE,key)}.doFinal(text.toByteArray()) else text.toByteArray()
        val bytes=ByteBuffer.allocate(payload.size+28).order(ByteOrder.BIG_ENDIAN)
            .putInt(0x55aa).putInt(0).putInt(if(encrypted)19 else 0).putInt(payload.size+12).putInt(0).put(payload).putInt(0).putInt(0xaa55).array()
        ByteBuffer.wrap(bytes).putInt(bytes.size-8,CRC32().apply {update(bytes,0,bytes.size-8)}.value.toInt())
        return bytes
    }
    private fun gcm(text:String):ByteArray {
        val plain=text.toByteArray();val nonce=ByteArray(12) {it.toByte()}
        val header=ByteBuffer.allocate(18).putInt(0x6699).putShort(0).putInt(1).putInt(35).putInt(plain.size+28).array()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,nonce));updateAAD(header,4,14)}
        return header+nonce+cipher.doFinal(plain)+ByteBuffer.allocate(4).putInt(0x9966).array()
    }
    @JvmStatic fun main(args:Array<String>) {
        val w=LanIdentity.parse(LanIdentity.Method.WIZ,packet(wiz))!!
        check(w.family=="wiz" && w.metadata.hints["Model"]==null,"WiZ module is not a retail model")
        check(w.metadata.hints["Device type"]==null,"WiZ ecosystem is not always a light")
        check(LanIdentity.parse(LanIdentity.Method.WIZ,packet(wiz,9999))==null,"WiZ wrong source port rejected")
        check(LanIdentity.parse(LanIdentity.Method.WIZ,packet(wiz.replace("getSystemConfig","registration")))==null,"Registration reply rejected")
        check(!String(LanIdentity.query(LanIdentity.Method.WIZ)).contains("registration"),"No WiZ state-changing registration")
        val govee="""{"msg":{"cmd":"scan","data":{"ip":"192.0.2.25","sku":"H6008","device":"fixture-not-retained"}}}"""
        check(LanIdentity.parse(LanIdentity.Method.GOVEE,packet(govee,4001))!!.metadata.hints["Model"]=="H6008","Govee reported SKU")
        check(LanIdentity.parse(LanIdentity.Method.GOVEE,packet(govee.replace("192.0.2.25","192.0.2.26"),4001))==null,"Govee cannot identify another peer")
        check(LanIdentity.parse(LanIdentity.Method.GOVEE,packet(govee.replace("scan","turn"),4001))==null,"Control reply not discovery")
        val yeelight="HTTP/1.1 200 OK\r\nLocation: yeelight://192.0.2.25:55443\r\nid: 0x001234\r\nmodel: color\r\nname: Fixture light\r\n\r\n"
        val y=LanIdentity.parse(LanIdentity.Method.YEELIGHT,packet(yeelight,1982))!!
        check(y.metadata.hints["Model"]==null && y.metadata.details.values.contains("color"),"Yeelight family is not exact retail model")
        for(location in listOf("yeelight://192.0.2.26:55443","http://192.0.2.25:55443","yeelight://user@192.0.2.25:55443","yeelight://192.0.2.25:55443/path","yeelight://fixture.invalid:55443")) {
            check(LanIdentity.parse(LanIdentity.Method.YEELIGHT,packet(yeelight.replace("yeelight://192.0.2.25:55443",location),1982))==null,"Reject unsafe/mismatched advertised endpoint")
        }
        for(method in LanIdentity.Method.values()) check(LanIdentity.parse(method,packet("x".repeat(4097)))==null,"Bounded packet")
        for(bytes in listOf(frame(tuya,false),frame(tuya,true),gcm(tuya))) {
            val result=TuyaAnnouncement.parse(bytes,peer)!!
            check(result.family=="tuya" && result.metadata.hints.isEmpty(),"Tuya is platform evidence only")
            check(result.metadata.details.values.none {it.contains("fixture_device")},"Tuya device identifier not retained")
            check(TuyaAnnouncement.parse(bytes,InetAddress.getByName("192.0.2.26"))==null,"Tuya IP must match current sender")
            for(i in listOf(0,12,bytes.size-1,bytes.size-10)) {
                val corrupt=bytes.clone();corrupt[i]=(corrupt[i].toInt() xor 1).toByte()
                check(TuyaAnnouncement.parse(corrupt,peer)==null,"Corrupt envelope/checksum/authentication rejected")
            }
            for(size in 0 until minOf(bytes.size,55)) check(TuyaAnnouncement.parse(bytes.copyOf(size),peer)==null,"Truncated frame rejected")
        }
        check(TuyaAnnouncement.parse(frame(tuya.replace("3.3","9.9"),false),peer)==null,"Unsupported protocol version rejected")
        check(TuyaAnnouncement.parse(frame(tuya.replace("192.0.2.25","host.invalid"),false),peer)==null,"No DNS resolution of announced names")
        val tv="""{"device":{"type":"Samsung SmartTV","modelName":"FixtureTV-1","name":"Fixture television","TokenAuthSupport":"true"}}"""
        check(SamsungInfo.parse(tv.toByteArray())!!.hints["Model"]=="FixtureTV-1","Samsung public identity")
        check(SamsungInfo.parse(tv.toByteArray())!!.hints["Manufacturer"]==null,"Samsung type marker does not fabricate a reported manufacturer")
        val samsung=DeviceCatalog.Evidence().apply { offer("Model","FixtureTV-1",LocalMetadata.Kind.SAMSUNG.source+" (reported)") }
        check(samsung.match()!!.product.title=="Samsung FixtureTV-1" && samsung.copy().match()==samsung.match(),"Samsung protocol brand qualifies reported model and survives copies")
        val goveeEvidence=DeviceCatalog.Evidence().apply { offer("Model","H6008",LanIdentity.Method.GOVEE.source+" (reported)") }
        check(goveeEvidence.match()!!.product.title=="Govee H6008" && goveeEvidence.categories().isEmpty(),"Govee SKU has a qualified title without guessing hardware type")
        val combined=DeviceCatalog.Evidence().apply {include(samsung);include(goveeEvidence)}
        check(combined.match()==null,"Conflicting protocol brands suppress exact title")
        check(SamsungInfo.parse(tv.replace("Samsung SmartTV","Other").toByteArray())==null,"Generic server cannot qualify Samsung identity")
        check(SamsungInfo.parse("""{"device.modelName":"FixtureTV-1","device":{"type":"Samsung SmartTV"}}""".toByteArray())==null,"Dotted key cannot impersonate nested model")
        val request=LocalMetadata.Request(LocalMetadata.Kind.SAMSUNG,8001,"/api/v2/")
        check(String(request.bytes(peer)).startsWith("GET /api/v2/ HTTP/1.1"),"Only fixed Samsung read")
        check(runCatching {LocalMetadata.Request(LocalMetadata.Kind.SAMSUNG,8001,"/api/v2/applications/x").bytes(peer)}.isFailure,"Samsung commands forbidden")
        var now=0L;var sent=0;var received=0;val queue=ArrayDeque<LanIdentity.Packet>().apply {add(packet(wiz));add(packet(wiz))}
        val channel=object:LanIdentity.Channel {
            override fun send(bytes:ByteArray) {sent++;check(bytes.contentEquals(LanIdentity.query(LanIdentity.Method.WIZ)),"Only fixed discovery packet")}
            override fun receive(timeoutMillis:Int):LanIdentity.Packet? {check(timeoutMillis in 1..200,"Bounded receive");now+=TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong());return queue.poll()}
        }
        val result=LanIdentity.discover(LanIdentity.Method.WIZ,channel,TimeUnit.SECONDS.toNanos(10),{true},{true},{now}) {_,_->received++}
        check(sent==2 && received==1 && result.devices==1 && !result.limited && !result.interrupted,"Bounded retries and duplicate suppression")
        val stopped=LanIdentity.discover(LanIdentity.Method.WIZ,channel,now+1000,{false},{true},{now}) {_,_->error("Cancelled callback")}
        check(stopped.interrupted && sent==2,"Cancellation emits no requests")
        println("PASS: $checks LAN identity checks")
    }
}
