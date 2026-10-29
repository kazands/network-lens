package dev.netlens

import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.Collections
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler

/** Read-only SADP inquiry; no credentials, configuration or video commands. */
class Sadp private constructor() {
    companion object {
        const val MAX_PACKET = 8192
        private fun uuid(value: String?): Boolean =
            value != null && value.matches(Regex("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}"))

        @JvmStatic fun query(id: String?): ByteArray {
            require(uuid(id)) { "Invalid inquiry identifier" }
            return ("<?xml version=\"1.0\" encoding=\"utf-8\"?><Probe><Uuid>" + id +
                "</Uuid><Types>inquiry</Types></Probe>").toByteArray(StandardCharsets.US_ASCII)
        }

        @JvmStatic fun parse(packet: ByteArray?, length: Int, id: String?, responder: InetAddress?): Map<String, String> {
            if (packet == null || length < 1 || length > MAX_PACKET || length > packet.size ||
                !uuid(id) || responder !is Inet4Address) return Collections.emptyMap()
            return try {
                val factory = SAXParserFactory.newInstance()
                factory.isNamespaceAware = true
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                val reader = factory.newSAXParser().xmlReader
                val handler = Reply()
                reader.contentHandler = handler
                reader.errorHandler = handler
                reader.entityResolver = handler
                reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
                reader.parse(InputSource(ByteArrayInputStream(packet, 0, length)))
                val fields = handler.fields
                if (!id.equals(fields["Uuid"], true) || fields["Types"] != "inquiry" ||
                    responder.hostAddress != fields["IPv4Address"]) return Collections.emptyMap()
                fields.remove("Uuid")
                fields.remove("Types")
                fields.remove("IPv4Address")
                fields
            } catch (_: Exception) { Collections.emptyMap() }
        }

        @JvmStatic fun port(value: String?): Int = try {
            if (value == null || !value.matches(Regex("[0-9]{1,5}"))) 0
            else Integer.parseInt(value).let { if (it in 1..65535) it else 0 }
        } catch (_: NumberFormatException) { 0 }
    }

    private class Reply : DefaultHandler(), LexicalHandler {
        val fields = LinkedHashMap<String, String>()
        var depth = 0
        var nodes = 0
        var field = ""
        val text = StringBuilder()

        override fun startElement(uri: String, local: String, q: String, attrs: Attributes) {
            if (++depth > 4 || ++nodes > 128 || uri.isNotEmpty()) throw SAXException("Invalid inquiry reply")
            val tag = if (local.isEmpty()) q else local
            if (depth == 1 && tag != "ProbeMatch") throw SAXException("Not an inquiry reply")
            if (depth == 2) { field = if (tag in KEYS) tag else ""; text.setLength(0) }
            if (depth > 2 && field.isNotEmpty()) throw SAXException("Nested identity field")
        }
        override fun characters(chars: CharArray, start: Int, count: Int) {
            if (depth == 2 && field.isNotEmpty()) {
                if (text.length + count > 256) throw SAXException("Identity field limit")
                text.append(chars, start, count)
            }
        }
        override fun endElement(uri: String, local: String, q: String) {
            if (depth == 2 && field.isNotEmpty()) {
                val value = SafeText.label(text.toString(), 160)
                if (fields.put(field, value) != null) throw SAXException("Repeated identity field")
                field = ""
            }
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

        companion object {
            private val KEYS = setOf("Uuid", "Types", "IPv4Address", "DeviceDescription", "SoftwareVersion",
                "MAC", "HttpPort", "CommandPort", "DigitalChannelNum", "SpecificDeviceType")
        }
    }
}

