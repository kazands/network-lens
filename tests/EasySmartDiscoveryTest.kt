import io.github.kazands.networklens.*
import java.net.InetAddress
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object EasySmartDiscoveryTest {
    private var checks=0
    private fun check(v:Boolean,why:String) {checks++;if(!v)error(why)}
    private val client=byteArrayOf(2,0,0,0,0,40)
    private val mac=byteArrayOf(2,0,0,0,0,41)
    private val peer=InetAddress.getByName("192.0.2.41")
    private fun response(extra:Boolean=false):ByteArray {
        val bytes=ByteArrayOutputStream();val out=DataOutputStream(bytes)
        fun tlv(id:Int,data:ByteArray) {out.writeShort(id);out.writeShort(data.size);out.write(data)}
        tlv(1,"TL-SG108E\u0000".toByteArray());tlv(2,"Fixture switch\u0000".toByteArray())
        tlv(3,mac);tlv(4,peer.address);tlv(7,"1.0\u0000".toByteArray())
        if(extra)tlv(512,"fixture-credential-not-retained".toByteArray())
        out.writeShort(65535);out.writeShort(0)
        val head=EasySmartDiscovery.transform(EasySmartDiscovery.query(client,123)).copyOf(32)
        val frame=head+bytes.toByteArray();frame[1]=2;mac.copyInto(frame,2);ByteBuffer.wrap(frame).putShort(20,frame.size.toShort())
        return frame
    }
    private fun parse(bytes:ByteArray,port:Int=29808)=EasySmartDiscovery.parse(LanIdentity.Packet(peer,port,EasySmartDiscovery.transform(bytes),bytes.size),client,setOf(123,124))
    @JvmStatic fun main(args:Array<String>) {
        val request=EasySmartDiscovery.transform(EasySmartDiscovery.query(client,123))
        check(request.size==36 && request[0].toInt()==1 && request[1].toInt()==0,"Discovery only, no GET, login or SET")
        check(request.sliceArray(32..35).contentEquals(byteArrayOf(-1,-1,0,0)),"No request TLVs")
        val r=parse(response())!!
        check(r.hints["Model"]=="TL-SG108E" && r.hints["Device type"]=="TP-Link Easy Smart network switch","Exact reported switch model and category")
        val evidence=DeviceCatalog.Evidence();evidence.offer("Model",r.hints.getValue("Model"),EasySmartDiscovery.MODEL_SOURCE)
        check(evidence.match()!!.product.title=="TP-Link TL-SG108E","Protocol-qualified display without fabricated reported maker")
        check(parse(response(),29809)==null,"Wrong source port rejected")
        check(parse(response(true))==null,"Credentials/configuration response rejected")
        for(i in listOf(0,1,8,14,16,20,22,24,28)) {
            val bad=response();bad[i]=(bad[i].toInt() xor 1).toByte()
            check(parse(bad)==null,"Header or request correlation rejected")
        }
        val wrong=response();wrong[2]=4
        check(parse(wrong)==null,"MAC header must agree with identity TLV")
        for(n in 0 until response().size) check(parse(response().copyOf(n))==null,"Truncated packet rejected")
        val random=java.util.Random(41)
        repeat(1000) {val b=ByteArray(random.nextInt(4200)).also(random::nextBytes);check(runCatching {parse(b)}.isSuccess,"Malformed frames cannot throw")}
        println("PASS: $checks Easy Smart identity checks")
    }
}
