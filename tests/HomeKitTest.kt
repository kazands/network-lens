@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import dev.netlens.*
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*

/** HomeKit facts and current-packet association at the untrusted DNS boundary. */
object HomeKitTest {
    private var checks: Int = 0

    private fun check(ok: Boolean, why: String): Unit {
        checks++
        if (!(ok)) {
            throw AssertionError(why)
        }
    }

    private fun ascii(s: String): ByteArray {
        return s.toByteArray(StandardCharsets.US_ASCII)
    }

    private fun attrs(vararg values: String): MutableMap<String, ByteArray> {
        var out: MutableMap<String, ByteArray> = LinkedHashMap()
        run {
            var n: Int = 0
            while ((n < values.size)) {
                out.put(values[n], ascii(values[(n + 1)]))
                n += 2
            }
        }
        return out
    }

    private fun name(out: DataOutputStream, value: String): Unit {
        for (label in java.util.regex.Pattern.compile("\\.").split(value, 0)) {
            var b: ByteArray = label.toByteArray(StandardCharsets.UTF_8)
            out.writeByte(b.size)
            out.write(b)
        }
        out.writeByte(0)
    }

    private fun dnsName(value: String): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        name(DataOutputStream(b), value)
        return b.toByteArray()
    }

    private fun rr(owner: String, kind: Int, value: ByteArray, ttl: Int): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var out: DataOutputStream = DataOutputStream(b)
        name(out, owner)
        out.writeShort(kind)
        out.writeShort(32769)
        out.writeInt(ttl)
        out.writeShort(value.size)
        out.write(value)
        return b.toByteArray()
    }

    private fun srv(host: String): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var out: DataOutputStream = DataOutputStream(b)
        out.writeShort(0)
        out.writeShort(0)
        out.writeShort(51827)
        name(out, host)
        return b.toByteArray()
    }

    private fun txt(vararg fields: String): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        for (field in fields) {
            var value: ByteArray = ascii(field)
            b.write(value.size)
            b.write(value)
        }
        return b.toByteArray()
    }

    private fun packet(records: List<ByteArray>): ByteArray {
        var b: ByteArrayOutputStream = ByteArrayOutputStream()
        var out: DataOutputStream = DataOutputStream(b)
        out.writeShort(0)
        out.writeShort(33792)
        out.writeShort(0)
        out.writeShort(records.size)
        out.writeInt(0)
        for (record in records) {
            out.write(record)
        }
        return b.toByteArray()
    }

    private fun records(ip: String, host: String): List<ByteArray> {
        var type: String = "_hap._tcp.local"
        var instance: String = ("Hall light." + type)
        var address: InetAddress = InetAddress.getByName(ip)
        return ArrayList(
            Arrays.asList(
                rr(type, 12, dnsName(instance), 120),
                rr(instance, 33, srv("lamp.local"), 120),
                rr(instance, 16, txt("md=Lamp model", "ci=5", "sf=5", "id=AA:BB:CC:DD:EE:FF"), 120),
                rr(host, (if ((address is Inet4Address)) 1 else 28), address.address, 120),
            )
        )
    }

    private fun parse(records: List<ByteArray>): MdnsPacket.Result {
        var p: ByteArray = packet(records)
        return MdnsPacket.parse(p, p.size)
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var light: HomeKitInfo.Report =
            HomeKitInfo.parse("_hap._tcp.", attrs("ci", "5", "sf", "5", "id", "AA:BB:CC:DD:EE:FF"))
        check(
            (light.kind.equals("HomeKit light") && light.setup.equals("Not paired")),
            "Explicit category and unpaired bit are decoded",
        )
        check(
            (HomeKitInfo.parse("_hap._udp.", attrs("CI", "6", "SF", "4")))!!
                .kind
                .equals("HomeKit lock"),
            "UDP HomeKit and ASCII key case work",
        )
        run {
            var flags: Int = 0
            while ((flags < 256)) {
                check(
                    (HomeKitInfo.parse(
                            "_hap._tcp.",
                            attrs("sf", java.lang.String.valueOf(flags)),
                        ))!!
                        .setup
                        .equals(
                            (if (((((flags).toInt() and (1).toInt())) == 1)) "Not paired"
                            else "Paired")
                        ),
                    "sf is a bitmask, not a two-value enum",
                )
                flags++
            }
        }
        for (invalid in
            arrayOf(
                "",
                "-1",
                "+1",
                "1 ",
                " 1",
                "1\n",
                "0x1",
                "256",
                "999",
                "1000",
                "1.0",
                "\u0000",
                "١",
            )) {
            var unknown: HomeKitInfo.Report =
                HomeKitInfo.parse("_hap._tcp.", attrs("ci", invalid, "sf", invalid))
            check(
                (unknown.kind.equals("HomeKit accessory") && unknown.setup.isEmpty()),
                "Malformed bytes do not invent category or paired state",
            )
        }
        check(
            (HomeKitInfo.parse("_hap._tcp.", Collections.emptyMap()))!!.setup.isEmpty(),
            "Absent status remains absent",
        )
        check(
            (HomeKitInfo.parse("_hap._tcp.", attrs("ci", "255")))!!
                .kind
                .equals("HomeKit accessory"),
            "Unknown category remains generic",
        )
        check(
            (HomeKitInfo.parse("_http._tcp.", attrs("ci", "5", "sf", "1")))!!.kind.isEmpty(),
            "HomeKit keys are not applied to unrelated services",
        )
        check(
            (HomeKitInfo.parse("_hap._tcp.", attrs("ci", "5", "CI", "6")))!!
                .kind
                .equals("HomeKit accessory"),
            "Conflicting case variants remain unknown",
        )
        check(
            (HomeKitInfo.parse("_hap._tcp.", attrs("ci", "5", "CI", "5")))!!
                .kind
                .equals("HomeKit light"),
            "Identical duplicate reports agree",
        )
        var large: MutableMap<String, ByteArray> = attrs("sf", "0")
        run {
            var n: Int = 0
            while ((n < 128)) {
                large.put(("field" + n), ascii("x"))
                n++
            }
        }
        check(
            (HomeKitInfo.parse("_hap._tcp.", large))!!.setup.isEmpty(),
            "Bound untrusted attribute maps",
        )
        var oversize: MutableMap<String, ByteArray> = attrs("sf", "0")
        oversize.put("sf", ByteArray(10000))
        check(
            (HomeKitInfo.parse("_hap._tcp.", oversize))!!.setup.isEmpty(),
            "Oversized flags are not parsed",
        )
        check(
            ((HomeKitInfo.category(17))!!.equals("HomeKit camera") &&
                (HomeKitInfo.category(18))!!.equals("HomeKit video doorbell")),
            "Camera and doorbell category numbers are distinct",
        )
        check(
            (HashSet(LocalServices.INITIAL).size == LocalServices.INITIAL.size),
            "No duplicate explicit browse types",
        )
        for (type in
            arrayOf(
                "_hap._tcp.",
                "_hap._udp.",
                "_matter._tcp.",
                "_matterc._udp.",
                "_meshcop._udp.",
            )) {
            check(
                LocalServices.INITIAL.contains(type),
                "Smart-home types have explicit bounded queries",
            )
        }
        try {
            (LocalServices.INITIAL as MutableList<String>).add("_unbounded._tcp.")
            throw AssertionError("Mutable service list")
        } catch (expected: UnsupportedOperationException) {
            check(true, "Initial list cannot be expanded by a caller")
        }
        for (type in
            arrayOf(
                "_hap._udp.",
                "_matterc._udp.",
                "_meshcop._udp.",
                "_matter._tcp.",
                "_http._udp.",
            )) {
            check(
                !(LocalServices.tcpConfirmation(type)),
                "Do not confirm UDP services or Matter's _tcp convention with a TCP connection",
            )
        }
        check(
            (LocalServices.tcpConfirmation("_hap._tcp.") &&
                LocalServices.tcpConfirmation("_http._tcp.")),
            "Real TCP service confirmation is retained",
        )
        var peer: InetAddress = InetAddress.getByName("192.168.1.20")
        var base: List<ByteArray> = records(peer.getHostAddress(), "lamp.local")
        var current: MdnsPacket.Result = parse(base)
        check(
            ((current.directServices(peer))!!.size == 1),
            "One complete current packet associates its SRV target with its sender",
        )
        var service: MdnsPacket.Service = (current.directServices(peer))!!.get(0)
        check(
            (((service.name.equals("Hall light") && (service.port == 51827)) &&
                service.txtReceived) && service.ptrReceived),
            "Complete instance, port and TXT/PTR evidence are retained",
        )
        check(
            (HomeKitInfo.parse(service.type, service.attributes))!!.kind.equals("HomeKit light"),
            "Wire category reaches the mapper",
        )
        check(
            ((service.copy())!!.ptrReceived && (service.copy())!!.txtReceived),
            "A service copy preserves packet evidence flags",
        )
        check(
            (current.directServices(InetAddress.getByName("192.168.1.21")))!!.isEmpty(),
            "A different responder cannot borrow this advertised address",
        )
        check(
            (current.directServices(InetAddress.getByName("192.168.1.1")))!!.isEmpty(),
            "A proxy reply is not direct accessory evidence",
        )
        var proxy: MutableList<ByteArray> = ArrayList(base)
        proxy.add(rr("router.local", 1, (InetAddress.getByName("192.168.1.1"))!!.address, 120))
        check(
            ((parse(proxy))!!.directServices(InetAddress.getByName("192.168.1.1")))!!.isEmpty(),
            "A proxy's unrelated A record cannot satisfy the SRV target",
        )
        check(
            ((parse(records(peer.getHostAddress(), "other.local")))!!.directServices(peer))!!
                .isEmpty(),
            "Address owner must match the exact service host",
        )
        run {
            var missing: Int = 0
            while ((missing < 4)) {
                var incomplete: MutableList<ByteArray> = ArrayList(base)
                incomplete.removeAt(missing)
                check(
                    ((parse(incomplete))!!.directServices(peer))!!.isEmpty(),
                    "Missing PTR, SRV, TXT or A cannot complete the fallback",
                )
                missing++
            }
        }
        var type: String = "_hap._tcp.local"
        var instance: String = ("Hall light." + type)
        run {
            var expired: Int = 0
            while ((expired < 4)) {
                var goodbye: MutableList<ByteArray> = ArrayList(base)
                var replacement: ByteArray =
                    (if ((expired == 0)) rr(type, 12, dnsName(instance), 0)
                    else
                        (if ((expired == 1)) rr(instance, 33, srv("lamp.local"), 0)
                        else
                            (if ((expired == 2)) rr(instance, 16, txt("ci=5"), 0)
                            else rr("lamp.local", 1, peer.address, 0))))
                goodbye.set(expired, replacement)
                check(
                    ((parse(goodbye))!!.directServices(peer))!!.isEmpty(),
                    "TTL-zero records cannot complete current evidence",
                )
                expired++
            }
        }
        var wrong: MutableList<ByteArray> = ArrayList(base)
        wrong.set(2, rr(("Other." + type), 16, txt("ci=5"), 120))
        check(
            ((parse(wrong))!!.directServices(peer))!!.isEmpty(),
            "TXT for a different instance cannot be combined with the SRV",
        )
        var standalone: MdnsPacket.Result = MdnsPacket.Result()
        standalone.services.put("old", service.copy())
        check(
            (standalone.directServices(peer))!!.isEmpty(),
            "A cached service object alone is not a current packet",
        )
        var v6: InetAddress = InetAddress.getByName("fd00::20")
        check(
            (((parse(records(v6.getHostAddress(), "lamp.local")))!!.directServices(v6))!!.size ==
                1),
            "Same-sender AAAA association works independently of IPv6 routing policy",
        )
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
                                            var d: Int = 0
                                            while ((d < 4)) {
                                                if ((((d != a) && (d != b)) && (d != c))) {
                                                    check(
                                                        (((parse(
                                                                    Arrays.asList(
                                                                        base.get(a),
                                                                        base.get(b),
                                                                        base.get(c),
                                                                        base.get(d),
                                                                    )
                                                                ))!!
                                                                .directServices(peer))!!
                                                            .size == 1),
                                                        "All 24 record orders preserve the same direct association",
                                                    )
                                                }
                                                d++
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
        var full: ByteArray = packet(base)
        run {
            var n: Int = 0
            while ((n < full.size)) {
                check(
                    ((MdnsPacket.parse(full, n))!!.directServices(peer))!!.isEmpty(),
                    "Truncated packet never returns partial identity",
                )
                n++
            }
        }
        check(
            (MdnsPacket.parse(ByteArray(8193), 8193))!!.addresses.isEmpty(),
            "Packet-size limit also bounds retained addresses",
        )
        var random: Random = Random(301)
        run {
            var n: Int = 0
            while ((n < 10000)) {
                var fuzz: ByteArray = ByteArray(random.nextInt(1024))
                random.nextBytes(fuzz)
                (MdnsPacket.parse(fuzz, fuzz.size))!!.directServices(peer)
                HomeKitInfo.parse("_hap._tcp.", Collections.singletonMap("sf", fuzz))
                n++
            }
        }
        check(true, "Malformed DNS and TXT data cannot crash or hang the mapper")
        System.out.println(
            (("PASS · " + checks) + " HomeKit assertions plus 10,000 malformed DNS/TXT inputs")
        )
    }
}
