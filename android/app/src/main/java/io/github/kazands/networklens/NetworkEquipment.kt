package io.github.kazands.networklens

import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

/** Public discovery only. Original parser; wire references and limits: docs/reference/NETWORK-EQUIPMENT.md. */
internal object NetworkEquipment {
    enum class Method(val port: Int, val source: String) {
        UBIQUITI(10001, "Ubiquiti discovery"), MIKROTIK(5678, "MikroTik discovery")
    }
    data class Packet(val peer: InetAddress, val port: Int, val bytes: ByteArray, val length: Int)
    data class Report(val family: String, val model: String, val name: String, val firmware: String, val mac: String)
    data class Result(val packets: Int, val devices: Int, val limited: Boolean, val interrupted: Boolean)
    interface Channel { fun send(bytes: ByteArray); fun receive(timeout: Int): Packet? }
    private fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 255) shl 8) or (b[p+1].toInt() and 255)
    private fun text(b: ByteArray?): String? {
        if (b == null) return ""
        if (b.size !in 1..160) return null
        return try {
            val value = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
            value.takeIf { it.none { c -> c.isISOControl() || c == '\u202e' || c == '\u202d' } }?.let { SafeText.label(it,160) }
        } catch (_: Exception) { null }
    }
    fun parse(method: Method, packet: Packet): Report? {
        if (packet.peer !is Inet4Address || packet.peer.isAnyLocalAddress || packet.peer.isLoopbackAddress ||
            packet.peer.isMulticastAddress || packet.port != method.port || packet.length !in 14..4096 || packet.length > packet.bytes.size) return null
        val b=packet.bytes; val ubnt=method==Method.UBIQUITI
        if (ubnt) {
            val version=b[0].toInt() and 255; val command=b[1].toInt() and 255
            if (!(version==1 && command==0 || version==2 && command in setOf(6,9)) || u16(b,2)!=packet.length-4) return null
        } else if (b[0].toInt()!=0 || b[1].toInt()!=0) return null
        val values=HashMap<Int,ByteArray>(); var p=4; var fields=0; var matchedIp=false
        while(p<packet.length) {
            val header=if(ubnt)3 else 4
            if (++fields>64 || packet.length-p<header) return null
            val type=if(ubnt)b[p].toInt() and 255 else u16(b,p)
            val size=u16(b,p+header-2); p+=header
            if(size>packet.length-p) return null
            val value=b.copyOfRange(p,p+size);p+=size
            // UBNT address tuples may describe multiple interfaces. Only the responding one is usable.
            if(ubnt && type==2) {
                if(size!=10) return null
                if(value.copyOfRange(6,10).contentEquals(packet.peer.address)) {
                    matchedIp=true
                    val previous=values[2]
                    if(previous!=null && !previous.contentEquals(value)) return null
                    values[2]=value
                }
            } else {
                val previous=values.put(type,value)
                if(previous!=null && !previous.contentEquals(value)) return null
            }
        }
        val macBytes:ByteArray
        val model:String; val name:String; val firmware:String
        if(ubnt) {
            if(!matchedIp) return null
            macBytes=values.getValue(2).copyOfRange(0,6)
            // product_name/model/platform are distinct fields; retain the most explicit available model.
            model=(text(values[21]) ?: return null).ifEmpty { text(values[20]) ?: return null }
                .ifEmpty { text(values[12]) ?: return null }
            name=(text(values[11]) ?: return null).ifEmpty { text(values[6]) ?: return null }
            firmware=text(values[3]) ?: return null
        } else {
            macBytes=values[1] ?: return null
            if(values[17]?.contentEquals(packet.peer.address)==false || values[17]?.size?.let {it!=4}==true) return null
            if(text(values[8])!="MikroTik") return null
            model=text(values[12]) ?: return null;name=text(values[5]) ?: return null;firmware=text(values[7]) ?: return null
        }
        if(macBytes.size!=6 || macBytes.all {it.toInt()==0} || macBytes[0].toInt() and 1 != 0 || model.isEmpty()) return null
        val mac=macBytes.joinToString(":") { "%02X".format(it.toInt() and 255) }
        return Report(if(ubnt)"ubiquiti" else "mikrotik",model,name,firmware,mac)
    }
    fun discover(method: Method, channel: Channel, deadline: Long, active: () -> Boolean,
                 allowed: (InetAddress) -> Boolean, now: () -> Long = System::nanoTime,
                 accept: (InetAddress,Report) -> Unit): Result {
        val start=now();val window=start+TimeUnit.SECONDS.toNanos(12);val end=minOf(deadline,window)
        var sent=0;var packets=0;val seen=HashSet<Pair<String,Report>>();var limited=false
        while(active() && now()<end && packets<512) {
            if(method==Method.UBIQUITI && sent<4 && now()-start>=TimeUnit.MILLISECONDS.toNanos((sent/2)*7000L+(sent%2)*250L)) {
                channel.send(if(sent%2==0)byteArrayOf(1,0,0,0) else byteArrayOf(2,8,0,0));sent++
            }
            val remaining=end-now();if(remaining<=0)break
            val packet=channel.receive(maxOf(1,minOf(250,TimeUnit.NANOSECONDS.toMillis(remaining))).toInt()) ?: continue
            packets++
            if(!active() || now()>=end || !allowed(packet.peer)) continue
            val report=parse(method,packet) ?: continue
            // Keep bounded distinct reports, so contradictory current observations are not hidden.
            val key=packet.peer.hostAddress!! to report
            if(key in seen)continue
            if(seen.size>=128) {limited=true;continue}
            seen.add(key);accept(packet.peer,report)
        }
        return Result(packets,seen.size,limited || packets>=512,!active() || deadline<window)
    }
}
