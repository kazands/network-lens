package io.github.kazands.networklens

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Minimal IPP Get-Printer-Attributes. Never submit jobs or fetch returned URIs. */
internal object IppInfo {
    private const val REQUEST_ID = 0x4e4c0001
    fun query(uri: String): ByteArray {
        require(uri.length <= 1024 && uri.startsWith("ipp"))
        val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
        out.writeShort(0x0101); out.writeShort(0x000b); out.writeInt(REQUEST_ID); out.writeByte(1)
        fun attribute(tag: Int, name: String, value: String) {
            val n = name.toByteArray(StandardCharsets.US_ASCII); val v = value.toByteArray(StandardCharsets.UTF_8)
            out.writeByte(tag); out.writeShort(n.size); out.write(n); out.writeShort(v.size); out.write(v)
        }
        attribute(0x47, "attributes-charset", "utf-8")
        attribute(0x48, "attributes-natural-language", "en")
        attribute(0x45, "printer-uri", uri)
        attribute(0x44, "requested-attributes", "printer-make-and-model")
        out.writeByte(3)
        return bytes.toByteArray()
    }

    /** null is malformed/error; an empty string is a valid response with no model. */
    fun parse(body: ByteArray): String? {
        if (body.size !in 9..65536) return null
        return try {
            val data = ByteBuffer.wrap(body)
            fun byte() = data.get().toInt() and 255
            fun short() = data.short.toInt() and 65535
            if (byte() !in 1..2 || byte() > 2 || short() > 0x00ff || data.int != REQUEST_ID) return null
            var group = 0; var previous = ""; var model = ""; var seen = false
            var charset = "utf-8"
            repeat(512) {
                val tag = byte()
                if (tag == 3) return if (!data.hasRemaining()) model else null
                if (tag < 0x10) {
                    if (tag !in listOf(1, 2, 4, 5)) return null
                    group = tag; previous = ""; return@repeat
                }
                if (group == 0) return null
                val nameLength = short()
                if (nameLength > 255 || nameLength > data.remaining()) return null
                val nameBytes = ByteArray(nameLength); data.get(nameBytes)
                val name = if (nameLength == 0) previous else String(nameBytes, StandardCharsets.US_ASCII)
                if (name.isEmpty() || name.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' }) return null
                previous = name
                val length = short()
                if (length > 4096 || length > data.remaining()) return null
                val bytes = ByteArray(length); data.get(bytes)
                if (group == 1 && name == "attributes-charset") {
                    if (tag != 0x47) return null
                    charset = String(bytes, StandardCharsets.US_ASCII).lowercase(java.util.Locale.ROOT)
                    if (charset != "utf-8" && charset != "us-ascii") return null
                }
                if (group == 4 && name == "printer-make-and-model") {
                    if (seen) return null
                    seen = true
                    val value = when (tag) {
                        0x41 -> bytes
                        0x35 -> {
                            val text = ByteBuffer.wrap(bytes)
                            val languageLength = text.short.toInt() and 65535
                            if (languageLength > 63 || languageLength > text.remaining() - 2) return null
                            text.position(text.position() + languageLength)
                            val textLength = text.short.toInt() and 65535
                            if (textLength != text.remaining()) return null
                            ByteArray(textLength).also { text.get(it) }
                        }
                        0x12 -> return@repeat // no-value: leave unknown
                        else -> return null
                    }
                    if (charset == "us-ascii" && value.any { it < 0 }) return null
                    model = SafeText.label(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString(), 160)
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
