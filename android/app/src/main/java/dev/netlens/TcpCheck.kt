package dev.netlens

import java.io.IOException
import java.util.function.BooleanSupplier

/** Confirm with two new connections and a bounded, cancellable gap. */
class TcpCheck private constructor() {
    fun interface Connector {
        @Throws(IOException::class) fun connect(): Int
    }
    interface Clock {
        fun nanos(): Long
        @Throws(InterruptedException::class) fun sleep(millis: Int)
    }
    enum class Status { CONFIRMED, UNCONFIRMED, CANCELLED }
    class Result internal constructor(
        @JvmField val status: Status,
        @JvmField val connections: Int,
        @JvmField val latency: Int,
        @JvmField val failure: Exception?
    )
    companion object {
        private val CLOCK = object : Clock {
            override fun nanos(): Long = System.nanoTime()
            override fun sleep(millis: Int) { Thread.sleep(millis.toLong()) }
        }
        @JvmStatic fun confirm(connector: Connector, stop: BooleanSupplier): Result = confirm(connector, stop, CLOCK)

        @JvmStatic fun confirm(connector: Connector, stop: BooleanSupplier, clock: Clock): Result {
            var connected = 0
            var latency = -1
            try {
                repeat(2) { attempt ->
                    if (stop.asBoolean || Thread.currentThread().isInterrupted) return Result(Status.CANCELLED, connected, -1, null)
                    if (attempt > 0) {
                        val end = clock.nanos() + 200_000_000L
                        while (clock.nanos() < end) {
                            if (stop.asBoolean || Thread.currentThread().isInterrupted) return Result(Status.CANCELLED, connected, -1, null)
                            val left = end - clock.nanos()
                            if (left > 0) clock.sleep(maxOf(1L, minOf(25L, (left + 999_999) / 1_000_000)).toInt())
                        }
                        if (stop.asBoolean || Thread.currentThread().isInterrupted) return Result(Status.CANCELLED, connected, -1, null)
                    }
                    latency = connector.connect()
                    if (latency < 0) throw IOException("Invalid connection timing")
                    connected++
                }
                if (stop.asBoolean || Thread.currentThread().isInterrupted) return Result(Status.CANCELLED, connected, -1, null)
                return Result(Status.CONFIRMED, connected, latency, null)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result(Status.CANCELLED, connected, -1, null)
            } catch (e: IOException) {
                return Result(if (stop.asBoolean) Status.CANCELLED else Status.UNCONFIRMED, connected, -1, e)
            } catch (e: SecurityException) {
                return Result(if (stop.asBoolean) Status.CANCELLED else Status.UNCONFIRMED, connected, -1, e)
            }
        }
    }
}

