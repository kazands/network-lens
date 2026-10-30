@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.*

object RoutedIpv6Test {
    var checks: Int = 0

    fun check(yes: Boolean, why: String): Unit {
        checks++
        if (!(yes)) {
            throw AssertionError(why)
        }
    }

    fun ip(s: String): InetAddress {
        return InetAddress.getByName(s)
    }

    fun rule(
        prefix: String,
        length: Int,
        gateway: String,
        iface: String,
        unicast: Boolean,
    ): Ipv6Routes.Rule {
        return Ipv6Routes.Rule(prefix, length, gateway, iface, unicast)
    }

    fun routes(vararg rules: Ipv6Routes.Rule): Ipv6Routes {
        return Ipv6Routes(Arrays.asList(*rules), "wlan0")
    }

    fun name(vararg labels: String): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        for (l in labels) {
            var v: ByteArray = l.toByteArray(java.nio.charset.Charset.forName("UTF-8"))
            b.write(v.size)
            b.write(v)
        }
        b.write(0)
        return b.toByteArray()
    }

    fun rr(owner: ByteArray, kind: Int, payload: ByteArray, ttl: Long): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var d: DataOutputStream = DataOutputStream(b)
        d.write(owner)
        d.writeShort(kind)
        d.writeShort((if ((kind == 12)) 1 else 32769))
        d.writeInt((ttl).toInt())
        d.writeShort(payload.size)
        d.write(payload)
        return b.toByteArray()
    }

    fun packet(vararg records: ByteArray): MdnsPacket.Result {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var d: DataOutputStream = DataOutputStream(b)
        d.writeShort(0)
        d.writeShort(33792)
        d.writeShort(0)
        d.writeShort(records.size)
        d.writeInt(0)
        for (r in records) {
            d.write(r)
        }
        var p: ByteArray = b.toByteArray()
        return MdnsPacket.parse(p, p.size)
    }

    fun graph(type: String, protocol: String): kotlin.Array<ByteArray> {
        var service: ByteArray = name(type, protocol, "local")
        var instance: ByteArray = name("Sleepy.sensor", type, protocol, "local")
        var host: ByteArray = name("sensor", "local")
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var d: DataOutputStream = DataOutputStream(b)
        d.writeInt(0)
        d.writeShort(12345)
        d.write(host)
        var txt: ByteArray =
            "md=Fixture sensor".toByteArray(java.nio.charset.Charset.forName("UTF-8"))
        var field: ByteArray = ByteArray((txt.size + 1))
        field[0] = (txt.size).toByte()
        System.arraycopy(txt, 0, field, 1, txt.size)
        return arrayOf(
            rr(service, 12, instance, 120),
            rr(instance, 33, b.toByteArray(), 120),
            rr(instance, 16, field, 120),
            rr(host, 28, (ip("fd70::20"))!!.address, 120),
        )
    }

    class Clock : IcmpEcho.Clock {
        var t: Long = 0L

        override fun nanos(): Long {
            return t
        }

        override fun sleep(ms: Int): Unit {
            t += (ms * 1000000L)
        }
    }

    class Channel : IcmpEcho.Channel {
        var clock: Clock? = null

        var sent: MutableList<ByteArray> = ArrayList()

        var closed: Boolean = false

        var respondAfter: Int = 0

        constructor(c: Clock, after: Int) {
            clock = c
            respondAfter = after
        }

        override fun identifier(): Int {
            return 32123
        }

        override fun send(p: ByteArray): Unit {
            sent.add(p.clone())
        }

        override fun receive(ms: Int): ByteArray? {
            clock!!.t += (ms * 1000000L)
            if (((respondAfter < 0) || (clock!!.t < (respondAfter * 1000000L)))) {
                return null
            }
            var p: ByteArray = (sent.get(0))!!.clone()
            p[0] = (129).toByte()
            return p
        }

        override fun close(): Unit {
            closed = true
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var omr: Ipv6Routes.Rule = rule("fd70::", 64, "fe80::1", "wlan0", true)
        var valid: Ipv6Routes = routes(omr)
        check(
            (valid.gateway(ip("fd70::20")))!!.equals("fe80::1"),
            "Explicit OMR /64 admits a routed ULA endpoint",
        )
        for (target in
            arrayOf(
                "fd71::20",
                "2001:4860:4860::8888",
                "fe80::20",
                "ff02::1",
                "::1",
                "::",
                "192.0.2.1",
                "64:ff9b::c000:201",
            )) {
            check((valid.gateway(ip(target)) === null), ("No off-route/special target " + target))
        }
        for (bad in
            arrayOf(
                rule("::", 0, "fe80::1", "wlan0", true),
                rule("fd00::", 8, "fe80::1", "wlan0", true),
                rule("fd70::", 64, "fe80::1", "rmnet0", true),
                rule("fd70::", 64, "::", "wlan0", true),
                rule("fd70::", 64, "fd00::1", "wlan0", true),
                rule("fd70::", 64, "fe80::1", "wlan0", false),
            )) {
            check(
                ((routes(bad))!!.gateway(ip("fd70::20")) === null),
                "Default, broad, wrong-interface, missing-hop and reject routes excluded",
            )
        }
        var reject: Ipv6Routes.Rule = rule("fd70::20", 128, "::", "wlan0", false)
        check(
            ((routes(omr, reject))!!.gateway(ip("fd70::20")) === null),
            "More-specific reject wins over admitted /64",
        )
        check(
            ((routes(omr, reject))!!.gateway(ip("fd70::21")) !== null),
            "Reject does not hide unrelated addresses",
        )
        check(
            valid.retains(routes(omr, rule("fd71::", 64, "fe80::2", "wlan0", true))),
            "An unrelated routed prefix can be added",
        )
        check(!(valid.retains(routes())), "Route withdrawal fences old work")
        check(!(valid.retains(routes(omr, reject))), "Adding an overlapping reject fences old work")
        check(
            !(valid.retains(routes(rule("fd70::", 64, "fe80::2", "wlan0", true)))),
            "Next-hop replacement fences old work",
        )
        check(
            ((routes(rule("2001:db8:70::", 64, "fe80::1", "wlan0", true)))!!.gateway(
                ip("2001:db8:70::20")
            ) !== null),
            "A specific GUA /64 is supported",
        )
        var connected: Ipv6Routes.Rule = rule("fe80::", 64, "::", "wlan0", true)
        var broad: Ipv6Routes.Rule = rule("fd00::", 8, "fe80::1", "wlan0", true)
        check(
            (routes(connected))!!.same(routes()),
            "Irrelevant connected IPv6 route metadata is not routed discovery scope",
        )
        check(
            (valid.same(routes(omr, connected, broad)) && valid.retains(routes(omr, broad))),
            "Less-specific and unrelated route metadata cannot erase accepted scope",
        )
        check(
            !(valid.same(routes(omr, reject))),
            "A more-specific reject changes effective discovery scope",
        )
        var now: Long = TimeUnit.SECONDS.toNanos(20)
        var g: kotlin.Array<ByteArray> = graph("_hap", "_udp")
        var proxy: InetAddress = ip("192.0.2.1")
        run {
            var a: Int = 0
            while ((a < 4)) {
                run {
                    var b: Int = 0
                    while ((b < 4)) {
                        if ((a != b)) {
                            run {
                                var c: Int = 0
                                while ((c < 4)) {
                                    if (((c != a) && (c != b))) {
                                        run {
                                            var e: Int = 0
                                            while ((e < 4)) {
                                                if ((((e != a) && (e != b)) && (e != c))) {
                                                    var assembly: MdnsAssembly = MdnsAssembly()
                                                    var order: IntArray = intArrayOf(a, b, c, e)
                                                    run {
                                                        var n: Int = 0
                                                        while ((n < 4)) {
                                                            check(
                                                                (assembly.accept(
                                                                        proxy,
                                                                        packet(g[order[n]]),
                                                                        (now + n),
                                                                    ))!!
                                                                    .isEmpty(),
                                                                "Proxy never becomes direct endpoint identity",
                                                            )
                                                            check(
                                                                ((assembly.routedCandidates(
                                                                        (now + n)
                                                                    ))!!
                                                                    .size ==
                                                                    ((if ((n == 3)) 1 else 0))),
                                                                "Complete proxied graph only, all packet orders",
                                                            )
                                                            n++
                                                        }
                                                    }
                                                }
                                                e++
                                            }
                                        }
                                    }
                                    c++
                                }
                            }
                        }
                        b++
                    }
                }
                a++
            }
        }
        var assembly: MdnsAssembly = MdnsAssembly()
        assembly.accept(proxy, packet(*g), now)
        check(
            (((assembly.routedCandidates(now))!!.get(0))!!.service)!!.name.equals("Sleepy.sensor"),
            "Dotted instance label preserved",
        )
        check(
            (assembly.routedCandidates((now + TimeUnit.SECONDS.toNanos(10))))!!.isEmpty(),
            "Proxy details expire within ten seconds",
        )
        run {
            var part: Int = 0
            while ((part < 4)) {
                assembly = MdnsAssembly()
                assembly.accept(proxy, packet(*g), now)
                var gone: ByteArray = (g[part])!!.clone()
                var ownerEnd: Int = 0
                while (((gone[ownerEnd]).toInt() != (0).toInt())) {
                    ownerEnd += ((((gone[ownerEnd]).toInt() and (255).toInt())) + 1)
                }
                ownerEnd++
                run {
                    var k: Int = (ownerEnd + 4)
                    while ((k < (ownerEnd + 8))) {
                        gone[k] = 0
                        k++
                    }
                }
                assembly.accept(proxy, packet(gone), (now + 1))
                check(
                    (assembly.routedCandidates((now + 2)))!!.isEmpty(),
                    "Goodbye of each graph edge prevents new metadata association",
                )
                part++
            }
        }
        assembly = MdnsAssembly()
        assembly.accept(proxy, packet(g[0], g[1], g[3]), now)
        assembly.accept(ip("192.0.2.2"), packet(g[2]), now)
        check(
            (assembly.routedCandidates(now))!!.isEmpty(),
            "Proxy senders cannot pool graph records",
        )
        assembly = MdnsAssembly()
        assembly.accept(proxy, packet(*graph("_http", "_tcp")), now)
        check(
            (assembly.routedCandidates(now))!!.isEmpty(),
            "Unrelated services cannot create routed targets",
        )
        assembly = MdnsAssembly()
        assembly.accept(proxy, packet(*g), now)
        assembly.close()
        check((assembly.routedCandidates(now))!!.isEmpty(), "Stop closes candidate cache")
        for (delay in intArrayOf(3, 1800, 2600, 3990)) {
            var clock: Clock = Clock()
            var channel: Channel = Channel((clock)!!, delay)
            var result: IcmpEcho.Result =
                IcmpEcho.probeV6(callback1@{ channel }, callback2@{ false }, 2000, 2, 200, clock)
            check(
                ((result.status === IcmpEcho.Status.REPLIED) && channel.closed),
                ("Delayed IPv6 echo confirms, then closes: " + delay),
            )
            check(
                (result.requests == ((if ((delay <= 2000)) 1 else 2))),
                "Retry follows real timeout",
            )
        }
        var clock: Clock = Clock()
        var channel: Channel = Channel((clock)!!, -(1))
        var result: IcmpEcho.Result =
            IcmpEcho.probeV6(callback3@{ channel }, callback4@{ false }, 2000, 2, 200, clock)
        check(
            ((((result.status === IcmpEcho.Status.NO_REPLY) && (result.requests == 2)) &&
                (clock!!.t == 4200000000L)) && channel.closed),
            "Unreachable sleepy accessory stays unconfirmed after bounded 4.2 seconds",
        )
        var cancelClock: Clock = Clock()
        var cancelled: Channel = Channel(cancelClock, -(1))
        result =
            IcmpEcho.probeV6(
                callback5@{ cancelled },
                callback6@{ (cancelClock.t >= 150000000L) },
                2000,
                2,
                200,
                cancelClock,
            )
        check(
            (((result.status === IcmpEcho.Status.CANCELLED) && (cancelClock.t == 150000000L)) &&
                cancelled.closed),
            "Cancellation is polled within 50 ms and closes the socket",
        )
        var nonce: ByteArray = ByteArray(16)
        Arrays.fill(nonce, (11).toByte())
        var request: ByteArray = IcmpEcho.requestV6(32123, 1, nonce)
        check(
            ((((((request[0]).toInt() and (255).toInt())) == 128) &&
                ((request[2]).toInt() == (0).toInt())) && ((request[3]).toInt() == (0).toInt())),
            "Kernel-checksummed IPv6 echo request",
        )
        var reply: ByteArray = request.clone()
        reply[0] = (129).toByte()
        check(
            (IcmpEcho.replySequenceV6(reply, 32123, 1, nonce) == 1),
            "Exact peer response matches nonce/id/sequence",
        )
        for (index in intArrayOf(0, 1, 4, 5, 6, 7, 8, 23)) {
            var bad: ByteArray = reply.clone()
            bad[index] = ((bad[index]).toInt() xor (1).toInt()).toByte()
            check(
                (IcmpEcho.replySequenceV6(bad, 32123, 1, nonce) == 0),
                ("Reject wrong IPv6 echo field " + index),
            )
        }
        run {
            var n: Int = 0
            while ((n < 24)) {
                check(
                    (IcmpEcho.replySequenceV6(Arrays.copyOf(reply, n), 32123, 1, nonce) == 0),
                    ("Reject truncated IPv6 datagram " + n),
                )
                n++
            }
        }
        System.out.println((("PASS · " + checks) + " routed IPv6 checks"))
    }
}
