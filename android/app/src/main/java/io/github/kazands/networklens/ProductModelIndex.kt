package io.github.kazands.networklens

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Locale

/** Immutable exact manufacturer/model facts; the data does not establish form factor. */
internal class ProductModelIndex private constructor(private val keys: Array<String>,
    private val brands: Array<String>, private val titles: Array<String>, private val sources: Array<String>) {
    data class Entry(val brand: String, val title: String, val source: String)
    val size: Int get() = keys.size
    fun lookup(manufacturer: String, model: String): Entry? {
        if (manufacturer.length !in 1..160 || model.length !in 1..160) return null
        val n = Arrays.binarySearch(keys, key(manufacturer) + '\u0000' + key(model))
        return if (n < 0) null else Entry(brands[n], titles[n], sources[n])
    }
    companion object {
        val EMPTY = ProductModelIndex(emptyArray(), emptyArray(), emptyArray(), emptyArray())
        private fun key(value: String) = value.trim(' ', '\t').lowercase(Locale.ROOT)
        @Throws(IOException::class) fun read(input: InputStream): ProductModelIndex {
            // Stream the public table: never retain a second full uncompressed copy.
            val bounded = object : InputStream() {
                var used = 0
                private fun count(n: Int): Int {
                    if (n > 0) { used += n; if (used > 32 * 1024 * 1024) throw IOException("Product catalogue size limit") }
                    return n
                }
                override fun read(): Int = input.read().also { if (it >= 0) count(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = count(input.read(b, off, minOf(len, 32 * 1024 * 1024 - used + 1)))
            }
            val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val reader = InputStreamReader(bounded, decoder)
            val keys = ArrayList<String>(); val brands = ArrayList<String>(); val titles = ArrayList<String>(); val sources = ArrayList<String>()
            val pool = HashMap<String, String>()
            var previous = ""; var header = false
            fun line(value: String) {
                if (!header) {
                    if (value != "# Network Lens product model catalogue v1") throw IOException("Unknown product catalogue format")
                    header = true; return
                }
                if (keys.size == 300000) throw IOException("Product catalogue row limit")
                val f = value.split('\t')
                if (f.size != 5 || f.any { it.isEmpty() || it.length > 160 || it != SafeText.label(it, 160) }) throw IOException("Invalid product catalogue row")
                if (f[0] != key(f[0]) || f[1] != key(f[1]) || f[0] != key(f[2])) throw IOException("Noncanonical product catalogue key")
                val joined = f[0] + '\u0000' + f[1]
                if (joined <= previous) throw IOException("Duplicate or unsorted product catalogue model")
                keys.add(joined); brands.add(pool.getOrPut(f[2]) { f[2] }); titles.add(pool.getOrPut(f[3]) { f[3] })
                sources.add(pool.getOrPut(f[4]) { f[4] }); previous = joined
            }
            val pending = StringBuilder(); val chars = CharArray(4096)
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                for (n in 0 until count) {
                    if (chars[n] == '\n') { line(pending.toString()); pending.setLength(0) }
                    else { if (pending.length == 804) throw IOException("Product catalogue line limit"); pending.append(chars[n]) }
                }
            }
            if (pending.isNotEmpty()) line(pending.toString())
            if (!header) throw IOException("Missing product catalogue header")
            return ProductModelIndex(keys.toTypedArray(), brands.toTypedArray(), titles.toTypedArray(), sources.toTypedArray())
        }
    }
}
