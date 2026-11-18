package io.github.kazands.networklens

import java.io.FilterInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Public EAP login-screen identity only. No credential fields, cookies, authentication or redirects. */
internal object EapInfo {
    const val SOURCE = "Network equipment identity checks"
    const val MODEL_SOURCE = "TP-Link EAP public device information"
    const val TRANSPORT = "GET /; matching EAP page only: POST /data/login.json (operation=read). No credentials or authentication. Requests may appear in device logs."
    private val nameMarker = Regex("id\\s*=\\s*[\"']devname[\"']")
    private val urlMarker = Regex("url\\s*:\\s*[\"'](?:\\./|/)data/login\\.json[\"']")
    private val readMarker = Regex("[\"']operation[\"']\\s*:\\s*[\"']read[\"']")
    private class OtherService : IOException()
    fun request(peer: InetAddress, identity: Boolean): ByteArray {
        val host = peer.hostAddress!!.substringBefore('%').let { if (it.contains(':')) "[$it]" else it }
        val body = if (identity) "operation=read" else ""
        return ((if (identity) "POST /data/login.json" else "GET /") + " HTTP/1.1\r\nHost: $host:80\r\n" +
            "Accept: ${if (identity) "application/json" else "text/html"}\r\n" +
            (if (identity) "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.length}\r\n" else "") +
            "Connection: close\r\n\r\n" + body).toByteArray(Charsets.US_ASCII)
    }
    fun eligiblePage(bytes: ByteArray): Boolean {
        if (bytes.size > UpnpDescription.MAX_BODY) return false
        // Markers corroborate the TP-Link HTTPD header. Never infer a model from HTML templates.
        val html = bytes.toString(Charsets.UTF_8)
        val page = StringBuilder(html.length)
        var offset = 0
        while (offset < html.length) {
            val opening = html.indexOf("<!--", offset)
            if (opening < 0) { page.append(html, offset, html.length); break }
            page.append(html, offset, opening)
            // Never rescan an unterminated tail, or use its contents to authorize a POST.
            val closing = html.indexOf("-->", opening + 4)
            if (closing < 0) break
            page.append(' ')
            offset = closing + 3
        }
        return nameMarker.containsMatchIn(page) && urlMarker.containsMatchIn(page) && readMarker.containsMatchIn(page)
    }
    fun parse(bytes: ByteArray): String? {
        if (bytes.size > 4096) return null
        val fields = BoundedJson.fields(bytes, setOf("dutMode"), setOf("error")) ?: return null
        if (fields["error"] != "0") return null
        return fields["dutMode"]?.takeIf {
            it.length <= 96 && it.matches(Regex("EAP[0-9]{2,4}(?:[- ][A-Za-z0-9]+)*(?:\\([A-Za-z0-9 -]+\\))?"))
        }
    }
    /** Factory returns a registered, selected-network-bound socket; every returned socket is closed. */
    fun read(peer: InetAddress, deadline: Long, timer: ScheduledExecutorService, active: () -> Boolean,
             open: () -> Socket, release: (Socket) -> Unit): String? {
        fun check() {
            if (!active() || Thread.currentThread().isInterrupted) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
            if (System.nanoTime() >= deadline) throw MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE)
        }
        fun exchange(identity: Boolean): ByteArray {
            check()
            val socket = open()
            var stage = MetadataHttp.Reason.CONNECTION_FAILED
            try {
                val closer = timer.schedule({ try { socket.close() } catch (_: IOException) {} },
                    maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
                try {
                    check(); socket.connect(InetSocketAddress(peer, 80), 400); socket.soTimeout = 600
                    stage = MetadataHttp.Reason.WRITE_FAILED
                    check(); socket.getOutputStream().write(request(peer, identity)); check()
                    stage = MetadataHttp.Reason.READ_FAILED
                    val raw = socket.getInputStream()
                    val input = object : FilterInputStream(raw) {
                        override fun read(): Int { check(); return raw.read() }
                        override fun read(b: ByteArray, off: Int, count: Int): Int { check(); return raw.read(b, off, count) }
                    }
                    val result = UpnpDescription.readResponse(input) { headers ->
                        if (!headers["server"].orEmpty().matches(Regex("TP-LINK HTTPD/[0-9.]+"))) throw OtherService()
                    }
                    check(); return result
                } finally { closer.cancel(false) }
            } catch (e: Exception) {
                check()
                if (e is OtherService || e is UpnpDescription.HttpStatusException || e is MetadataHttp.Failure) throw e
                val reason = when (e) {
                    is SocketTimeoutException -> if (stage == MetadataHttp.Reason.CONNECTION_FAILED)
                        MetadataHttp.Reason.CONNECTION_TIMEOUT else MetadataHttp.Reason.READ_TIMEOUT
                    is ConnectException -> MetadataHttp.Reason.CONNECTION_REFUSED
                    is NoRouteToHostException -> MetadataHttp.Reason.ROUTING_FAILED
                    is UpnpDescription.ResponseException -> MetadataHttp.Reason.INVALID_HTTP
                    else -> stage
                }
                throw MetadataHttp.Failure(reason, cause = e)
            } finally { try { socket.close() } finally { release(socket) } }
        }
        return try {
            if (!eligiblePage(exchange(false))) null else parse(exchange(true))
        } catch (_: OtherService) { null }
        catch (_: UpnpDescription.HttpStatusException) { null } // Normal nonmatching web service. No redirects/auth retries.
        catch (e: Exception) {
            check()
            if (e is MetadataHttp.Failure) throw e
            throw MetadataHttp.Failure(if (e is SocketTimeoutException) MetadataHttp.Reason.READ_TIMEOUT else MetadataHttp.Reason.REQUEST_FAILED, cause = e)
        }
    }
}
