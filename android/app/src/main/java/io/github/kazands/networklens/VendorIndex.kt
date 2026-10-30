package io.github.kazands.networklens

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Arrays

/** Offline registry organizations, kept distinct from reported manufacturers. */
class VendorIndex private constructor(
    @JvmField val snapshot: Long,
    private val names: Array<String>,
    private val prefixes: LongArray,
    private val organizations: IntArray
) {
    fun lookup(address: String?): String {
        val mac = Address.mac(address)
        if (mac.isEmpty() || (Integer.parseInt(mac.substring(0, 2), 16) and 2) != 0) return ""
        val value = java.lang.Long.parseLong(mac.replace(":", ""), 16)
        for (width in intArrayOf(9, 7, 6)) {
            val key = ((value ushr (48 - width * 4)) shl 4) or width.toLong()
            val n = Arrays.binarySearch(prefixes, key)
            if (n >= 0) {
                val name = names[organizations[n]]
                return if (name.equals("PRIVATE", true) || name.equals("IEEE Registration Authority", true)) "" else name
            }
        }
        return ""
    }

    companion object {
        @JvmField val EMPTY = VendorIndex(0, emptyArray(), longArrayOf(), intArrayOf())

        @JvmStatic @Throws(IOException::class) fun read(input: InputStream): VendorIndex {
            val stream = DataInputStream(input)
            if (stream.readInt() != 0x444c5631) throw IOException("Unknown MAC registry format")
            val date = stream.readLong()
            val count = stream.readInt()
            val rows = stream.readInt()
            if (date < 0 || count !in 0..80000 || rows !in 0..100000) throw IOException("MAC registry limits")
            val names = Array(count) { "" }
            var used = 20L + rows * 12L
            for (n in names.indices) {
                val bytes = stream.readUnsignedShort()
                used += bytes + 2
                if (bytes !in 1..640 || used > 8 * 1024 * 1024) throw IOException("MAC registry size")
                val data = ByteArray(bytes)
                stream.readFully(data)
                val value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString()
                if (value.length > 160 || value != SafeText.label(value, 160)) throw IOException("Invalid registry organization")
                names[n] = value
            }
            val keys = LongArray(rows)
            val ids = IntArray(rows)
            var previous = -1L
            for (n in keys.indices) {
                val key = stream.readLong()
                val id = stream.readInt()
                val width = (key and 15).toInt()
                if (key <= previous || width !in intArrayOf(6, 7, 9) ||
                    (key ushr 4) >= (1L shl (width * 4)) || id < 0 || id >= count) throw IOException("Invalid registry prefix")
                keys[n] = key
                ids[n] = id
                previous = key
            }
            if (stream.read() != -1) throw IOException("Trailing registry data")
            return VendorIndex(date, names, keys, ids)
        }
    }
}

