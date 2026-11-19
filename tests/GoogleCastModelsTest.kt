import io.github.kazands.networklens.*

object GoogleCastModelsTest {
    private var checks = 0
    private fun check(value: Boolean, why: String) { checks++; if (!value) throw AssertionError(why) }
    @JvmStatic fun main(args: Array<String>) {
        check(DeviceCatalog.lookup("Google Nest Mini")?.title == "Google Nest Mini",
            "A current Google Nest Mini model identifies its product without a reported manufacturer")
        val aliasReports = listOf(Triple("Manufacturer", "Google", "UPnP device description"),
            Triple("Model", "Eureka Dongle", "UPnP device description"),
            Triple("Model", "Chromecast", "mDNS current _googlecast._tcp. TXT"))
        for (order in listOf(listOf(0,1,2), listOf(0,2,1), listOf(1,0,2), listOf(1,2,0), listOf(2,0,1), listOf(2,1,0))) {
            val e = DeviceCatalog.Evidence()
            order.forEach { n -> aliasReports[n].let { (f,v,s) -> e.offer(f,v,s) } }
            check(e.match()?.product?.id == "google:chromecast" && !e.hasRecordedConflict(), "Generic UPnP alias corroborates a current Chromecast model in order $order")
            check(e.match()!!.source.contains("_googlecast") && e.match()!!.input.lowercase().contains("chromecast"), "Actual identifying model and source are reported")
            check(e.copy().match() == e.match(), "Alias provenance survives snapshot")
            e.offer("Model", "Google Home Mini", "Current conflicting model")
            check(e.match() == null && e.hasRecordedConflict(), "Real conflicting Google products remain rejected")
        }
        for (source in listOf("UPnP device description", "Synthetic unrelated source")) {
            for (maker in listOf("", "Google", "Synthetic other maker")) {
                val e = DeviceCatalog.Evidence().apply {
                    offer("Manufacturer", maker, "Current manufacturer")
                    offer("Model", "Eureka Dongle", source)
                }
                check(e.match() == null, "Eureka token alone cannot select a Chromecast")
                e.offer("Model", "Chromecast", "mDNS current _googlecast._tcp. TXT")
                check((e.match() != null) == (maker == "Google" && source == "UPnP device description"), "Alias exemption requires reviewed source and reported Google maker")
            }
        }
        val cases = listOf(
            Triple("Google Nest Mini", "Google Nest Mini", DeviceCatalog.Category.SPEAKER),
            Triple("Nest Audio", "Google Nest Audio", DeviceCatalog.Category.SPEAKER),
            Triple("Google Home", "Google Home", DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Mini", "Google Home Mini", DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Max", "Google Home Max", DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Speaker", "Google Home Speaker", DeviceCatalog.Category.SPEAKER),
            Triple("Google Home Hub", "Google Nest Hub", DeviceCatalog.Category.SMART_DISPLAY),
            Triple("Nest Hub Max", "Google Nest Hub Max", DeviceCatalog.Category.SMART_DISPLAY),
            Triple("Chromecast", "Google Chromecast", DeviceCatalog.Category.TV_PLAYER),
            Triple("Chromecast Ultra", "Google Chromecast Ultra", DeviceCatalog.Category.TV_PLAYER),
            Triple("Chromecast with Google TV", "Google Chromecast with Google TV", DeviceCatalog.Category.TV_PLAYER),
            Triple("Google TV Streamer", "Google TV Streamer", DeviceCatalog.Category.TV_PLAYER),
            Triple("Chromecast Audio", "Google Chromecast Audio", DeviceCatalog.Category.AUDIO_RECEIVER),
            Triple("Nest Wifi Point", "Google Nest Wifi Point", DeviceCatalog.Category.ACCESS_POINT),
            Triple("Pixel Tablet", "Google Pixel Tablet", DeviceCatalog.Category.TABLET)
        )
        val source = "mDNS current _googlecast._tcp. TXT"
        for ((model, title, category) in cases) {
            val product = DeviceCatalog.lookup(model)!!
            check(product.brand == "Google" && product.title == title && product.category == category,
                "Exact $model supplies the reviewed product title and physical type")
            val evidence = DeviceCatalog.Evidence().apply { offer("Model", model, source) }
            check(evidence.match()?.product == product && evidence.match()!!.source.contains(source),
                "$model keeps its current model source without requiring a missing manufacturer")
            check(HardwareClassification.resolve(evidence.categories()).category == category,
                "$model has a physical category independent of Cast capability")
            val labels = DevicePresentation.labels("Synthetic room", "synthetic-cast.local", model,
                "Casting device", product, false, false)
            check(labels.title == title && labels.subtitle == category.label + " · Synthetic room",
                "$model leads with the product and retains a custom name below it")
            check(DevicePresentation.labels(model, "", model, "Casting device", product, false, false).subtitle == category.label,
                "A redundant reported $model name is not repeated")
            for (alias in product.models) {
                check(DeviceCatalog.lookup(alias) == product && DeviceCatalog.lookup(alias, "Google Inc.") == product,
                    "Reviewed $model aliases agree with optional reported Google manufacturer")
                evidence.offer("Model", alias, "Current corroborating model")
            }
            check(evidence.match()?.product == product, "All exact $model aliases corroborate the same product")
            check(DeviceCatalog.lookup(" \t" + model.uppercase() + "\t ", "Google LLC") == product,
                "Google model matching tolerates only its existing case and edge-space normalization")
            val grouped = DeviceCatalog.Evidence().apply { include(evidence); include(evidence.copy()) }
            check(grouped.match()?.product == product, "Repeated IPv4/IPv6 model evidence preserves $model identity")
            val snapshot = evidence.copy()
            evidence.offer("Model", "Synthetic incompatible model", "Current conflicting model")
            check(evidence.match() == null && snapshot.match()?.product == product,
                "A conflicting model suppresses $model without mutating a previous snapshot")
            for (makerFirst in listOf(false, true)) {
                val conflict = DeviceCatalog.Evidence()
                if (makerFirst) conflict.offer("Manufacturer", "Synthetic other brand", "Current maker")
                conflict.offer("Model", model, source)
                if (!makerFirst) conflict.offer("Manufacturer", "Synthetic other brand", "Current maker")
                check(conflict.match() == null && conflict.categories().isEmpty(),
                    "Conflicting manufacturer cannot borrow $model identity or category in either order")
            }
            check(DeviceCatalog.lookup(model, "Synthetic other brand") == null,
                "An exact Google model does not bypass a contradictory manufacturer")
            for (bad in listOf("$model Plus", "Synthetic $model", "$model-extra", model + "\n"))
                check(DeviceCatalog.lookup(bad) == null, "An unreviewed near-match cannot invent $model")
        }
        for (generic in listOf("Google Cast", "Google Cast Group", "Cast device", "Android TV", "Google", "Home Mini", "Nest", "Google Nest Mini 3"))
            check(DeviceCatalog.lookup(generic) == null, "A service, brand, generic label or unknown generation cannot create Google hardware")
        val nameOnly = DeviceCatalog.Evidence().apply { offer("Name", "Google Nest Mini", "Current friendly name") }
        check(nameOnly.match() == null && nameOnly.categories().isEmpty(), "A Google-looking friendly name is not model evidence")
        check(DeviceCatalog.Evidence().apply { offer("Manufacturer", "Google", "Current maker") }.match() == null,
            "A Google manufacturer alone cannot identify hardware")
        check(DeviceCatalog.lookup("Nest Hub")!!.familyOnly && DeviceCatalog.lookup("Chromecast")!!.familyOnly &&
            DeviceCatalog.lookup("Chromecast with Google TV")!!.familyOnly,
            "Shared model names retain family identity without guessing generation or HD/4K variant")
        check(DeviceCatalog.Evidence().match() == null, "Fresh scans inherit no Google device identity")
        println("PASS · $checks exact Google product, category, provenance and conflict checks")
    }
}
