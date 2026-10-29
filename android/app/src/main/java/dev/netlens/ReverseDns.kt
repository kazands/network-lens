package dev.netlens

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Bounded RFC 1035 PTR codec; the caller selects an on-link DNS server. */
class ReverseDns private constructor() {
    enum class Status { INVALID, NEGATIVE, POSITIVE, ERROR }
    class Result internal constructor(
        @JvmField val status: Status,
        @JvmField val name: String,
        @JvmField val identifier: String,
        @JvmField val reason: String
    )

    companion object {
        private val INVALID = Result(Status.INVALID, "", "", "DNS reply could not be validated")

        @JvmStatic fun question(ip: String?): String {
            val a = Address.bytes(ip) ?: return ""
            val out = StringBuilder()
            if (a.size == 4) {
                for (i in 3 downTo 0) out.append(a[i].toInt() and 255).append('.')
                return out.toString() + "in-addr.arpa"
            }
            for (i in 15 downTo 0) {
                out.append(Integer.toHexString(a[i].toInt() and 15)).append('.')
                    .append(Integer.toHexString((a[i].toInt() ushr 4) and 15)).append('.')
            }
            return out.toString() + "ip6.arpa"
        }

        @JvmStatic fun query(ip: String, id: Int): ByteArray {
            val name = question(ip)
            require(name.isNotEmpty()) { "Numeric address required" }
            try {
                val bytes = ByteArrayOutputStream()
                val out = DataOutputStream(bytes)
                out.writeShort(id)
                out.writeShort(0x100)
                out.writeShort(1)
                out.write(ByteArray(6))
                for (label in name.javaSplit("\\.")) {
                    out.writeByte(label.length)
                    out.write(label.toByteArray(StandardCharsets.US_ASCII))
                }
                out.writeByte(0)
                out.writeShort(12)
                out.writeShort(1)
                return bytes.toByteArray()
            } catch (e: IOException) { throw AssertionError(e) }
        }

        @JvmStatic fun parse(bytes: ByteArray?, length: Int, id: Int, ip: String): String =
            response(bytes, length, id, ip).name

        @JvmStatic fun response(b: ByteArray?, length: Int, id: Int, ip: String): Result {
            if (b == null || length < 12 || length > b.size || length > 4096) return INVALID
            try {
                if (u16(b, 0) != id || (u16(b, 2) and 0xfa00) != 0x8000 || u16(b, 4) != 1) return INVALID
                val answers = u16(b, 6)
                val records = answers + u16(b, 8) + u16(b, 10)
                if (records > 128) return INVALID
                val pos = intArrayOf(12)
                val expected = question(ip)
                val q = name(b, length, pos)
                if (!q.equals(expected, true) || pos[0] + 4 > length ||
                    u16(b, pos[0]) != 12 || u16(b, pos[0] + 2) != 1) return INVALID
                pos[0] += 4
                var best = ""
                var identifier = ""
                for (i in 0 until records) {
                    val owner = name(b, length, pos)
                    if (pos[0] + 10 > length) return INVALID
                    val type = u16(b, pos[0])
                    val cls = u16(b, pos[0] + 2)
                    val size = u16(b, pos[0] + 8)
                    pos[0] += 10
                    val end = pos[0] + size
                    if (end > length) return INVALID
                    if (i < answers && type == 12 && cls == 1 && owner.equals(expected, true)) {
                        val valuePos = intArrayOf(pos[0])
                        val value = name(b, length, valuePos)
                        if (valuePos[0] != end) return INVALID
                        if (value.isNotEmpty() && !value.equals(expected, true)) {
                            if (hostname(value)) { if (before(value, best)) best = value }
                            else if (before(value, identifier)) identifier = value
                        }
                    }
                    pos[0] = end
                }
                if (pos[0] != length) return INVALID
                val rcode = u16(b, 2) and 15
                if (rcode == 3) return if (best.isEmpty() && identifier.isEmpty()) Result(Status.NEGATIVE, "", "", "") else INVALID
                if (rcode != 0) return Result(Status.ERROR, "", "", "DNS server returned error " + rcode)
                return Result(if (best.isEmpty() && identifier.isEmpty()) Status.NEGATIVE else Status.POSITIVE,
                    SafeText.label(best, 160), SafeText.label(identifier, 160), "")
            } catch (_: IllegalArgumentException) { return INVALID }
            catch (_: IndexOutOfBoundsException) { return INVALID }
        }

        private fun u16(b: ByteArray, i: Int): Int =
            ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)

        private fun name(b: ByteArray, length: Int, pos: IntArray): String {
            var cursor = pos[0]
            var next = -1
            var steps = 0
            var expanded = 0
            val out = StringBuilder()
            val visited = HashSet<Int>()
            while (true) {
                if (cursor >= length || !visited.add(cursor) || ++steps > 128) throw IllegalArgumentException()
                val n = b[cursor++].toInt() and 255
                if (n == 0) { pos[0] = if (next < 0) cursor else next; return out.toString() }
                if ((n and 0xc0) == 0xc0) {
                    if (cursor >= length) throw IllegalArgumentException()
                    val target = ((n and 63) shl 8) or (b[cursor++].toInt() and 255)
                    if (target < 12 || target >= cursor - 2) throw IllegalArgumentException()
                    if (next < 0) next = cursor
                    cursor = target
                    continue
                }
                if (n > 63 || cursor + n > length) throw IllegalArgumentException()
                expanded += n + 1
                if (expanded > 254) throw IllegalArgumentException()
                if (out.isNotEmpty()) out.append('.')
                repeat(n) {
                    val c = b[cursor++].toInt() and 255
                    if (c in 33..126 && c != '.'.code && c != '\\'.code) out.append(c.toChar())
                    else out.append('\\').append(('0'.code + c / 100).toChar())
                        .append(('0'.code + c / 10 % 10).toChar()).append(('0'.code + c % 10).toChar())
                }
            }
        }

        private fun before(value: String, previous: String): Boolean =
            previous.isEmpty() || value.compareTo(previous, true) < 0 ||
                (value.equals(previous, true) && value.compareTo(previous) < 0)

        private fun hostname(value: String): Boolean =
            value.matches(Regex("[a-zA-Z0-9_.-]+")) &&
                !value.matches(Regex("(?:[0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}|[0-9a-fA-F]{12}|[0-9]{1,3}(?:\\.[0-9]{1,3}){3}"))
    }
}

