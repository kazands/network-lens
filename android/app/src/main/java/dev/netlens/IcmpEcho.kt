package dev.netlens

import java.io.IOException
import java.security.SecureRandom
import java.util.Arrays
import java.util.function.BooleanSupplier

/** Bounded echo exchange; the transport connects to exactly one peer. */
class IcmpEcho private constructor() {
    enum class Status { REPLIED, NO_REPLY, CANCELLED, ERROR }
    enum class Problem { PERMISSION, UNSUPPORTED, SETUP, ROUTE, IO, REPLY_LIMIT, PEER_UNREACHABLE }

    class Failure(@JvmField val problem: Problem, message: String) : IOException(message)

    interface Channel : AutoCloseable {
        fun identifier(): Int
        @Throws(IOException::class) fun send(packet: ByteArray)
        /** Only connected-peer ICMP bytes, or null within waitMillis. */
        @Throws(IOException::class) fun receive(waitMillis: Int): ByteArray?
        @Throws(IOException::class) override fun close()
    }
    fun interface Factory { @Throws(IOException::class) fun open(): Channel }
    interface Clock {
        fun nanos(): Long
        @Throws(InterruptedException::class) fun sleep(millis: Int)
    }
    class Result internal constructor(
        @JvmField val status: Status,
        @JvmField val problem: Problem?,
        @JvmField val requests: Int,
        @JvmField val latencyMillis: Long
    ) {
        fun reason(): String = when (problem) {
            null -> ""
            Problem.PERMISSION -> "Android or a VPN blocked ICMP echo requests"
            Problem.UNSUPPORTED -> "ICMP echo sockets are unavailable on this Android device"
            Problem.SETUP -> "Could not prepare an ICMP socket on the selected Wi-Fi"
            Problem.ROUTE -> "The selected Wi-Fi route changed or became unavailable"
            Problem.REPLY_LIMIT -> "Too many unrelated ICMP replies; the safety limit was reached"
            else -> "An ICMP request could not be sent or received"
        }
    }

    companion object {
        private val RANDOM = SecureRandom()
        private val CLOCK = object : Clock {
            override fun nanos(): Long = System.nanoTime()
            override fun sleep(millis: Int) { Thread.sleep(millis.toLong()) }
        }
        @JvmStatic fun probe(factory: Factory, cancelled: BooleanSupplier, timeoutMillis: Int,
            attempts: Int, retryDelayMillis: Int): Result =
            probe(factory, cancelled, timeoutMillis, attempts, retryDelayMillis, CLOCK)

        @JvmStatic fun probe(factory: Factory, cancelled: BooleanSupplier, timeoutMillis: Int,
            attempts: Int, retryDelayMillis: Int, clock: Clock): Result =
            exchange(factory, cancelled, timeoutMillis, attempts, retryDelayMillis, clock, false)

        @JvmStatic fun probeV6(factory: Factory, cancelled: BooleanSupplier, timeoutMillis: Int,
            attempts: Int, retryDelayMillis: Int): Result =
            probeV6(factory, cancelled, timeoutMillis, attempts, retryDelayMillis, CLOCK)

        @JvmStatic fun probeV6(factory: Factory, cancelled: BooleanSupplier, timeoutMillis: Int,
            attempts: Int, retryDelayMillis: Int, clock: Clock): Result =
            exchange(factory, cancelled, timeoutMillis, attempts, retryDelayMillis, clock, true)

        private fun exchange(factory: Factory, cancelled: BooleanSupplier, timeoutMillis: Int,
            attempts: Int, retryDelayMillis: Int, clock: Clock, ipv6: Boolean): Result {
            require(timeoutMillis in 1..(if (ipv6) 2500 else 1000) && attempts in 1..2 && retryDelayMillis in 0..500) { "Invalid echo budget" }
            var sent = 0
            var channel: Channel? = null
            var result: Result? = null
            try {
                if (cancelled.asBoolean) return Result(Status.CANCELLED, null, 0, -1)
                val nonce = ByteArray(16)
                RANDOM.nextBytes(nonce)
                channel = factory.open()
                if (channel.identifier() !in 1..65535) throw Failure(Problem.SETUP, "Invalid echo socket identifier")
                val starts = LongArray(attempts)
                result = Result(Status.NO_REPLY, null, 0, -1)
                exchange@ for (attempt in 1..attempts) {
                    if (cancelled.asBoolean) { result = Result(Status.CANCELLED, null, sent, -1); break }
                    if (attempt > 1) {
                        val until = clock.nanos() + retryDelayMillis * 1_000_000L
                        while (clock.nanos() < until) {
                            if (cancelled.asBoolean) { result = Result(Status.CANCELLED, null, sent, -1); break@exchange }
                            clock.sleep(minOf(25L, maxOf(1L, (until - clock.nanos() + 999999) / 1_000_000)).toInt())
                        }
                    }
                    if (cancelled.asBoolean) { result = Result(Status.CANCELLED, null, sent, -1); break }
                    starts[attempt - 1] = clock.nanos()
                    channel.send(if (ipv6) requestV6(channel.identifier(), attempt, nonce) else request(channel.identifier(), attempt, nonce))
                    sent++
                    val deadline = starts[attempt - 1] + timeoutMillis * 1_000_000L
                    var packets = 0
                    while (clock.nanos() < deadline) {
                        if (cancelled.asBoolean) { result = Result(Status.CANCELLED, null, sent, -1); break@exchange }
                        val wait = minOf(50L, maxOf(1L, (deadline - clock.nanos() + 999999) / 1_000_000)).toInt()
                        val reply = channel.receive(wait)
                        if (cancelled.asBoolean) { result = Result(Status.CANCELLED, null, sent, -1); break@exchange }
                        if (reply == null) continue
                        val sequence = if (ipv6) replySequenceV6(reply, channel.identifier(), attempt, nonce)
                            else replySequence(reply, channel.identifier(), attempt, nonce)
                        if (sequence > 0) {
                            result = Result(Status.REPLIED, null, sent, maxOf(0L, (clock.nanos() - starts[sequence - 1]) / 1_000_000))
                            break@exchange
                        }
                        if (++packets >= 64) { result = Result(Status.ERROR, Problem.REPLY_LIMIT, sent, -1); break@exchange }
                    }
                    result = Result(Status.NO_REPLY, null, sent, -1)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                result = Result(Status.CANCELLED, null, sent, -1)
            } catch (e: IOException) {
                result = if (cancelled.asBoolean) Result(Status.CANCELLED, null, sent, -1)
                else if (e is Failure && e.problem == Problem.PEER_UNREACHABLE) Result(Status.NO_REPLY, null, sent, -1)
                else Result(Status.ERROR, if (e is Failure) e.problem else Problem.IO, sent, -1)
            } catch (_: SecurityException) {
                result = Result(if (cancelled.asBoolean) Status.CANCELLED else Status.ERROR,
                    if (cancelled.asBoolean) null else Problem.PERMISSION, sent, -1)
            } finally {
                val opened = channel
                if (opened != null) try { opened.close() } catch (_: IOException) {
                    val current = result
                    if (current != null && current.status != Status.CANCELLED) {
                        result = Result(if (current.status == Status.REPLIED) Status.REPLIED else Status.ERROR,
                            Problem.IO, sent, current.latencyMillis)
                    }
                }
            }
            return checkNotNull(result)
        }

        @JvmStatic fun request(identifier: Int, sequence: Int, nonce: ByteArray?): ByteArray {
            require(nonce != null && nonce.size == 16 && identifier in 1..65535 && sequence in 1..2) { "Invalid echo request" }
            val packet = ByteArray(24)
            packet[0] = 8
            packet[4] = (identifier ushr 8).toByte()
            packet[5] = identifier.toByte()
            packet[6] = (sequence ushr 8).toByte()
            packet[7] = sequence.toByte()
            System.arraycopy(nonce, 0, packet, 8, 16)
            val checksum = checksum(packet)
            packet[2] = (checksum ushr 8).toByte()
            packet[3] = checksum.toByte()
            return packet
        }

        /** Linux ping sockets supply and validate the IPv6 pseudo-header checksum. */
        @JvmStatic fun requestV6(identifier: Int, sequence: Int, nonce: ByteArray?): ByteArray =
            request(identifier, sequence, nonce).also { it[0] = 128.toByte(); it[2] = 0; it[3] = 0 }

        /** Only for checksum-validated, connected-peer ICMPv6 datagrams, never raw packets. */
        @JvmStatic fun replySequenceV6(packet: ByteArray?, identifier: Int, sentSequence: Int, nonce: ByteArray?): Int {
            if (packet == null || packet.size != 24 || packet[0].toInt() and 255 != 129 || packet[1].toInt() != 0) return 0
            return matchingSequence(packet, identifier, sentSequence, nonce)
        }

        @JvmStatic fun replySequence(packet: ByteArray?, identifier: Int, sentSequence: Int, nonce: ByteArray?): Int {
            if (packet == null || packet.size != 24 || nonce == null || nonce.size != 16 ||
                packet[0].toInt() != 0 || packet[1].toInt() != 0 || checksum(packet) != 0) return 0
            return matchingSequence(packet, identifier, sentSequence, nonce)
        }
        private fun matchingSequence(packet: ByteArray, identifier: Int, sentSequence: Int, nonce: ByteArray?): Int {
            if (nonce == null || nonce.size != 16) return 0
            if ((((packet[4].toInt() and 255) shl 8) or (packet[5].toInt() and 255)) != identifier) return 0
            val sequence = ((packet[6].toInt() and 255) shl 8) or (packet[7].toInt() and 255)
            if (sequence < 1 || sequence > sentSequence) return 0
            return if (Arrays.equals(Arrays.copyOfRange(packet, 8, 24), nonce)) sequence else 0
        }

        @JvmStatic fun checksum(bytes: ByteArray): Int {
            var sum = 0
            var i = 0
            while (i < bytes.size) {
                sum += ((bytes[i].toInt() and 255) shl 8) or
                    (if (i + 1 < bytes.size) bytes[i + 1].toInt() and 255 else 0)
                i += 2
            }
            while ((sum ushr 16) != 0) sum = (sum and 65535) + (sum ushr 16)
            return sum.inv() and 65535
        }
    }
}

