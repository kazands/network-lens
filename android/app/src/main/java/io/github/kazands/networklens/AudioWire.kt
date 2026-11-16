package io.github.kazands.networklens

import java.io.*
import java.net.*
import java.util.concurrent.*

/** One same-peer request on a caller-bound socket; a whole-operation timer also bounds writes. */
internal object AudioWire {
    data class Query(val port: Int, val path: String = "", val command: String = "") {
        fun bytes(peer: InetAddress): ByteArray {
            if (command.isNotEmpty()) {
                require(port == 1255 && command in setOf("player/get_players", "group/get_groups"))
                return "heos://$command\r\n".toByteArray(Charsets.US_ASCII)
            }
            require(port in 1..65535 && path in setOf("/SyncStatus", "/YamahaExtendedControl/v1/system/getDeviceInfo",
                "/YamahaExtendedControl/v1/system/getFeatures", "/YamahaExtendedControl/v1/dist/getDistributionInfo",
                "/setup/eureka_info?params=multizone"))
            val host = peer.hostAddress!!.substringBefore('%').let { if (it.contains(':')) "[$it]" else it }
            return "GET $path HTTP/1.1\r\nHost: $host:$port\r\nAccept: application/json, text/xml\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII)
        }
    }
    fun exchange(socket: Socket, peer: InetAddress, query: Query, deadline: Long, timer: ScheduledExecutorService, active: () -> Boolean): ByteArray {
        fun check() {
            if (!active() || Thread.currentThread().isInterrupted) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
            if (System.nanoTime() >= deadline) throw MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE)
        }
        check()
        val closer = timer.schedule({ try { socket.close() } catch (_: IOException) {} }, maxOf(1, deadline-System.nanoTime()), TimeUnit.NANOSECONDS)
        var connected = false; var written = false
        try {
            socket.connect(InetSocketAddress(peer, query.port), 400); connected = true; check(); socket.soTimeout = 600
            socket.getOutputStream().write(query.bytes(peer)); written = true; check()
            val raw = socket.getInputStream()
            val input = object : FilterInputStream(raw) {
                override fun read(): Int { check(); return raw.read() }
                override fun read(b: ByteArray, off: Int, count: Int): Int { check(); return raw.read(b, off, count) }
            }
            val body = if (query.command.isEmpty()) UpnpDescription.readResponse(input) else {
                val out = ByteArrayOutputStream()
                while (true) {
                    val c = input.read()
                    if (c < 0) throw EOFException()
                    if (c == 10) break
                    if (out.size() >= BoundedJson.MAX_BODY) throw IOException("Audio response limit")
                    out.write(c)
                }
                out.toByteArray()
            }
            check(); return body
        } catch (e: Exception) {
            check()
            if (e is MetadataHttp.Failure) throw e
            throw MetadataHttp.Failure(when {
                e is SocketTimeoutException -> if (connected) MetadataHttp.Reason.READ_TIMEOUT else MetadataHttp.Reason.CONNECTION_TIMEOUT
                e is NoRouteToHostException -> MetadataHttp.Reason.ROUTING_FAILED
                !connected -> MetadataHttp.Reason.CONNECTION_FAILED
                !written -> MetadataHttp.Reason.WRITE_FAILED
                e is UpnpDescription.HttpStatusException -> MetadataHttp.Reason.HTTP_STATUS
                else -> MetadataHttp.Reason.READ_FAILED
            }, (e as? UpnpDescription.HttpStatusException)?.statusCode, e)
        } finally { closer.cancel(false) }
    }
}
