package io.github.kazands.networklens

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Strict, bounded JSON objects. Retain only explicitly requested scalar paths. */
internal object BoundedJson {
    const val MAX_BODY = 32768
    fun fields(bytes: ByteArray?, strings: Set<String>, numbers: Set<String> = emptySet()): Map<String, String>? {
        return paths(bytes, strings.map { listOf(it) }.toSet(), numbers.map { listOf(it) }.toSet())
            ?.mapKeys { it.key.single() }
    }
    /** Path segments are separate: dotted keys and array members cannot impersonate objects. */
    fun paths(bytes: ByteArray?, strings: Set<List<String>>, numbers: Set<List<String>> = emptySet()): Map<List<String>, String>? {
        if (bytes == null || bytes.isEmpty() || bytes.size > MAX_BODY) return null
        return try {
            val json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val reader = Reader(json, strings, numbers)
            reader.space()
            if (reader.at >= json.length || json[reader.at] != '{') return null
            reader.value(0, emptyList())
            reader.space()
            if (reader.at != json.length) null else reader.fields
        } catch (_: IllegalArgumentException) { null }
        catch (_: CharacterCodingException) { null }
    }
    sealed interface Value
    data class ObjectValue(val values: Map<String, Value>) : Value
    data class ArrayValue(val values: List<Value>) : Value
    data class Scalar(val text: String, val number: Boolean) : Value
    data class BooleanValue(val value: Boolean) : Value
    data object NullValue : Value
    /** Ephemeral bounded tree for protocol rosters. Callers retain only reviewed fields. */
    fun document(bytes: ByteArray): ObjectValue? {
        if (bytes.isEmpty() || bytes.size > MAX_BODY) return null
        return try {
            val json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val reader = Reader(json, emptySet(), emptySet(), true)
            val result = reader.value(0, emptyList()) as? ObjectValue
            reader.space()
            result?.takeIf { reader.at == json.length }
        } catch (_: IllegalArgumentException) { null } catch (_: CharacterCodingException) { null }
    }
    /** Separate bounds for GitHub's public release list; discovery limits above remain unchanged. */
    fun releaseArray(bytes: ByteArray): ArrayValue? {
        if (bytes.isEmpty() || bytes.size > 2 * 1024 * 1024) return null
        return try {
            val json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val reader = Reader(json, emptySet(), emptySet(), true, 100000, 65536)
            val result = reader.value(0, emptyList()) as? ArrayValue
            reader.space()
            result?.takeIf { reader.at == json.length && it.values.size <= 100 }
        } catch (_: IllegalArgumentException) { null } catch (_: CharacterCodingException) { null }
    }
    private class Reader(val text: String, val strings: Set<List<String>>, val numbers: Set<List<String>>, val tree: Boolean = false, val maxTokens: Int = 2048, val maxString: Int = 4096) {
        var at = 0
        var tokens = 0
        val fields = LinkedHashMap<List<String>, String>()
        fun space() { while (at < text.length && " \t\r\n".contains(text[at])) at++ }
        fun require(c: Char) { space(); if (at >= text.length || text[at++] != c) throw IllegalArgumentException() }

        fun value(depth: Int, path: List<String>?): Value? {
            space()
            if (depth > 16 || ++tokens > maxTokens || at >= text.length) throw IllegalArgumentException()
            val c = text[at]
            if (c == '"') return Scalar(string(), false)
            if (c == '{') {
                at++
                space()
                val keys = HashSet<String>()
                val members = LinkedHashMap<String, Value>()
                if (at < text.length && text[at] == '}') { at++; return if (tree) ObjectValue(members) else null }
                repeat(128) {
                    space()
                    val key = string()
                    if (!keys.add(key)) throw IllegalArgumentException()
                    require(':')
                    val child = path?.plus(key)
                    val v = value(depth + 1, child)
                    if (tree && v != null) members[key] = v
                    if (child != null && v is Scalar && (if (v.number) child in numbers else child in strings)) fields[child] = v.text
                    space()
                    if (at < text.length && text[at] == '}') { at++; return if (tree) ObjectValue(members) else null }
                    require(',')
                }
                throw IllegalArgumentException()
            }
            if (c == '[') {
                val members = ArrayList<Value>()
                at++
                space()
                if (at < text.length && text[at] == ']') { at++; return if (tree) ArrayValue(members) else null }
                repeat(256) {
                    val v = value(depth + 1, null)
                    if (tree && v != null) members.add(v)
                    space()
                    if (at < text.length && text[at] == ']') { at++; return if (tree) ArrayValue(members) else null }
                    require(',')
                }
                throw IllegalArgumentException()
            }
            for (literal in arrayOf("true", "false", "null")) if (text.startsWith(literal, at)) {
                at += literal.length
                return if (!tree) null else if (literal == "null") NullValue else BooleanValue(literal == "true")
            }
            val start = at
            while (at < text.length && "0123456789.eE+-".contains(text[at])) at++
            if (at - start > 64 || !text.substring(start, at).matches(Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))) throw IllegalArgumentException()
            return Scalar(text.substring(start, at), true)
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
                if (out.length > maxString) throw IllegalArgumentException()
            }
            throw IllegalArgumentException()
        }
    }
}
