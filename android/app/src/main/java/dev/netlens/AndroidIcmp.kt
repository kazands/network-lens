package dev.netlens

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.IOException
import java.net.*

/** Rootless echo datagrams bound to the selected Wi-Fi Network. No raw sockets. */
class AndroidIcmp private constructor() {
    companion object {
        @JvmStatic @Throws(IOException::class)
        fun open(net: NetworkState, target: InetAddress?): IcmpEcho.Channel {
            val ipv6 = target is Inet6Address
            if (target == null || !net.admitted(target) || net.network == null || (!ipv6 && net.ip.isEmpty())) {
                throw IcmpEcho.Failure(IcmpEcho.Problem.SETUP, "Target is outside the selected Wi-Fi")
            }
            var fd: FileDescriptor? = null
            var complete = false
            try {
                val descriptor = Os.socket(if (ipv6) OsConstants.AF_INET6 else OsConstants.AF_INET,
                    OsConstants.SOCK_DGRAM, if (ipv6) OsConstants.IPPROTO_ICMPV6 else OsConstants.IPPROTO_ICMP)
                fd = descriptor
                net.network!!.bindSocket(descriptor)
                Os.bind(descriptor, InetAddress.getByName(if (ipv6) "::" else net.ip), 0)
                Os.fcntlInt(descriptor, OsConstants.F_SETFL,
                    Os.fcntlInt(descriptor, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
                val peer = if (ipv6) InetAddress.getByName(net.scoped(target)) else target
                Os.connect(descriptor, peer, 0)
                val id = (Os.getsockname(descriptor) as InetSocketAddress).port
                val channel = SocketChannel(descriptor, id, peer)
                complete = true
                return channel
            } catch (e: ErrnoException) { throw failure(e, true) }
            catch (_: SecurityException) { throw IcmpEcho.Failure(IcmpEcho.Problem.PERMISSION, "ICMP permission denied") }
            catch (e: IOException) {
                if (e is IcmpEcho.Failure) throw e
                var cause: Throwable? = e
                while (cause != null) {
                    if (cause is ErrnoException) throw failure(cause, true)
                    cause = cause.cause
                }
                throw IcmpEcho.Failure(IcmpEcho.Problem.SETUP, "Could not bind the ICMP socket to Wi-Fi")
            } finally {
                if (!complete && fd != null) try { Os.close(fd) } catch (_: ErrnoException) {}
            }
        }

        private fun failure(e: ErrnoException, setup: Boolean): IcmpEcho.Failure {
            val problem = when (e.errno) {
                OsConstants.EPERM, OsConstants.EACCES -> IcmpEcho.Problem.PERMISSION
                OsConstants.EPROTONOSUPPORT, OsConstants.EAFNOSUPPORT, OsConstants.EOPNOTSUPP -> IcmpEcho.Problem.UNSUPPORTED
                OsConstants.EHOSTUNREACH, OsConstants.ECONNREFUSED, OsConstants.ECONNRESET -> IcmpEcho.Problem.PEER_UNREACHABLE
                OsConstants.ENETUNREACH, OsConstants.ENETDOWN, OsConstants.ENETRESET -> IcmpEcho.Problem.ROUTE
                else -> if (setup) IcmpEcho.Problem.SETUP else IcmpEcho.Problem.IO
            }
            return IcmpEcho.Failure(problem, "ICMP socket error " + e.errno)
        }
    }

    private class SocketChannel(private val fd: FileDescriptor, private val id: Int, private val peer: InetAddress) : IcmpEcho.Channel {
        private var closed = false
        override fun identifier(): Int = id

        @Throws(IOException::class) override fun send(packet: ByteArray) {
            try {
                if (Os.write(fd, packet, 0, packet.size) != packet.size) throw IcmpEcho.Failure(IcmpEcho.Problem.IO, "Short ICMP send")
            } catch (e: ErrnoException) { throw failure(e, false) }
        }

        @Throws(IOException::class) override fun receive(waitMillis: Int): ByteArray? {
            val poll = StructPollfd()
            poll.fd = fd
            poll.events = OsConstants.POLLIN.toShort()
            try {
                if (Os.poll(arrayOf(poll), waitMillis) == 0) return null
                if ((poll.revents.toInt() and OsConstants.POLLNVAL) != 0) throw IcmpEcho.Failure(IcmpEcho.Problem.IO, "Invalid ICMP socket")
                val reply = ByteArray(256)
                val sender = InetSocketAddress(0)
                val count = Os.recvfrom(fd, reply, 0, reply.size, 0, sender)
                if (count <= 0) throw IcmpEcho.Failure(IcmpEcho.Problem.IO, "ICMP socket closed unexpectedly")
                // Linux ping-socket demultiplexing is not a remote-address filter.
                // Charge mismatched peers against the exchange's unrelated-packet limit.
                if (sender.address == null || !java.util.Arrays.equals(sender.address.address, peer.address)) return ByteArray(0)
                return java.util.Arrays.copyOf(reply, count)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR || e.errno == OsConstants.EAGAIN) return null
                if (e.errno == OsConstants.EHOSTUNREACH || e.errno == OsConstants.ECONNREFUSED || e.errno == OsConstants.ECONNRESET) return null
                throw failure(e, false)
            }
        }

        @Throws(IOException::class) override fun close() {
            if (!closed) {
                closed = true
                try { Os.close(fd) } catch (e: ErrnoException) { throw failure(e, false) }
            }
        }
    }
}

