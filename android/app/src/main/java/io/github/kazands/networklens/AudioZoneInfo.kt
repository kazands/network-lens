package io.github.kazands.networklens

import java.io.*
import java.net.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler

/** Two fixed read-only queries. Never follows topology URLs or subscribes/changes playback. */
internal object AudioZoneInfo {
    private const val TOPOLOGY = "urn:schemas-upnp-org:service:ZoneGroupTopology:1"
    private const val SOAP = "http://schemas.xmlsoap.org/soap/envelope/"
    private const val UPNP = "urn:schemas-upnp-org:device-1-0"
    fun vsslCandidate(service: MdnsPacket.Service): Boolean =
        LocalServices.canonicalType(service.type) == "_airplay._tcp." &&
            service.attributes.filterKeys { it.equals("manufacturer", true) }.values.singleOrNull()
                ?.contentEquals("VSSL".toByteArray()) == true

    fun sonosIdentity(body: ByteArray): String? {
        val root = xml(body) ?: return null
        if (root.name != "root" || root.ns !in setOf("", UPNP)) return null
        val d = root.one("device", root.ns) ?: return null
        val maker = d.one("manufacturer", root.ns)?.scalar() ?: return null
        if (DeviceCatalog.key(maker) !in setOf("sonos", "sonos, inc.", "sonos inc.")) return null
        return uid(d.one("UDN", root.ns)?.scalar()?.removePrefix("uuid:"))
    }
    private fun uid(value: String?): String? = value?.takeIf { it.matches(Regex("RINCON_[A-Fa-f0-9]{12}[0-9]{5}")) }
        ?.uppercase(java.util.Locale.ROOT)?.let(AudioZones::token)

    fun request(kind: AudioZones.Kind, peer: InetAddress): ByteArray {
        if (kind == AudioZones.Kind.VSSL) return byteArrayOf(16, 0, 1, 8)
        val body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"$SOAP\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:GetZoneGroupState xmlns:u=\"$TOPOLOGY\"/></s:Body></s:Envelope>".toByteArray(Charsets.UTF_8)
        val host = peer.hostAddress!!.substringBefore('%').let { if (it.contains(':')) "[$it]" else it }
        return ("POST /ZoneGroupTopology/Control HTTP/1.1\r\nHost: $host:1400\r\n" +
            "SOAPACTION: \"$TOPOLOGY#GetZoneGroupState\"\r\nContent-Type: text/xml; charset=\"utf-8\"\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII) + body
    }

    fun vssl(input: InputStream): AudioZones.Report? {
        val head = ByteArray(3); DataInputStream(input).readFully(head)
        if (head[0] != 16.toByte() || head[1] != 0.toByte()) return null
        val size = head[2].toInt() and 255
        if (size < 2) return null
        val data = ByteArray(size); DataInputStream(input).readFully(data)
        if (data[0] != 8.toByte()) return null
        val fields = BoundedJson.fields(data.copyOfRange(1, size), setOf("mc", "id", "tp")) ?: return null
        val model = when (fields["tp"]) { "1" -> "A.1"; "3" -> "A.3"; "6" -> "A.6"; "11" -> "A.1x"; "13" -> "A.3x"; "16" -> "A.6x"; else -> return null }
        val zone = fields["id"]?.toIntOrNull() ?: return null
        val count = model[2].digitToInt()
        if (count == 1 && zone != 7 || count > 1 && zone !in 1..count) return null
        // 'mc' identifies the chassis (zone-one interface), never this zone's hardware MAC.
        val chassis = fields["mc"]?.takeIf { it.matches(Regex("[A-Fa-f0-9]{12}")) && Address.mac(it).isNotEmpty() } ?: return null
        return AudioZones.Report(AudioZones.Kind.VSSL, AudioZones.token(chassis.uppercase(java.util.Locale.ROOT)), model, if (zone == 7) 1 else zone)
    }

    fun sonos(body: ByteArray, self: String): AudioZones.Report? {
        val envelope = xml(body) ?: return null
        if (envelope.name != "Envelope" || envelope.ns != SOAP) return null
        val response = envelope.one("Body", SOAP)?.one("GetZoneGroupStateResponse", TOPOLOGY) ?: return null
        val payload = response.one("ZoneGroupState", "")?.scalar() ?: return null
        val root = xml(payload.toByteArray(Charsets.UTF_8)) ?: return null
        if (root.name != "ZoneGroupState" || root.ns.isNotEmpty()) return null
        val container = if (root.children.any { it.name == "ZoneGroups" }) root.one("ZoneGroups", "") ?: return null else root
        val seen = HashSet<String>(); val groups = ArrayList<AudioZones.Report>()
        for (group in container.children.filter { it.name == "ZoneGroup" && it.ns.isEmpty() }) {
            val coordinator = uid(group.attrs["Coordinator"]) ?: return null
            val id = group.attrs["ID"]?.takeIf { it.length in 1..128 && it.all { c -> c.code in 33..126 } } ?: return null
            val members = ArrayList<AudioZones.Member>()
            fun add(node: BoundedXml.Node, parent: String = ""): String? {
                val memberId = uid(node.attrs["UUID"]) ?: return null
                if (!seen.add(memberId) || seen.size > 64) return null
                val room = node.attrs["ZoneName"].orEmpty()
                if (room.length > 160 || SafeText.label(room, 160) != room) return null
                members.add(AudioZones.Member(memberId, room, parent)); return memberId
            }
            for (member in group.children.filter { it.name == "ZoneGroupMember" && it.ns.isEmpty() }) {
                val parent = add(member) ?: return null
                for (satellite in member.children.filter { it.name == "Satellite" && it.ns.isEmpty() }) if (add(satellite, parent) == null) return null
            }
            if (members.none { it.id == coordinator && it.parent.isEmpty() }) return null
            groups.add(AudioZones.Report(AudioZones.Kind.SONOS, self, group = AudioZones.token(id), coordinator = coordinator, members = members.toList()))
        }
        return groups.singleOrNull { it.members.any { member -> member.id == self } }
    }

    fun exchange(socket: Socket, peer: InetAddress, kind: AudioZones.Kind, self: String,
                 deadline: Long, timer: ScheduledExecutorService, active: () -> Boolean): AudioZones.Report {
        fun check() {
            if (!active() || Thread.currentThread().isInterrupted) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
            if (System.nanoTime() >= deadline) throw MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE)
        }
        check()
        val closer = timer.schedule({ try { socket.close() } catch (_: IOException) {} }, maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        var connected = false
        try {
            socket.connect(InetSocketAddress(peer, kind.port), 400); connected = true; check()
            socket.soTimeout = 600
            socket.getOutputStream().write(request(kind, peer)); check()
            val raw = socket.getInputStream()
            val input = object : FilterInputStream(raw) {
                override fun read(): Int { check(); return raw.read() }
                override fun read(b: ByteArray, off: Int, len: Int): Int { check(); return raw.read(b, off, len) }
            }
            val report = if (kind == AudioZones.Kind.VSSL) vssl(input) else sonos(UpnpDescription.readResponse(input), self)
            check(); return report ?: throw MetadataHttp.Failure(MetadataHttp.Reason.INVALID_METADATA)
        } catch (e: Exception) {
            check()
            if (e is MetadataHttp.Failure) throw e
            throw MetadataHttp.Failure(when (e) {
                is SocketTimeoutException -> if (connected) MetadataHttp.Reason.READ_TIMEOUT else MetadataHttp.Reason.CONNECTION_TIMEOUT
                is ConnectException -> MetadataHttp.Reason.CONNECTION_FAILED
                is NoRouteToHostException -> MetadataHttp.Reason.ROUTING_FAILED
                is UpnpDescription.HttpStatusException -> MetadataHttp.Reason.HTTP_STATUS
                else -> MetadataHttp.Reason.READ_FAILED
            }, (e as? UpnpDescription.HttpStatusException)?.statusCode, e)
        } finally { closer.cancel(false) }
    }

    private fun xml(bytes: ByteArray) = BoundedXml.parse(bytes)
}
