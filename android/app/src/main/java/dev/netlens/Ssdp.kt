package dev.netlens

import java.nio.charset.StandardCharsets
import java.util.Locale

/** Bounded SSDP headers; LOCATION is validated separately against its responder. */
class Ssdp private constructor() {
    companion object {
        @JvmStatic fun parse(packet: ByteArray?, length: Int): MutableMap<String, String> {
            val out = LinkedHashMap<String, String>()
            if (packet == null || length < 12 || length > 4096 || length > packet.size) return out
            val lines = String(packet, 0, length, StandardCharsets.ISO_8859_1).javaSplit("\r?\n", 65)
            if (!lines[0].matches(Regex("HTTP/1\\.[01] 200(?: .*)?"))) return out
            for (i in 1 until minOf(lines.size, 65)) {
                if (lines[i].isEmpty()) break
                val split = lines[i].indexOf(':')
                if (split < 1) continue
                val key = lines[i].substring(0, split).javaTrim().lowercase(Locale.ROOT)
                if (key == "server" || key == "st" || key == "usn") {
                    out[key] = SafeText.label(lines[i].substring(split + 1), 160)
                }
                if (key == "location") {
                    val location = lines[i].substring(split + 1).javaTrim()
                    if (location.length <= 1024) out[key] = location
                }
            }
            return out
        }
    }
}

