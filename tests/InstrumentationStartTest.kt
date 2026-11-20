import io.github.kazands.networklens.test.InstrumentationStart
import java.io.IOException

/** Simulates startup without ADB, Android state or physical-device access. */
object InstrumentationStartTest {
    private var checks = 0
    private fun verify(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
    @JvmStatic fun main(args: Array<String>) {
        for (mode in listOf("network-names", "kidde", "app-updates", "device-identities", "fibe-tv", "compose-ui", "compose-typography", "compose-reports", "compose-updates")) {
            val events = arrayListOf<String>()
            try {
                InstrumentationStart.prepare(mode, {
                    events.add("guard"); throw IllegalStateException("Synthetic non-emulator")
                }, { events.add("state") })
                throw AssertionError("Non-emulator $mode startup must be rejected")
            } catch (_: IllegalStateException) {}
            verify(events == listOf("guard"), "Rejected $mode must not touch appearance or trigger Store migration")
            events.clear()
            InstrumentationStart.prepare(mode, { events.add("guard") }, { events.add("state") })
            verify(events == listOf("guard", "state"), "Accepted $mode checks identity before initialization")
            events.clear()
            try {
                InstrumentationStart.prepare(mode, {
                    events.add("guard"); throw IOException("Synthetic property-read failure")
                }, { events.add("state") })
                throw AssertionError("Failed $mode identity read must fail closed")
            } catch (_: IOException) {}
            verify(events == listOf("guard"), "Failed $mode identity read cannot initialize target state")
        }
        val events = arrayListOf<String>()
        for (mode in listOf("session-only", "session-cold", "legacy-seed")) {
            InstrumentationStart.prepare(mode, { events.add("guard") }, { events.add("state") })
            verify(events == listOf("guard"), "Existing $mode startup checks its emulator and keeps appearance intact");events.clear()
        }
        InstrumentationStart.prepare("recognition-report", { events.add("guard") }, { events.add("state") })
        verify(events == listOf("guard", "state"), "Report mode checks its emulator before state initialization")
        println("PASS · $checks instrumentation startup safety assertions")
    }
}
