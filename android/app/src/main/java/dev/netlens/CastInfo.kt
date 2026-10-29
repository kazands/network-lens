package dev.netlens

import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Bounded name-only JSON; never retain claimed addresses or location data. */
class CastInfo private constructor() {
    companion object {
        const val MAX_BODY = 32768
        @JvmStatic fun needsName(name: String?): Boolean = !IdentityHints.specificName(name)

        @JvmStatic fun request(responder: InetAddress): ByteArray {
            var host = responder.hostAddress!!.javaSplit("%", 2)[0]
            if (responder is Inet6Address) host = "[" + host + "]"
            return ("GET /setup/eureka_info?options=detail HTTP/1.1\r\nHost: " + host +
                ":8008\r\nAccept: application/json\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        }

        @JvmStatic fun name(bytes: ByteArray?): String {
            if (bytes == null || bytes.isEmpty() || bytes.size > MAX_BODY) return ""
            return try {
                val json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                val reader = Reader(json)
                reader.space()
                if (reader.at >= json.length || json[reader.at] != '{') return ""
                reader.value(0)
                reader.space()
                if (reader.at != json.length) return ""
                val name = SafeText.label(reader.name, 160)
                if (needsName(name)) "" else name
            } catch (_: IllegalArgumentException) { "" }
            catch (_: CharacterCodingException) { "" }
        }
    }

    private class Reader(val text: String) {
        var at = 0
        var tokens = 0
        var name = ""
        fun space() { while (at < text.length && " \t\r\n".contains(text[at])) at++ }
        fun require(c: Char) { space(); if (at >= text.length || text[at++] != c) throw IllegalArgumentException() }

        fun value(depth: Int): String? {
            space()
            if (depth > 16 || ++tokens > 2048 || at >= text.length) throw IllegalArgumentException()
            val c = text[at]
            if (c == '"') return string()
            if (c == '{') {
                at++
                space()
                val keys = HashSet<String>()
                if (at < text.length && text[at] == '}') { at++; return null }
                repeat(128) {
                    space()
                    val key = string()
                    if (!keys.add(key)) throw IllegalArgumentException()
                    require(':')
                    val v = value(depth + 1)
                    if (depth == 0 && key == "name" && v != null) name = v
                    space()
                    if (at < text.length && text[at] == '}') { at++; return null }
                    require(',')
                }
                throw IllegalArgumentException()
            }
            if (c == '[') {
                at++
                space()
                if (at < text.length && text[at] == ']') { at++; return null }
                repeat(256) {
                    value(depth + 1)
                    space()
                    if (at < text.length && text[at] == ']') { at++; return null }
                    require(',')
                }
                throw IllegalArgumentException()
            }
            for (literal in arrayOf("true", "false", "null")) if (text.startsWith(literal, at)) {
                at += literal.length
                return null
            }
            val start = at
            while (at < text.length && "0123456789.eE+-".contains(text[at])) at++
            if (at - start > 64 || !text.substring(start, at).matches(Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))) throw IllegalArgumentException()
            return null
        }

        fun string(): String {
            if (at >= text.length || text[at++] != '"') throw IllegalArgumentException()
            val out = StringBuilder()
            while (at < text.length) {
                var c = text[at++]
                if (c == '"') {
                    var i = 0
                    while (i < out.length) {
                        if (Character.isSurrogate(out[i])) {
                            if (!Character.isHighSurrogate(out[i]) || i + 1 >= out.length ||
                                !Character.isLowSurrogate(out[++i])) throw IllegalArgumentException()
                        }
                        i++
                    }
                    return out.toString()
                }
                if (c.code < 32) throw IllegalArgumentException()
                if (c == '\\') {
                    if (at >= text.length) throw IllegalArgumentException()
                    c = when (val escaped = text[at++]) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            if (at + 4 > text.length || !text.substring(at, at + 4).matches(Regex("[0-9a-fA-F]{4}"))) throw IllegalArgumentException()
                            val code = Integer.parseInt(text.substring(at, at + 4), 16).toChar()
                            at += 4
                            code
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
                out.append(c)
                if (out.length > 4096) throw IllegalArgumentException()
            }
            throw IllegalArgumentException()
        }
    }
}

