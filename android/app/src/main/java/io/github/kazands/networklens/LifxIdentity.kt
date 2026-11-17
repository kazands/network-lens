package io.github.kazands.networklens

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** LIFX GetService/GetVersion only. Product numbers remain numbers without a reviewed registry. */
internal object LifxIdentity {
    const val SOURCE="LIFX discovery"
    interface Channel {
        fun send(peer: InetAddress, port: Int, bytes: ByteArray)
        fun receive(timeoutMillis: Int): LanIdentity.Packet?
    }
    data class Reply(val target: ByteArray, val port: Int = 0, val vendor: Long = 0, val product: Long = 0)
    fun query(source: Int, target: ByteArray? = null): ByteArray {
        require(source != 0 && (target == null || target.size == 6))
        return ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0,36); putShort(2,if(target==null) 0x3400 else 0x1400);putInt(4,source)
            if(target!=null) {position(8);put(target)}
            put(23,if(target==null) 0 else 1);putShort(32,if(target==null) 2 else 32)
        }.array()
    }
    fun parse(packet: LanIdentity.Packet, source: Int): Reply? {
        if(packet.length !in setOf(41,48) || packet.length>packet.bytes.size) return null
        val b=ByteBuffer.wrap(packet.bytes,0,packet.length).order(ByteOrder.LITTLE_ENDIAN)
        val flags=b.getShort(2).toInt() and 65535
        if((b.getShort(0).toInt() and 65535)!=packet.length || flags and 0xcfff != 0x0400 || flags and 0x1000==0 || b.getInt(4)!=source) return null
        val target=packet.bytes.copyOfRange(8,14)
        if(target.all {it.toInt()==0} || target[0].toInt() and 1!=0 || packet.bytes[14].toInt()!=0 || packet.bytes[15].toInt()!=0) return null
        return when(b.getShort(32).toInt() and 65535) {
            3 -> {
                if(packet.length!=41 || b.get(23).toInt()!=0 || b.get(36).toInt()!=1) return null
                val port=b.getInt(37).toLong() and 0xffffffffL
                if(port !in 1..65535) null else Reply(target,port.toInt())
            }
            33 -> if(packet.length!=48 || b.get(23).toInt()!=1) null else Reply(target,
                vendor=b.getInt(36).toLong() and 0xffffffffL,product=b.getInt(40).toLong() and 0xffffffffL)
            else -> null
        }
    }
    fun discover(channel: Channel, broadcast: InetAddress, source: Int, deadline: Long, active: ()->Boolean,
                 eligible: (InetAddress)->Boolean, now: ()->Long=System::nanoTime,
                 accept: (InetAddress,LanIdentity.Report)->Unit): LanIdentity.Result {
        val start=now();val end=minOf(deadline,start+TimeUnit.SECONDS.toNanos(6))
        data class Candidate(val target: ByteArray,val port: Int,var attempts: Int=0,var last: Long=0)
        val candidates=linkedMapOf<String,Candidate>();val delivered=linkedSetOf<String>();val blocked=linkedSetOf<String>()
        var sent=0;var packets=0;var limited=false
        while(active() && now()<end) {
            if(packets>=512) {limited=true;break}
            if(sent<2 && now()>=start+TimeUnit.SECONDS.toNanos(sent.toLong())) {channel.send(broadcast,56700,query(source));sent++}
            for((ip,candidate) in candidates) if(active() && now()<end && candidate.attempts<2 && now()-candidate.last>=TimeUnit.MILLISECONDS.toNanos(500)) {
                channel.send(InetAddress.getByName(ip),candidate.port,query(source,candidate.target));candidate.attempts++;candidate.last=now()
            }
            val p=channel.receive(maxOf(1,minOf(200,TimeUnit.NANOSECONDS.toMillis(end-now()))).toInt()) ?: continue
            packets++
            if(!active() || now()>=end || !eligible(p.peer)) continue
            val r=parse(p,source) ?: continue;val ip=p.peer.hostAddress!!
            if(r.port!=0) {
                val old=candidates[ip]
                if(old!=null) {if(old.port!=r.port || !old.target.contentEquals(r.target)) {limited=true;candidates.remove(ip);blocked.add(ip)}}
                else if(ip !in delivered && ip !in blocked && candidates.size+blocked.size<128) candidates[ip]=Candidate(r.target,r.port)
                else if(ip !in delivered && ip !in blocked) limited=true
            } else {
                val candidate=candidates[ip] ?: continue
                if(candidate.attempts==0 || p.port!=candidate.port || !candidate.target.contentEquals(r.target) || !delivered.add(ip)) continue
                val metadata=LocalMetadata.Report(details=mapOf("LIFX protocol vendor ID" to r.vendor.toString(),"LIFX protocol product ID" to r.product.toString()))
                accept(p.peer,LanIdentity.Report(if(r.vendor==1L) "lifx" else "lifx_protocol",metadata))
            }
        }
        return LanIdentity.Result(packets,delivered.size,limited,!active() || deadline<start+TimeUnit.SECONDS.toNanos(6))
    }
}
