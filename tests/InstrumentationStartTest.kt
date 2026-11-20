import io.github.kazands.networklens.test.InstrumentationStart
import java.io.IOException

/** Simulates startup without ADB, Android state or physical-device access. */
object InstrumentationStartTest {
    private var checks = 0
    private fun verify(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
    @JvmStatic fun main(args: Array<String>) {
        for (mode in listOf("network-names", "kidde", "app-updates", "device-identities")) {
            val events = arrayListOf<String>()
            try {
                InstrumentationStart.prepare(mode, {
                    events.add("guard"); throw IllegalStateException("Synthetic non-emulator")
                }, { events.add("state") })
                throw AssertionError("Non-emulator startup must be rejected")
            } catch (_: IllegalStateException) {}
            verify(events == listOf("guard"), "Rejected $mode must not touch appearance or trigger Store migration")
            events.clear()
            InstrumentationStart.prepare(mode, { events.add("guard") }, { events.add("state") })
            verify(events == listOf("guard", "state"), "Accepted $mode checks identity before initialization")
        }
        val events = arrayListOf<String>()
        try {
            InstrumentationStart.prepare("network-names", { throw IOException("Synthetic property-read failure") }, { events.add("state") })
            throw AssertionError("Failed identity read must fail closed")
        } catch (_: IOException) {}
        verify(events.isEmpty(), "Property-read failure cannot initialize target state")
        for (mode in listOf("session-only", "session-cold", "legacy-seed")) {
            InstrumentationStart.prepare(mode, { events.add("guard") }, { events.add("state") })
            verify(events.isEmpty(), "Existing $mode startup keeps its state intact")
        }
        InstrumentationStart.prepare("recognition-report", { events.add("guard") }, { events.add("state") })
        verify(events == listOf("state"), "Unchanged modes retain their existing startup behavior")
        println("PASS · $checks instrumentation startup safety assertions")
    }
}
