package io.github.kazands.networklens

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Selected unverified labels from canonical RFC2253 output, never hardware identity. */
internal object CertificateSubject {
    const val MAX_SUBJECT=2048
    const val MAX_VALUE=160
    data class Selection(val values:Map<String,String>,val notice:String="")
    private fun hex(c:Char):Int=when(c) {in '0'..'9'->c-'0';in 'A'..'F'->c-'A'+10;in 'a'..'f'->c-'a'+10;else->-1}
    private const val SPECIAL=" ,+\"\\<>;=#"
    /** Consume exactly one escape, with no retry or backtracking. */
    private fun escapeEnd(text:String,at:Int):Int {
        if(at+1>=text.length) return -1
        if(at+2<text.length && hex(text[at+1])>=0 && hex(text[at+2])>=0) return at+3
        return if(text[at+1] in SPECIAL) at+2 else -1
    }
    fun select(subject:String):Selection {
        if(subject.length>MAX_SUBJECT) return Selection(emptyMap(),"Subject name exceeded capture limit")
        if(subject.isEmpty()) return Selection(emptyMap())
        val values=linkedMapOf<String,String>();val seen=hashSetOf<String>();var omitted=false;var at=0
        fun invalid()=Selection(emptyMap(),"Subject name could not be decoded")
        while(at<subject.length) {
            val keyStart=at
            while(at<subject.length && subject[at]!='=') {
                if(subject[at] in ",+\\\"") return invalid()
                at++
            }
            if(at==subject.length || at==keyStart) return invalid()
            val key=subject.substring(keyStart,at).trim().uppercase(Locale.ROOT)
            at++;val start=at
            while(at<subject.length && subject[at] !in ",+") {
                if(subject[at]=='\\') {at=escapeEnd(subject,at);if(at<0) return invalid()}
                else {if(subject[at]=='\"' || subject[at]==';') return invalid();at++}
            }
            if(key=="CN" || key=="O") {
                if(!seen.add(key)) {values.remove(key);omitted=true}
                else {
                    val decoded=decode(subject.substring(start,at))
                    if(decoded==null) omitted=true else values[key]=decoded
                }
            }
            if(at<subject.length && ++at==subject.length) return invalid()
        }
        return Selection(values,if(omitted) "Overlong, unsupported or duplicate subject attributes omitted" else "")
    }
    private fun decode(raw:String):String? {
        // Binary ASN.1 representations are outside this display-only reader.
        if(raw.isEmpty() || raw.startsWith('#')) return null
        val bytes=ByteArrayOutputStream();var at=0
        while(at<raw.length) {
            if(raw[at]=='\\') {
                val end=escapeEnd(raw,at);if(end<0) return null
                if(end==at+3) bytes.write((hex(raw[at+1]) shl 4) or hex(raw[at+2]))
                else bytes.write(raw[at+1].code)
                at=end
            } else {
                val start=at
                while(at<raw.length && raw[at]!='\\') at++
                val encoded=try {Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(raw,start,at))}
                    catch(_:CharacterCodingException) {return null}
                val chunk=ByteArray(encoded.remaining());encoded.get(chunk);bytes.write(chunk)
            }
            if(bytes.size()>MAX_VALUE*4) return null
        }
        val value=try {Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()} catch(_:CharacterCodingException) {return null}
        if(value.length !in 1..MAX_VALUE) return null
        return SafeText.label(value,MAX_VALUE).takeIf {it.isNotEmpty()}
    }
}
