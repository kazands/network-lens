@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

/** Independent wire fixtures: split responses, isolation, lifetime and bounded questions. */
object MdnsAssemblyTest {
    private var checks: Int = 0

    private var TYPE: List<String> = Arrays.asList("_googlecast", "_tcp", "local")

    private var INSTANCE: List<String> =
        Arrays.asList("Living.room TV", "_googlecast", "_tcp", "local")

    private var HOST: List<String> = Arrays.asList("cast-fixture", "local")

    private var PEER: InetAddress = ip("192.0.2.20")

    private fun ip(value: String): InetAddress {
        try {
            return InetAddress.getByName(value)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    private fun check(value: Boolean, reason: String): Unit {
        checks++
        if (!(value)) {
            throw AssertionError(reason)
        }
    }

    private fun name(labels: List<String>): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        for (l in labels) {
            var v: ByteArray = l.toByteArray(StandardCharsets.UTF_8)
            b.write(v.size)
            b.write(v)
        }
        b.write(0)
        return b.toByteArray()
    }

    private fun txt(vararg fields: String): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        for (f in fields) {
            var v: ByteArray = f.toByteArray(StandardCharsets.UTF_8)
            b.write(v.size)
            b.write(v)
        }
        return b.toByteArray()
    }

    private fun srv(target: List<String>): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var d: DataOutputStream = DataOutputStream(b)
        d.writeInt(0)
        d.writeShort(8009)
        d.write(name(target))
        return b.toByteArray()
    }

    private fun rr(
        owner: List<String>,
        kind: Int,
        payload: ByteArray,
        ttl: Long,
        flush: Boolean,
    ): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var d: DataOutputStream = DataOutputStream(b)
        d.write(name(owner))
        d.writeShort(kind)
        d.writeShort((if (flush) 32769 else 1))
        d.writeInt((ttl).toInt())
        d.writeShort(payload.size)
        d.write(payload)
        return b.toByteArray()
    }

    private fun packet(vararg records: ByteArray): ByteArray {
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
        return b.toByteArray()
    }

    private fun parse(vararg records: ByteArray): MdnsPacket.Result {
        var p: ByteArray = packet(*records)
        return MdnsPacket.parse(p, p.size)
    }

    private fun parts(address: InetAddress): kotlin.Array<ByteArray> {
        return arrayOf(
            rr(TYPE, 12, name(INSTANCE), 120, false),
            rr(INSTANCE, 33, srv(HOST), 120, true),
            rr(INSTANCE, 16, txt("fn=Living room TV", "md=Fixture television"), 120, true),
            rr(HOST, (if ((address is Inet4Address)) 1 else 28), address.address, 120, true),
        )
    }

    private fun all(
        a: MdnsAssembly,
        peer: InetAddress,
        p: kotlin.Array<ByteArray>,
        now: Long,
    ): List<MdnsPacket.Service> {
        return a.accept(peer, parse(*p), now)
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var p: kotlin.Array<ByteArray> = parts(PEER)
        var start: Long = TimeUnit.SECONDS.toNanos(30)
        run {
            var i: Int = 0
            while ((i < 4)) {
                run {
                    var j: Int = 0
                    while ((j < 4)) {
                        if ((j != i)) {
                            run {
                                var k: Int = 0
                                while ((k < 4)) {
                                    if (((k != i) && (k != j))) {
                                        run {
                                            var l: Int = 0
                                            while ((l < 4)) {
                                                if ((((l != i) && (l != j)) && (l != k))) {
                                                    var a: MdnsAssembly = MdnsAssembly()
                                                    var order: IntArray = intArrayOf(i, j, k, l)
                                                    run {
                                                        var n: Int = 0
                                                        while ((n < 4)) {
                                                            var out: List<MdnsPacket.Service> =
                                                                a.accept(
                                                                    PEER,
                                                                    parse(p[order[n]]),
                                                                    (start + n),
                                                                )
                                                            check(
                                                                (out.size ==
                                                                    ((if ((n == 3)) 1 else 0))),
                                                                "Only complete records resolve, in all 24 message orders",
                                                            )
                                                            if ((n == 3)) {
                                                                var s: MdnsPacket.Service =
                                                                    out.get(0)
                                                                check(
                                                                    ((s.name.equals(
                                                                        "Living.room TV"
                                                                    ) &&
                                                                        s.host.equals(
                                                                            "cast-fixture.local"
                                                                        )) && (s.port == 8009)),
                                                                    "Literal dots stay inside the service instance",
                                                                )
                                                                check(
                                                                    String(
                                                                            (s.attributes.get(
                                                                                "md"
                                                                            ))!!,
                                                                            StandardCharsets.UTF_8,
                                                                        )
                                                                        .equals(
                                                                            "Fixture television"
                                                                        ),
                                                                    "Model comes from this peer's TXT record",
                                                                )
                                                            }
                                                            n++
                                                        }
                                                    }
                                                }
                                                l++
                                            }
                                        }
                                    }
                                    k++
                                }
                            }
                        }
                        j++
                    }
                }
                i++
            }
        }
        var a: MdnsAssembly = MdnsAssembly()
        check(
            ((all(a, PEER, p, start))!!.size == 1),
            "Same-packet general service resolves without Android NSD",
        )
        check(
            (all(MdnsAssembly(), ip("192.0.2.1"), p, start))!!.isEmpty(),
            "Proxy cannot borrow the advertised peer identity",
        )
        a = MdnsAssembly()
        a.accept(PEER, parse(p[0], p[1], p[3]), start)
        check(
            (a.accept(ip("192.0.2.21"), parse(p[2]), start))!!.isEmpty(),
            "TXT from another sender cannot join",
        )
        check(
            (a.accept(PEER, MdnsPacket.Result(), start))!!.isEmpty(),
            "Empty or cached objects never complete the assembly",
        )
        a = MdnsAssembly()
        a.accept(PEER, parse(p[0], p[1], p[2]), start)
        check(
            (a.accept(PEER, parse(p[3]), (start + TimeUnit.SECONDS.toNanos(10))))!!.isEmpty(),
            "Ten-second assembly lifetime expires earlier records",
        )
        a = MdnsAssembly()
        a.accept(PEER, parse(rr(TYPE, 12, name(INSTANCE), 1, false), p[1], p[2]), start)
        check(
            (a.accept(PEER, parse(p[3]), (start + TimeUnit.SECONDS.toNanos(1))))!!.isEmpty(),
            "Wire TTL shorter than safety window is honored",
        )
        a = MdnsAssembly()
        all(a, PEER, p, start)
        a.accept(PEER, parse(rr(TYPE, 12, name(INSTANCE), 0, false)), (start + 1))
        check(
            (a.accept(PEER, parse(p[0]), (start + 2)))!!.isEmpty(),
            "Goodbye invalidates the instance details before it can return",
        )
        run {
            var index: Int = 1
            while ((index < 4)) {
                a = MdnsAssembly()
                all(a, PEER, p, start)
                var gone: ByteArray =
                    (if ((index == 1)) rr(INSTANCE, 33, srv(HOST), 0, true)
                    else
                        (if ((index == 2))
                            rr(
                                INSTANCE,
                                16,
                                txt("fn=Living room TV", "md=Fixture television"),
                                0,
                                true,
                            )
                        else rr(HOST, 1, PEER.address, 0, true)))
                check(
                    (a.accept(PEER, parse(gone), (start + 1)))!!.isEmpty(),
                    "TTL-zero SRV/TXT/address prevents further association",
                )
                index++
            }
        }
        a = MdnsAssembly()
        all(a, PEER, p, start)
        var refreshed: List<MdnsPacket.Service> =
            a.accept(
                PEER,
                parse(rr(INSTANCE, 16, txt("fn=New name"), 120, true)),
                (start + TimeUnit.SECONDS.toNanos(2)),
            )
        check(refreshed.isEmpty(), "Conflicting TXT is withheld during cache-flush grace")
        refreshed = a.accept(PEER, parse(p[3]), (start + TimeUnit.SECONDS.toNanos(3)))
        check(
            ((refreshed.size == 1) && !((refreshed.get(0))!!.attributes.containsKey("md"))),
            "Cache-flush TXT replaces the RRset; removed fields do not survive in the assembly",
        )
        a = MdnsAssembly()
        a.accept(PEER, parse(p[0], rr(INSTANCE, 33, srv(HOST), 120, false), p[2], p[3]), start)
        check(
            (a.accept(
                    PEER,
                    parse(rr(INSTANCE, 33, srv(Arrays.asList("other", "local")), 120, false)),
                    (start + 1),
                ))!!
                .isEmpty(),
            "Conflicting SRV records are not arbitrarily selected",
        )
        a = MdnsAssembly()
        a.accept(PEER, parse(p[0]), start)
        var q: List<MdnsPacket.Question> = a.due(false, start, 4)
        check(
            (((q.size == 2) && (q.stream())!!.anyMatch(callback1@{ x -> (x.kind == 33) })) &&
                (q.stream())!!.anyMatch(callback2@{ x -> (x.kind == 16) })),
            "PTR triggers explicit missing SRV and TXT queries",
        )
        check(((q.get(0))!!.labels)!!.equals(INSTANCE), "Follow-up retains wire label boundaries")
        var request: ByteArray = MdnsPacket.followUp(q)
        check(
            ((((request[12]).toInt() and (255).toInt())) ==
                ("Living.room TV".toByteArray(StandardCharsets.UTF_8))!!.size),
            "Query encodes dotted instance as one label",
        )
        check(
            ((a.due(false, (start + 1), 4))!!.isEmpty() &&
                (a.due(true, (start + TimeUnit.SECONDS.toNanos(2)), 4))!!.isEmpty()),
            "Retries are spaced and do not leak across IP families",
        )
        check(
            ((a.due(false, (start + TimeUnit.MILLISECONDS.toNanos(1200)), 4))!!.size == 2),
            "One timed retry is available",
        )
        check(
            ((a.due(false, (start + TimeUnit.SECONDS.toNanos(3)), 4))!!.isEmpty() &&
                !(a.pending((start + TimeUnit.SECONDS.toNanos(3))))),
            "Unanswered records cannot cause unbounded queries or wait",
        )
        a.accept(PEER, parse(p[1], p[2]), (start + TimeUnit.SECONDS.toNanos(3)))
        q = a.due(false, (start + TimeUnit.SECONDS.toNanos(3)), 4)
        check(
            (((q.size == 1) && ((q.get(0))!!.kind == 1)) && ((q.get(0))!!.labels)!!.equals(HOST)),
            "SRV yields a targeted missing A question",
        )
        check(
            (((a.accept(PEER, parse(p[3]), (start + TimeUnit.SECONDS.toNanos(4))))!!.size == 1) &&
                !(a.pending((start + TimeUnit.SECONDS.toNanos(4))))),
            "Complete service cancels unnecessary follow-ups",
        )
        var timed: MdnsAssembly = MdnsAssembly()
        all(timed, PEER, p, start)
        timed.accept(
            PEER,
            parse(rr(INSTANCE, 16, txt("fn=Updated name"), 120, true)),
            (start + TimeUnit.SECONDS.toNanos(2)),
        )
        check(
            timed.pending((start + TimeUnit.MILLISECONDS.toNanos(2500))),
            "Pending grace prevents an early quiet-window exit",
        )
        check(
            (timed.ready((start + TimeUnit.MILLISECONDS.toNanos(2500))))!!.isEmpty(),
            "Timer never selects conflicting TXT prematurely",
        )
        var ready: Map<InetAddress, List<MdnsPacket.Service>> =
            timed.ready((start + TimeUnit.SECONDS.toNanos(3)))
        check(
            ((ready.size == 1) &&
                String(
                        (((ready.get(PEER))!!.get(0))!!.attributes.get("fn"))!!,
                        StandardCharsets.UTF_8,
                    )
                    .equals("Updated name")),
            "Expiry alone publishes the now-unambiguous service",
        )
        check(
            ((timed.ready((start + TimeUnit.MILLISECONDS.toNanos(3100))))!!.isEmpty() &&
                !(timed.pending((start + TimeUnit.MILLISECONDS.toNanos(3100))))),
            "Repeated timer ticks neither republish nor extend scan quiet time",
        )
        timed.close()
        check(
            (timed.ready((start + TimeUnit.SECONDS.toNanos(4))))!!.isEmpty(),
            "Stop discards timer completions",
        )
        var v6: InetAddress = ip("fd00::20")
        a = MdnsAssembly()
        var six: kotlin.Array<ByteArray> = parts(v6)
        a.accept(v6, parse(six[0], six[1], six[2]), start)
        q = a.due(true, start, 4)
        check(((q.size == 1) && ((q.get(0))!!.kind == 28)), "IPv6 sender gets an AAAA follow-up")
        check(
            ((a.accept(v6, parse(six[3]), (start + 1)))!!.size == 1),
            "Same-sender IPv6 assembly works",
        )
        var burst: MdnsAssembly = MdnsAssembly()
        burst.accept(v6, parse(six[0], six[1], six[3]), start)
        var alternate: ByteArray = rr(HOST, 28, (ip("fd00::21"))!!.address, 120, true)
        burst.accept(v6, parse(alternate), (start + TimeUnit.MILLISECONDS.toNanos(200)))
        check(
            ((burst.accept(v6, parse(six[2]), (start + TimeUnit.MILLISECONDS.toNanos(400))))!!
                .size == 1),
            "Same-burst cache flush preserves an earlier matching AAAA member",
        )
        burst.accept(v6, parse(alternate), (start + TimeUnit.SECONDS.toNanos(2)))
        check(
            ((burst.accept(v6, parse(six[2]), (start + TimeUnit.MILLISECONDS.toNanos(2500))))!!
                .size == 1),
            "Old RRset member remains only during one-second grace",
        )
        check(
            (burst.accept(v6, parse(six[2]), (start + TimeUnit.SECONDS.toNanos(3))))!!.isEmpty(),
            "Unrefreshed old address expires at the grace deadline",
        )
        check(
            ((burst.due(true, (start + TimeUnit.SECONDS.toNanos(3)), 4))!!.stream())!!.anyMatch(
                callback3@{ x -> (x.kind == 28) }
            ),
            "An alternate address cannot suppress a missing sender-address retry",
        )
        check(
            ((burst.accept(v6, parse(six[3]), (start + TimeUnit.MILLISECONDS.toNanos(3100))))!!
                .size == 1),
            "A refreshed matching address repairs the association",
        )
        var refreshedBurst: MdnsAssembly = MdnsAssembly()
        all(refreshedBurst, v6, six, start)
        refreshedBurst.accept(v6, parse(alternate), (start + TimeUnit.SECONDS.toNanos(2)))
        refreshedBurst.accept(v6, parse(six[3]), (start + TimeUnit.MILLISECONDS.toNanos(2400)))
        check(
            ((refreshedBurst.accept(v6, parse(six[2]), (start + TimeUnit.SECONDS.toNanos(4))))!!
                .size == 1),
            "A matching member refreshed during the burst keeps its own TTL",
        )
        var dottedAlias: List<String> =
            Arrays.asList("Living", "room TV", "_googlecast", "_tcp", "local")
        check(
            (MdnsAssembly()
                    .accept(
                        PEER,
                        parse(p[0], rr(dottedAlias, 33, srv(HOST), 120, true), p[2], p[3]),
                        start,
                    ))!!
                .isEmpty(),
            "Different DNS label boundaries cannot borrow a matching dotted instance",
        )
        var unicode: List<String> = Arrays.asList("Café TV", "_http", "_tcp", "local")
        var unicodeQuery: ByteArray =
            MdnsPacket.followUp(Arrays.asList(MdnsPacket.Question(unicode, 16)))
        check(
            ((((unicodeQuery[12]).toInt() and (255).toInt())) ==
                ("Café TV".toByteArray(StandardCharsets.UTF_8))!!.size),
            "Follow-up encoding preserves UTF-8 label bytes",
        )
        a.close()
        check(
            (((a.accept(v6, parse(*six), (start + 2)))!!.isEmpty() &&
                (a.due(true, (start + 2), 4))!!.isEmpty()) && !(a.pending((start + 2)))),
            "Closed scan discards late replies and queued work",
        )
        check(
            (MdnsAssembly().accept(PEER, parse(p[3]), start))!!.isEmpty(),
            "A new scan cannot inherit prior service records",
        )
        for (invalid in
            Arrays.asList(
                MdnsPacket.Question(Arrays.asList("example", "com"), 1),
                MdnsPacket.Question(INSTANCE, 255),
                MdnsPacket.Question(Arrays.asList("bad\nname", "_http", "_tcp", "local"), 16),
            )) {
            try {
                MdnsPacket.followUp(Arrays.asList(invalid))
                throw AssertionError("Unsafe question")
            } catch (expected: IllegalArgumentException) {
                check(true, "Reject non-local, arbitrary-type and control-character queries")
            }
        }
        a = MdnsAssembly()
        a.accept(PEER, parse(p[0]), -(TimeUnit.SECONDS.toNanos(10)))
        check(
            ((a.due(false, -(TimeUnit.SECONDS.toNanos(10)), 4))!!.size == 2),
            "Monotonic clocks need no positive epoch",
        )
        var full: ByteArray = packet(*p)
        run {
            var n: Int = 0
            while ((n < full.size)) {
                check(
                    (MdnsPacket.parse(full, n))!!.records.isEmpty(),
                    "Truncated packets do not leak partial wire records",
                )
                n++
            }
        }
        a = MdnsAssembly()
        run {
            var n: Int = 1
            while ((n <= 129)) {
                a.accept(ip(("192.0.2." + n)), parse(p[0]), start)
                n++
            }
        }
        check(a.limited, "Peer admission is bounded and surfaced")
        a = MdnsAssembly()
        run {
            var n: Int = 0
            while ((n < 150)) {
                var inst: List<String> = Arrays.asList(("Peer " + n), "_http", "_tcp", "local")
                a.accept(
                    ip(("198.51.100." + (((n / 30) + 1)))),
                    parse(rr(Arrays.asList("_http", "_tcp", "local"), 12, name(inst), 120, false)),
                    start,
                )
                a.due(false, start, 4)
                n++
            }
        }
        check(a.limited, "RRset/query budget pressure is surfaced")
        var random: Random = Random(303)
        run {
            var n: Int = 0
            while ((n < 10000)) {
                var data: ByteArray = ByteArray(random.nextInt(512))
                random.nextBytes(data)
                var result: MdnsPacket.Result = MdnsPacket.parse(data, data.size)
                var bounded: MdnsAssembly = MdnsAssembly()
                bounded.accept(PEER, result, start)
                for (question in bounded.due(false, start, 4)) {
                    MdnsPacket.followUp(Arrays.asList(question))
                }
                n++
            }
        }
        System.out.println(
            (("PASS · " + checks) +
                " split mDNS, fresh association, TTL, query and bound checks; 10000 malformed packets")
        )
    }
}
