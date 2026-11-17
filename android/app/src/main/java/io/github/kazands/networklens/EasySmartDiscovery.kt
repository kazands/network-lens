package io.github.kazands.networklens

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Discovery opcode 0 only, never GET/SET/login. Stream state/transform adapted from
 * rgl/ansible-collection-tp-link-easy-smart-switch (GPL-3.0), changed 2026-11-17.
 * Exact source, license and provenance: android/easysmart-original/. */
internal object EasySmartDiscovery {
    const val SOURCE="TP-Link Easy Smart discovery"
    const val MODEL_SOURCE="TP-Link Easy Smart reported model"
    private val initial=intArrayOf(191,155,227,202,99,162,79,104,49,18,190,164,30,76,189,131,23,52,86,106,207,125,126,169,196,28,172,58,188,132,160,3,36,120,144,168,12,231,116,44,41,97,108,213,42,198,32,148,218,107,247,112,204,14,66,68,91,224,206,235,33,130,203,178,1,134,199,78,249,123,7,145,73,208,209,100,74,115,72,118,8,22,243,147,64,96,5,87,60,113,233,152,31,219,143,174,232,153,245,158,254,70,170,75,77,215,211,59,71,133,214,157,151,6,46,81,94,136,166,210,4,43,241,29,223,176,67,63,186,137,129,40,248,255,55,15,62,183,222,105,236,197,127,54,179,194,229,185,37,90,237,184,25,156,173,26,187,220,2,225,0,240,50,251,212,253,167,17,193,205,177,21,181,246,82,226,38,101,163,182,242,92,20,11,95,13,230,16,121,124,109,195,117,39,98,239,84,56,139,161,47,201,51,135,250,10,19,150,45,111,27,24,142,80,85,83,234,138,216,57,93,65,154,141,122,34,140,128,238,88,89,9,146,171,149,53,102,61,114,69,217,175,103,228,35,180,252,200,192,165,159,221,244,110,119,48)
    internal fun transform(bytes:ByteArray):ByteArray {
        val state=initial.clone();var j=0
        return ByteArray(bytes.size) {at->
            val i=(at+1) and 255;j=(j+state[i]) and 255
            val tmp=state[i];state[i]=state[j];state[j]=tmp
            (bytes[at].toInt() xor state[(state[i]+state[j]) and 255]).toByte()
        }
    }
    fun query(client:ByteArray,sequence:Int):ByteArray {
        require(client.size==6 && client[0].toInt() and 1==0 && sequence in 0..65535)
        val data=ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN)
        data.put(0,1);data.position(8);data.put(client);data.putShort(14,sequence.toShort());data.putShort(20,36)
        data.putShort(32,(-1).toShort())
        return transform(data.array())
    }
    fun parse(packet:LanIdentity.Packet,client:ByteArray,sequences:Set<Int>):LocalMetadata.Report? {
        if(packet.port!=29808 || packet.length !in 36..4096 || packet.length>packet.bytes.size || client.size!=6) return null
        val data=transform(packet.bytes.copyOf(packet.length));val b=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        fun short(at:Int)=b.getShort(at).toInt() and 65535
        if(data[0].toInt()!=1 || data[1].toInt()!=2 || !data.copyOfRange(8,14).contentEquals(client) ||
            short(14) !in sequences || b.getInt(16)!=0 || short(20)!=data.size || short(22)!=0 || short(24)!=0 || b.getInt(28)!=0) return null
        val fields=linkedMapOf<Int,ByteArray>();var at=32;var ended=false
        while(at+4<=data.size) {
            val type=short(at);val length=short(at+2);at+=4
            if(type==65535) {if(length!=0 || at!=data.size)return null;ended=true;break}
            // Accept public discovery fields only; never retain authentication/configuration TLVs.
            if(type !in 1..16 || length>256 || length>data.size-at || type in fields) return null
            fields[type]=data.copyOfRange(at,at+length);at+=length
        }
        if(!ended || !fields[4].contentEquals(packet.peer.address) || !fields[3].contentEquals(data.copyOfRange(2,8))) return null
        val mac=Address.mac(data.copyOfRange(2,8).joinToString(":") {"%02x".format(it.toInt() and 255)})
        if(mac.isEmpty())return null
        fun text(type:Int):String {
            val bytes=fields[type] ?: return ""
            val end=bytes.indexOf(0).let {if(it<0)bytes.size else it}
            if(bytes.drop(end).any {it.toInt()!=0} || bytes.take(end).any {it.toInt() !in 32..126}) return ""
            return SafeText.label(String(bytes,0,end,Charsets.US_ASCII),160)
        }
        val model=text(1)
        if(!model.matches(Regex("[A-Za-z0-9][A-Za-z0-9 ._()/-]{0,95}")))return null
        return LocalMetadata.Report(mapOf("Model" to model,"Name" to text(2),"Firmware" to text(7),
            "Device type" to "TP-Link Easy Smart network switch"),mapOf("Easy Smart hardware version" to text(8)),mac)
    }
}
