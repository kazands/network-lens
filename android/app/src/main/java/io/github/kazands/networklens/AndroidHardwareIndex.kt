package io.github.kazands.networklens

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Separately attributed physical-type facts. No names, address inference or network requests. */
internal class AndroidHardwareIndex private constructor(private val rows: Map<String, Entry>) {
    data class Entry(val category: DeviceCatalog.Category, val source: String)
    val size: Int get() = rows.size
    fun lookup(manufacturer: String, model: String): Entry? {
        if (manufacturer.length !in 1..160 || model.length !in 1..160) return null
        val maker = key(manufacturer); val code = key(model)
        return rows[maker + '\u0000' + code] ?: rows[maker + '\u0000' + code.removePrefix("$maker ")]
    }
    companion object {
        val EMPTY = AndroidHardwareIndex(emptyMap())
        private fun key(value: String) = value.trim(' ', '\t').lowercase(Locale.ROOT)
        @Throws(IOException::class) fun read(input: InputStream): AndroidHardwareIndex {
            try {
                val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > 512 * 1024) throw IOException("Hardware category size limit")
                    out.write(buffer, 0, n)
                }
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString()
                val lines = text.removeSuffix("\n").split('\n')
                if (lines.firstOrNull() != "# Network Lens Android hardware categories v1" || lines.size > 5001)
                    throw IOException("Invalid hardware category header/size")
                val rows = linkedMapOf<String, Entry>(); var previous = ""
                for (line in lines.drop(1)) {
                    val fields = line.split('\t')
                    if (fields.size != 4 || fields.any { it.isEmpty() || it.length > 160 || SafeText.label(it, 160) != it })
                        throw IOException("Invalid hardware category row")
                    if (fields[0] != key(fields[0]) || fields[1] != key(fields[1])) throw IOException("Noncanonical hardware key")
                    val joined = fields[0] + '\u0000' + fields[1]
                    if (joined <= previous) throw IOException("Unsorted/duplicate hardware category")
                    val category = DeviceCatalog.Category.valueOf(fields[2])
                    if (category !in setOf(DeviceCatalog.Category.PHONE, DeviceCatalog.Category.TABLET,
                            DeviceCatalog.Category.TV_PLAYER, DeviceCatalog.Category.TV)) throw IOException("Unsupported hardware category")
                    if (!fields[3].matches(Regex("LineageOS@[a-f0-9]{40}/[A-Za-z0-9_-]{1,80}"))) throw IOException("Invalid category provenance")
                    rows[joined] = Entry(category, fields[3]); previous = joined
                }
                return AndroidHardwareIndex(rows)
            } catch (e: IllegalArgumentException) { throw IOException("Invalid hardware categories", e) }
        }
    }
}
