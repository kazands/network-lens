package io.github.kazands.networklens.test

import io.github.kazands.networklens.Device
import io.github.kazands.networklens.DeviceIcon
import io.github.kazands.networklens.Recog

/** Exercises actual Device/IdentityHints state, including bounded-observation eviction. */
internal object RecognitionConflicts {
    fun run(check: (Boolean, String) -> Unit) {
        val code = "MacBookPro18,3"
        val description = Recog.lookup(code)!!.description
        fun recognize(d: Device) {
            d.recognizeModel(mapOf("model" to code.toByteArray()))
            d.hint("Model", code, "mDNS current _device-info._tcp. TXT", 20)
        }
        for (field in listOf("Manufacturer", "Model")) for (before in listOf(false, true)) {
            val d = Device().apply { ip = "192.0.2.70" }
            if (!before) recognize(d)
            val initial = d.copy()
            val bad = if (field == "Manufacturer") "Fixture Devices" else "Fixture other model"
            val good = if (field == "Manufacturer") "Apple" else code
            d.hint(field, bad, "mDNS current _zfixture._tcp. TXT", 80)
            check(d.locallyRecognized() == null, "$field conflict suppresses interpretation before observation eviction")
            repeat(12) { n -> d.hint(field, good, "mDNS current _fixture$n._tcp. TXT", 80) }
            if (before) recognize(d)
            val retained = d.observations().filter { it.field == field }
            check(retained.size == 8 && retained.none { it.value == bad }, "$field fixture reaches actual eight-observation eviction")
            check(d.locallyRecognized() == null && d.groupRecognition() == null,
                "$field conflict survives eviction when reported ${if (before) "before" else "after"} the model code")
            check(DeviceIcon.forGroup(d).label == "Device type unknown", "$field eviction cannot restore an inferred icon")
            val snapshot = d.copy()
            repeat(12) { n -> d.hint(field, good, "Later fixture $n", 100) }
            check(snapshot.locallyRecognized() == null && d.locallyRecognized() == null,
                "$field conflict survives progress copies and later higher-priority reports")
            check(initial.locallyRecognized()?.code == if (before) null else code, "Earlier snapshots keep independent $field conflict state")
            val fresh = Device().apply { ip = d.ip }; recognize(fresh)
            check(fresh.locallyRecognized()?.code == code, "A new scan of the same address inherits no $field conflict")
        }
        val equivalent = Device()
        equivalent.hint("Model", description, "UPnP description", 80)
        recognize(equivalent)
        repeat(20) { n -> equivalent.hint("Model", if (n % 2 == 0) code else description, "Equivalent fixture $n", 80) }
        check(equivalent.locallyRecognized()?.code == code, "The input code and its recognized description may corroborate each other")
        val ignored = Device()
        ignored.hint("Manufacturer", "Fixture Devices", "", 80)
        ignored.hint("Manufacturer", "Unknown", "Generic fixture", 80)
        ignored.hint("Model", "Unknown", "Generic fixture", 80)
        recognize(ignored)
        check(ignored.locallyRecognized()?.code == code, "Rejected-source and generic reports cannot create recognition conflicts")
        val third = Device()
        third.hint("Model", description, "First model fixture", 80)
        third.hint("Model", code, "Second model fixture", 80)
        third.hint("Model", "Fixture third model", "Third model fixture", 80)
        repeat(12) { n -> third.hint("Model", code, "Higher model fixture $n", 100) }
        recognize(third)
        check(third.locallyRecognized() == null, "Three distinct specific model reports remain conflicting before recognition arrives")
    }
}
