import io.github.kazands.networklens.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object NetworkEquipmentTest {
    private var checks=0
    private fun check(value:Boolean,message:String) {checks++;if(!value)error(message)}
    private fun tlv(type:Int,value:ByteArray,wide:Boolean=false):ByteArray =
        (if(wide)byteArrayOf((type shr 8).toByte(),type.toByte()) else byteArrayOf(type.toByte())) +
            byteArrayOf((value.size shr 8).toByte(),value.size.toByte()) + value
    private val peer=InetAddress.getByName("198.18.0.42")
    private val mac=byteArrayOf(2,0,0,0,0,42)
    private fun ubi(extra:ByteArray=byteArrayOf(),version:Int=1,command:Int=0):ByteArray {
        val body=tlv(2,mac+peer.address)+tlv(12,"Synthetic-AP".toByteArray())+tlv(11,"Fixture equipment".toByteArray())+extra
        return byteArrayOf(version.toByte(),command.toByte(),(body.size shr 8).toByte(),body.size.toByte())+body
    }
    private fun mik(extra:ByteArray=byteArrayOf()):ByteArray = byteArrayOf(0,0,0,1)+tlv(1,mac,true)+
        tlv(8,"MikroTik".toByteArray(),true)+tlv(12,"Synthetic-board".toByteArray(),true)+tlv(17,peer.address,true)+extra
    private fun parse(method:NetworkEquipment.Method,bytes:ByteArray,port:Int=method.port,p:InetAddress=peer)=
        NetworkEquipment.parse(method,NetworkEquipment.Packet(p,port,bytes,bytes.size))
    @JvmStatic fun main(args:Array<String>) {
        val u=NetworkEquipment.Method.UBIQUITI; val m=NetworkEquipment.Method.MIKROTIK
        check(parse(u,ubi())?.model=="Synthetic-AP","UBNT platform is retained as reported model, not guessed marketing name")
        check(parse(u,ubi(version=2,command=9))?.family=="ubiquiti","V2 response supported")
        check(parse(u,ubi(version=2,command=8))==null,"Request echo is not presence")
        check(parse(u,byteArrayOf(1,0,0,0))==null,"Header-only echo rejected")
        check(parse(u,ubi(tlv(21,"Synthetic product".toByteArray())))?.model=="Synthetic product","Explicit product name wins over board")
        check(parse(u,ubi(tlv(12,"Contradiction".toByteArray())))==null,"Conflicting fields rejected")
        check(parse(u,ubi(tlv(2,byteArrayOf(2,0,0,0,0,43)+peer.address)))==null,"Conflicting interface addresses rejected")
        check(parse(u,ubi(),p=InetAddress.getByName("198.18.0.43"))==null,"Another device's IP cannot be claimed")
        check(parse(m,mik())?.mac=="02:00:00:00:00:2A","MNDP MAC and board admitted")
        check(parse(m,mik(tlv(8,"Not MikroTik".toByteArray(),true)))==null,"Contradictory platform rejected")
        check(parse(m,mik(),p=InetAddress.getByName("198.18.0.43"))==null,"MNDP IP must agree with sender when reported")
        for((method,bytes) in listOf(u to ubi(),m to mik())) {
            check(parse(method,bytes,port=1)==null,"Wrong source port rejected")
            check(parse(method,bytes,p=InetAddress.getByName("::1"))==null,"Unsupported address family rejected")
            check(parse(method,bytes+byteArrayOf(1))==null,"Incomplete trailing TLV rejected")
            check(parse(method,ByteArray(4097))==null,"Oversized datagram rejected")
            // Some complete MNDP field boundaries are valid shorter advertisements; partial TLVs cannot throw.
            for(i in bytes.indices) { parse(method,bytes.copyOf(i));checks++ }
        }
        check(parse(u,ubi(tlv(21,byteArrayOf(0xc0.toByte(),0xaf.toByte()))))==null,"Invalid UTF8 rejected")
        var clock=0L;var sends=0;var receives=0;var emitted=0
        val channel=object:NetworkEquipment.Channel {
            override fun send(bytes:ByteArray) {sends++}
            override fun receive(timeout:Int):NetworkEquipment.Packet? {
                clock+=TimeUnit.MILLISECONDS.toNanos(timeout.toLong());receives++
                val bytes=ubi()
                return NetworkEquipment.Packet(peer,u.port,bytes,bytes.size)
            }
        }
        val result=NetworkEquipment.discover(u,channel,TimeUnit.SECONDS.toNanos(55),{true},{it==peer},{clock}){_,_->emitted++}
        check(sends==4 && emitted==1 && !result.interrupted,"Two V1/V2 waves; duplicate replies do not repeat identity")
        check(clock<=TimeUnit.SECONDS.toNanos(12),"Equipment listener is bounded")
        clock=0;sends=0;emitted=0
        NetworkEquipment.discover(m,channel,TimeUnit.SECONDS.toNanos(55),{true},{true},{clock}){_,_->emitted++}
        check(sends==0 && emitted==0,"MNDP is passive and does not accept UBNT envelopes")
        clock=0;sends=0
        NetworkEquipment.discover(u,channel,1,{false},{true},{clock}){_,_->error("Late callback")}
        check(sends==0,"Cancellation cannot send probes")
        clock=0;emitted=0
        NetworkEquipment.discover(u,channel,1,{true},{true},{clock}){_,_->emitted++}
        check(emitted==0,"Receive crossing deadline cannot publish")
        clock=0;emitted=0
        NetworkEquipment.discover(u,channel,TimeUnit.SECONDS.toNanos(12),{true},{false},{clock}){_,_->emitted++}
        check(emitted==0,"Out-of-scope replies rejected")
        fun unusual(model:String,name:String):ByteArray {
            val body=tlv(2,mac+peer.address)+tlv(12,model.toByteArray())+tlv(11,name.toByteArray())
            return byteArrayOf(1,0,(body.size shr 8).toByte(),body.size.toByte())+body
        }
        val alternatives=listOf(unusual("A, name=B","C"),unusual("A","B, name=C"))
        check(parse(u,alternatives[0]).toString()==parse(u,alternatives[1]).toString(),"Fixture demonstrates ambiguous diagnostic rendering")
        clock=0;emitted=0;var next=0
        val conflicting=object:NetworkEquipment.Channel {
            override fun send(bytes:ByteArray) {}
            override fun receive(timeout:Int):NetworkEquipment.Packet? {
                clock+=TimeUnit.MILLISECONDS.toNanos(timeout.toLong())
                val bytes=alternatives.getOrNull(next++) ?: return null
                return NetworkEquipment.Packet(peer,u.port,bytes,bytes.size)
            }
        }
        NetworkEquipment.discover(u,conflicting,TimeUnit.SECONDS.toNanos(12),{true},{true},{clock}){_,_->emitted++}
        check(emitted==2,"Structured deduplication preserves contradictory reports even when text renderings collide")
        println("PASS: $checks network-equipment parser, scope and lifecycle checks")
    }
}
