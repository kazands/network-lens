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
        check((r.due(start, 8))!!.isEmpty(), "No unsolicited retries")
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
        var first: List<String> = r.due(start, 8)
        check(
            first.equals(Arrays.asList("_hap._tcp.", "_http._tcp.")),
            "Fresh PTRs are deterministic and shared by instances",
        )
        check(
            ((MdnsPacket.query(Arrays.asList((first.get(0) + "local"), (first.get(1) + "local"))))!!
                .size < 512),
            "Retry uses bounded existing query encoder",
        )
        check((r.due(((start + gap) - 1), 8))!!.isEmpty(), "Retry spacing is enforced")
        check(
            (((r.due((start + gap), 1))!!.size == 1) &&
                ((r.due((start + gap), 1))!!.size == 1)),
            "Packet capacity does not starve later types",
        )
        check(
            (r.due((start + (gap * 20)), 8))!!.isEmpty(),
            "At most two follow-up questions per type per family",
        )
        var bounded: MdnsRetries = MdnsRetries()
        run {
            var i: Int = 0
            while ((i < 512)) {
                check(bounded.request((("_fixture" + i) + "._tcp.")), "512-type safety bound")
                i++
            }
        }
        check(!(bounded.request("_extra._tcp.")), "New types cannot exceed the cap")
        check((bounded.due(0, 0))!!.isEmpty(), "Zero packet capacity consumes no attempts")
        var count: Int = 0
        run {
            var i: Int = 0
            while ((i < 64)) {
                count += (bounded.due(0, 8))!!.size
                i++
            }
        }
        check(
            ((count == 512) && (bounded.due(0, 8))!!.isEmpty()),
            "Each packet has at most eight questions",
        )
        var invalid: Boolean = false
        try {
            bounded.due(0, 9)
        } catch (e: IllegalArgumentException) {
            invalid = true
        }
        check(invalid, "Oversized packet request rejected")
        // A settled scan can admit recovery much later than initial discovery.
        // Every admitted query and its response window must count as pending.
        val recovery = MdnsRetries()
        val queuedAt = TimeUnit.SECONDS.toNanos(20)
        val types = (0 until 32).map { "_recovery$it._tcp." }
        check(!recovery.pending(queuedAt), "No recovery keeps an idle scan open")
        check(recovery.recover(types) == 32, "Full bounded late recovery batch admitted")
        check(recovery.pending(queuedAt), "IPv4 recovery begins with pending work")
        var lastSend = queuedAt
        val sent = linkedSetOf<String>()
        repeat(16) { batch ->
            lastSend = queuedAt + TimeUnit.MILLISECONDS.toNanos(batch * 400L)
            check(recovery.pending(lastSend), "Queued recovery prevents quiet completion")
            sent.addAll(recovery.due(lastSend, 2))
        }
        check(sent == types.toSet(), "All recovery types get one opportunity")
        check(recovery.pending(lastSend + gap - 1), "Final query retains its reply window")
        check(!recovery.pending(lastSend + gap), "Drained family becomes idle after replies can arrive")
        check(recovery.recover(types) == 0 && !recovery.pending(lastSend + gap), "Duplicate recovery cannot extend a completed window")
        val negativeClock = MdnsRetries()
        negativeClock.recover(listOf("_http._tcp."))
        check(negativeClock.due(start, 2).size == 1, "Recovery works with a negative monotonic-clock origin")
        check(negativeClock.pending(start + gap - 1) && !negativeClock.pending(start + gap), "Reply window uses elapsed time, not a zero timestamp sentinel")
        System.out.println((("PASS · " + checks) + " mDNS retry checks"))
    }
}
