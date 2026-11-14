import io.github.kazands.networklens.*

object DeviceCatalogTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
    @JvmStatic fun main(args: Array<String>) {
        val cases = listOf("DS-7608NI-I2/8P" to "Hikvision DS-7608NI-I2/8P",
            "SHIELD Android TV" to "NVIDIA SHIELD TV", "PS5" to "Sony PlayStation 5")
        for ((code, title) in cases) {
            val p = DeviceCatalog.lookup(code)!!
            check(p.title == title, "Exact reported $code has its audited product title")
            val evidence = DeviceCatalog.Evidence()
            evidence.offer("Name", code, "Fixture name")
            check(evidence.match() == null, "A name cannot seed a $code product")
            evidence.offer("Manufacturer", p.brand, "Fixture maker")
            check(evidence.match() == null, "A brand alone cannot identify $code")
            evidence.offer("Model", code, "Fixture model")
            check(evidence.match()?.product == p && evidence.match()!!.source.contains("Fixture model"), "Reported model retains product and source")
            for (alias in p.models) evidence.offer("Model", alias, "Corroborating fixture")
            check(evidence.match()?.product == p, "All reviewed aliases can corroborate $code without growing evidence")
            val copy = evidence.copy()
            evidence.offer("Model", "Fixture incompatible model", "Fixture conflict")
            repeat(40) { evidence.offer("Model", code, "Later fixture $it") }
            check(evidence.match() == null && copy.match()?.product == p, "Model conflict remains suppressed; earlier copy stays independent")
            val maker = copy.copy()
            maker.offer("Manufacturer", "Fixture unrelated brand", "Fixture manufacturer conflict")
            maker.offer("Manufacturer", p.brand, "Later fixture brand")
            check(maker.match() == null && copy.match()?.product == p, "Manufacturer conflicts are scan-sticky and copy-independent")
            for (field in listOf("Manufacturer", "Model")) {
                val before = DeviceCatalog.Evidence()
                before.offer(field, "Fixture incompatible value", "Earlier fixture")
                before.offer("Model", code, "Fixture model")
                check(before.match() == null, "$field conflict before recognition stays effective")
            }
            check(!DeviceCatalog.Evidence().apply { offer("Model", "Fixture unmatched", "Fixture source") }.compatible(p), "Unmatched model cannot borrow a grouped product")
            check(DeviceCatalog.Evidence().compatible(p), "An endpoint without identity may corroborate a group without acquiring it")
            val display = DevicePresentation.labels("Fixture room", "", code, "", p, false, false)
            check(display.title == title && display.subtitle == p.category.label + " · Fixture room", "Custom network name stays below $code product")
            check(DevicePresentation.labels(code, "", code, "", p, false, false).subtitle == p.category.label, "A redundant $code name is not repeated")
            for (bad in listOf("${code}extra", "prefix$code", "$code Pro", "x".repeat(5000)))
                check(DeviceCatalog.lookup(bad) == null, "Unreviewed near-match cannot invent a $code model")
        }
        check(DeviceCatalog.lookup("SHIELD") == null, "A generic SHIELD string is not a hardware model mapping")
        val brandAliases = DeviceCatalog.Evidence().apply {
            offer("Manufacturer", "NVIDIA Corporation", "Fixture maker")
            offer("Manufacturer", "NVIDIA", "Other fixture maker")
            offer("Model", "SHIELD Android TV", "Fixture model")
        }
        check(brandAliases.match()?.product?.brand == "NVIDIA", "Reviewed maker aliases corroborate the same brand")
        val rejected = DeviceCatalog.Evidence().apply {
            offer("Model", "PS5", ""); offer("Model", "Unknown", "Fixture model")
        }
        check(rejected.match() == null, "Rejected and generic observations cannot seed a catalogue match")
        val manyAliases = DeviceCatalog.Product("fixture:aliases", "Fixture", "Fixture model", DeviceCatalog.Category.UNSPECIFIED,
            (1..9).mapTo(HashSet()) { "fixture alias $it" })
        val bounded = DeviceCatalog.Evidence().apply {
            repeat(8) { offer("Model", "Fixture alias ${it + 1}", "Current fixture model") }
        }
        check(bounded.compatible(manyAliases), "Retained unresolved models can all corroborate the same product")
        val withinBound = bounded.copy()
        bounded.offer("Model", "Fixture alias 9", "Current fixture model")
        repeat(20) { bounded.offer("Model", "Fixture alias 1", "Later corroborating fixture") }
        check(!bounded.compatible(manyAliases) && !bounded.copy().compatible(manyAliases) && withinBound.compatible(manyAliases),
            "Evidence overflow fails closed for the scan without changing earlier copies")
        val consoleSource = "PlayStation discovery host-type"
        for (first in listOf("PS5", "PlayStation 5", "Sony PlayStation 5")) for (consoleFirst in listOf(false, true)) {
            val console = DeviceCatalog.Evidence()
            val reports = listOf(Triple("Model", first, "Current ordinary model"), Triple("Model", "PS5", consoleSource))
            val ordered = if (consoleFirst) reports.reversed() else reports
            ordered.first().let { (field, value, source) -> console.offer(field, value, source) }
            val earlier = console.copy()
            ordered.last().let { (field, value, source) -> console.offer(field, value, source) }
            check(console.match()?.product?.title == "Sony PlayStation 5", "Console corroboration works with $first, console first=$consoleFirst")
            check(console.match()!!.source.contains(consoleSource) && console.copy().match() == console.match(), "Console provenance and copied evidence retain the trusted response")
            check((earlier.match() != null) == consoleFirst, "Later console corroboration cannot alter earlier snapshots")
            for (field in listOf("Model", "Manufacturer")) for (before in listOf(false, true)) {
                val conflict = if (before) DeviceCatalog.Evidence() else console.copy()
                conflict.offer(field, "Fixture incompatible value", "Current conflicting response")
                ordered.forEach { (f, value, source) -> conflict.offer(f, value, source) }
                check(conflict.match() == null && conflict.copy().match() == null, "Console evidence cannot override a $field conflict, before=$before")
            }
        }
        for (source in listOf("", "Current ordinary model", "$consoleSource (cached)")) {
            val untrusted = DeviceCatalog.Evidence().apply { offer("Model", "PS5", source) }
            check(untrusted.match() == null, "A missing or unrelated console source cannot authorize PS5 recognition")
        }
        check(DeviceCatalog.Evidence().apply { offer("Model", "PlayStation 5", consoleSource) }.match() == null,
            "The trusted console source must report the protocol's exact PS5 host type")
        check(DeviceCatalog.Evidence().match() == null, "Console corroboration is not retained by a fresh scan")
        val apple = DeviceCatalog.fromRecog(Recog.lookup("MacBookAir10,1")!!)
        check(apple.product.title == "Apple MacBook Air (M1, 2020)" && apple.source.contains(RecogModels.REVISION), "Existing Recog product gains a canonical brand while preserving rule provenance")
        check(DeviceCatalog.fromRecog(Recog.lookup("AppleTV11,1")!!).product.title == "Apple TV", "A product already containing its brand does not repeat it")
        fun labels(name: String = "", host: String = "", model: String = "", role: String = "") =
            DevicePresentation.labels(name, host, model, role, null, false, false)
        check(labels().title == "Unidentified device" && labels().subtitle.isEmpty(), "A missing identity stays unidentified")
        check(labels("Fixture lounge", role = "Casting device").subtitle == "Casting device", "A casting role does not guess hardware")
        check(labels("thinkstation-X900.local", "thinkstation-X900.local").let { it.title == "ThinkStation X900" && it.subtitle == "Reported device name" }, "Host typography does not assert a Lenovo product")
        check(labels("Custom.local").title == "Custom.local", "Custom names are preserved without matching hostname evidence")
        check(labels("Fixture Name.local", "Fixture Name.local").title == "Fixture Name.local", "Non-hostname names retain their suffix")
        check(labels(model = "Fixture model").subtitle == "Reported model", "An unmatched model is labelled as reported")
        check(DevicePresentation.labels("Fixture phone", "", "", "", apple.product, true, false).subtitle == "This phone", "Self role keeps precedence")
        check(DevicePresentation.labels("", "", "", "", apple.product, false, true).title == "Router", "Gateway role keeps precedence")
        println("PASS · $checks product catalogue and presentation checks")
    }
}
