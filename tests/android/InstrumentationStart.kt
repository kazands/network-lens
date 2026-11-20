package io.github.kazands.networklens.test

/** Test-only startup sequencing, shared with host regressions; no Android state before rejection. */
internal object InstrumentationStart {
    private val emulatorModes = setOf("app-updates", "audio-zones", "scan-efficiency", "scan-ownership",
        "performance", "local-metadata", "recog", "device-identities", "google-cast", "fire-tv",
        "envisalink", "smartthings", "kidde", "network-names")

    fun prepare(mode: String, requireEmulator: () -> Unit, initializeState: () -> Unit) {
        if (mode in emulatorModes) requireEmulator()
        if (!mode.startsWith("session-") && mode != "legacy-seed") initializeState()
    }
}
