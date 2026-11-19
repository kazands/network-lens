package io.github.kazands.networklens

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/** Read-only TP-Link discovery. No login, relay commands, pairing or cloud requests.
 * Wire references and independently maintained model mappings: docs/reference/TP-LINK.md. */
internal object TpLinkDiscovery {
    const val SOURCE = "TP-Link discovery"
    const val MODEL_SOURCE = "TP-Link discovery reported model"
    const val MAX_PACKET = 8192
    const val MAX_PACKETS = 512
    const val MAX_DEVICES = 128
    private val ports = listOf(9999, 20002)
    data class Response(val model: String, val family: String, val name: String,
                        val firmware: String, val hardware: String, val mac: String)
    data class Packet(val peer: InetAddress, val port: Int, val bytes: ByteArray, val length: Int)
    data class Result(val sent: Int, val packets: Int, val devices: Int, val limited: Boolean, val interrupted: Boolean)
    interface Channel {
        fun send(target: InetAddress, port: Int, bytes: ByteArray)
        fun receive(timeoutMillis: Int): Packet?
    }
    /** Broadcast only for the whole attached subnet; narrowed scans query eligible hosts directly. */
    fun targets(ip: String, prefix: Int, selected: IpRange): List<InetAddress> {
        val attached = IpRange(ip, prefix)
        require(selected.first >= attached.first && selected.last <= attached.last)
        return if (prefix < 31 && selected.first == attached.first && selected.last == attached.last)
            listOf(InetAddress.getByName(IpRange.address(attached.last + 1)))
        else selected.hosts(1024).filter { it != ip }.map(InetAddress::getByName)
    }
    fun query(port: Int, transaction: Int): ByteArray = when (port) {
        9999 -> {
            var key = 0xab
            "{\"system\":{\"get_sysinfo\":{}}}".toByteArray(Charsets.US_ASCII).map { b ->
                key = key xor (b.toInt() and 255); key.toByte()
            }.toByteArray()
        }
        20002 -> ByteArray(16).also { bytes ->
            bytes[0] = 2; bytes[3] = 1
            putInt(bytes, 8, transaction); checksum(bytes)
        }
        else -> throw IllegalArgumentException("Unsupported discovery port")
    }
    private fun putInt(bytes: ByteArray, at: Int, value: Int) {
        for (n in 0..3) bytes[at + n] = (value ushr (24 - 8 * n)).toByte()
    }
    private fun int(bytes: ByteArray, at: Int): Int = (0..3).fold(0) { result, n ->
        (result shl 8) or (bytes[at + n].toInt() and 255)
    }
    private fun checksum(bytes: ByteArray) {
        putInt(bytes, 12, 0x5a6b7c8d)
        putInt(bytes, 12, CRC32().apply { update(bytes) }.value.toInt())
    }
    fun parse(packet: Packet, transaction: Int): Response? {
        val length = packet.length
        if (length !in 1..MAX_PACKET || length > packet.bytes.size || packet.port !in ports) return null
        val bytes = packet.bytes.copyOf(length)
        val modern = packet.port == 20002
        val body = if (modern) {
            if (length <= 16 || bytes[0].toInt() != 2 || bytes[1].toInt() != 0 ||
                bytes[2].toInt() != 0 || bytes[3].toInt() != 1 || bytes[6].toInt() != 2 || bytes[7].toInt() != 0 ||
                ((bytes[4].toInt() and 255) * 256 + (bytes[5].toInt() and 255)) != length - 16 ||
                int(bytes, 8) != transaction) return null
            val received = int(bytes, 12)
            checksum(bytes)
            if (received != int(bytes, 12)) return null
            bytes.copyOfRange(16, length)
        } else {
            var key = 0xab
            bytes.map { b -> val next = b.toInt() and 255; val plain = (key xor next).toByte(); key = next; plain }.toByteArray()
        }
        val root = if (modern) listOf("result") else listOf("system", "get_sysinfo")
        val wanted = if (modern) setOf("device_model", "device_type", "hw_ver", "hardware_version", "firmware_version", "mac")
            else setOf("model", "type", "mic_type", "alias", "sw_ver", "hw_ver", "mac")
        val error = if (modern) listOf("error_code") else root + "err_code"
        val fields = BoundedJson.paths(body, wanted.map { root + it }.toSet(), setOf(error)) ?: return null
        if (fields[error] != "0") return null
        fun field(key: String) = fields[root + key].orEmpty()
        val model = field(if (modern) "device_model" else "model")
        val family = if (modern) field("device_type") else field("mic_type").ifEmpty { field("type") }
        // Preserve raw model; suffix stripping belongs solely to the qualified product catalogue.
        if (!model.matches(Regex("[A-Za-z0-9][A-Za-z0-9 ._()/-]{0,95}")) ||
            family !in setOf("IOT.SMARTPLUGSWITCH", "SMART.TAPOPLUG", "SMART.KASAPLUG", "SMART.TAPOSWITCH", "SMART.KASASWITCH")) return null
        return Response(model, family, SafeText.label(field("alias"), 128),
            SafeText.label(field(if (modern) "firmware_version" else "sw_ver"), 128),
            SafeText.label(field("hw_ver").ifEmpty { field("hardware_version") }, 64), Address.mac(field("mac")))
    }
    fun discover(channel: Channel, targets: List<InetAddress>, transaction: Int, deadline: Long,
                 active: () -> Boolean, allowed: (InetAddress) -> Boolean,
                 now: () -> Long = System::nanoTime, accept: (InetAddress, Int, Response) -> Unit): Result {
        require(targets.size <= 1024 && targets.all { it is Inet4Address })
        val start = now(); val window = start + TimeUnit.SECONDS.toNanos(4)
        val end = minOf(deadline, window)
        var sent = 0; var packets = 0; var round = 0; var next = 0; var limited = false
        val seen = HashSet<String>(); val devices = HashSet<String>()
        val queries = ports.associateWith { query(it, transaction) }
        while (targets.isNotEmpty() && active() && now() < end && packets < MAX_PACKETS) {
            if (round < 2 && now() - start >= TimeUnit.MILLISECONDS.toNanos(round * 1000L)) {
                val target = targets[next]
                for (port in ports) {
                    if (!active() || now() >= end) break
                    channel.send(target, port, queries.getValue(port)); sent++
                }
                if (++next == targets.size) { next = 0; round++ }
            }
            val left = end - now()
            if (left <= 0 || !active()) break
            val pending = round < 2 && now() - start >= TimeUnit.MILLISECONDS.toNanos(round * 1000L)
            val packet = channel.receive(maxOf(1, minOf(if (pending) 1 else 250,
                TimeUnit.NANOSECONDS.toMillis(left))).toInt()) ?: continue
            packets++
            if (!active() || now() >= end) break
            if (packet.peer !is Inet4Address || !allowed(packet.peer)) continue
            val response = parse(packet, transaction) ?: continue
            val ip = packet.peer.hostAddress ?: continue
            val key = "$ip:${packet.port}"
            if (key in seen) continue
            if (ip !in devices && devices.size == MAX_DEVICES) { limited = true; break }
            seen.add(key); devices.add(ip)
            if (active() && now() < end) accept(packet.peer, packet.port, response)
        }
        return Result(sent, packets, devices.size, limited || packets == MAX_PACKETS,
            !active() || deadline < window || targets.isNotEmpty() && round < 2)
    }
}
