package io.github.kazands.networklens

import java.io.IOException
import java.net.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Envisalink DSC TPI 1.09, sections 2.2/3.1. Receive only: never send login or commands. */
internal object EnvisalinkInfo {
    const val PORT = 4025
    const val SOURCE = "Envisalink identity check"
    const val SERVICE = "Envisalink TPI"
    const val PRODUCT_ID = "envisalink:tpi-family"
    private val greeting = "5053CD\r\n".toByteArray(Charsets.US_ASCII)
    enum class Report(val connected: Boolean, val text: String) {
        IDENTIFIED(true, "Envisalink greeting received; model not reported"),
        OTHER(true, "No Envisalink greeting reported"),
        NO_GREETING(true, "No greeting received; service may be in use"),
        UNAVAILABLE(false, "Service unavailable")
    }
    fun matches(bytes: ByteArray): Boolean = bytes.contentEquals(greeting)
    fun identity() = DeviceCatalog.Match(DeviceCatalog.Product(PRODUCT_ID, "Envisacor", "Envisalink",
        DeviceCatalog.Category.ALARM_INTERFACE, emptySet(), familyOnly = true),
        "Envisalink DSC TPI greeting", "5053CD (login request; checksum verified)")

    /** Caller supplies a scoped, tracked socket and closes it on return/cancellation. No retries. */
    fun exchange(socket: Socket, peer: InetAddress, deadline: Long, timer: ScheduledExecutorService,
                 port: Int = PORT, active: () -> Boolean): Report {
        fun remaining(): Int {
            if (!active() || Thread.currentThread().isInterrupted) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
            val left = deadline - System.nanoTime()
            if (left <= 0) throw MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE)
            // Round up: a sub-millisecond remainder must not become an early read timeout.
            return minOf(400, 1 + (left - 1) / 1_000_000).toInt()
        }
        remaining()
        val closer = timer.schedule({ try { socket.close() } catch (_: IOException) {} },
            maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        var connected = false
        try {
            socket.connect(InetSocketAddress(peer, port), minOf(250, remaining()))
            connected = true
            val input = socket.getInputStream()
            val bytes = ByteArray(greeting.size)
            // Fixed eight-byte frame: fragmented reads accepted, no unbounded banner buffering.
            for (i in bytes.indices) {
                socket.soTimeout = remaining()
                val value = input.read()
                remaining()
                if (value < 0) return Report.NO_GREETING
                bytes[i] = value.toByte()
                if (bytes[i] != greeting[i]) return Report.OTHER
            }
            return if (matches(bytes)) Report.IDENTIFIED else Report.OTHER
        } catch (e: IOException) {
            remaining()
            return when (e) {
                is ConnectException -> Report.UNAVAILABLE
                is SocketTimeoutException -> if (connected) Report.NO_GREETING else Report.UNAVAILABLE
                is SocketException -> if (connected) Report.NO_GREETING else throw e
                else -> throw e
            }
        } finally { closer.cancel(false) }
    }
}
