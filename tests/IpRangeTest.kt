@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.IpRange

object IpRangeTest {
    var checks: Int = 0

    fun eq(a: Any, b: Any): Unit {
        checks++
        if (!(a.equals(b))) {
            throw AssertionError(((java.lang.String.valueOf(a) + " != ") + b))
        }
    }

    fun rejects(r: Runnable): Unit {
        checks++
        try {
            r.run()
            throw AssertionError("Expected rejection")
        } catch (expected: IllegalArgumentException) {}
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var r: IpRange = IpRange("192.168.1.50", 24)
        eq(r.size(), 254L)
        eq(IpRange.address(r.first), "192.168.1.1")
        eq(IpRange.address(r.last), "192.168.1.254")
        eq(r.contains("192.168.2.1"), false)
        eq((r.hosts(1024))!!.size, 254)
        eq(IpRange("10.2.3.4", 16).size(), 65534L)
        rejects(callback1@{ IpRange("10.2.3.4", 16).hosts(1024) })
        eq((IpRange("192.168.1.11", 31).hosts(2))!!.toString(), "[192.168.1.10, 192.168.1.11]")
        eq((IpRange("192.168.1.11", 32).hosts(1))!!.toString(), "[192.168.1.11]")
        eq(IpRange("255.255.255.255", 0).size(), 4294967294L)
        eq(IpRange.number("255.255.255.255"), 4294967295L)
        eq(IpRange.address(4294967295L), "255.255.255.255")
        for (s in arrayOf("256.0.0.1", "1.2.3", "1..2.3", "1.2.3.4.5", "-1.2.3.4", "1.2.3.x")) {
            rejects(callback2@{ IpRange.number(s) })
        }
        rejects(callback3@{ IpRange("1.2.3.4", 33) })
        rejects(callback4@{ IpRange("1.2.3.4", -(1)) })
        eq(IpRange.validHost("router.local"), "router.local")
        eq(IpRange.validHost("::1"), "::1")
        for (s in arrayOf("-c 50", "1.2.3.4;id", "\$(id)", "https://example.com", "", "-host")) {
            rejects(callback5@{ IpRange.validHost(s) })
        }
        run {
            var prefix: Int = 0
            while ((prefix <= 32)) {
                var v: IpRange = IpRange("10.0.0.1", prefix)
                eq(
                    ((v.first <= IpRange.number("10.0.0.1")) &&
                        (v.last >= IpRange.number("10.0.0.1"))),
                    true,
                )
                prefix++
            }
        }
        System.out.println(
            (("PASS · " + checks) + " CIDR, boundary, range-limit, and input checks")
        )
    }
}
