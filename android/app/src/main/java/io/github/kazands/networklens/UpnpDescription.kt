package io.github.kazands.networklens

import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler

/** Same-peer, credential-free HTTP descriptions; no redirects or child fetches. */
class UpnpDescription private constructor() {
    /** Fixed parser failures, separate from an IOException raised by the transport. */
    internal open class ResponseException(message: String) : IOException(message)
    internal class HttpStatusException(val statusCode: Int) : ResponseException("HTTP status $statusCode")

    companion object {
        const val MAX_BODY = 65536
        // Amazon's documented DIAL LOCATION. Never guess the UUID or widen this to arbitrary /desc paths.
        private val fireTvPath = Regex("/upnp/dev/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/desc")

        @JvmStatic fun target(location: String?, responder: InetAddress?): URI? {
            try {
                if (location == null || location.length > 1024 || responder == null ||
                    responder.isLoopbackAddress || responder.isAnyLocalAddress || responder.isMulticastAddress) return null
                if (location.any { it.code < 33 || it.code > 126 }) return null
                val uri = URI(location)
                if (!"http".equals(uri.scheme, true) || uri.rawUserInfo != null || uri.rawFragment != null) return null
                var host = uri.host ?: return null
                if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length - 1)
                if (host.contains('%')) return null
                val address = Address.bytes(host) ?: return null
                if (!java.util.Arrays.equals(address, responder.address)) return null
                if (address.size == 4 && host != responder.hostAddress) return null
                val port = uri.port
                if (port == 0 || port > 65535 || port < -1) return null
                val path = uri.rawPath
                val query = uri.rawQuery
                if (path == null || !path.startsWith("/") || path.contains('\\')) return null
                val windows = path.equals("/upnphost/udhisapi.dll", true) && query != null &&
                    query.matches(Regex("content=uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                val ordinary = query == null &&
                    (path == "/" || path == "/description" || path.lowercase(Locale.ROOT).endsWith(".xml") ||
                        path.matches(Regex("/smp_[0-9]{1,5}_")) || fireTvPath.matches(path))
                return if (windows || ordinary) uri else null
            } catch (_: URISyntaxException) { return null }
            catch (_: IllegalArgumentException) { return null }
        }

        @JvmStatic fun request(target: URI): ByteArray =
            ("GET " + target.rawPath + (if (target.rawQuery == null) "" else "?" + target.rawQuery) +
                " HTTP/1.1\r\nHost: " + target.rawAuthority +
                "\r\nAccept: text/xml, application/xml\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)

        @JvmStatic @Throws(IOException::class)
        fun readResponse(input: InputStream): ByteArray = readResponse(input) { }

        /** Inspect validated headers without exposing redirects, cookies or arbitrary follow-up URLs. */
        internal fun readResponse(input: InputStream, inspectHeaders: (Map<String, String>) -> Unit): ByteArray {
            val stream = BufferedInputStream(input, 1024)
            val headerBytes = intArrayOf(0)
            val status = line(stream, headerBytes, 8192)
            if (!status.matches(Regex("HTTP/1\\.[01] 200(?: .*)?"))) {
                val code = Regex("HTTP/1\\.[01] ([1-5][0-9]{2})(?: .*)?").matchEntire(status)?.groupValues?.get(1)?.toInt()
                if (code != null) throw HttpStatusException(code)
                throw ResponseException("Invalid HTTP status line")
            }
            val headers = HashMap<String, String>()
            var i = 0
            while (true) {
                if (i++ >= 64) throw ResponseException("Too many headers")
                val s = line(stream, headerBytes, 8192)
                if (s.isEmpty()) break
                val colon = s.indexOf(':')
                if (colon < 1 || s[0] == ' ' || s[0] == '\t') throw ResponseException("Invalid header")
                val key = s.substring(0, colon).lowercase(Locale.ROOT)
                val value = s.substring(colon + 1).javaTrim()
                if (headers.put(key, value) != null) throw ResponseException("Repeated header")
            }
            val encoding = headers["content-encoding"]
            if (encoding != null && !encoding.equals("identity", true)) throw ResponseException("Encoded body unsupported")
            val transfer = headers["transfer-encoding"]
            val length = headers["content-length"]
            if (transfer != null && length != null) throw ResponseException("Ambiguous body framing")
            inspectHeaders(headers)
            val body = ByteArrayOutputStream()
            if (transfer != null) {
                if (!transfer.equals("chunked", true)) throw ResponseException("Unsupported transfer encoding")
                val chunkHeaders = intArrayOf(0)
                repeat(2048) {
                    val size = line(stream, chunkHeaders, 16384)
                    if (!size.matches(Regex("[0-9a-fA-F]{1,6}"))) throw ResponseException("Invalid chunk size")
                    val n = Integer.parseInt(size, 16)
                    if (n > MAX_BODY - body.size()) throw ResponseException("Description too large")
                    if (n == 0) {
                        repeat(32) { if (line(stream, chunkHeaders, 16384).isEmpty()) return body.toByteArray() }
                        throw ResponseException("Too many trailers")
                    }
                    copy(stream, body, n)
                    if (stream.read() != 13 || stream.read() != 10) throw ResponseException("Invalid chunk ending")
                }
                throw ResponseException("Too many chunks")
            }
            if (length != null) {
                if (!length.matches(Regex("[0-9]{1,6}"))) throw ResponseException("Invalid body length")
                val n = Integer.parseInt(length)
                if (n > MAX_BODY) throw ResponseException("Description too large")
                copy(stream, body, n)
            } else {
                val buffer = ByteArray(2048)
                while (true) {
                    val n = stream.read(buffer)
                    if (n == -1) break
                    if (body.size() + n > MAX_BODY) throw ResponseException("Description too large")
                    body.write(buffer, 0, n)
                }
            }
            return body.toByteArray()
        }

        private fun copy(input: InputStream, out: OutputStream, count: Int) {
            var n = count
            val buffer = ByteArray(2048)
            while (n > 0) {
                val got = input.read(buffer, 0, minOf(n, buffer.size))
                if (got < 1) throw ResponseException("Truncated body")
                out.write(buffer, 0, got)
                n -= got
            }
        }

        private fun line(input: InputStream, count: IntArray, maximum: Int): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val c = input.read()
                if (c == -1) throw ResponseException("Truncated header")
                if (++count[0] > maximum || bytes.size() > 2048) throw ResponseException("Header limit")
                if (c == 10) {
                    val value = bytes.toByteArray()
                    if (value.isEmpty() || value[value.size - 1].toInt() != 13) throw ResponseException("Invalid line ending")
                    return String(value, 0, value.size - 1, StandardCharsets.ISO_8859_1)
                }
                bytes.write(c)
            }
        }

        @JvmStatic fun parse(body: ByteArray?): Map<String, String> {
            if (body == null || body.isEmpty() || body.size > MAX_BODY) return Collections.emptyMap()
            return try {
                val factory = SAXParserFactory.newInstance()
                factory.isNamespaceAware = true
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                val reader = factory.newSAXParser().xmlReader
                val handler = Description()
                reader.contentHandler = handler
                reader.errorHandler = handler
                reader.entityResolver = handler
                reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
                reader.parse(InputSource(ByteArrayInputStream(body)))
                handler.values
            } catch (_: Exception) { Collections.emptyMap() }
        }

        /** Known software descriptions remain service evidence, never the physical host's maker/model. */
        internal fun softwareIdentity(values: Map<String, String>): Boolean =
            DeviceCatalog.key(values["manufacturer"].orEmpty()) in setOf("jminim.org", "minimserver.com") &&
                values["modelName"] in setOf("MinimServer", "MonitorService2") ||
            values["manufacturer"] == "JF Light Industries" && values["modelName"] == "UpMPD"

        // Samba's generic Bonjour icon/model hint does not identify Apple hardware.
        internal fun presentationModel(value: String, source: String): Boolean =
            value == "Xserve" && source == "mDNS current _device-info._tcp. TXT"

        /** A DLNA renderer can be a TV, speaker or software. Only explicit Samsung TV
         * descriptions qualify here; manufacturer, friendlyName and MediaRenderer alone do not. */
        internal fun deviceKind(values: Map<String, String>): String {
            when (values["deviceType"]) {
                "urn:schemas-upnp-org:device:DigitalSecurityCamera:1" -> return "UPnP camera"
                "urn:schemas-upnp-org:device:DimmableLight:1" -> return "UPnP light"
            }
            val maker = DeviceCatalog.key(values["manufacturer"].orEmpty())
            if (maker !in setOf("samsung", "samsung electronics", "samsung electronics co., ltd.")) return ""
            val type = values["deviceType"].orEmpty()
            val description = values["modelDescription"].orEmpty()
            val tvType = type == "urn:samsung.com:device:MainTVServer2:1"
            val tvDescription = description in setOf("Samsung TV", "Samsung DTV DMR", "Samsung DTV RCR", "Samsung DTV MainTVServer2")
            return if (tvType || tvDescription && type in setOf("urn:schemas-upnp-org:device:MediaRenderer:1", "urn:samsung.com:device:RemoteControlReceiver:1")) "Samsung television" else ""
        }
    }

    private class Description : DefaultHandler(), LexicalHandler {
        val values = LinkedHashMap<String, String>()
        var depth = 0
        var nodes = 0
        var rootDevice = false
        var field = ""
        val text = StringBuilder()

        override fun startElement(uri: String, local: String, q: String, attrs: Attributes) {
            if (++depth > 24 || ++nodes > 2000) throw SAXException("XML limit")
            val tag = if (local.isEmpty()) q else local
            if (depth == 1 && (tag != "root" || (uri.isNotEmpty() && uri != "urn:schemas-upnp-org:device-1-0"))) throw SAXException("Not a UPnP description")
            if (depth == 2) rootDevice = tag == "device"
            if (depth == 3 && rootDevice && tag in arrayOf("friendlyName", "manufacturer", "manufacturerURL", "X_MqttOnLan", "X_MqttTokenPairing", "modelName", "modelNumber", "modelDescription", "serialNumber", "UDN", "deviceType")) {
                field = tag
                text.setLength(0)
            }
        }
        override fun characters(chars: CharArray, start: Int, n: Int) {
            if (depth == 3 && field.isNotEmpty() && text.length < 512) text.append(chars, start, minOf(n, 512 - text.length))
        }
        override fun endElement(uri: String, local: String, q: String) {
            if (depth == 3 && field.isNotEmpty()) {
                val value = SafeText.label(text.toString(), 160)
                if (value.isNotEmpty()) values.putIfAbsent(field, value)
                field = ""
            }
            if (depth == 2) rootDevice = false
            depth--
        }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("External entity rejected")
        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("DTD rejected") }
        override fun endDTD() {}
        override fun startEntity(name: String?) {}
        override fun endEntity(name: String?) {}
        override fun startCDATA() {}
        override fun endCDATA() {}
        override fun comment(chars: CharArray, start: Int, n: Int) {}
        override fun error(e: SAXParseException) { throw e }
        override fun fatalError(e: SAXParseException) { throw e }
    }
}

