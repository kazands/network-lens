package dev.netlens

import java.nio.charset.StandardCharsets

/** Public HomeKit discovery facts. A pairing ID is never a hardware MAC. */
class HomeKitInfo private constructor() {
    class Report internal constructor(
        @JvmField val kind: String,
        @JvmField val setup: String
    )

    companion object {
        @JvmStatic fun applies(type: String): Boolean =
            type == "_hap._tcp." || type == "_hap._udp."

        @JvmStatic fun category(value: Int): String = when (value) {
            1 -> "HomeKit accessory"
            2 -> "HomeKit bridge"
            3 -> "HomeKit fan"
            4 -> "HomeKit garage door"
            5 -> "HomeKit light"
            6 -> "HomeKit lock"
            7 -> "HomeKit outlet"
            8, 15 -> "HomeKit switch"
            9 -> "HomeKit thermostat"
            10 -> "HomeKit sensor"
            11 -> "HomeKit security system"
            12 -> "HomeKit door"
            13 -> "HomeKit window"
            14 -> "HomeKit window covering"
            16 -> "HomeKit range extender"
            17 -> "HomeKit camera"
            18 -> "HomeKit video doorbell"
            19 -> "HomeKit air purifier"
            20 -> "HomeKit heater"
            21 -> "HomeKit air conditioner"
            22 -> "HomeKit humidifier"
            23 -> "HomeKit dehumidifier"
            24 -> "HomeKit TV service"
            25, 26 -> "HomeKit speaker"
            27, 33 -> "HomeKit router service"
            28 -> "HomeKit sprinkler"
            29 -> "HomeKit faucet"
            30 -> "HomeKit shower"
            31, 35, 36 -> "HomeKit TV service"
            32 -> "HomeKit remote"
            34 -> "HomeKit audio receiver"
            else -> "HomeKit accessory"
        }

        @JvmStatic fun parse(type: String, attributes: Map<String, ByteArray>): Report {
            if (!applies(type)) return Report("", "")
            val categoryId = byteNumber(attributes, "ci")
            val flags = byteNumber(attributes, "sf")
            return Report(category(categoryId ?: -1), if (flags == null) "" else
                if (flags and 1 != 0) "Not paired" else "Paired")
        }

        // Accept an unsigned decimal byte only. Missing, conflicting, oversized
        // or malformed reports stay unknown; do not turn them into a default 0.
        private fun byteNumber(attributes: Map<String, ByteArray>, key: String): Int? {
            if (attributes.size > 128) return null
            var text: String? = null
            for ((name, bytes) in attributes) if (name.equals(key, true)) {
                if (bytes.isEmpty() || bytes.size > 3 || bytes.any { it < 48 || it > 57 }) return null
                val value = String(bytes, StandardCharsets.US_ASCII)
                if (text != null && text != value) return null
                text = value
            }
            val number = text?.toIntOrNull() ?: return null
            return number.takeIf { it in 0..255 }
        }
    }
}
