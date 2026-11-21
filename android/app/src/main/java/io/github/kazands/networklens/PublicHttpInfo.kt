package io.github.kazands.networklens

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** One public root response. Headers are clues, never hardware identity or follow-up instructions. */
internal object PublicHttpInfo {
    const val PREFIX = 8192
    private const val HEADER_LIMIT = 16384
    private val STATUS = Regex("HTTP/1\\.[01] ([1-5][0-9]{2})(?: [ -~]*)?")
    private val TOKEN = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    private val LENGTH = Regex("[0-9]{1,10}")
    private val CHUNK = Regex("[0-9a-fA-F]{1,8}")
    private val EAP_SERVER=Regex("TP-LINK HTTPD/[0-9.]+")
    private val CHARSET=Regex("""(?:^|;)\s*charset\s*=\s*"?([^;\s"]+)""")
    private val RAW_START=Regex("(?i)<(script|style|textarea)(?:[ \t][^>]{0,1024})?>")
    private val TITLE = Regex("(?i)<title(?:[ \\t][^>]{0,256})?>")
    private val END_TITLE = Regex("(?i)</title[ \\t]*>")
    private val REALM = Regex("(?i)(?:^|[, \\t])realm[ \\t]*=[ \\t]*(?:\"((?:[^\"\\\\\\r\\n]|\\\\[ -~]){0,256})\"|([A-Za-z0-9_. -]{1,128})(?:,|$))")
    enum class Software(val label: String, val family: String) {
        HOME_ASSISTANT("Home Assistant web service", "public_web_homeassistant"),
        PI_HOLE("Pi-hole web service", "public_web_pihole"),
        TASMOTA("Tasmota web service", "public_web_tasmota"),
        OCTOPRINT("OctoPrint web service", "octoprint")
    }
    data class Report(val status: Int, val fields: Map<String,String>, val prefixLimited: Boolean = false,
                      val software: Software? = null, val eapEligible: Boolean = false) {
        val outcome: String get() = when {
            status in 300..399 -> "Redirect not followed"
            status == 401 || status == 403 -> "Authentication required; no credentials sent"
            fields.isEmpty() -> "Response received without selected public clues"
            else -> "Public response clues received; hardware identity not established"
        }
    }
    fun request(authority: String): ByteArray {
        require(authority.length <= 128 && authority.all {it.code in 33..126 && it !in "\\/\r\n"})
        return ("GET / HTTP/1.1\r\nHost: $authority\r\nAccept: text/html\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
    }
    fun read(input: InputStream): Report {
        var headerBytes=0
        fun line(limit: Int=HEADER_LIMIT): String {
            val out=ByteArrayOutputStream()
            while(true) {
                val b=input.read();if(b<0) throw IOException("Truncated public HTTP header")
                if(++headerBytes>limit || out.size()>8192) throw IOException("Public HTTP framing limit")
                if(b==13) {if(input.read()!=10 || ++headerBytes>limit) throw IOException("Invalid public HTTP line");return out.toString("US-ASCII")}
                if(b !in 32..126 && b!=9) throw IOException("Invalid public HTTP character")
                out.write(b)
            }
        }
        val status=STATUS.matchEntire(line())?.groupValues?.get(1)?.toInt() ?: throw IOException("Invalid public HTTP status")
        val headers=linkedMapOf<String,String>();val challenges=ArrayList<String>()
        var ended=false
        repeat(64) {
            if(!ended) {
                val row=line()
                if(row.isEmpty()) ended=true else {
                    val colon=row.indexOf(':');if(colon<1 || !TOKEN.matches(row.substring(0,colon))) throw IOException("Invalid public HTTP header")
                    val key=row.substring(0,colon).lowercase(Locale.ROOT);val value=row.substring(colon+1).trim()
                    if(key=="www-authenticate") {if(challenges.size>=4) throw IOException("Challenge limit");challenges.add(value)}
                    else if(key in setOf("server","content-type","content-encoding","content-length","transfer-encoding") && headers.put(key,value)!=null) throw IOException("Ambiguous public HTTP header")
                }
            }
        }
        if(!ended) throw IOException("Public HTTP header count limit")
        val transfer=headers["transfer-encoding"];val length=headers["content-length"]
        if(transfer!=null && (length!=null || !transfer.equals("chunked",true))) throw IOException("Ambiguous public HTTP framing")
        val count=length?.let {if(!LENGTH.matches(it)) throw IOException("Invalid public HTTP length");it.toLong()}
        val fields=linkedMapOf<String,String>()
        headers["server"]?.let {fields["HTTP server label"]=SafeText.label(it,160)}
        val realms=challenges.flatMap { REALM.findAll(it).map { m -> m.groupValues[1].ifEmpty {m.groupValues[2]}.replace("\\\"","\"").replace("\\\\","\\") }.toList() }.distinct()
        if(realms.size==1) fields["HTTP authentication realm"]=SafeText.label(realms.single(),160)
        if(challenges.isNotEmpty()) fields["Authentication challenges"]=challenges.size.toString()
        // No reading login/error/redirect bodies, and never retain Location, cookies or challenge nonces.
        if(status!=200) return Report(status,fields)
        val eapServer=EAP_SERVER.matches(headers["server"].orEmpty())
        val prefix=if(eapServer) UpnpDescription.MAX_BODY else PREFIX
        val media=headers["content-type"].orEmpty().lowercase(Locale.ROOT)
        if(!eapServer && media.substringBefore(';').trim()!="text/html" || headers["content-encoding"]?.let {!it.equals("identity",true)}==true)
            return Report(status,fields)
        val charset=CHARSET.find(media)?.groupValues?.get(1)
        if(charset!=null && charset !in setOf("utf-8","us-ascii")) return Report(status,fields)
        val body=ByteArrayOutputStream();var limited=false
        fun copy(n: Long) {
            var left=n
            val bytes=ByteArray(1024)
            while(left>0 && body.size()<prefix) {
                val got=input.read(bytes,0,minOf(left,bytes.size.toLong(),(prefix-body.size()).toLong()).toInt())
                if(got<1) throw IOException("Truncated public HTTP body")
                body.write(bytes,0,got);left-=got
            }
            if(left>0) limited=true
        }
        if(transfer!=null) {
            var done=false
            repeat(256) {
                if(!done && !limited) {
                    val size=line(HEADER_LIMIT+4096);if(!CHUNK.matches(size)) throw IOException("Invalid public HTTP chunk")
                    val n=size.toLong(16)
                    if(n==0L) done=true else {copy(n);if(!limited && (input.read()!=13 || input.read()!=10)) throw IOException("Invalid public HTTP chunk ending")}
                }
            }
            if(!done && !limited) throw IOException("Public HTTP chunk count limit")
            if(done && eapServer) {
                var trailersEnded=false
                repeat(32) {if(!trailersEnded && line(HEADER_LIMIT+4096).isEmpty()) trailersEnded=true}
                if(!trailersEnded) throw IOException("Public HTTP trailer limit")
            }
        } else if(count!=null) copy(count)
        else {
            val bytes=ByteArray(1024)
            while(body.size()<prefix) {val got=input.read(bytes,0,minOf(bytes.size,prefix-body.size()));if(got<0) break;if(got==0) throw IOException("Empty public HTTP read");body.write(bytes,0,got)}
            limited=body.size()==prefix
        }
        val html=try {
            val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val bytes=ByteBuffer.wrap(body.toByteArray())
            if(!limited) decoder.decode(bytes).toString() else {
                // A bounded prefix may split a valid final code point. Keep complete characters,
                // while still rejecting malformed interior bytes; do not repair invalid content.
                val chars=CharBuffer.allocate(bytes.remaining())
                val result=decoder.decode(bytes,chars,false)
                if(result.isError) result.throwException()
                chars.flip();chars.toString()
            }
        } catch(_:java.nio.charset.CharacterCodingException) {return Report(status,fields,limited)}
        val page=withoutRawText(withoutComments(html))
        val titles=TITLE.findAll(page).take(2).toList()
        if(titles.size==1) {
            val start=titles.single().range.last+1
            val end=END_TITLE.find(page,start)?.range?.first
            if(end!=null && end-start<=512) {
                val title=page.substring(start,end)
                if('<' !in title) fields["HTTP page title"]=SafeText.label(entities(title),160)
            }
        }
        val lower=page.lowercase(Locale.ROOT)
        val title=fields["HTTP page title"].orEmpty().lowercase(Locale.ROOT)
        val matches=listOfNotNull(
            Software.HOME_ASSISTANT.takeIf {title=="home assistant" && ("<home-assistant" in lower || "/frontend_latest/" in lower)},
            Software.PI_HOLE.takeIf {title.startsWith("pi-hole") && ("pi-hole.net" in lower || "id=\"pihole" in lower)},
            Software.TASMOTA.takeIf {"tasmota" in title && ("tasmota.github.io" in lower || "tasmota firmware" in lower)},
            Software.OCTOPRINT.takeIf {"octoprint" in title && ("data-octoprint-version=" in lower || "id=\"octoprint" in lower || "octoprint.org" in lower)})
        return Report(status,fields.filterValues {it.isNotEmpty()},limited,matches.singleOrNull(),eapServer && !limited && EapInfo.eligiblePage(body.toByteArray()))
    }
    private fun withoutRawText(html:String):String=buildString {
        var offset=0
        while(offset<html.length) {
            val open=RAW_START.find(html,offset)
            if(open==null) {append(html,offset,html.length);break}
            append(html,offset,open.range.last+1)
            var close=html.indexOf("</"+open.groupValues[1],open.range.last+1,ignoreCase=true)
            val nameEnd=2+open.groupValues[1].length
            while(close>=0 && html.getOrNull(close+nameEnd) !in listOf('>',' ','\t','\r','\n')) close=html.indexOf("</"+open.groupValues[1],close+nameEnd,ignoreCase=true)
            if(close<0) break
            val end=html.indexOf('>',close)
            if(end<0) break
            append(' ');offset=end+1
        }
    }
    internal fun withoutComments(html: String): String = buildString {
        var offset=0
        while(offset<html.length) {
            val open=html.indexOf("<!--",offset)
            if(open<0) {append(html,offset,html.length);break}
            append(html,offset,open)
            val close=html.indexOf("-->",open+4)
            if(close<0) break
            append(' ');offset=close+3
        }
    }
    private fun entities(text: String): String = text.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#39;","'")
}
