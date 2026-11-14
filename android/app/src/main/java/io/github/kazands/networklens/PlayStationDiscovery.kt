package io.github.kazands.networklens

import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Read-only PS5 discovery. No registration, credentials, wakeup or console control. */
internal object PlayStationDiscovery {
    const val SOURCE = "PlayStation discovery"
    const val PORT = 9302
    const val MAX_PACKET = 4096
    const val MAX_PACKETS = 64
    const val MAX_DEVICES = 32
    private const val VERSION = "00030010"
    data class Response(val name: String, val standby: Boolean)
    data class Packet(val peer: InetAddress, val port: Int, val bytes: ByteArray, val length: Int)
    data class Result(val sent: Int, val packets: Int, val devices: Int, val limited: Boolean, val interrupted: Boolean)
    interface Channel {
        fun send(bytes: ByteArray)
        /** Null means a bounded receive timeout. */
        fun receive(timeoutMillis: Int): Packet?
    }
    fun query(): ByteArray = "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:$VERSION\n".toByteArray(StandardCharsets.US_ASCII)
    fun parse(bytes: ByteArray, length: Int): Response? {
        if (length !in 12..MAX_PACKET || length > bytes.size) return null
        val lines = String(bytes, 0, length, StandardCharsets.UTF_8).javaSplit("\r?\n", 66)
        if (lines.size > 65) return null
        val status = Regex("HTTP/1\\.1 (200|620)(?: [\\x20-\\x7e]{1,80})?").matchEntire(lines[0]) ?: return null
        val fields = HashMap<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            if (line.length > 512) return null
            val split = line.indexOf(':')
            if (split < 1) return null
            val key = line.substring(0, split).lowercase(Locale.ROOT)
            if (!key.matches(Regex("[a-z0-9-]{1,64}"))) return null
            if (key !in listOf("host-type", "device-discovery-protocol-version", "host-name")) continue
            if (fields.containsKey(key)) return null
            fields[key] = line.substring(split + 1).trim(' ', '\t')
        }
        if (fields["host-type"] != "PS5" || fields["device-discovery-protocol-version"] != VERSION) return null
        val name = SafeText.label(fields["host-name"], 128)
        return Response(name, status.groupValues[1] == "620")
    }
    fun discover(channel: Channel, deadline: Long, active: () -> Boolean, allowed: (InetAddress) -> Boolean,
                 now: () -> Long = System::nanoTime, accept: (InetAddress, Response) -> Unit): Result {
        val start = now()
        val end = minOf(deadline, start + TimeUnit.SECONDS.toNanos(2))
        var sent = 0; var packets = 0; var limited = false
        val seen = HashSet<String>()
        while (active() && now() < end && packets < MAX_PACKETS) {
            if (sent < 2 && now() - start >= TimeUnit.MILLISECONDS.toNanos(sent * 750L)) {
                if (!active() || now() >= end) break
                channel.send(query()); sent++
            }
            val left = end - now()
            if (left <= 0 || !active()) break
            val packet = channel.receive(maxOf(1, minOf(250, TimeUnit.NANOSECONDS.toMillis(left))).toInt()) ?: continue
            packets++
            if (!active() || now() >= end) break
            if (packet.port != PORT || !allowed(packet.peer)) continue
            val response = parse(packet.bytes, packet.length) ?: continue
            val key = packet.peer.hostAddress ?: continue
            if (key in seen) continue
            if (seen.size == MAX_DEVICES) { limited = true; break }
            seen.add(key)
            if (active() && now() < end) accept(packet.peer, response)
        }
        return Result(sent, packets, seen.size, limited || packets == MAX_PACKETS,
            !active() || deadline < start + TimeUnit.SECONDS.toNanos(2) || now() < end && !limited && packets < MAX_PACKETS)
    }
}
