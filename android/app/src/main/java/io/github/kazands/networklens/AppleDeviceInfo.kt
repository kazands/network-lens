package io.github.kazands.networklens

import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler

/** Read-only Lockdown discovery: QueryType, then only GetValue(ProductType). No pairing or sessions. */
internal object AppleDeviceInfo {
    const val PORT = 62078
    const val SOURCE = "Apple device model"
    const val MODEL_SOURCE = "Apple device service ProductType"
    const val SERVICE = "Apple device service"
    const val MAX_BODY = 8192
    private const val TYPE = "com.apple.mobile.lockdown"
    private const val PLIST_DTD = "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
    enum class Status(val text: String) {
        MODEL("Apple service verified; model received"),
        WITHHELD("Apple service verified; model not provided"),
        UNAVAILABLE("Apple service verified; model lookup unavailable"),
        OTHER("Service did not identify itself as an Apple device service")
    }
    data class Report(val status: Status, val model: String = "", val failure: MetadataHttp.Reason? = null)

    fun request(model: Boolean): ByteArray {
        val fields = if (model) "<key>Request</key><string>GetValue</string><key>Key</key><string>ProductType</string>"
            else "<key>Request</key><string>QueryType</string>"
        val body = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><plist version=\"1.0\"><dict>" +
            "<key>Label</key><string>Network Lens</string>" + fields + "</dict></plist>").toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(body.size); write(body) } }.toByteArray()
    }

    /** Parse a small flat string dictionary only. Never resolve a DTD, entity or nested value. */
    fun parse(body: ByteArray, diagnosticFields: Boolean = false): Map<String, String> {
        if (body.size !in 1..MAX_BODY) throw IOException("Invalid Apple response size")
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString()
            // Apple's normal plist declaration is inert. All other declarations remain forbidden.
            val dtd = text.indexOf(PLIST_DTD)
            if (dtd >= 0 && (text.indexOf("<plist") in 0 until dtd || text.indexOf(PLIST_DTD, dtd + 1) >= 0))
                throw SAXException("Misplaced plist declaration")
            val xml = if (dtd >= 0) text.removeRange(dtd, dtd + PLIST_DTD.length) else text
            if (xml.contains("<!")) throw SAXException("XML declaration rejected")
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val handler = Plist(diagnosticFields)
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler; reader.errorHandler = handler; reader.entityResolver = handler
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.parse(InputSource(StringReader(xml)))
            if (!handler.complete) throw SAXException("Incomplete plist")
            return handler.values
        } catch (_: Exception) { throw IOException("Invalid Apple response") }
    }

    fun serviceResponse(fields: Map<String, String>): Boolean =
        fields["Request"] == "QueryType" && !fields.containsKey("Error") && fields["Type"] == TYPE

    fun modelResponse(fields: Map<String, String>): Report {
        if (fields["Request"] != "GetValue" || fields["Key"] != "ProductType") throw IOException("Uncorrelated Apple model response")
        if (fields.containsKey("Error")) {
            if (fields.containsKey("Value")) throw IOException("Contradictory Apple model response")
            return Report(Status.WITHHELD)
        }
        val model = fields["Value"] ?: return Report(Status.WITHHELD)
        if (model.length !in 1..64 || !model.matches(Regex("[A-Za-z][A-Za-z0-9]{0,47},[0-9]{1,6}")))
            throw IOException("Invalid Apple model code")
        return Report(Status.MODEL, model)
    }

    /** Caller binds this socket to selected Wi-Fi and owns cancellation/closure. */
    fun exchange(socket: Socket, peer: InetAddress, deadline: Long, timer: ScheduledExecutorService,
                 port: Int = PORT, diagnostics: DiagnosticEvidence? = null, attempt: Int = 1, active: () -> Boolean): Report {
        val started = System.nanoTime()
        val budgetMillis = maxOf(0, TimeUnit.NANOSECONDS.toMillis(deadline - started))
        var operation = "Connect"
        var step = "Opening socket"
        var received = 0
        var connectMillis = -1L
        var connectTimeout = 0
        var readTimeout = 0
        var outcome = "Exchange did not finish"
        fun remaining(): Int {
            if (!active() || Thread.currentThread().isInterrupted) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
            val left = deadline - System.nanoTime()
            if (left <= 0) throw MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE)
            return maxOf(1, minOf(600, TimeUnit.NANOSECONDS.toMillis(left))).toInt()
        }
        remaining()
        val timeout = timer.schedule({ try { socket.close() } catch (_: IOException) {} },
            maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        var stage = 0
        var verified = false
        try {
            connectTimeout = minOf(400, remaining())
            socket.connect(InetSocketAddress(peer, port), connectTimeout)
            connectMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            fun roundTrip(model: Boolean): Map<String, String> {
                operation = if (model) "GetValue(ProductType)" else "QueryType"
                step = "Writing request"; received = 0; readTimeout = 0
                remaining(); stage = 1; socket.getOutputStream().write(request(model)); stage = 2
                step = "Reading response header"
                val input = socket.getInputStream()
                fun read(size: Int): ByteArray {
                    val bytes = ByteArray(size); var offset = 0
                    while (offset < size) {
                        readTimeout = remaining(); socket.soTimeout = readTimeout
                        val got = input.read(bytes, offset, size - offset)
                        if (got < 0) throw EOFException()
                        offset += got; received += got
                    }
                    return bytes
                }
                val size = DataInputStream(ByteArrayInputStream(read(4))).readInt()
                if (size !in 1..MAX_BODY) throw MetadataHttp.Failure(MetadataHttp.Reason.INVALID_METADATA)
                step = "Reading response body"
                val body = read(size); remaining(); stage = 3; step = "Validating response"
                val fields = parse(body, true)
                for ((key, value) in fields) {
                    val keep = key == "Error" && value in setOf("PasswordProtected", "GetProhibited", "MissingValue", "InvalidArgument", "InvalidHostID", "MissingHostID", "PairingDialogResponsePending", "UserDeniedPairing", "SessionInactive", "InvalidService", "UnknownError") ||
                        key == "Value" && model && value.matches(Regex("[A-Za-z][A-Za-z0-9]{0,47},[0-9]{1,6}")) ||
                        key == "Request" && value in setOf("QueryType", "GetValue") || key == "Type" && value == TYPE || key == "Key" && value == "ProductType"
                    diagnostics?.record(SOURCE + if (model) " GetValue" else " QueryType", DiagnosticEvidence.fieldName(key),
                        if (keep) value else "[value withheld; ${value.length} characters]", "Returned Apple field; acceptance is recorded in lookup outcome")
                }
                return fields
            }
            if (!serviceResponse(roundTrip(false))) { outcome = Status.OTHER.text; return Report(Status.OTHER) }
            verified = true
            val report = modelResponse(roundTrip(true)); remaining(); outcome = report.status.text; step = "Complete"; return report
        } catch (e: Exception) {
            if (e is MetadataHttp.Failure) {
                outcome = e.reason.detail
                if (verified && e.reason !in setOf(MetadataHttp.Reason.CANCELLED, MetadataHttp.Reason.DEADLINE))
                    return Report(Status.UNAVAILABLE, failure = e.reason)
                throw e
            }
            val reason = when {
                !active() || Thread.currentThread().isInterrupted -> MetadataHttp.Reason.CANCELLED
                System.nanoTime() >= deadline -> MetadataHttp.Reason.DEADLINE
                stage == 3 -> MetadataHttp.Reason.INVALID_METADATA
                stage == 0 && e is SocketTimeoutException -> MetadataHttp.Reason.CONNECTION_TIMEOUT
                stage == 0 && e is ConnectException -> MetadataHttp.Reason.CONNECTION_REFUSED
                stage == 0 && e is NoRouteToHostException -> MetadataHttp.Reason.ROUTING_FAILED
                stage == 0 -> MetadataHttp.Reason.CONNECTION_FAILED
                stage == 1 -> MetadataHttp.Reason.WRITE_FAILED
                e is SocketTimeoutException -> MetadataHttp.Reason.READ_TIMEOUT
                else -> MetadataHttp.Reason.READ_FAILED
            }
            outcome = reason.detail + if (reason in setOf(MetadataHttp.Reason.CANCELLED, MetadataHttp.Reason.DEADLINE,
                MetadataHttp.Reason.INVALID_METADATA)) "" else "; " + TransportDiagnostic.failure(e).text
            if (verified && reason != MetadataHttp.Reason.CANCELLED && reason != MetadataHttp.Reason.DEADLINE)
                return Report(Status.UNAVAILABLE, failure = reason)
            throw MetadataHttp.Failure(reason, cause = e)
        } finally {
            timeout.cancel(false)
            if (diagnostics != null && active() && !Thread.currentThread().isInterrupted) {
                val elapsed = maxOf(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                val number = attempt.coerceIn(1, 2)
                diagnostics.record(SOURCE, "Transport attempt $number", "$operation: $step; $outcome; Apple service verified: $verified",
                    "Transport observation only; does not identify the hardware or explain why the peer closed", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
                diagnostics.record(SOURCE, "Transport timing $number", "connect ${if (connectMillis < 0) elapsed else connectMillis} ms; elapsed $elapsed ms; budget at start $budgetMillis ms; connect timeout $connectTimeout ms; read timeout $readTimeout ms; response bytes $received",
                    "Response bytes refer to the current request; zero read timeout means reading was not reached", DiagnosticEvidence.Retention.IDENTITY_OR_OUTCOME)
            }
        }
    }

    private class Plist(val diagnosticFields: Boolean) : DefaultHandler(), LexicalHandler {
        val values = LinkedHashMap<String, String>()
        private val keys = HashSet<String>()
        private var depth = 0
        private var nodes = 0
        private var dictionary = false
        private var pending: String? = null
        private var tag = ""
        private val text = StringBuilder()
        var complete = false
        override fun startElement(uri: String, local: String, q: String, attrs: Attributes) {
            if (++depth > 3 || ++nodes > 34 || uri.isNotEmpty() || q != local) throw SAXException("Invalid plist structure")
            if (depth == 1) {
                if (complete || local != "plist" || attrs.length != 1 || attrs.getValue("version") != "1.0") throw SAXException("Invalid plist root")
            } else if (attrs.length != 0 || depth == 2 && (local != "dict" || dictionary) ||
                depth == 3 && local != (if (pending == null) "key" else "string")) throw SAXException("Invalid plist field")
            if (depth == 2) dictionary = true
            tag = local; text.setLength(0)
        }
        override fun characters(chars: CharArray, start: Int, length: Int) {
            if (depth != 3) {
                if ((start until start + length).any { !chars[it].isWhitespace() }) throw SAXException("Unexpected plist text")
            } else {
                if (text.length + length > 256) throw SAXException("Plist text limit")
                text.append(chars, start, length)
            }
        }
        override fun endElement(uri: String, local: String, q: String) {
            if (depth == 3) {
                if (tag == "key") {
                    val key = text.toString()
                    if (key.isEmpty() || !keys.add(key)) throw SAXException("Duplicate plist key")
                    pending = key
                } else {
                    if (diagnosticFields || pending in setOf("Request", "Type", "Key", "Value", "Error")) values[pending!!] = text.toString()
                    pending = null
                }
            }
            if (depth == 2 && pending != null) throw SAXException("Missing plist value")
            if (depth == 1) complete = dictionary
            depth--
        }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("External entity rejected")
        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("DTD rejected") }
        override fun endDTD() {}
        override fun startEntity(name: String?) {}
        override fun endEntity(name: String?) {}
        override fun startCDATA() { throw SAXException("CDATA rejected") }
        override fun endCDATA() {}
        override fun comment(chars: CharArray, start: Int, length: Int) {}
        override fun error(e: SAXParseException) { throw e }
        override fun fatalError(e: SAXParseException) { throw e }
    }
}
