package io.github.kazands.networklens

/** Offline interpretations of current device-info model fields, never identity proof. */
internal object Recog {
    enum class Category(val label: String) {
        LAPTOP("Laptop"), DESKTOP("Desktop computer"), TABLET("Tablet"), PHONE("Smartphone"),
        SPEAKER("Smart speaker"), TV_PLAYER("TV media player"), HANDHELD("Portable media player")
    }
    data class Model(val code: String, val description: String, val category: Category,
                     val rule: Int, val familyOnly: Boolean,
                     val observationSource: String = "mDNS current _device-info._tcp. TXT",
                     val catalogueSource: String? = null) {
        val canonicalCode: String get() = AppleModels.canonicalCode(code)
        val source: String get() = if (catalogueSource != null) "$catalogueSource · $observationSource"
            else if (observationSource == "mDNS current _device-info._tcp. TXT")
            "Recog · mdns.device-info.txt · rule $rule · ${RecogModels.REVISION}"
            else "Apple model catalogue · Recog model mapping $rule · ${RecogModels.REVISION} · $observationSource"
        val input: String get() {
            val field = when (observationSource) {
                AppleDeviceInfo.MODEL_SOURCE -> "ProductType"
                "mDNS current _companion-link._tcp. TXT" -> "rpMd"
                "mDNS current _raop._tcp. TXT" -> "am"
                else -> "model"
            }
            return "$observationSource · $field=$code"
        }
    }

    fun lookup(code: String): Model? = if (code.length in 1..64) RecogModels.lookup(code) ?: AppleModels.lookup(code) else null

    fun sameModel(first: String, second: String): Boolean = first == second ||
        lookup(first)?.let { a -> lookup(second)?.canonicalCode == a.canonicalCode } == true

    fun accepts(model: Model, value: String): Boolean = value == model.description || sameModel(model.code, value)

    /** Fixed-size scan evidence; display-observation eviction cannot erase a conflict. */
    class Evidence {
        // A known Apple model is still observed in typeModels when a prior maker
        // conflict prevents code from being selected. Ordinary non-Apple reports alone
        // must not count as an Apple identity conflict.
        internal fun hasRecordedConflict(): Boolean = typeModels.isNotEmpty() && conflicted
        private var code = ""
        private var observationSource = "mDNS current _device-info._tcp. TXT"
        private val typeModels = linkedMapOf<Category, String>()
        private var makerConflict = false
        private var conflicted = false
        private val reportedModels = linkedSetOf<String>()

        /** Receives only accepted, bounded, source-labelled reports from Device.hint. */
        fun observeReport(field: String, value: String) {
            if (field == "Manufacturer" && IdentityHints.specific(value) && !appleMaker(value)) makerConflict = true
            if (conflicted || !IdentityHints.specific(value)) return
            when (field) {
                "Manufacturer" -> if (!appleMaker(value)) conflicted = true
                "Model" -> if (value !in reportedModels) {
                    if (reportedModels.size == 8) conflicted = true else reportedModels.add(value)
                }
            }
            lookup(code)?.let { if (!reportsAgree(it)) conflicted = true }
        }

        private fun reportsAgree(model: Model): Boolean = reportedModels.all { accepts(model, it) }

        fun offer(attributes: Map<String, ByteArray>) = offerField(attributes, "model", "mDNS current _device-info._tcp. TXT")

        fun offerAppleModel(code: String) = offerField(mapOf("model" to code.toByteArray(Charsets.UTF_8)), "model", AppleDeviceInfo.MODEL_SOURCE)

        /** Reviewed model fields only; still called solely after current-wire admission. */
        fun offerService(type: String, attributes: Map<String, ByteArray>) {
            val key = when (type) {
                "_device-info._tcp.", "_airplay._tcp." -> "model"
                "_companion-link._tcp." -> "rpmd"
                "_raop._tcp." -> "am"
                else -> return
            }
            offerField(attributes, key, "mDNS current $type TXT")
        }
        private fun offerField(attributes: Map<String, ByteArray>, key: String, source: String) {
            // Wire parsing already folds keys and retains the first occurrence (RFC 6763 6.4).
            // Reject a noncanonical map defensively; it is not the network duplicate-key policy.
            val fields = attributes.filterKeys { it.equals(key, true) }.values
            if (fields.isEmpty()) return
            if (fields.size != 1) { conflicted = true; return }
            val bytes = fields.single()
            if (bytes.size !in 1..64 || bytes.any { it.toInt() !in 33..126 }) { conflicted = true; return }
            val next = String(bytes, Charsets.US_ASCII)
            lookup(next)?.copy(observationSource = source)?.let {
                typeModels.putIfAbsent(it.category, "${it.source} · ${it.input}")
            }
            if (conflicted) return
            if (code.isEmpty()) { code = next; observationSource = source } else if (!sameModel(code, next)) conflicted = true
            lookup(code)?.let { if (!reportsAgree(it)) conflicted = true }
        }

        fun match(): Model? = if (conflicted) null else lookup(code)?.copy(observationSource = observationSource)
        fun categories(): List<HardwareClassification.Fact> = if (makerConflict) emptyList() else typeModels.map { (category, source) ->
            val mapped = when (category) {
                Category.LAPTOP -> DeviceCatalog.Category.LAPTOP
                Category.DESKTOP -> DeviceCatalog.Category.DESKTOP
                Category.TABLET -> DeviceCatalog.Category.TABLET
                Category.PHONE -> DeviceCatalog.Category.PHONE
                Category.SPEAKER -> DeviceCatalog.Category.SPEAKER
                Category.TV_PLAYER -> DeviceCatalog.Category.TV_PLAYER
                Category.HANDHELD -> DeviceCatalog.Category.HANDHELD
            }
            HardwareClassification.Fact(mapped, "Apple model catalogue · $source")
        }
        fun compatible(model: Model): Boolean = !conflicted && (code.isEmpty() || sameModel(code, model.code)) && reportsAgree(model)
        fun copy(): Evidence = Evidence().also {
            it.code = code; it.conflicted = conflicted; it.observationSource = observationSource
            it.typeModels.putAll(typeModels); it.makerConflict = makerConflict
            it.reportedModels.addAll(reportedModels)
        }
    }
    fun appleMaker(value: String): Boolean = DeviceCatalog.key(value) in setOf("apple", "apple inc.", "apple inc", "apple computer, inc.")
}
