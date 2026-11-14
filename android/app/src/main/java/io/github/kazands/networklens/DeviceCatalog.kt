package io.github.kazands.networklens

import java.util.Locale

/** Small audited product catalogue. Matching a product never supplies reported fields. */
internal object DeviceCatalog {
    enum class Category(val label: String) {
        LAPTOP("Laptop"), DESKTOP("Desktop computer"), TABLET("Tablet"), PHONE("Smartphone"),
        SPEAKER("Smart speaker"), TV_PLAYER("Streaming device"), HANDHELD("Portable media player"),
        RECORDER("Network video recorder"), CONSOLE("Game console"), UNSPECIFIED("Not classified")
    }
    data class Product(val id: String, val brand: String, val title: String, val category: Category,
                       val models: Set<String>, val redundantNames: Set<String> = emptySet(), val familyOnly: Boolean = false,
                       val reference: String = "") {
        private fun modelKey(value: String) = key(value).removePrefix(key(brand) + " ")
        fun acceptsModel(value: String): Boolean = key(value) in models ||
            reference.isNotEmpty() && models.any { modelKey(it) == modelKey(value) } && global.lookup(brand, value)?.title == title
        fun redundantName(value: String): Boolean = key(value) in models || key(value) in redundantNames || key(value) == key(title)
    }
    data class Match(val product: Product, val source: String, val input: String, val recog: Recog.Model? = null)

    private fun keys(vararg values: String) = values.mapTo(HashSet(), ::key)
    // Exact reported-model aliases and primary references: docs/DEVICE-IDENTITIES.md.
    private val products = listOf(
        Product("hikvision:ds-7608ni-i2-8p", "Hikvision", "Hikvision DS-7608NI-I2/8P", Category.RECORDER,
            keys("DS-7608NI-I2/8P", "Hikvision DS-7608NI-I2/8P")),
        Product("nvidia:shield-tv", "NVIDIA", "NVIDIA SHIELD TV", Category.TV_PLAYER,
            keys("SHIELD Android TV", "SHIELD TV", "NVIDIA SHIELD Android TV", "NVIDIA SHIELD TV"), keys("SHIELD"), true),
        Product("sony:playstation-5", "Sony", "Sony PlayStation 5", Category.CONSOLE,
            keys("PS5", "PlayStation 5", "Sony PlayStation 5"), familyOnly = true)
    )
    private fun brand(value: String): String = when (key(value)) {
        "hikvision", "hangzhou hikvision digital technology co., ltd.", "hangzhou hikvision digital technology co.,ltd." -> "hikvision"
        "nvidia", "nvidia corporation" -> "nvidia"
        "sony", "sony corporation", "sony interactive entertainment", "sony interactive entertainment inc." -> "sony"
        "samsung", "samsung electronics", "samsung electronics co., ltd." -> "samsung"
        "google", "google llc" -> "google"
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
    internal fun install(index: ProductModelIndex) { global = index }
    internal fun extendedSize(): Int = global.size
    fun lookup(model: String, manufacturer: String = ""): Product? {
        if (model.length !in 1..160) return null
        products.singleOrNull { it.acceptsModel(model) && (manufacturer.isEmpty() || brand(manufacturer) == brand(it.brand)) }?.let { return it }
        val maker = brand(manufacturer)
        val entry = global.lookup(maker, model) ?: return null
        return Product("catalog:$maker:${key(model).removePrefix(maker + " ")}", entry.brand, entry.title, Category.UNSPECIFIED,
            keys(model), familyOnly = entry.source.contains("NIST-CPE@"), reference = entry.source)
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
        private var manufacturer = ""
        private var manufacturerSource = ""
        private var conflicted = false
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
                    val next = key(value)
                    if (next !in models) {
                        if (models.size == 8) conflicted = true
                        else models.add(next)
                    }
                    if (firstModel.isEmpty()) { firstModel = value; modelSource = source }
                }
            }
        }
        fun compatible(product: Product): Boolean = !conflicted &&
            models.all(product::acceptsModel) &&
            (manufacturer.isEmpty() || manufacturer == brand(product.brand))
        fun match(): Match? {
            val product = lookup(firstModel, manufacturer) ?: return null
            if (!compatible(product)) return null
            // PS5 is a short, reusable code. A console response or reported Sony brand is required.
            if (product.id == "sony:playstation-5" && manufacturer.isEmpty() && modelSource != "PlayStation discovery host-type") return null
            return if (product.reference.isEmpty()) Match(product, "Local product catalogue · ${product.id} · $modelSource", "Model=$firstModel")
                else Match(product, "Product model catalogue · ${product.reference} · Model: $modelSource · Manufacturer: $manufacturerSource",
                    "Normalized manufacturer=$manufacturer; Model=$firstModel")
        }
        fun copy(): Evidence = Evidence().also {
            it.firstModel = firstModel; it.modelSource = modelSource
            it.models.addAll(models)
            it.manufacturer = manufacturer; it.conflicted = conflicted
            it.manufacturerSource = manufacturerSource
        }
    }
}
