package io.github.kazands.networklens

import java.io.FilterInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLException

/** One bounded exchange on a caller-owned, selected-network-bound socket. No redirects/auth. */
internal object MetadataHttp {
    enum class Reason(val detail: String, val event: DiscoveryHealth.Diagnostic?) {
        CONNECTION_TIMEOUT("Connection timed out", DiscoveryHealth.Diagnostic.CONNECTION_TIMEOUT),
        CONNECTION_FAILED("Connection failed", DiscoveryHealth.Diagnostic.CONNECTION_FAILED),
        TLS_FAILED("TLS handshake or secure transport failed", DiscoveryHealth.Diagnostic.TLS_FAILED),
        WRITE_FAILED("Request write failed", DiscoveryHealth.Diagnostic.WRITE_FAILED),
        READ_TIMEOUT("Response read timed out", DiscoveryHealth.Diagnostic.READ_TIMEOUT),
        READ_FAILED("Response read failed", DiscoveryHealth.Diagnostic.READ_FAILED),
        HTTP_STATUS("HTTP response error", DiscoveryHealth.Diagnostic.HTTP_STATUS),
        INVALID_HTTP("Invalid or unsupported HTTP response", DiscoveryHealth.Diagnostic.INVALID_HTTP),
        INVALID_METADATA("No valid metadata in response", DiscoveryHealth.Diagnostic.INVALID_METADATA),
        DEADLINE("Metadata time limit reached", DiscoveryHealth.Diagnostic.DEADLINE),
        CANCELLED("Metadata request cancelled", null)
    }
    class Failure(val reason: Reason, val httpStatus: Int? = null, cause: Exception? = null) :
        IOException(reason.detail + if (httpStatus != null) " ($httpStatus)" else "", cause)
    private enum class Stage { CONNECT, TLS, WRITE, READ }

    /** Only the fixed reason and numeric HTTP status reach scan diagnostics. */
    fun recordFailure(health: DiscoveryHealth, source: String, error: Exception, expired: Boolean) {
        val failure = error as? Failure
        if (failure?.reason == Reason.CANCELLED && !expired) return
        val reason = if (expired) Reason.DEADLINE else failure?.reason
        health.diagnostic(source, reason?.event ?: DiscoveryHealth.Diagnostic.REQUEST_FAILED)
        val detail = reason?.detail ?: "Metadata request could not complete"
        if (reason == Reason.DEADLINE) health.truncated(source, detail)
        else health.fail(source, detail + if (reason == Reason.HTTP_STATUS) " (${failure!!.httpStatus})" else "")
    }

    fun exchange(socket: Socket, peer: InetAddress, request: LocalMetadata.Request, deadline: Long,
                 timer: ScheduledExecutorService, active: () -> Boolean): LocalMetadata.Report {
        var deadlineLimitedTimeout = false
        fun remaining(): Int {
            if (!active() || Thread.currentThread().isInterrupted) throw Failure(Reason.CANCELLED)
            if (System.nanoTime() >= deadline) throw Failure(Reason.DEADLINE)
            return maxOf(1, minOf(600, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))).toInt()
        }
        fun socketTimeout(maximum: Int = 600): Int {
            val timeout = minOf(maximum, remaining())
            // Millisecond rounding can fire this timeout just before the nanosecond deadline.
            deadlineLimitedTimeout = timeout < maximum
            return timeout
        }
        remaining()
        // Also bounds TLS handshake and blocked writes, including a peer that drips bytes.
        val timeout = timer.schedule({ try { socket.close() } catch (_: IOException) {} },
            maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        var transport = socket
        var stage = Stage.CONNECT
        try {
            socket.connect(InetSocketAddress(peer, request.port), socketTimeout(400))
            socket.soTimeout = socketTimeout()
            if (request.secure) {
                stage = Stage.TLS
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(socket, peer.hostAddress!!.substringBefore('%'), request.port, false) as SSLSocket
                transport = tls
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.soTimeout = socketTimeout()
                tls.startHandshake() // Platform trust only; never bypass certificates or downgrade IPPS.
            }
            remaining()
            stage = Stage.WRITE
            transport.getOutputStream().write(request.bytes(peer))
            stage = Stage.READ
            val input = transport.getInputStream()
            val bounded = object : FilterInputStream(input) {
                var bytes = 0
                private fun check(n: Int) {
                    bytes += n
                    if (bytes > UpnpDescription.MAX_BODY + 24576) throw Failure(Reason.INVALID_HTTP)
                    transport.soTimeout = socketTimeout()
                }
                override fun read(): Int { check(0); val got = input.read(); if (got >= 0) check(1); return got }
                override fun read(b: ByteArray, offset: Int, count: Int): Int {
                    check(0); val got = input.read(b, offset, minOf(count, 2048)); if (got > 0) check(got); return got
                }
            }
            val body = UpnpDescription.readResponse(bounded)
            remaining()
            return LocalMetadata.parse(request.kind, body) ?: throw Failure(Reason.INVALID_METADATA)
        } catch (e: Exception) {
            if (e is Failure) throw e
            val reason = when {
                !active() || Thread.currentThread().isInterrupted -> Reason.CANCELLED
                System.nanoTime() >= deadline -> Reason.DEADLINE
                e is SocketTimeoutException && deadlineLimitedTimeout -> Reason.DEADLINE
                e is UpnpDescription.HttpStatusException -> Reason.HTTP_STATUS
                e is UpnpDescription.ResponseException -> Reason.INVALID_HTTP
                e is SSLException || stage == Stage.TLS -> Reason.TLS_FAILED
                stage == Stage.CONNECT && e is SocketTimeoutException -> Reason.CONNECTION_TIMEOUT
                stage == Stage.CONNECT -> Reason.CONNECTION_FAILED
                stage == Stage.WRITE -> Reason.WRITE_FAILED
                e is SocketTimeoutException -> Reason.READ_TIMEOUT
                else -> Reason.READ_FAILED
            }
            throw Failure(reason, (e as? UpnpDescription.HttpStatusException)?.statusCode, e)
        } finally {
            timeout.cancel(false)
            if (transport !== socket) try { transport.close() } catch (_: IOException) {}
        }
    }
}
