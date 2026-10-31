package io.github.kazands.networklens

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Advertised service metadata only; no web crawling, account actions or model guessing. */
internal object LocalMetadata {
    enum class Kind(val source: String) {
        SHELLY("Shelly device information"), WLED("WLED device information"),
        SPOTIFY("Spotify Connect device information"), IPP("IPP printer information")
    }
    data class Report(val hints: Map<String, String> = emptyMap(), val details: Map<String, String> = emptyMap(), val mac: String = "")
    data class Request(val kind: Kind, val port: Int, val path: String, val secure: Boolean = false) {
        fun bytes(peer: InetAddress): ByteArray {
            require(port in 1..65535 && safePath(path) == path)
            val authority = authority(peer, port)
            val body = if (kind == Kind.IPP) IppInfo.query("${if (secure) "ipps" else "ipp"}://$authority$path") else ByteArray(0)
            val target = path + if (kind == Kind.SPOTIFY) "?action=getInfo" else ""
            val headers = "${if (kind == Kind.IPP) "POST" else "GET"} $target HTTP/1.1\r\nHost: $authority\r\n" +
                "Accept: ${if (kind == Kind.IPP) "application/ipp" else "application/json"}\r\n" +
                (if (kind == Kind.IPP) "Content-Type: application/ipp\r\nContent-Length: ${body.size}\r\n" else "") +
                "Connection: close\r\n\r\n"
            return headers.toByteArray(StandardCharsets.US_ASCII) + body
        }
    }

    fun advertisement(type: String, attrs: Map<String, ByteArray>): Report {
        val hints = LinkedHashMap<String, String>(); val details = LinkedHashMap<String, String>()
        when (LocalServices.canonicalType(type)) {
            "_meshcop._udp." -> {
                hints["Model"] = text(attrs, "mn")
                hints["Manufacturer"] = text(attrs, "vn")
            }
            "_esphomelib._tcp." -> {
                hints["Name"] = text(attrs, "friendly_name")
                hints["Firmware"] = text(attrs, "version")
                // Firmware compilation targets are not necessarily retail device models.
                details["ESPHome board (advertised)"] = text(attrs, "board")
                details["ESPHome platform (advertised)"] = text(attrs, "platform")
            }
        }
        return Report(hints.filterValues { it.isNotEmpty() }, details.filterValues { it.isNotEmpty() })
    }

    fun request(service: MdnsPacket.Service): Request? {
        if (service.port !in 1..65535) return null
        return when (LocalServices.canonicalType(service.type)) {
            "_shelly._tcp." -> Request(Kind.SHELLY, service.port, "/shelly")
            "_http._tcp." -> {
                // Gen1 uses a short (last three MAC bytes) or full ID in its instance/host.
                val instance = service.name.lowercase(Locale.ROOT)
                val host = service.host.substringBefore('.').lowercase(Locale.ROOT)
                val shelly = Regex("shelly[a-z0-9-]*-(?:[0-9a-f]{6}|[0-9a-f]{12})")
                if (shelly.matches(instance) || shelly.matches(host)) Request(Kind.SHELLY, service.port, "/shelly") else null
            }
            "_wled._tcp." -> Request(Kind.WLED, service.port, "/json/info")
            "_spotify-connect._tcp." -> safePath(rawText(service.attributes, "CPath"))?.let { Request(Kind.SPOTIFY, service.port, it) }
            "_ipp._tcp.", "_ipps._tcp." -> {
                val rp = rawText(service.attributes, "rp")
                if (rp.isEmpty()) null else safePath(if (rp.startsWith('/')) rp else "/$rp")?.let {
                    Request(Kind.IPP, service.port, it, LocalServices.canonicalType(service.type) == "_ipps._tcp.")
                }
            }
            else -> null
        }
    }

    /** A path, never a URL or a supplied query. The only Spotify action is generated above. */
    fun safePath(path: String): String? = try {
        if (path.isEmpty() || path.length > 512 || !path.startsWith('/') || path.startsWith("//") ||
            path.any { it.code !in 33..126 || it == '\\' }) null
        else {
            val uri = URI(path)
            val decoded = uri.path ?: ""
            if (uri.rawAuthority != null || uri.scheme != null || uri.rawQuery != null || uri.rawFragment != null ||
                decoded.any { it.code < 32 || it.code == 127 || it == '\\' || it == '?' || it == '#' } ||
                decoded.startsWith("//") || decoded.split('/').any { it == "." || it == ".." } ||
                Regex("(?i)%2f|%5c|%25").containsMatchIn(path)) null else path
        }
    } catch (_: Exception) { null }

    fun parse(kind: Kind, body: ByteArray): Report? {
        if (kind == Kind.IPP) return IppInfo.parse(body)?.let { Report(mapOf("Model" to it)) }
        val fields = when (kind) {
            Kind.SHELLY -> BoundedJson.fields(body, setOf("model", "type", "mac", "ver", "fw"))
            Kind.WLED -> BoundedJson.fields(body, setOf("name", "ver", "mac", "arch"))
            Kind.SPOTIFY -> BoundedJson.fields(body, setOf("modelDisplayName", "brandDisplayName", "remoteName", "deviceType"), setOf("status", "spotifyError"))
        } ?: return null
        fun field(key: String) = SafeText.label(fields[key], 160)
        val report = when (kind) {
            Kind.SHELLY -> {
                val model = field("model").ifEmpty { field("type") }
                if (model.isEmpty()) null else Report(mapOf("Model" to model, "Firmware" to field("ver").ifEmpty { field("fw") }), mac = Address.mac(field("mac")))
            }
            Kind.WLED -> Report(mapOf("Name" to field("name"), "Firmware" to field("ver")),
                mapOf("WLED platform (reported)" to field("arch")), Address.mac(field("mac")))
            Kind.SPOTIFY -> if (fields["status"] != "101" || fields["spotifyError"] != "0") null else Report(mapOf(
                "Name" to field("remoteName"), "Model" to field("modelDisplayName"), "Manufacturer" to field("brandDisplayName")),
                mapOf("Spotify device type (reported)" to field("deviceType")))
        }
        return report?.copy(hints = report.hints.filterValues { it.isNotEmpty() }, details = report.details.filterValues { it.isNotEmpty() })
    }

    private fun text(attrs: Map<String, ByteArray>, key: String): String = SafeText.label(rawText(attrs, key), 160)
    private fun rawText(attrs: Map<String, ByteArray>, key: String): String {
        val matches = attrs.filterKeys { it.equals(key, true) }.values
        if (matches.size != 1) return ""
        val bytes = matches.single()
        if (bytes.size > 512) return ""
        return try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: Exception) { "" }
    }
    private fun authority(peer: InetAddress, port: Int): String {
        val host = peer.hostAddress!!.substringBefore('%')
        return (if (peer is Inet6Address) "[$host]" else host) + ":$port"
    }
}
