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

    private fun associatedAppleModels() {
        val peer = ip("192.0.2.90")
        val other = ip("192.0.2.91")
        val type = listOf("_companion-link", "_tcp", "local")
        val instance = listOf("Synthetic tablet") + type
        val host = listOf("synthetic-tablet", "local")
        val modelOwner = listOf("Synthetic tablet", "_device-info", "_tcp", "local")
        val anchor = parse(rr(type, 12, name(instance), 120, false), rr(instance, 33, srv(host), 120, true),
            rr(instance, 16, txt("rpVr=1"), 120, true), rr(host, 1, peer.address, 120, true))
        val model = parse(rr(modelOwner, 16, txt("model=iPad14,1"), 120, true))
        val now = 1_000_000_000L
        for (modelFirst in listOf(false, true)) {
            val assembly = MdnsAssembly()
            assembly.accept(peer, if (modelFirst) model else anchor, now)
            check(assembly.associatedModels(peer, now).isEmpty(), "Neither standalone model nor model-less anchor fabricates model evidence")
            if (!modelFirst) {
                val questions = assembly.due(false, now, 4)
                check(questions.any { it.kind == 16 && it.labels == modelOwner }, "Complete Companion service requests its bounded associated TXT model")
                check(MdnsPacket.followUp(questions).isNotEmpty(), "Associated model query uses the normal bounded wire encoder")
            }
            assembly.accept(peer, if (modelFirst) anchor else model, now + 1)
            check(String(assembly.associatedModels(peer, now + 1).single().getValue("model")) == "iPad14,1", "TXT-only device-info binds to current same-sender exact instance in either order")
            check(assembly.associatedModels(other, now + 1).isEmpty(), "Another endpoint cannot borrow the model")
            check(assembly.modelPeers(now + 1) == listOf(peer), "Only the associated current endpoint is emitted")
            assembly.accept(peer, parse(rr(modelOwner, 16, txt("model=iPhone14,3"), 120, true)), now + 2)
            check(assembly.associatedModels(peer, now + 2).isEmpty(), "Conflicting current TXT RRset is not arbitrarily resolved")
            check(assembly.associatedModels(peer, now + 11_000_000_000L).isEmpty(), "Expired model and anchor cannot be reused")
            assembly.close()
            check(assembly.associatedModels(peer, now).isEmpty() && assembly.modelPeers(now).isEmpty() && assembly.due(false, now, 4).isEmpty(), "Stopped scan emits no associated models or queries")
        }
        val proxy = MdnsAssembly()
        proxy.accept(other, anchor, now); proxy.accept(other, model, now)
        check(proxy.associatedModels(other, now).isEmpty(), "Proxy advertisement for a different address cannot authorize auxiliary model identity")
        val mismatch = MdnsAssembly()
        mismatch.accept(peer, anchor, now)
        mismatch.accept(peer, parse(rr(listOf("Other name", "_device-info", "_tcp", "local"), 16, txt("model=iPad14,1"), 120, true)), now)
        check(mismatch.associatedModels(peer, now).isEmpty(), "Same sender but different service instance is not enough")
        mismatch.accept(peer, model, now)
        mismatch.accept(peer, parse(rr(modelOwner, 16, txt("model=iPad14,1"), 0, true)), now + 1)
        check(mismatch.associatedModels(peer, now + 1).isEmpty(), "TXT goodbye immediately removes model evidence")
        val retry = MdnsAssembly(); retry.accept(peer, anchor, now)
        check(retry.due(false, now, 4).size == 1 && retry.due(false, now + 100, 4).isEmpty(), "Auxiliary queries respect retry spacing")
        check(retry.due(false, now + 2_000_000_000L, 4).size == 1 && retry.due(false, now + 4_000_000_000L, 4).isEmpty(), "Auxiliary query has at most two attempts per scan")
    }

    private fun associatedModelWireNames() {
        val now = 1_000_000_000L
        for (peer in listOf(ip("192.0.2.92"), ip("2001:db8::92"))) {
            for (service in listOf("_companion-link", "_airplay", "_smb", "_workstation")) {
                for (rawName in listOf(" ", "  ", " Fixture", "Fixture ", " Fixture ", "Fixture", "Fixture.room", "é".repeat(31))) {
                    val type = listOf(service, "_tcp", "local")
                    val instance = listOf(rawName) + type
                    val host = listOf("synthetic-wire-name", "local")
                    val owner = listOf(rawName, "_device-info", "_tcp", "local")
                    val assembly = MdnsAssembly()
                    val anchor = parse(rr(type, 12, name(instance), 120, false),
                        rr(instance, 33, srv(host), 120, true), rr(instance, 16, txt("fixture=1"), 120, true),
                        rr(host, if (peer is Inet4Address) 1 else 28, peer.address, 120, true))
                    check(assembly.accept(peer, anchor, now).size == 1, "Valid raw instance completes on either address family")
                    val questions = assembly.due(peer is Inet6Address, now, 4)
                    // Exercise the same encoder called by the production receive loop. A display
                    // name trimmed to empty must never turn a valid DNS label into a loop-ending exception.
                    val wire = MdnsPacket.followUp(questions)
                    check(MdnsPacket.multicastQuestions(wire, wire.size) == listOf(MdnsPacket.Question(owner, 16)),
                        "Auxiliary question round-trips the original instance label, including whitespace and UTF-8")
                    val differentName = rawName.trim().takeIf { it.isNotEmpty() && it != rawName } ?: "Different fixture"
                    assembly.accept(peer, parse(rr(listOf(differentName, "_device-info", "_tcp", "local"),
                        16, txt("model=iPhone17,3"), 120, true)), now + 1)
                    check(assembly.associatedModels(peer, now + 1).isEmpty(), "A distinct raw name cannot supply this instance's model")
                    assembly.accept(peer, parse(rr(owner, 16, txt("model=iPad13,18"), 120, true)), now + 2)
                    check(String(assembly.associatedModels(peer, now + 2).single().getValue("model")) == "iPad13,18",
                        "Only the exact raw instance supplies its model after another name was observed")
                    check(assembly.due(peer is Inet6Address, now + 2_000_000_000L, 4).isEmpty(),
                        "The exact model response satisfies its original query")
                    assembly.close()
                }
            }
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        associatedModelWireNames()
        associatedAppleModels()
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
