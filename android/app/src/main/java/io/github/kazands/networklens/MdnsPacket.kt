package io.github.kazands.networklens

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.InetAddress
import java.util.Arrays
import java.util.Locale
import java.util.TreeMap
import java.util.TreeSet

/** Bounded local mDNS packets; advertised addresses do not establish presence. */
class MdnsPacket private constructor() {
    class Result {
        @JvmField val serviceTypes: MutableSet<String> = TreeSet()
        @JvmField val hostnames: MutableMap<String, String> = TreeMap()
        @JvmField val services: MutableMap<String, Service> = TreeMap()
        @JvmField val addresses: MutableMap<String, MutableSet<String>> = TreeMap()
        @JvmField val records: MutableList<Record> = ArrayList()
        @JvmField var freshResponse = false
        /** Diagnostic only; does not authorize presence or identity. */
        @JvmField var parsedResponse = false

        /** One complete current packet, with SRV/TXT and target address equal to the sender.
         * Proxy-only advertisements cannot qualify through this association. */
        fun directServices(peer: InetAddress): List<Service> {
            if (!freshResponse || peer.isAnyLocalAddress || peer.isLoopbackAddress || peer.isMulticastAddress) return emptyList()
            return services.values.filter { service ->
                service.ptrReceived && service.txtReceived && service.port in 1..65535 &&
                    addresses[service.host.lowercase(Locale.ROOT)]?.any { address ->
                        Arrays.equals(Address.bytes(address), peer.address)
                    } == true
            }
        }
    }
    class Service(@JvmField val name: String, @JvmField val type: String) {
        @JvmField var host = ""
        @JvmField var port = 0
        @JvmField var ptrReceived = false
        @JvmField var txtReceived = false
        @JvmField val attributes: MutableMap<String, ByteArray> = TreeMap()
        fun copy(): Service {
            val copy = Service(name, type)
            copy.host = host
            copy.port = port
            copy.ptrReceived = ptrReceived
            copy.txtReceived = txtReceived
            for ((key, value) in attributes) copy.attributes[key] = value.clone()
            return copy
        }
    }
    /** Label boundaries are retained: a dot inside an instance is not a DNS separator. */
    data class Question(val labels: List<String>, val kind: Int)
    data class Record(val owner: List<String>, val kind: Int, val ttl: Long, val flush: Boolean,
                      val target: List<String> = emptyList(), val port: Int = 0,
                      val attributes: Map<String, ByteArray> = emptyMap(), val address: String = "")
    private class Name(val value: String, val plain: Boolean, val labels: List<String>)

    companion object {
        const val ENUMERATION = "_services._dns-sd._udp.local"
        const val MAX_PACKET = 8192

        internal fun key(labels: List<String>): String = labels.joinToString("") {
            val folded = it.map { c -> if (c in 'A'..'Z') c + 32 else c }.joinToString("")
            "${folded.length}:$folded"
        }
        internal fun instanceType(labels: List<String>): String =
            if (labels.size == 4) serviceType(labels.drop(1).joinToString(".")) else ""
        private fun safeLabels(labels: List<String>): Boolean = labels.isNotEmpty() &&
            labels.sumOf { it.toByteArray(StandardCharsets.UTF_8).size + 1 } < 255 &&
            labels.all { label -> label.toByteArray(StandardCharsets.UTF_8).size in 1..63 &&
                label.none { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() || it == '\ufffd' } }

        /** Only local SRV/TXT or host-address follow-ups, never an arbitrary DNS server. */
        @JvmStatic fun followUp(questions: List<Question>): ByteArray {
            require(questions.size in 1..8)
            val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
            out.writeShort(0); out.writeShort(0); out.writeShort(questions.size); out.write(ByteArray(6))
            for (q in questions) {
                require(safeLabels(q.labels) && q.labels.last().equals("local", true))
                require(if (q.kind == 16 || q.kind == 33) instanceType(q.labels).isNotEmpty()
                    else q.kind in listOf(1, 28) && q.labels.size >= 2 && q.labels.all { it.matches(Regex("[a-zA-Z0-9_-]+")) })
                for (label in q.labels) { val b = label.toByteArray(StandardCharsets.UTF_8); out.writeByte(b.size); out.write(b) }
                out.writeByte(0); out.writeShort(q.kind); out.writeShort(1)
            }
            require(bytes.size() <= 1200) { "DNS question size limit" }
            return bytes.toByteArray()
        }

        @JvmStatic fun serviceType(name: String?): String {
            if (name == null) return ""
            var value = name.lowercase(Locale.ROOT)
            if (value.endsWith(".")) value = value.substring(0, value.length - 1)
            if (!value.matches(Regex("_[a-z0-9][a-z0-9_-]{0,61}\\._(?:tcp|udp)\\.local"))) return ""
            return value.substring(0, value.length - 6) + "."
        }

        @JvmStatic fun query(names: Collection<String?>?): ByteArray {
            require(names != null && names.isNotEmpty() && names.size <= 8) { "One to eight local questions required" }
            try {
                val bytes = ByteArrayOutputStream()
                val out = DataOutputStream(bytes)
                out.writeShort(0)
                out.writeShort(0)
                out.writeShort(names.size)
                out.write(ByteArray(6))
                for (name in names) {
                    require(name != null && name.length <= 253 && name.matches(Regex("[a-zA-Z0-9_.-]+")) &&
                        !name.endsWith(".") && (name == ENUMERATION || name.endsWith(".in-addr.arpa") ||
                        name.endsWith(".ip6.arpa") || serviceType(name).isNotEmpty())) {
                        "Only local service or reverse questions allowed"
                    }
                    for (label in name.javaSplit("\\.", -1)) {
                        require(label.isNotEmpty() && label.length <= 63) { "Invalid DNS label" }
                        out.writeByte(label.length)
                        out.write(label.toByteArray(StandardCharsets.US_ASCII))
                    }
                    out.writeByte(0)
                    out.writeShort(12)
                    out.writeShort(1)
                }
                require(bytes.size() <= 1200) { "DNS question size limit" }
                return bytes.toByteArray()
            } catch (e: IOException) { throw AssertionError(e) }
        }

        /** Only complete, empty-answer multicast questions can defer our own identical query.
         * Query answer sections are never consumed as presence, identity or cached records. */
        internal fun multicastQuestions(data: ByteArray, length: Int): List<Question> {
            if (length !in 12..1200 || length > data.size) return emptyList()
            try {
                if (u16(data, 0) != 0 || u16(data, 2) != 0 || u16(data, 4) !in 1..8 ||
                    u16(data, 6) != 0 || u16(data, 8) != 0 || u16(data, 10) != 0) return emptyList()
                val pos = intArrayOf(12); val out = ArrayList<Question>()
                repeat(u16(data, 4)) {
                    val owner = name(data, length, pos)
                    if (pos[0] + 4 > length || !safeLabels(owner.labels)) return emptyList()
                    val kind = u16(data, pos[0]); val cls = u16(data, pos[0] + 2)
                    pos[0] += 4
                    // QU, probes, foreign domains and unsupported question kinds do not coalesce.
                    if (cls != 1 || kind !in listOf(1, 12, 16, 28, 33) ||
                        !(owner.value.endsWith(".local", true) || owner.value.endsWith(".in-addr.arpa", true) ||
                          owner.value.endsWith(".ip6.arpa", true))) return emptyList()
                    out.add(Question(owner.labels.toList(), kind))
                }
                return if (pos[0] == length) out else emptyList()
            } catch (_: IllegalArgumentException) { return emptyList() }
            catch (_: IndexOutOfBoundsException) { return emptyList() }
        }

        /** RFC 6762 section 7.2: a complete, at-most-four-packet known-PTR-answer sequence.
         * If the whole sequence cannot fit, send an ordinary query without suppression. */
        internal fun queryPackets(names: List<String>, answers: List<Record>): List<ByteArray> {
            val base = query(names)
            if (answers.isEmpty() || answers.size > 64) return listOf(base)
            val owners = names.map { key(it.split('.')) }.toSet()
            fun wireName(labels: List<String>): ByteArray {
                require(safeLabels(labels))
                val bytes = ByteArrayOutputStream()
                for (label in labels) {
                    val value = label.toByteArray(StandardCharsets.UTF_8)
                    bytes.write(value.size); bytes.write(value)
                }
                bytes.write(0); return bytes.toByteArray()
            }
            val records = try {
                answers.map { r ->
                    require(r.kind == 12 && key(r.owner) in owners && r.ttl in 1..0xffffffffL &&
                        r.owner.last().equals("local", true) && r.target.last().equals("local", true))
                    val target = wireName(r.target)
                    val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
                    out.write(wireName(r.owner)); out.writeShort(12); out.writeShort(1)
                    out.writeInt(r.ttl.toInt()); out.writeShort(target.size); out.write(target)
                    bytes.toByteArray()
                }
            } catch (_: IllegalArgumentException) { return listOf(base) }
            catch (_: NoSuchElementException) { return listOf(base) }
            val packets = ArrayList<ByteArray>(); var bytes = ByteArrayOutputStream().apply { write(base) }
            var count = 0
            fun finish(more: Boolean) {
                val packet = bytes.toByteArray()
                packet[2] = if (more) 2 else 0 // TC; all other query flags stay zero.
                packet[6] = (count ushr 8).toByte(); packet[7] = count.toByte()
                packets.add(packet)
            }
            for (record in records) {
                if (bytes.size() + record.size > 1200) {
                    finish(true)
                    if (packets.size >= 4) return listOf(base)
                    bytes = ByteArrayOutputStream().apply { write(ByteArray(12)) }; count = 0
                }
                if (bytes.size() + record.size > 1200) return listOf(base)
                bytes.write(record); count++
            }
            finish(false)
            return packets
        }

        @JvmStatic fun parse(data: ByteArray?, length: Int): Result {
            val result = Result()
            if (data == null || length < 12 || length > data.size || length > MAX_PACKET) return result
            try {
                if (u16(data, 0) != 0 || (u16(data, 2) and 0xfa0f) != 0x8000) return result
                val questions = u16(data, 4)
                val records = u16(data, 6) + u16(data, 8) + u16(data, 10)
                if (questions > 32 || records > 128) return result
                val pos = intArrayOf(12)
                repeat(questions) {
                    name(data, length, pos)
                    if (pos[0] + 4 > length) throw IllegalArgumentException()
                    pos[0] += 4
                }
                repeat(records) {
                    val owner = name(data, length, pos)
                    if (pos[0] + 10 > length) throw IllegalArgumentException()
                    val kind = u16(data, pos[0])
                    val cls = u16(data, pos[0] + 2)
                    val size = u16(data, pos[0] + 8)
                    val live = (data[pos[0] + 4].toInt() or data[pos[0] + 5].toInt() or
                        data[pos[0] + 6].toInt() or data[pos[0] + 7].toInt()) != 0
                    pos[0] += 10
                    val end = pos[0] + size
                    if (end > length) throw IllegalArgumentException()
                    if ((cls and 0x7fff) == 1) {
                        val ttl = ((data[pos[0] - 6].toLong() and 255) shl 24) or
                            ((data[pos[0] - 5].toLong() and 255) shl 16) or
                            ((data[pos[0] - 4].toLong() and 255) shl 8) or (data[pos[0] - 3].toLong() and 255)
                        wireRecord(data, length, owner, kind, ttl, cls and 0x8000 != 0, pos[0], end)?.let { result.records.add(it) }
                    }
                    if ((cls and 0x7fff) == 1 && live) {
                        val ownerKey = owner.value.lowercase(Locale.ROOT)
                        val local = ownerKey.endsWith(".local")
                        if (kind == 1 && local) {
                            if (size != 4) throw IllegalArgumentException()
                            if (owner.plain) result.addresses.getOrPut(ownerKey) { TreeSet() }
                                .add(InetAddress.getByAddress(Arrays.copyOfRange(data, pos[0], end)).hostAddress!!)
                            result.freshResponse = true
                        } else if (kind == 28 && local) {
                            if (size != 16) throw IllegalArgumentException()
                            if (owner.plain) result.addresses.getOrPut(ownerKey) { TreeSet() }
                                .add(InetAddress.getByAddress(Arrays.copyOfRange(data, pos[0], end)).hostAddress!!)
                            result.freshResponse = true
                        } else if (kind == 33 && local) {
                            if (size < 7) throw IllegalArgumentException()
                            val targetPos = intArrayOf(pos[0] + 6)
                            val target = name(data, length, targetPos)
                            if (targetPos[0] != end) throw IllegalArgumentException()
                            if (target.plain && target.value.lowercase(Locale.ROOT).endsWith(".local") && u16(data, pos[0] + 4) > 0) {
                                result.freshResponse = true
                                addType(result, ownerKey)
                                service(result, owner.value)?.let { it.host = target.value; it.port = u16(data, pos[0] + 4) }
                            }
                        } else if (kind == 16 && local) {
                            val s = service(result, owner.value)
                            var cursor = pos[0]
                            var fields = 0
                            while (cursor < end) {
                                if (++fields > 128) throw IllegalArgumentException()
                                val n = data[cursor++].toInt() and 255
                                if (cursor + n > end) throw IllegalArgumentException()
                                var equal = 0
                                while (equal < n && data[cursor + equal].toInt() != '='.code) equal++
                                val key = String(data, cursor, equal, StandardCharsets.US_ASCII).lowercase(Locale.ROOT)
                                if (s != null && s.attributes.size < 32 &&
                                    key.matches(Regex("[a-z0-9_-]{1,63}")) && !s.attributes.containsKey(key)) {
                                    s.attributes[key] = Arrays.copyOfRange(data, cursor + minOf(n, equal + 1), cursor + n)
                                }
                                cursor += n
                            }
                            if (s != null) s.txtReceived = true
                            if (size > 0) { result.freshResponse = true; addType(result, ownerKey) }
                        }
                    }
                    if (kind == 12 && (cls and 0x7fff) == 1 && live) {
                        val targetPos = intArrayOf(pos[0])
                        val target = name(data, length, targetPos)
                        if (targetPos[0] != end) throw IllegalArgumentException()
                        val ownerKey = owner.value.lowercase(Locale.ROOT)
                        val targetKey = target.value.lowercase(Locale.ROOT)
                        if (ownerKey.endsWith(".local") && targetKey.endsWith(".local")) {
                            result.freshResponse = true
                            val type = serviceType(ownerKey)
                            if (type.isNotEmpty()) {
                                result.serviceTypes.add(type)
                                if (targetKey.endsWith("." + ownerKey)) service(result, target.value)?.ptrReceived = true
                            }
                        }
                        if (owner.plain && target.plain) {
                            val key = owner.value.lowercase(Locale.ROOT)
                            if (key == ENUMERATION) {
                                val type = serviceType(target.value)
                                if (type.isNotEmpty()) result.serviceTypes.add(type)
                            } else if ((key.endsWith(".in-addr.arpa") || key.endsWith(".ip6.arpa")) &&
                                target.value.lowercase(Locale.ROOT).endsWith(".local")) {
                                val old = result.hostnames[key]
                                if (old == null || target.value.compareTo(old, true) < 0 ||
                                    (target.value.equals(old, true) && target.value.compareTo(old) < 0)) {
                                    result.hostnames[key] = target.value
                                }
                            }
                        }
                    }
                    pos[0] = end
                }
                result.parsedResponse = true
                return result
            } catch (_: IllegalArgumentException) { return Result() }
            catch (_: IndexOutOfBoundsException) { return Result() }
        }

        private fun wireRecord(data: ByteArray, length: Int, owner: Name, kind: Int, ttl: Long,
                               flush: Boolean, start: Int, end: Int): Record? {
            val labels = owner.labels
            if (!safeLabels(labels) || !labels.last().equals("local", true)) return null
            if (kind == 1 || kind == 28) {
                if (end - start != if (kind == 1) 4 else 16) throw IllegalArgumentException()
                if (!owner.plain) return null
                return Record(labels, kind, ttl, flush, address = InetAddress.getByAddress(data.copyOfRange(start, end)).hostAddress!!)
            }
            if (kind == 12) {
                if (labels.size != 3 || serviceType(owner.value).isEmpty()) return null
                val p = intArrayOf(start); val target = name(data, length, p).labels
                if (p[0] != end) throw IllegalArgumentException()
                if (!safeLabels(target) || instanceType(target).isEmpty() || key(target.drop(1)) != key(labels)) return null
                return Record(labels, kind, ttl, flush, target = target)
            }
            if (instanceType(labels).isEmpty()) return null
            if (kind == 33) {
                if (end - start < 7) throw IllegalArgumentException()
                val p = intArrayOf(start + 6); val host = name(data, length, p)
                if (p[0] != end) throw IllegalArgumentException()
                if (!safeLabels(host.labels) || !host.plain || !host.labels.last().equals("local", true) || host.labels.size < 2) return null
                return Record(labels, kind, ttl, flush, host.labels, u16(data, start + 4))
            }
            if (kind != 16) return null
            val attrs = TreeMap<String, ByteArray>(); var cursor = start; var fields = 0
            while (cursor < end) {
                if (++fields > 128) throw IllegalArgumentException()
                val n = data[cursor++].toInt() and 255
                if (cursor + n > end) throw IllegalArgumentException()
                var equal = 0
                while (equal < n && data[cursor + equal].toInt() != '='.code) equal++
                val key = String(data, cursor, equal, StandardCharsets.US_ASCII).lowercase(Locale.ROOT)
                if (attrs.size < 32 && key.matches(Regex("[a-z0-9_-]{1,63}")) && !attrs.containsKey(key))
                    attrs[key] = data.copyOfRange(cursor + minOf(n, equal + 1), cursor + n)
                cursor += n
            }
            return Record(labels, kind, ttl, flush, attributes = attrs)
        }

        private fun service(result: Result, owner: String): Service? {
            val labels = owner.javaSplit("\\.")
            if (labels.size < 4) return null
            val type = serviceType(labels[labels.size - 3] + "." + labels[labels.size - 2] + ".local")
            if (type.isEmpty()) return null
            val name = SafeText.label(owner.substring(0, owner.length - (type + "local").length - 1), 128)
            if (name.isEmpty()) return null
            val key = name.lowercase(Locale.ROOT) + "|" + type
            return result.services[key] ?: Service(name, type).also { result.services[key] = it }
        }

        private fun addType(result: Result, owner: String) {
            val labels = owner.javaSplit("\\.")
            if (labels.size >= 4) {
                val type = serviceType(labels[labels.size - 3] + "." + labels[labels.size - 2] + ".local")
                if (type.isNotEmpty()) result.serviceTypes.add(type)
            }
        }

        private fun name(data: ByteArray, length: Int, pos: IntArray): Name {
            var cursor = pos[0]
            var next = -1
            var steps = 0
            var expanded = 0
            val value = StringBuilder()
            val labels = ArrayList<String>()
            val visited = HashSet<Int>()
            var plain = true
            while (true) {
                if (cursor >= length || !visited.add(cursor) || ++steps > 128) throw IllegalArgumentException()
                val n = data[cursor++].toInt() and 255
                if (n == 0) { pos[0] = if (next < 0) cursor else next; return Name(value.toString(), plain, labels.toList()) }
                if ((n and 0xc0) == 0xc0) {
                    if (cursor >= length) throw IllegalArgumentException()
                    val target = ((n and 63) shl 8) or (data[cursor++].toInt() and 255)
                    if (target < 12 || target >= cursor - 2) throw IllegalArgumentException()
                    if (next < 0) next = cursor
                    cursor = target
                    continue
                }
                if (n > 63 || cursor + n > length || expanded + n + 1 > 254) throw IllegalArgumentException()
                if (value.isNotEmpty()) value.append('.')
                expanded += n + 1
                val label = String(data, cursor, n, StandardCharsets.UTF_8)
                labels.add(label)
                plain = plain && label.matches(Regex("[a-zA-Z0-9_-]+"))
                value.append(label)
                cursor += n
            }
        }

        private fun u16(data: ByteArray, at: Int): Int =
            ((data[at].toInt() and 255) shl 8) or (data[at + 1].toInt() and 255)
    }
}

