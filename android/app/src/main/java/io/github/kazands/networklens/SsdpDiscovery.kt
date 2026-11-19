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
    // Two separated waves leave room for Wi-Fi power-save and multicast loss.
    // A target is a discovery query only; no application launch or control request.
    internal val targets = listOf("ssdp:all", "upnp:rootdevice", "urn:dial-multiscreen-org:service:dial:1",
        "urn:schemas-upnp-org:device:ZonePlayer:1", "urn:Belkin:device:controllee:1")
    internal fun query(target: String, ipv6: Boolean): ByteArray =
        "M-SEARCH * HTTP/1.1\r\nHOST: ${if (ipv6) "[FF02::C]" else "239.255.255.250"}:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n".toByteArray(Charsets.US_ASCII)
    fun discover(channel: Channel, deadline: Long, active: () -> Boolean, allowed: (InetAddress) -> Boolean,
                 now: () -> Long = System::nanoTime, ipv6: Boolean = false, resolved: (InetAddress, Map<String,String>) -> Unit): Result {
        val start=now();val window=start+TimeUnit.SECONDS.toNanos(12);val end=minOf(window,deadline)
        var sent=0;var packets=0;val seen=HashSet<String>()
        while(active() && now()<end && packets<MAX_PACKETS) {
            val wave = sent / targets.size
            val offset = wave * 7000L + (sent % targets.size) * 250L
            if(sent < targets.size * 2 && now()-start >= TimeUnit.MILLISECONDS.toNanos(offset)) {
                channel.send(query(targets[sent % targets.size], ipv6)); sent++
            }
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
