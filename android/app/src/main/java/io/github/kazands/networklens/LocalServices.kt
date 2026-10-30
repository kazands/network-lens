package io.github.kazands.networklens

import java.util.Collections
import java.util.Locale

/** Explicit queries complement bounded DNS-SD enumeration; they do not widen IP scope. */
class LocalServices private constructor() {
    companion object {
        @JvmField val INITIAL: List<String> = Collections.unmodifiableList(listOf(
            "_http._tcp.", "_https._tcp.", "_googlecast._tcp.", "_airplay._tcp.",
            "_raop._tcp.", "_ipp._tcp.", "_ipps._tcp.", "_printer._tcp.",
            "_smb._tcp.", "_workstation._tcp.", "_ssh._tcp.", "_device-info._tcp.",
            "_hap._tcp.", "_spotify-connect._tcp.", "_pdl-datastream._tcp.",
            "_companion-link._tcp.", "_hap._udp.", "_matter._tcp.",
            "_matterc._udp.", "_meshcop._udp."
        ))

        /** Android's legacy resolver can return ._http._tcp instead of _http._tcp. */
        @JvmStatic fun canonicalType(type: String?): String {
            if (type == null || type.length > 128) return ""
            var value = type.lowercase(Locale.ROOT)
            // Remove only the platform's single leading separator, not arbitrary labels.
            if (value.startsWith(".")) value = value.substring(1)
            if (!value.endsWith(".local") && !value.endsWith(".local.")) {
                value += if (value.endsWith(".")) "local" else ".local"
            }
            return MdnsPacket.serviceType(value)
        }

        @JvmStatic fun tcpConfirmation(type: String): Boolean {
            val canonical = canonicalType(type)
            return canonical.endsWith("._tcp.") && canonical != "_matter._tcp."
        }
        @JvmStatic fun routedAccessory(type: String): Boolean = canonicalType(type) in
            setOf("_hap._tcp.", "_hap._udp.", "_matter._tcp.", "_matterc._udp.")
        // Matter operational discovery keeps a _tcp service name even when the
        // endpoint supports only UDP. Do not mistake its SRV port for a TCP port.
    }
}
