package io.github.kazands.networklens.test

/** Test-only startup sequencing, shared with host regressions; no Android state before rejection. */
internal object InstrumentationStart {
    fun prepare(mode: String, requireEmulator: () -> Unit, initializeState: () -> Unit) {
        requireEmulator()
        if (!mode.startsWith("session-") && mode != "legacy-seed") initializeState()
    }
}
