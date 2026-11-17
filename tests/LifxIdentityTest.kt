import io.github.kazands.networklens.*
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

object LifxIdentityTest {
    private var checks=0
    private fun check(v:Boolean,why:String) {checks++;if(!v)error(why)}
    private val target=byteArrayOf(2,0,0,0,0,31)
    private val peer=InetAddress.getByName("192.0.2.31")
    private fun reply(type:Int,source:Int=123,port:Int=56701):LanIdentity.Packet {
        val bytes=LifxIdentity.query(source,target).copyOf(if(type==3)41 else 48)
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(0,bytes.size.toShort());b.putShort(32,type.toShort());b.put(23,if(type==3)0 else 1)
        if(type==3) {b.put(36,1);b.putInt(37,port)} else {b.putInt(36,1);b.putInt(40,999)}
        return LanIdentity.Packet(peer,port,bytes,bytes.size)
    }
    @JvmStatic fun main(args:Array<String>) {
        check(ByteBuffer.wrap(LifxIdentity.query(123)).order(ByteOrder.LITTLE_ENDIAN).getShort(32).toInt()==2,"Only GetService broadcast")
        check(ByteBuffer.wrap(LifxIdentity.query(123,target)).order(ByteOrder.LITTLE_ENDIAN).getShort(32).toInt()==32,"Only GetVersion followup")
        check(LifxIdentity.parse(reply(3),123)!!.port==56701,"Honor advertised UDP port")
        check(LifxIdentity.parse(reply(33),123)!!.product==999L,"Read numeric product")
        check(LifxIdentity.parse(reply(33),456)==null,"Wrong scan transaction rejected")
        check(LifxIdentity.parse(reply(3,port=0),123)==null,"Invalid advertised port rejected")
        for(i in listOf(0,2,4,23,32)) {
            val r=reply(33);r.bytes[i]=(r.bytes[i].toInt() xor 1).toByte()
            check(LifxIdentity.parse(r,123)==null,"Invalid LIFX header rejected")
        }
        for(n in 0..40) check(LifxIdentity.parse(reply(3).copy(length=n),123)==null,"Truncated LIFX reply")
        var now=0L;val packets=ArrayDeque<LanIdentity.Packet>();val sends=mutableListOf<Pair<Int,Int>>();var identified=0
        val channel=object:LifxIdentity.Channel {
            override fun send(peer:InetAddress,port:Int,bytes:ByteArray) {
                val type=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(32).toInt()
                sends.add(port to type);packets.add(reply(if(type==2)3 else 33))
            }
            override fun receive(timeoutMillis:Int):LanIdentity.Packet? {now+=TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong());return packets.poll()}
        }
        val result=LifxIdentity.discover(channel,InetAddress.getByName("192.0.2.255"),123,TimeUnit.SECONDS.toNanos(10),{true},{true},{now}) {_,r->
            identified++;check(r.family=="lifx" && r.metadata.hints.isEmpty(),"Product number is not guessed retail name or hardware type")
        }
        check(identified==1 && result.devices==1 && sends.count {it.second==2}==2 && sends.filter {it.second==32}.all {it.first==56701},"Bounded discovery and same-peer advertised-port followup")
        now=0L
        val conflicts=ArrayDeque<LanIdentity.Packet>().apply {add(reply(3));add(reply(3,port=56702));add(reply(33));add(reply(3))}
        val conflictChannel=object:LifxIdentity.Channel {
            override fun send(peer:InetAddress,port:Int,bytes:ByteArray) {}
            override fun receive(timeoutMillis:Int):LanIdentity.Packet? {now+=TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong());return conflicts.poll()}
        }
        val conflictResult=LifxIdentity.discover(conflictChannel,InetAddress.getByName("192.0.2.255"),123,TimeUnit.SECONDS.toNanos(10),{true},{true},{now}) {_,_->error("Conflicting candidate must not identify")}
        check(conflictResult.limited && conflictResult.devices==0,"Conflicting candidates stay blocked and are not counted as identified devices")
        println("PASS: $checks LIFX identity checks")
    }
}
