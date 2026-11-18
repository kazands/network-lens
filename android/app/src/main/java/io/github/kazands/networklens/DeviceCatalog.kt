package io.github.kazands.networklens

import java.util.Locale

/** Small audited product catalogue. Matching a product never supplies reported fields. */
internal object DeviceCatalog {
    enum class Category(val label: String) {
        LAPTOP("Laptop"), DESKTOP("Desktop computer"), TABLET("Tablet"), PHONE("Smartphone"),
        SPEAKER("Speaker"), TV_PLAYER("Streaming device"), SMART_DISPLAY("Smart display"), HANDHELD("Portable media player"),
        RECORDER("Network video recorder"), CONSOLE("Game console"), THERMOSTAT("Thermostat"), TV("Television"),
        CAMERA("Camera"), DOORBELL("Video doorbell"), LIGHT("Light"), LOCK("Lock"), OUTLET("Outlet"),
        SWITCH("Switch"), SENSOR("Sensor"), FAN("Fan"), HEATER("Heater"), AIR_CONDITIONER("Air conditioner"),
        AIR_PURIFIER("Air purifier"), HUMIDIFIER("Humidifier"), DEHUMIDIFIER("Dehumidifier"),
        AMPLIFIER("Streaming amplifier"), AUDIO_RECEIVER("Audio receiver"), BRIDGE("Smart-home bridge"), GARAGE_DOOR("Garage door opener"),
        SECURITY_SYSTEM("Security system"), DOOR("Door"), WINDOW("Window"), WINDOW_COVERING("Window covering"),
        ACCESS_POINT("Wi-Fi access point"), NETWORK_SWITCH("Network switch"),
        ROUTER("Router"), RANGE_EXTENDER("Range extender"), SPRINKLER("Sprinkler"), FAUCET("Faucet"),
        SHOWER("Shower"), REMOTE("Remote control"), UNSPECIFIED("Not classified")
    }
    data class Product(val id: String, val brand: String, val title: String, val category: Category,
                       val models: Set<String>, val redundantNames: Set<String> = emptySet(), val familyOnly: Boolean = false,
                       val reference: String = "", val manufacturerRequired: Boolean = false, val displayType: String = "") {
        private fun modelKey(value: String) = key(value).removePrefix(key(brand) + " ")
        fun acceptsModel(value: String): Boolean = key(value) in models ||
            id.startsWith("tplink:") && TpLinkModels.sameModel(id, value) ||
            reference.isNotEmpty() && models.any { modelKey(it) == modelKey(value) } && global.lookup(brand, value)?.title == title
        fun redundantName(value: String): Boolean = key(value) in models || key(value) in redundantNames || key(value) == key(title)
    }
    data class Match(val product: Product, val source: String, val input: String, val recog: Recog.Model? = null)

    private const val PLAYSTATION_SOURCE = "PlayStation discovery host-type"
    private fun keys(vararg values: String) = values.mapTo(HashSet(), ::key)
    // Exact reported-model aliases and primary references: docs/DEVICE-IDENTITIES.md.
    private val products = listOf("A.1", "A.3", "A.6", "A.1x", "A.3x", "A.6x").map { model ->
        Product("vssl:${key(model)}", "VSSL", "VSSL $model", Category.AMPLIFIER,
            keys(model, model.replace(".", ""), "VSSL $model", "VSSL ${model.replace(".", "")}"), manufacturerRequired = true)
    } + listOf(
        Product("hikvision:ds-7608ni-i2-8p", "Hikvision", "Hikvision DS-7608NI-I2/8P", Category.RECORDER,
            keys("DS-7608NI-I2/8P", "Hikvision DS-7608NI-I2/8P")),
        Product("nvidia:shield-tv", "NVIDIA", "NVIDIA SHIELD TV", Category.TV_PLAYER,
            keys("SHIELD Android TV", "SHIELD TV", "NVIDIA SHIELD Android TV", "NVIDIA SHIELD TV"), keys("SHIELD"), true),
        Product("sony:playstation-5", "Sony", "Sony PlayStation 5", Category.CONSOLE,
            keys("PS5", "PlayStation 5", "Sony PlayStation 5"), familyOnly = true),
        Product("ecobee:smart-thermostat-premium", "ecobee", "ecobee Smart Thermostat Premium", Category.THERMOSTAT,
            keys("Smart_Thermostat_Premium", "Smart Thermostat Premium", "ecobee Smart Thermostat Premium", "ECB601"), manufacturerRequired = true),
        Product("samsung:pn59d550", "Samsung", "Samsung PN59D550", Category.TV,
            keys("PN59D550", "PN59D550C1F", "PN59D550C1FXZC"), familyOnly = true, manufacturerRequired = true)
    )
    private fun brand(value: String): String = when (key(value)) {
        "hikvision", "hangzhou hikvision digital technology co., ltd.", "hangzhou hikvision digital technology co.,ltd." -> "hikvision"
        "nvidia", "nvidia corporation" -> "nvidia"
        "sony", "sony corporation", "sony interactive entertainment", "sony interactive entertainment inc.", "sony_tv" -> "sony"
        "samsung", "samsung electronics", "samsung electronics co., ltd." -> "samsung"
        "sonos", "sonos, inc.", "sonos inc." -> "sonos"
        "ecobee", "ecobee inc.", "ecobee inc" -> "ecobee"
        "apple", "apple inc.", "apple inc", "apple computer, inc." -> "apple"
        "google", "google llc", "google inc.", "google inc" -> "google"
        "amazon", "amazon.com", "amazon.com, inc.", "amazon.com inc." -> "amazon"
        "dlink", "d-link", "d-link corporation" -> "d-link"
        "hp", "hp inc.", "hewlett-packard", "hewlett packard", "hewlett-packard company" -> "hp"
        "qnap", "qnap systems, inc." -> "qnap"
        "synology", "synology inc.", "synology incorporated" -> "synology"
        "axis", "axis communications", "axis communications ab" -> "axis"
        "tp-link", "tp-link technologies co., ltd." -> "tp-link"
        "asus", "asustek computer inc." -> "asus"
        "canon", "canon inc." -> "canon"
        "epson", "seiko epson corporation" -> "epson"
        "brother", "brother industries, ltd." -> "brother"
        "dell", "dell inc." -> "dell"
        "cisco", "cisco systems", "cisco systems, inc." -> "cisco"
        "western digital", "western digital technologies, inc." -> "western digital"
        else -> key(value)
    }
    internal fun key(value: String): String = value.trim(' ', '\t').lowercase(Locale.ROOT)
    @Volatile private var global = ProductModelIndex.EMPTY
    @Volatile private var hardware = AndroidHardwareIndex.EMPTY
    internal fun install(index: ProductModelIndex) { global = index }
    internal fun installHardware(index: AndroidHardwareIndex) { hardware = index }
    internal fun extendedSize(): Int = global.size
    fun lookup(model: String, manufacturer: String = ""): Product? {
        if (model.length !in 1..160) return null
        products.singleOrNull { it.acceptsModel(model) && (!it.manufacturerRequired || manufacturer.isNotEmpty()) &&
            (manufacturer.isEmpty() || brand(manufacturer) == brand(it.brand)) }?.let { return it }
        val maker = brand(manufacturer)
        if (maker.isEmpty() || maker == "google") GoogleCastModels.lookup(model)?.let { return it }
        if (maker == "tp-link") TpLinkModels.lookup(model)?.let { return it }
        val entry = global.lookup(maker, model) ?: return null
        val type = hardware.lookup(maker, model)
        return Product("catalog:$maker:${key(entry.title)}", entry.brand, entry.title, type?.category ?: Category.UNSPECIFIED,
            keys(model), familyOnly = entry.source.contains("NIST-CPE@"), reference = entry.source + (type?.let { ";" + it.source } ?: ""))
    }
    fun fromRecog(model: Recog.Model): Match {
        val category = when (model.category) {
            Recog.Category.LAPTOP -> Category.LAPTOP
            Recog.Category.DESKTOP -> Category.DESKTOP
            Recog.Category.TABLET -> Category.TABLET
            Recog.Category.PHONE -> Category.PHONE
            Recog.Category.SPEAKER -> Category.SPEAKER
            Recog.Category.TV_PLAYER -> Category.TV_PLAYER
            Recog.Category.HANDHELD -> Category.HANDHELD
        }
        val title = if (model.description.startsWith("Apple ")) model.description else "Apple ${model.description}"
        return Match(Product("recog:${model.code}", "Apple", title, category, keys(model.code, model.description),
            familyOnly = model.familyOnly), model.source, model.input, model)
    }

    /** Retain at most eight distinct models until the maker/catalogue can qualify their aliases.
     * No model is evicted: conflicts (including overflow) survive display-observation eviction. */
    class Evidence {
        private var firstModel = ""
        private var modelSource = ""
        private val models = HashSet<String>()
        private val modelSources = HashMap<String, String>()
        private var manufacturer = ""
        private var manufacturerSource = ""
        private var playStationResponse = false
        private var tpLinkResponse = false
        private var omadaResponse = false
        private var omadaModelSource = ""
        private var protocolBrand = ""
        private var protocolSource = ""
        private var conflicted = false
        private var registryMatch: Match? = null
        private var registryIdentity = ""
        private var fireTvType = ""
        private var fireTvSource = ""
        private var fireTvHardwareObserved = false
        // A later conflicting code can suppress the match, but cannot erase known
        // Amazon hardware evidence when another recognition engine checks compatibility.
        fun hasFireTvHardwareIdentity(): Boolean = fireTvHardwareObserved
        fun offerFireTvType(code: String, source: String) {
            if (code.isEmpty() || source.isEmpty()) return
            fireTvHardwareObserved = fireTvHardwareObserved || FireTvInfo.product(code) != null
            if (fireTvType.isNotEmpty() && fireTvType != code) conflicted = true
            else { fireTvType = code; fireTvSource = source }
        }
        fun offerRegistryIdentity(id: String) {
            if (registryIdentity.isNotEmpty() && registryIdentity != id) conflicted = true
            else registryIdentity = id
        }
        private fun registry(): Match? {
            registryMatch?.let { return it }
            val p = registryIdentity.split(':')
            if (p.size != 3 || p[0] != "matter") return null
            return MatterInfo.index.lookup(p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
        }
        fun offerProduct(match: Match) {
            if (registryMatch?.product?.id?.let { it != match.product.id } == true) conflicted = true
            else registryMatch = match
        }
        fun offer(field: String, value: String, source: String) {
            if (conflicted || source.isEmpty() || !IdentityHints.specific(value)) return
            when (field) {
                "Manufacturer" -> {
                    val next = brand(value)
                    if (manufacturer.isEmpty()) { manufacturer = next; manufacturerSource = source }
                    else if (manufacturer != next) conflicted = true
                }
                "Model" -> {
                    if (value.length > 160) return
                    if (value == "PS5" && source == PLAYSTATION_SOURCE) playStationResponse = true
                    if (source == TpLinkDiscovery.MODEL_SOURCE) tpLinkResponse = true
                    if (source == OmadaDiscovery.MODEL_SOURCE || source == EapInfo.MODEL_SOURCE || source == EasySmartDiscovery.MODEL_SOURCE) {
                        omadaResponse = true
                        if (omadaModelSource.isEmpty()) omadaModelSource = source
                    }
                    val candidateBrand = when (source) {
                        LocalMetadata.Kind.SAMSUNG.source + " (reported)" -> "Samsung"
                        LanIdentity.Method.GOVEE.source + " (reported)" -> "Govee"
                        else -> ""
                    }
                    if (candidateBrand.isNotEmpty()) {
                        if (protocolBrand.isNotEmpty() && protocolBrand != candidateBrand) conflicted = true
                        else { protocolBrand = candidateBrand; protocolSource = source }
                    }
                    val next = key(value)
                    if (next !in models) {
                        if (models.size == 8) conflicted = true
                        else models.add(next)
                        if (!conflicted) modelSources[next] = source
                    }
                    if (firstModel.isEmpty()) { firstModel = value; modelSource = source }
                }
            }
        }
        fun compatible(product: Product): Boolean = !conflicted &&
            (fireTvType.isEmpty() || (FireTvInfo.product(fireTvType)?.let { it.id == product.id }
                ?: !product.id.startsWith(FireTvInfo.PRODUCT_PREFIX))) &&
            // A missing registry row is not missing identity. Grouped endpoints must agree
            // on a Matter candidate's raw vendor/product pair even before the index loads.
            (registryIdentity.isEmpty() || !product.id.startsWith("matter:") || registryIdentity == product.id) &&
            (registry() == null || registry()!!.product.id == product.id) &&
            models.all(product::acceptsModel) &&
            (manufacturer.isEmpty() || manufacturer == brand(product.brand)) &&
            (protocolBrand.isEmpty() || brand(protocolBrand) == brand(product.brand))
        /** Hardware can be known when the exact product is ambiguous. Unknown model tokens
         * do not turn a known thermostat into an audio device; contradictory known types do. */
        fun categories(): List<HardwareClassification.Fact> {
            if (conflicted) return emptyList()
            // A reviewed hardware identity constrains every catalogue category,
            // not just the product title. Do not fall back to a rejected model.
            if (fireTvHardwareObserved) return listOfNotNull(
                FireTvInfo.product(fireTvType)?.takeIf(::compatible)?.let {
                    HardwareClassification.Fact(it.category, "Amazon device-type catalogue · $fireTvSource")
                })
            return listOfNotNull(
                registry()?.takeIf { compatible(it.product) && it.product.category != Category.UNSPECIFIED }?.let { HardwareClassification.Fact(it.product.category, it.source) }) + models.mapNotNull { model ->
                val product = qualifiedProduct(model) ?: return@mapNotNull null
                if (product.category == Category.UNSPECIFIED ||
                    product.id == "sony:playstation-5" && manufacturer.isEmpty() && !playStationResponse) null
                else HardwareClassification.Fact(product.category, "Local product catalogue · Model=$model · ${modelSources[model]}" +
                    (hardware.lookup(manufacturer, model)?.let { " · " + it.source } ?: ""))
            }
        }
        private fun qualifiedManufacturer(): String = manufacturer.ifEmpty { if (tpLinkResponse || omadaResponse) "tp-link" else brand(protocolBrand) }
        private fun qualifiedProduct(model: String): Product? =
            lookup(model, qualifiedManufacturer()) ?: when {
                omadaResponse && qualifiedManufacturer() == "tp-link" -> OmadaDiscovery.product(model)
                protocolBrand.isNotEmpty() && key(model).isNotEmpty() -> Product(
                    "protocol:${brand(protocolBrand)}:${key(model)}", protocolBrand,
                    if (model.startsWith("$protocolBrand ", true)) model else "$protocolBrand $model",
                    if (protocolBrand == "Samsung") Category.TV else Category.UNSPECIFIED, setOf(key(model)))
                else -> null
            }
        fun match(): Match? {
            registry()?.let { return it.takeIf { compatible(it.product) } }
            FireTvInfo.product(fireTvType)?.let { product ->
                return if (compatible(product)) Match(product, "Amazon device-type catalogue · ${product.reference} · $fireTvSource", "TXT ad=$fireTvType") else null
            }
            val product = qualifiedProduct(firstModel) ?: return null
            if (!compatible(product)) return null
            // PS5 is a short, reusable code. A console response or reported Sony brand is required.
            if (product.id == "sony:playstation-5" && manufacturer.isEmpty() && !playStationResponse) return null
            val corroboration = if (product.id == "sony:playstation-5" && playStationResponse && modelSource != PLAYSTATION_SOURCE)
                " · Model=PS5: $PLAYSTATION_SOURCE"
                else if (product.id.startsWith("tplink:") && tpLinkResponse && modelSource != TpLinkDiscovery.MODEL_SOURCE)
                    " · " + TpLinkDiscovery.MODEL_SOURCE
                else if (omadaResponse && modelSource != omadaModelSource) " · $omadaModelSource" else ""
            if (product.id.startsWith("protocol:"))
                return Match(product, "Protocol-reported model · $protocolSource", "Model=$firstModel")
            if (product.id.startsWith("omada:"))
                return Match(product, "Protocol-reported model · $modelSource$corroboration", "Model=$firstModel")
            return if (product.reference.isEmpty()) Match(product, "Local product catalogue · ${product.id} · $modelSource$corroboration", "Model=$firstModel")
                else Match(product, "Product model catalogue · ${product.reference} · Model: $modelSource · Manufacturer: $manufacturerSource",
                    "Normalized manufacturer=$manufacturer; Model=$firstModel")
        }
        fun copy(): Evidence = Evidence().also {
            it.firstModel = firstModel; it.modelSource = modelSource
            it.models.addAll(models); it.modelSources.putAll(modelSources)
            it.manufacturer = manufacturer; it.conflicted = conflicted
            it.manufacturerSource = manufacturerSource
            it.registryMatch = registryMatch; it.registryIdentity = registryIdentity
            it.fireTvType = fireTvType; it.fireTvSource = fireTvSource
            it.fireTvHardwareObserved = fireTvHardwareObserved
            it.playStationResponse = playStationResponse
            it.tpLinkResponse = tpLinkResponse
            it.omadaResponse = omadaResponse
            it.omadaModelSource = omadaModelSource
            it.protocolBrand = protocolBrand; it.protocolSource = protocolSource
        }
        fun include(other: Evidence) {
            if (other.conflicted) conflicted = true
            fireTvHardwareObserved = fireTvHardwareObserved || other.fireTvHardwareObserved
            offerFireTvType(other.fireTvType, other.fireTvSource)
            if (other.protocolBrand.isNotEmpty()) {
                if (protocolBrand.isNotEmpty() && protocolBrand != other.protocolBrand) conflicted = true
                else { protocolBrand = other.protocolBrand; protocolSource = other.protocolSource }
            }
            if (other.registryIdentity.isNotEmpty()) offerRegistryIdentity(other.registryIdentity)
            other.registryMatch?.let(::offerProduct)
            // Preserve the reported spelling for protocol-qualified models that have no catalogue alias.
            offer("Model", other.firstModel, other.modelSource)
            other.models.sorted().forEach { offer("Model", it, other.modelSources[it].orEmpty()) }
            offer("Manufacturer", other.manufacturer, other.manufacturerSource)
            playStationResponse = playStationResponse || other.playStationResponse
            tpLinkResponse = tpLinkResponse || other.tpLinkResponse
            omadaResponse = omadaResponse || other.omadaResponse
            if (omadaModelSource.isEmpty()) omadaModelSource = other.omadaModelSource
        }
    }
}
