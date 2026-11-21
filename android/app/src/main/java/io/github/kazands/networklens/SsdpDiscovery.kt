package io.github.kazands.networklens

import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Bounded SSDP exchange, independent of Android sockets and device mutation. */
internal object SsdpDiscovery {
    const val SOURCE = "SSDP / UPnP"
    const val MAX_PACKETS = 4096
    const val MAX_PEERS = 512
    const val MAX_PEER_PACKETS = 128
    const val MAX_PEER_RECORDS = 64
    const val MAX_RECORDS = 400
    data class Packet(val peer: InetAddress, val bytes: ByteArray, val length: Int)
    data class Result(val sent: Int, val packets: Int, val unique: Int, val limited: Boolean, val interrupted: Boolean,
                      val limitedPeers: Int = 0, val duplicates: Int = 0, val peers: Int = 0)
    private class Peer { var packets = 0; var records = 0; var limited = false }
    interface Channel {
        fun send(bytes: ByteArray)
        fun receive(timeoutMillis: Int): Packet?
    }
    // Two separated waves leave room for Wi-Fi power-save and multicast loss.
    // A target is a discovery query only; no application launch or control request.
    internal val targets = listOf("ssdp:all", "upnp:rootdevice", "urn:dial-multiscreen-org:service:dial:1",
        "urn:schemas-upnp-org:device:ZonePlayer:1", "urn:Belkin:device:controllee:1")
    internal fun query(target: String): ByteArray =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n".toByteArray(Charsets.US_ASCII)
    fun discover(channel: Channel, deadline: Long, active: () -> Boolean, allowed: (InetAddress) -> Boolean,
                 now: () -> Long = System::nanoTime, resolved: (InetAddress, Map<String,String>) -> Unit): Result {
        val start=now();val window=start+TimeUnit.SECONDS.toNanos(12);val end=minOf(window,deadline)
        var sent=0;var packets=0;var duplicates=0;var limited=false
        val seen=HashSet<String>();val peers=HashMap<InetAddress,Peer>()
        while(active() && now()<end && packets<MAX_PACKETS && seen.size<MAX_RECORDS) {
            val wave = sent / targets.size
            val offset = wave * 7000L + (sent % targets.size) * 250L
            if(sent < targets.size * 2 && now()-start >= TimeUnit.MILLISECONDS.toNanos(offset)) {
                channel.send(query(targets[sent % targets.size])); sent++
            }
            val left=end-now()
            if(left<=0)break
            val packet=channel.receive(maxOf(1,minOf(300,TimeUnit.NANOSECONDS.toMillis(left))).toInt()) ?: continue
            packets++
            if(!active() || now()>=end || packet.peer !is java.net.Inet4Address || !allowed(packet.peer) || packet.length !in 1..4096 || packet.length>packet.bytes.size)continue
            val peer=peers[packet.peer] ?: if(peers.size<MAX_PEERS) Peer().also {peers[packet.peer]=it}
                else {limited=true;continue}
            // Excess traffic from one sender must not spend other senders' parse/record budget.
            // The separate raw-packet ceiling still bounds work during a sustained flood.
            if(peer.packets>=MAX_PEER_PACKETS) {peer.limited=true;continue}
            peer.packets++
            val headers=Ssdp.parse(packet.bytes,packet.length)
            if(headers.isEmpty())continue
            val key=packet.peer.hostAddress+"|"+headers["st"].orEmpty()+"|"+headers["usn"].orEmpty()+"|"+headers["location"].orEmpty()
            if(key in seen) {duplicates++;continue}
            if(peer.records>=MAX_PEER_RECORDS) {peer.limited=true;continue}
            seen.add(key);peer.records++
            resolved(packet.peer,headers)
        }
        val limitedPeers=peers.values.count {it.limited}
        return Result(sent,packets,seen.size,limited || limitedPeers>0 || packets>=MAX_PACKETS || seen.size>=MAX_RECORDS,
            !active() || deadline<window,limitedPeers,duplicates,peers.size)
    }
}
