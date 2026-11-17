package io.github.kazands.networklens

import java.net.InetAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Fixed read-only discovery messages. No registration, control commands, redirects or pairing. */
internal object LanIdentity {
    enum class Method(val source: String, val port: Int, val listenPort: Int = 0) {
        WIZ("WiZ discovery", 38899), YEELIGHT("Yeelight discovery", 1982), GOVEE("Govee discovery", 4001, 4002)
    }
    data class Report(val family: String, val metadata: LocalMetadata.Report)
    data class Packet(val peer: InetAddress, val port: Int, val bytes: ByteArray, val length: Int)
    data class Result(val packets: Int, val devices: Int, val limited: Boolean, val interrupted: Boolean)
    interface Channel {
        fun send(bytes: ByteArray)
        fun receive(timeoutMillis: Int): Packet?
    }
    fun query(method: Method): ByteArray = when (method) {
        Method.WIZ -> """{"method":"getSystemConfig","params":{}}"""
        Method.GOVEE -> """{"msg":{"cmd":"scan","data":{"account_topic":"reserve"}}}"""
        Method.YEELIGHT -> "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1982\r\nMAN: \"ssdp:discover\"\r\nST: wifi_bulb\r\n\r\n"
    }.toByteArray(Charsets.US_ASCII)
    fun parse(method: Method, packet: Packet): Report? {
        if (packet.length !in 1..4096 || packet.length > packet.bytes.size || packet.peer.isAnyLocalAddress ||
            packet.peer.isLoopbackAddress || packet.peer.isMulticastAddress || packet.port !in 1..65535) return null
        val bytes = packet.bytes.copyOf(packet.length)
        return when (method) {
            Method.WIZ -> {
                if (packet.port != 38899) return null
                val fields = BoundedJson.paths(bytes, setOf(listOf("method"), listOf("result", "mac"),
                    listOf("result", "moduleName"), listOf("result", "fwVersion"))) ?: return null
                if (fields[listOf("method")] != "getSystemConfig") return null
                val mac = Address.mac(fields[listOf("result", "mac")])
                val module = SafeText.label(fields[listOf("result", "moduleName")], 160)
                if (mac.isEmpty() || module.isEmpty()) return null
                // Module identifies electronics, not necessarily the retail product or even a light.
                Report("wiz", LocalMetadata.Report(mapOf("Firmware" to SafeText.label(fields[listOf("result", "fwVersion")], 160)),
                    mapOf("WiZ module (reported)" to module), mac))
            }
            Method.GOVEE -> {
                val fields = BoundedJson.paths(bytes, setOf(listOf("msg", "cmd"), listOf("msg", "data", "ip"),
                    listOf("msg", "data", "sku"), listOf("msg", "data", "bleVersionHard"))) ?: return null
                if (fields[listOf("msg", "cmd")] != "scan" ||
                    !Address.bytes(fields[listOf("msg", "data", "ip")].orEmpty()).contentEquals(packet.peer.address)) return null
                val model = fields[listOf("msg", "data", "sku")].orEmpty()
                if (!model.matches(Regex("H[0-9A-Z]{4,15}"))) return null
                Report("govee", LocalMetadata.Report(mapOf("Model" to model), mapOf("Govee LAN protocol" to "Current scan response")))
            }
            Method.YEELIGHT -> {
                val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (_: Exception) { return null }
                val lines = text.split("\r\n")
                if (lines.firstOrNull() != "HTTP/1.1 200 OK" || lines.size > 64) return null
                val values = LinkedHashMap<String, String>()
                for (line in lines.drop(1)) {
                    if (line.isEmpty()) break
                    val split = line.indexOf(':'); if (split < 1) return null
                    val key = line.substring(0, split).lowercase(Locale.ROOT)
                    val value = line.substring(split+1).trim()
                    if (value.length > 1024 || values.containsKey(key) && values[key] != value) return null
                    values[key] = value
                }
                val target = try { URI(values["location"].orEmpty()) } catch (_: Exception) { return null }
                if (target.scheme != "yeelight" || target.userInfo != null || target.rawQuery != null || target.fragment != null ||
                    target.port !in 1..65535 || !target.path.isNullOrEmpty() ||
                    !Address.bytes(target.host.orEmpty()).contentEquals(packet.peer.address)) return null
                val model = values["model"].orEmpty()
                if (!model.matches(Regex("[a-zA-Z0-9_-]{1,64}")) || !values["id"].orEmpty().matches(Regex("0x[0-9a-fA-F]{1,16}"))) return null
                // mono/color/ceiling are protocol families, not exact retail models.
                Report("yeelight", LocalMetadata.Report(mapOf("Name" to SafeText.label(values["name"],160),
                    "Firmware" to SafeText.label(values["fw_ver"],160), "Device type" to "Yeelight light"),
                    mapOf("Yeelight model family (reported)" to model)))
            }
        }
    }
    fun discover(method: Method, channel: Channel, scanDeadline: Long, active: () -> Boolean,
                 eligible: (InetAddress) -> Boolean, now: () -> Long = System::nanoTime,
                 accept: (InetAddress, Report) -> Unit): Result {
        val start = now(); val end = minOf(scanDeadline, start + TimeUnit.SECONDS.toNanos(6))
        var sent = 0; var packets = 0; val devices = LinkedHashMap<String, MutableSet<Report>>(); var limited = false
        while (active() && now() < end) {
            if (packets >= 512) { limited = true; break }
            if (sent < 2 && now() >= start + TimeUnit.SECONDS.toNanos(sent.toLong())) { channel.send(query(method)); sent++ }
            val timeout = maxOf(1, minOf(200, TimeUnit.NANOSECONDS.toMillis(end-now()))).toInt()
            val packet = channel.receive(timeout) ?: continue
            packets++
            if (!active() || now() >= end || !eligible(packet.peer)) continue
            val report = parse(method, packet) ?: continue
            val key = packet.peer.hostAddress!!
            val reports = devices[key] ?: if (devices.size < 128) linkedSetOf<Report>().also { devices[key]=it }
                else { limited = true; continue }
            if (report in reports) continue
            if (reports.size >= 4) { limited=true; continue }
            reports.add(report); accept(packet.peer, report)
        }
        return Result(packets, devices.size, limited, !active() || scanDeadline < start+TimeUnit.SECONDS.toNanos(6))
    }
}
