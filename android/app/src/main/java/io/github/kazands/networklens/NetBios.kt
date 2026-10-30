package io.github.kazands.networklens

import java.nio.charset.StandardCharsets
import java.util.Locale

/** Bounded RFC 1002 node status. Replies are self-reported, not authentication. */
class NetBios private constructor() {
    class Result {
        @JvmField var name = ""
        @JvmField var mac = ""
    }
    companion object {
        @JvmStatic fun query(transaction: Int): ByteArray {
            val packet = ByteArray(50)
            packet[0] = (transaction ushr 8).toByte()
            packet[1] = transaction.toByte()
            packet[5] = 1
            packet[12] = 32
            val name = ByteArray(16)
            name[0] = '*'.code.toByte()
            for (i in 0 until 16) {
                packet[13 + i * 2] = ('A'.code + ((name[i].toInt() ushr 4) and 15)).toByte()
                packet[14 + i * 2] = ('A'.code + (name[i].toInt() and 15)).toByte()
            }
            packet[47] = 0x21
            packet[49] = 1
            return packet
        }

        @JvmStatic fun parse(packet: ByteArray?, length: Int, transaction: Int): Result? {
            if (packet == null || length < 12 || length > packet.size || length > 4096) return null
            try {
                if (u16(packet, 0) != transaction || (u16(packet, 2) and 0xf80f) != 0x8000) return null
                val questions = u16(packet, 4)
                val answers = u16(packet, 6)
                if (questions > 1 || answers !in 1..16) return null
                var at = 12
                repeat(questions) {
                    at = skipName(packet, at, length)
                    if (at + 4 > length) return null
                    at += 4
                }
                repeat(answers) {
                    at = skipName(packet, at, length)
                    if (at + 10 > length) return null
                    val type = u16(packet, at)
                    val cls = u16(packet, at + 2)
                    val size = u16(packet, at + 8)
                    at += 10
                    if (size > length - at) return null
                    if (type == 0x21 && cls == 1 && size >= 7) {
                        val count = packet[at].toInt() and 255
                        val stats = at + 1 + count * 18
                        val end = at + size
                        if (stats + 6 > end) return null
                        val result = Result()
                        for (n in 0 until count) {
                            val offset = at + 1 + n * 18
                            val suffix = packet[offset + 15].toInt() and 255
                            val flags = u16(packet, offset + 16)
                            if ((flags and 0x8000) == 0 && (suffix == 0 || suffix == 0x20)) {
                                val name = SafeText.label(String(packet, offset, 15, StandardCharsets.US_ASCII), 64)
                                if (name.isNotEmpty() && (result.name.isEmpty() || suffix == 0)) result.name = name
                            }
                        }
                        val mac = StringBuilder()
                        repeat(6) { n ->
                            if (n > 0) mac.append(':')
                            mac.append(String.format(Locale.ROOT, "%02X", packet[stats + n].toInt() and 255))
                        }
                        result.mac = Address.mac(mac.toString())
                        return result
                    }
                    at += size
                }
            } catch (_: IndexOutOfBoundsException) { return null }
            return null
        }

        private fun u16(p: ByteArray, i: Int): Int =
            ((p[i].toInt() and 255) shl 8) or (p[i + 1].toInt() and 255)

        private fun skipName(p: ByteArray, start: Int, end: Int): Int {
            var at = start
            var labels = 0
            while (labels < 128 && at < end) {
                labels++
                val size = p[at++].toInt() and 255
                if (size == 0) return at
                if ((size and 0xc0) == 0xc0) {
                    if (at >= end) throw IndexOutOfBoundsException()
                    val pointer = ((size and 0x3f) shl 8) or (p[at].toInt() and 255)
                    if (pointer >= end) throw IndexOutOfBoundsException()
                    return at + 1
                }
                if (size > 63 || size > end - at) throw IndexOutOfBoundsException()
                at += size
            }
            throw IndexOutOfBoundsException()
        }
    }
}

