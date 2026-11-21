package io.github.kazands.networklens

import java.io.IOException
import java.io.InputStream

/** RFC 4795 section 2.4: correlated reverse lookup to the current peer over TCP 5355. */
internal object LlmnrInfo {
    private val LABEL=Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    fun query(ip: String,id: Int): ByteArray {
        val dns=ReverseDns.query(ip,id).also {it[2]=0;it[3]=0}
        return byteArrayOf((dns.size ushr 8).toByte(),dns.size.toByte())+dns
    }
    fun response(bytes: ByteArray,ip: String,id: Int): String? {
        if(bytes.size !in 12..4096 || ReverseDns.u16(bytes,2) and 0xfff0 != 0x8000) return null
        val parsed=ReverseDns.response(bytes,bytes.size,id,ip)
        if(parsed.status!=ReverseDns.Status.POSITIVE || parsed.names.size!=1) return null
        return parsed.names.single().takeIf {it.length in 1..253 && Address.bytes(it)==null && it.split('.').all {s->LABEL.matches(s)}}
    }
    fun read(input: InputStream): ByteArray {
        val a=input.read();val b=input.read();if(a<0 || b<0) throw IOException("Truncated LLMNR length")
        val n=(a shl 8) or b;if(n !in 12..4096) throw IOException("LLMNR response limit")
        val result=ByteArray(n);var offset=0
        while(offset<n) {val got=input.read(result,offset,n-offset);if(got<1) throw IOException("Truncated LLMNR response");offset+=got}
        return result
    }
}
