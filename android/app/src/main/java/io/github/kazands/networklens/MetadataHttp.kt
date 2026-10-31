package io.github.kazands.networklens

import java.io.FilterInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** One bounded exchange on a caller-owned, selected-network-bound socket. No redirects/auth. */
internal object MetadataHttp {
    fun exchange(socket: Socket, peer: InetAddress, request: LocalMetadata.Request, deadline: Long,
                 timer: ScheduledExecutorService, active: () -> Boolean): LocalMetadata.Report? {
        fun remaining(): Int {
            if (!active() || Thread.currentThread().isInterrupted || System.nanoTime() >= deadline) throw IOException("Metadata deadline")
            return maxOf(1, minOf(600, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))).toInt()
        }
        remaining()
        // Also bounds TLS handshake and blocked writes, including a peer that drips bytes.
        val timeout = timer.schedule({ try { socket.close() } catch (_: IOException) {} },
            maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        var transport = socket
        try {
            socket.connect(InetSocketAddress(peer, request.port), minOf(400, remaining()))
            socket.soTimeout = remaining()
            if (request.secure) {
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(socket, peer.hostAddress!!.substringBefore('%'), request.port, false) as SSLSocket
                transport = tls
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.soTimeout = remaining()
                tls.startHandshake() // Platform trust only; never bypass certificates or downgrade IPPS.
            }
            remaining()
            transport.getOutputStream().write(request.bytes(peer))
            val input = transport.getInputStream()
            val bounded = object : FilterInputStream(input) {
                var bytes = 0
                private fun check(n: Int) {
                    bytes += n
                    if (bytes > UpnpDescription.MAX_BODY + 24576) throw IOException("Metadata response limit")
                    transport.soTimeout = remaining()
                }
                override fun read(): Int { check(0); val got = input.read(); if (got >= 0) check(1); return got }
                override fun read(b: ByteArray, offset: Int, count: Int): Int {
                    check(0); val got = input.read(b, offset, minOf(count, 2048)); if (got > 0) check(got); return got
                }
            }
            val body = UpnpDescription.readResponse(bounded)
            remaining()
            return LocalMetadata.parse(request.kind, body)
        } finally {
            timeout.cancel(false)
            if (transport !== socket) try { transport.close() } catch (_: IOException) {}
        }
    }
}
