package io.github.kazands.networklens

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.charset.*
import java.util.Collections
import java.util.Locale
import java.util.TreeSet
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler

/** Bounded Probe/ProbeMatches subset; never fetch advertised addresses. */
class WsDiscovery private constructor() {
    enum class Version(
        @JvmField val discovery: String,
        @JvmField val addressing: String,
        @JvmField val destination: String
    ) {
        LEGACY("http://schemas.xmlsoap.org/ws/2005/04/discovery",
            "http://schemas.xmlsoap.org/ws/2004/08/addressing", "urn:schemas-xmlsoap-org:ws:2005:04:discovery"),
        STANDARD("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01",
            "http://www.w3.org/2005/08/addressing", "urn:docs-oasis-open-org:ws-dd:ns:discovery:2009:01")
    }

    class Result internal constructor(
        @JvmField val identity: String,
        @JvmField val kind: String,
        names: Collection<String>, hardware: Collection<String>, macs: Collection<String>, ports: Collection<Int>
    ) {
        @JvmField val names: List<String> = Collections.unmodifiableList(ArrayList(names))
        @JvmField val hardware: List<String> = Collections.unmodifiableList(ArrayList(hardware))
        @JvmField val macs: List<String> = Collections.unmodifiableList(ArrayList(macs))
        @JvmField val ports: List<Int> = Collections.unmodifiableList(ArrayList(ports))
        fun fingerprint(): String = identity + '\u0000' + kind + '\u0000' + names + '\u0000' +
            hardware + '\u0000' + macs + '\u0000' + ports
    }

    private class Match {
        var endpoint = ""
        var matchesResponder = false
        val fields = HashSet<String>()
        val names = TreeSet<String>()
        val hardware = TreeSet<String>()
        val macs = TreeSet<String>()
        val kinds = TreeSet<String>()
        val ports = TreeSet<Int>()
    }
    private class Tag(val uri: String, val name: String)

    companion object {
        const val MAX_PACKET = 16384
        private const val SOAP = "http://www.w3.org/2003/05/soap-envelope"
        private fun uuid(s: String?): Boolean =
            s != null && s.matches(Regex("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}"))

        @JvmStatic fun query(version: Version?, id: String?): ByteArray {
            require(version != null && uuid(id)) { "Invalid discovery request" }
            return ("<s:Envelope xmlns:s=\"" + SOAP + "\" xmlns:a=\"" + version.addressing +
                "\" xmlns:d=\"" + version.discovery + "\"><s:Header><a:Action>" + version.discovery +
                "/Probe</a:Action><a:MessageID>urn:uuid:" + id + "</a:MessageID><a:To>" +
                version.destination + "</a:To></s:Header><s:Body><d:Probe/></s:Body></s:Envelope>")
                .toByteArray(StandardCharsets.US_ASCII)
        }

        @JvmStatic fun parse(bytes: ByteArray?, length: Int, version: Version?, id: String?, responder: InetAddress?): List<Result> {
            if (bytes == null || length < 1 || length > MAX_PACKET || length > bytes.size ||
                version == null || !uuid(id) || responder !is Inet4Address || responder.isAnyLocalAddress ||
                responder.isLoopbackAddress || responder.isMulticastAddress) return Collections.emptyList()
            return try {
                val factory = SAXParserFactory.newInstance()
                factory.isNamespaceAware = true
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                val reader = factory.newSAXParser().xmlReader
                val handler = Reply(version, responder)
                reader.contentHandler = handler
                reader.errorHandler = handler
                reader.entityResolver = handler
                reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
                reader.parse(InputSource(ByteArrayInputStream(bytes, 0, length)))
                if (!handler.header || !handler.body || !handler.matches ||
                    handler.action != version.discovery + "/ProbeMatches" ||
                    !handler.related.equals("urn:uuid:" + id, true)) return Collections.emptyList()
                val results = ArrayList<Result>()
                for (m in handler.records) if (m.matchesResponder && m.endpoint.isNotEmpty()) {
                    val identity = if (m.endpoint.matches(Regex("(?i)urn:uuid:[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")))
                        "wsd:" + m.endpoint.substring(9).lowercase(Locale.ROOT) else ""
                    val kind = when {
                        "Video surveillance device" in m.kinds -> "Video surveillance device"
                        "Printer service" in m.kinds -> "Printer service"
                        "Computer" in m.kinds -> "Computer"
                        "ONVIF device" in m.kinds -> "ONVIF device"
                        else -> ""
                    }
                    results.add(Result(identity, kind, m.names, m.hardware, m.macs, m.ports))
                }
                results
            } catch (_: Exception) { Collections.emptyList() }
        }

        private fun words(text: String, maximum: Int): List<String> {
            if (text.isEmpty()) return Collections.emptyList()
            val list = text.javaSplit("\\s+")
            if (list.size > maximum) throw SAXException("List limit")
            return list.asList()
        }

        private fun types(text: String, namespaces: Map<String, String>, match: Match) {
            for (word in words(text, 16)) {
                if (!word.matches(Regex("[a-zA-Z_][a-zA-Z0-9_.-]*(?::[a-zA-Z_][a-zA-Z0-9_.-]*)?"))) continue
                val colon = word.indexOf(':')
                val uri = namespaces[if (colon < 0) "" else word.substring(0, colon)]
                val name = word.substring(colon + 1)
                if (uri == "http://www.onvif.org/ver10/network/wsdl" && name == "NetworkVideoTransmitter") match.kinds.add("Video surveillance device")
                else if (uri == "http://www.onvif.org/ver10/device/wsdl" && name == "Device") match.kinds.add("ONVIF device")
                else if (uri == "http://schemas.microsoft.com/windows/2006/08/wdp/print" && name == "PrintDeviceType") match.kinds.add("Printer service")
                else if (uri == "http://schemas.microsoft.com/windows/pub/2005/07" && name == "Computer") match.kinds.add("Computer")
            }
        }

        private fun scopes(text: String, match: Match) {
            for (word in words(text, 64)) try {
                val uri = URI(word)
                if (!"onvif".equals(uri.scheme, true) || !"www.onvif.org".equals(uri.host, true) ||
                    uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) continue
                val path = uri.rawPath!!.javaSplit("/", 3)
                if (path.size != 3) continue
                val category = path[1].lowercase(Locale.ROOT)
                if (category !in arrayOf("name", "hardware", "macaddress")) continue
                val value = decode(path[2])
                if (value.isEmpty()) continue
                if (category == "name" && match.names.size < 8) match.names.add(value)
                else if (category == "hardware" && match.hardware.size < 8) match.hardware.add(value)
                else if (category == "macaddress" && match.macs.size < 4) {
                    val mac = Address.mac(value)
                    if (mac.isNotEmpty()) match.macs.add(mac)
                }
            } catch (_: URISyntaxException) {} catch (_: IllegalArgumentException) {}
        }

        private fun decode(raw: String): String {
            try {
                val out = ByteArrayOutputStream()
                var i = 0
                while (i < raw.length) {
                    if (raw[i] == '%') {
                        if (i + 2 >= raw.length) return ""
                        val a = Character.digit(raw[i + 1], 16)
                        val b = Character.digit(raw[i + 2], 16)
                        if (a < 0 || b < 0) return ""
                        out.write(a * 16 + b)
                        i += 3
                    } else {
                        val cp = raw.codePointAt(i)
                        val encoded = String(Character.toChars(cp)).toByteArray(StandardCharsets.UTF_8)
                        out.write(encoded, 0, encoded.size)
                        i += Character.charCount(cp)
                    }
                }
                return SafeText.label(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray())).toString(), 160)
            } catch (_: CharacterCodingException) { return "" }
        }

        private fun addresses(text: String, responder: InetAddress, match: Match) {
            for (word in words(text, 8)) try {
                val uri = URI(word)
                var host = uri.host
                if (uri.scheme !in arrayOf("http", "https") || host == null || uri.rawUserInfo != null ||
                    uri.rawQuery != null || uri.rawFragment != null || uri.port == 0 || uri.port > 65535) continue
                if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length - 1)
                val numeric = Address.bytes(host)
                if (numeric != null && java.util.Arrays.equals(numeric, responder.address)) {
                    match.matchesResponder = true
                    val port = if (uri.port == -1) if (uri.scheme == "https") 443 else 80 else uri.port
                    if (match.ports.size < 4) match.ports.add(port)
                }
            } catch (_: URISyntaxException) {} catch (_: IllegalArgumentException) {}
        }
    }

    private class Reply(val version: Version, val responder: InetAddress) : DefaultHandler(), LexicalHandler {
        val records = ArrayList<Match>()
        val path = ArrayList<Tag>()
        val namespaces = ArrayList<Map<String, String>>()
        val mappings = HashMap<String, String>()
        val headers = HashSet<String>()
        val text = StringBuilder()
        var action = ""
        var related = ""
        var field = ""
        var fieldDepth = 0
        var nodes = 0
        var totalText = 0
        var header = false
        var body = false
        var matches = false
        var current: Match? = null

        private fun at(depth: Int, uri: String, name: String): Boolean =
            path.size >= depth && path[depth - 1].uri == uri && path[depth - 1].name == name

        override fun startPrefixMapping(prefix: String, uri: String) {
            if (prefix.length > 64 || uri.length > 256 || mappings.size >= 32) throw SAXException("Namespace limit")
            mappings[prefix] = uri
        }
        override fun startElement(uri: String, local: String, q: String, attrs: Attributes) {
            if (path.size >= 12 || ++nodes > 512 || attrs.length > 32 || local.length > 128 ||
                uri.length > 256 || fieldDepth != 0) throw SAXException("Discovery shape limit")
            val ns = if (namespaces.isEmpty()) HashMap() else HashMap(namespaces.last())
            ns.putAll(mappings)
            mappings.clear()
            if (ns.size > 64) throw SAXException("Namespace limit")
            namespaces.add(ns)
            path.add(Tag(uri, local))
            val depth = path.size
            if (depth == 1 && !at(1, SOAP, "Envelope")) throw SAXException("Not SOAP")
            if (depth == 2) {
                if (at(2, SOAP, "Header") && !header && !body) header = true
                else if (at(2, SOAP, "Body") && !body) body = true
                else throw SAXException("Repeated or invalid SOAP section")
            }
            if (depth == 3 && at(2, SOAP, "Header") && uri == version.addressing &&
                (local == "Action" || local == "RelatesTo")) {
                if (!headers.add(local)) throw SAXException("Repeated header")
                capture(local, depth)
            }
            if (depth == 3 && at(2, SOAP, "Body")) {
                if (!at(3, version.discovery, "ProbeMatches") || matches) throw SAXException("Not a probe reply")
                matches = true
            }
            if (depth == 4 && at(3, version.discovery, "ProbeMatches") && uri == version.discovery && local == "ProbeMatch") {
                if (records.size >= 8) throw SAXException("Match limit")
                current = Match().also { records.add(it) }
            }
            val match = current
            if (match != null && depth == 5 && at(4, version.discovery, "ProbeMatch") &&
                uri == version.discovery && local in arrayOf("Types", "Scopes", "XAddrs")) {
                if (!match.fields.add(local)) throw SAXException("Repeated field")
                capture(local, depth)
            }
            if (match != null && depth == 6 && at(4, version.discovery, "ProbeMatch") &&
                at(5, version.addressing, "EndpointReference") && uri == version.addressing && local == "Address") {
                if (!match.fields.add("Address")) throw SAXException("Repeated address")
                capture("Address", depth)
            }
        }
        private fun capture(name: String, depth: Int) { field = name; fieldDepth = depth; text.setLength(0) }

        override fun characters(chars: CharArray, start: Int, count: Int) {
            totalText += count
            if (totalText > 20000) throw SAXException("Text limit")
            if (fieldDepth != 0) {
                val cap = when (field) { "Scopes" -> 4096; "XAddrs" -> 2048; "Types" -> 1024; else -> 512 }
                if (text.length + count > cap) throw SAXException("Field limit")
                text.append(chars, start, count)
            }
        }
        override fun endElement(uri: String, local: String, q: String) {
            val depth = path.size
            if (fieldDepth == depth) {
                val value = text.toString().javaTrim()
                when (field) {
                    "Action" -> action = value
                    "RelatesTo" -> related = value
                    "Address" -> try {
                        val endpoint = URI(value)
                        if (!endpoint.isAbsolute) throw URISyntaxException(value, "Relative endpoint")
                        current!!.endpoint = value
                    } catch (_: URISyntaxException) { throw SAXException("Invalid endpoint") }
                    "Types" -> types(value, namespaces.last(), current!!)
                    "Scopes" -> scopes(value, current!!)
                    "XAddrs" -> addresses(value, responder, current!!)
                }
                fieldDepth = 0
                field = ""
            }
            if (depth == 4 && at(4, version.discovery, "ProbeMatch")) current = null
            path.removeAt(depth - 1)
            namespaces.removeAt(depth - 1)
        }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("Entity rejected")
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

