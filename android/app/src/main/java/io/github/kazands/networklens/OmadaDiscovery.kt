package io.github.kazands.networklens

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Receive-only ECSP discovery announcements. There is deliberately no send/adopt API.
 * Protocol evidence and coverage limits: docs/OMADA.md. */
internal object OmadaDiscovery {
    const val SOURCE = "Omada announcements"
    const val MODEL_SOURCE = "Omada discovery reported model"
    const val PORT = 29810
    const val MAX_PACKET = 2004
    const val MAX_PACKETS = 512
    const val MAX_DEVICES = 128
    const val WINDOW_SECONDS = 10L
    data class Response(val model: String, val family: String, val name: String,
                        val firmware: String, val hardware: String, val mac: String, val version: String) {
        val kind: String get() = when (family) {
            "ap" -> "Omada access point"
            "switch" -> "Omada network switch"
            "gateway" -> "Omada gateway"
            else -> ""
        }
    }
    data class Packet(val peer: InetAddress, val port: Int, val bytes: ByteArray, val length: Int)
    data class Result(val packets: Int, val devices: Int, val limited: Boolean, val interrupted: Boolean)
    fun interface Channel { fun receive(timeoutMillis: Int): Packet? }

    fun parse(packet: Packet): Response? {
        val length = packet.length
        if (packet.peer !is Inet4Address || packet.port !in 1..65535 || length !in 5..MAX_PACKET || length > packet.bytes.size) return null
        val declared = (0..3).fold(0L) { n, i -> (n shl 8) or (packet.bytes[i].toLong() and 255) }
        if (declared != (length - 4).toLong()) return null
        val root = BoundedJson.document(packet.bytes.copyOfRange(4, length)) ?: return null
        fun obj(parent: BoundedJson.ObjectValue, key: String) = parent.values[key] as? BoundedJson.ObjectValue
        fun text(parent: BoundedJson.ObjectValue, key: String): String? =
            (parent.values[key] as? BoundedJson.Scalar)?.takeUnless { it.number }?.text
        fun number(parent: BoundedJson.ObjectValue, key: String): String? =
            (parent.values[key] as? BoundedJson.Scalar)?.takeIf { it.number }?.text
        val header = obj(root, "header") ?: return null
        val info = obj(obj(root, "body") ?: return null, "deviceInfo") ?: return null
        if (number(header, "type") != "1" ||
            "error" in header.values && number(header, "error") != "0" ||
            "compress" in header.values && text(header, "compress") != "") return null
        val family = text(header, "device") ?: return null
        if (family !in setOf("ap", "switch", "gateway")) return null
        val version = text(header, "version") ?: return null
        if (!version.matches(Regex("[12]\\.[0-9]{1,3}\\.[0-9]{1,3}"))) return null
        val model = text(info, "model") ?: return null
        if (!validModel(model)) return null
        val mac = Address.mac(text(header, "mac").orEmpty())
        if (mac.isEmpty()) return null
        // A second identity may corroborate the sender, but may never redirect/merge another device.
        val mainMac = text(info, "mainMac")
        if ("mainMac" in info.values && (mainMac == null || Address.mac(mainMac) != mac)) return null
        return Response(model, family, SafeText.label(text(info, "name"), 128),
            SafeText.label(text(info, "firmwareVersion"), 128), SafeText.label(text(info, "hardwareVersion"), 64), mac, version)
    }
    private fun validModel(model: String) = model.matches(Regex("[A-Za-z0-9][A-Za-z0-9 ._()/-]{0,95}")) && model == model.trim()

    /** Protocol-qualified reported name, not an inferred retail variant or model-prefix classifier. */
    fun product(model: String): DeviceCatalog.Product? = if (!validModel(model)) null else
        DeviceCatalog.Product("omada:${DeviceCatalog.key(model)}", "TP-Link", "TP-Link $model",
            DeviceCatalog.Category.UNSPECIFIED, setOf(DeviceCatalog.key(model)))

    fun listen(channel: Channel, deadline: Long, active: () -> Boolean, allowed: (InetAddress) -> Boolean,
               now: () -> Long = System::nanoTime, accept: (InetAddress, Response) -> Unit): Result {
        val window = now() + TimeUnit.SECONDS.toNanos(WINDOW_SECONDS)
        val end = minOf(window, deadline)
        var packets = 0; var limited = false
        val seen = HashMap<String, MutableSet<Response>>()
        while (active() && now() < end && packets < MAX_PACKETS) {
            val left = end - now()
            if (left <= 0) break
            val packet = channel.receive(maxOf(1, minOf(250, TimeUnit.NANOSECONDS.toMillis(left))).toInt()) ?: continue
            packets++
            if (!active() || now() >= end) break
            if (packet.peer !is Inet4Address || !allowed(packet.peer)) continue
            val response = parse(packet) ?: continue
            val ip = packet.peer.hostAddress ?: continue
            if (ip !in seen && seen.size == MAX_DEVICES) { limited = true; break }
            val observations = seen.getOrPut(ip) { HashSet() }
            if (response in observations) continue
            if (observations.size == 8) { limited = true; break }
            observations.add(response)
            if (active() && now() < end) accept(packet.peer, response)
        }
        return Result(packets, seen.size, limited || packets == MAX_PACKETS, !active() || deadline < window)
    }
}
