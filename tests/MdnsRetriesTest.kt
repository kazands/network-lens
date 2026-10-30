@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.*
import java.util.*
import java.util.concurrent.TimeUnit

object MdnsRetriesTest {
    private var checks: Int = 0

    private fun check(value: Boolean, message: String): Unit {
        checks++
        if (!(value)) {
            throw AssertionError(message)
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var r: MdnsRetries = MdnsRetries()
        var start: Long = -(5000000000L)
        var gap: Long = TimeUnit.MILLISECONDS.toNanos(1200)
        check((r.due(false, start, 8))!!.isEmpty(), "No unsolicited retries")
        check(
            (((!(r.request(null)) && !(r.request("_http._tcp.evil."))) &&
                !(r.request("_http._tcp.local."))) && !(r.request("HTTP"))),
            "Only canonical local service types accepted",
        )
        check((r.request("_http._tcp.") && r.request("_hap._tcp.")), "Valid admitted types queued")
        run {
            var i: Int = 0
            while ((i < 100)) {
                check(r.request("_http._tcp."), "Duplicate instances coalesce")
                i++
            }
        }
        var first: List<String> = r.due(false, start, 8)
        check(
            first.equals(Arrays.asList("_hap._tcp.", "_http._tcp.")),
            "Fresh PTRs are deterministic and shared by instances",
        )
        check(
            ((MdnsPacket.query(Arrays.asList((first.get(0) + "local"), (first.get(1) + "local"))))!!
                .size < 512),
            "Retry uses bounded existing query encoder",
        )
        check((r.due(false, ((start + gap) - 1), 8))!!.isEmpty(), "Retry spacing is enforced")
        check((r.due(true, start, 8))!!.equals(first), "IPv6 has its own finite sends")
        check(
            (((r.due(false, (start + gap), 1))!!.size == 1) &&
                ((r.due(false, (start + gap), 1))!!.size == 1)),
            "Packet capacity does not starve later types",
        )
        check(
            (r.due(false, (start + (gap * 20)), 8))!!.isEmpty(),
            "At most two follow-up questions per type per family",
        )
        check(
            ((r.due(true, (start + gap), 8))!!.equals(first) &&
                (r.due(true, (start + (gap * 2)), 8))!!.isEmpty()),
            "IPv6 also stops after two questions",
        )
        var bounded: MdnsRetries = MdnsRetries()
        run {
            var i: Int = 0
            while ((i < 48)) {
                check(bounded.request((("_fixture" + i) + "._tcp.")), "48-type admission bound")
                i++
            }
        }
        check(!(bounded.request("_extra._tcp.")), "New types cannot exceed the cap")
        check((bounded.due(false, 0, 0))!!.isEmpty(), "Zero packet capacity consumes no attempts")
        var count: Int = 0
        run {
            var i: Int = 0
            while ((i < 6)) {
                count += (bounded.due(false, 0, 8))!!.size
                i++
            }
        }
        check(
            ((count == 48) && (bounded.due(false, 0, 8))!!.isEmpty()),
            "Each packet has at most eight questions",
        )
        var invalid: Boolean = false
        try {
            bounded.due(false, 0, 9)
        } catch (e: IllegalArgumentException) {
            invalid = true
        }
        check(invalid, "Oversized packet request rejected")
        System.out.println((("PASS · " + checks) + " mDNS retry checks"))
    }
}
