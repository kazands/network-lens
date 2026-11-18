package io.github.kazands.networklens

import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Bounded SSDP exchange, independent of Android sockets and device mutation. */
internal object SsdpDiscovery {
    const val SOURCE = "SSDP / UPnP"
    const val MAX_PACKETS = 400
    data class Packet(val peer: InetAddress, val bytes: ByteArray, val length: Int)
    data class Result(val sent: Int, val packets: Int, val unique: Int, val limited: Boolean, val interrupted: Boolean)
    interface Channel {
        fun send(bytes: ByteArray)
        fun receive(timeoutMillis: Int): Packet?
    }
    private val query = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n".toByteArray(Charsets.US_ASCII)
    fun discover(channel: Channel, deadline: Long, active: () -> Boolean, allowed: (InetAddress) -> Boolean,
                 now: () -> Long = System::nanoTime, resolved: (InetAddress, Map<String,String>) -> Unit): Result {
        val start=now();val window=start+TimeUnit.SECONDS.toNanos(5);val end=minOf(window,deadline)
        var sent=0;var packets=0;val seen=HashSet<String>()
        while(active() && now()<end && packets<MAX_PACKETS) {
            if(sent<2 && now()-start>=TimeUnit.MILLISECONDS.toNanos(sent*1200L)) {channel.send(query.copyOf());sent++}
            val left=end-now()
            if(left<=0)break
            val packet=channel.receive(maxOf(1,minOf(300,TimeUnit.NANOSECONDS.toMillis(left))).toInt()) ?: continue
            packets++
            if(!active() || now()>=end || !allowed(packet.peer) || packet.length !in 1..4096 || packet.length>packet.bytes.size)continue
            val headers=Ssdp.parse(packet.bytes,packet.length)
            if(headers.isEmpty())continue
            val key=packet.peer.hostAddress+"|"+headers["st"].orEmpty()+"|"+headers["usn"].orEmpty()+"|"+headers["location"].orEmpty()
            if(seen.add(key))resolved(packet.peer,headers)
        }
        return Result(sent,packets,seen.size,packets>=MAX_PACKETS,!active() || deadline<window)
    }
}
