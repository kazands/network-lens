package io.github.kazands.networklens

import java.net.Inet6Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets

/** Bounded, explicit Cast identity fields; never retain addresses, accounts or location data. */
class CastInfo private constructor() {
    companion object {
        const val MAX_BODY = 32768
        @JvmStatic fun needsName(name: String?): Boolean = !IdentityHints.specificName(name)

        internal fun isGroup(service: MdnsPacket.Service): Boolean {
            if (LocalServices.canonicalType(service.type) != "_googlecast._tcp.") return false
            fun txt(key: String): String {
                val values = service.attributes.filterKeys { it.equals(key, true) }.values
                if (values.size != 1 || values.single().size > 160) return ""
                return String(values.single(), StandardCharsets.UTF_8)
            }
            val flags = txt("ca").takeIf { it.matches(Regex("[0-9]{1,10}")) }?.toLongOrNull()
            return txt("md").equals("Google Cast Group", true) || flags != null && flags and 32L != 0L
        }

        @JvmStatic fun request(responder: InetAddress): ByteArray {
            var host = responder.hostAddress!!.javaSplit("%", 2)[0]
            if (responder is Inet6Address) host = "[" + host + "]"
            return ("GET /setup/eureka_info?params=device_info,name HTTP/1.1\r\nHost: " + host +
                ":8008\r\nAccept: application/json\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        }

        @JvmStatic fun name(bytes: ByteArray?): String {
            return parse(bytes)?.hints?.get("Name").orEmpty()
        }

        internal fun parse(bytes: ByteArray?): LocalMetadata.Report? {
            val fields = BoundedJson.paths(bytes, setOf(listOf("name"), listOf("device_info", "name"),
                listOf("device_info", "model_name"), listOf("device_info", "manufacturer"))) ?: return null
            fun field(vararg path: String): String {
                val raw = fields[path.toList()] ?: return ""
                // Never truncate identifiers into a different valid catalogue key.
                if (raw.length > 160 || SafeText.label(raw, 160) != raw) return ""
                if (raw.equals("Unknown manufacturer", true) || raw.equals("Unknown model name", true) || raw.equals("Android TV", true)) return ""
                return raw.takeIf(IdentityHints::specific).orEmpty()
            }
            val name = field("device_info", "name").ifEmpty { field("name") }
            if (field("device_info", "model_name").equals("Google Cast Group", true))
                return LocalMetadata.Report(details = mapOf("Cast role (reported)" to "Speaker group"))
            return LocalMetadata.Report(mapOf(
                "Name" to name.takeUnless(::needsName).orEmpty(),
                "Model" to field("device_info", "model_name"),
                "Manufacturer" to field("device_info", "manufacturer")
            ).filterValues { it.isNotEmpty() })
        }
    }
}
