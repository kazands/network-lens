package io.github.kazands.networklens

/** Offline interpretations of current device-info model fields, never identity proof. */
internal object Recog {
    enum class Category(val label: String) {
        LAPTOP("Laptop"), DESKTOP("Desktop computer"), TABLET("Tablet"), PHONE("Smartphone"),
        SPEAKER("Smart speaker"), TV_PLAYER("TV media player"), HANDHELD("Portable media player")
    }
    data class Model(val code: String, val description: String, val category: Category,
                     val rule: Int, val familyOnly: Boolean) {
        val source: String get() = "Recog · mdns.device-info.txt · rule $rule · ${RecogModels.REVISION}"
        val input: String get() = "mDNS current _device-info._tcp. TXT · model=$code"
    }

    fun lookup(code: String): Model? = if (code.length in 1..64) RecogModels.lookup(code) else null

    /** Fixed-size scan evidence; display-observation eviction cannot erase a conflict. */
    class Evidence {
        private var code = ""
        private var conflicted = false
        private var firstReportedModel = ""
        private var secondReportedModel = ""

        /** Receives only accepted, bounded, source-labelled reports from Device.hint. */
        fun observeReport(field: String, value: String) {
            if (conflicted || !IdentityHints.specific(value)) return
            when (field) {
                "Manufacturer" -> if (!value.equals("Apple", true)) conflicted = true
                "Model" -> when {
                    firstReportedModel.isEmpty() -> firstReportedModel = value
                    value == firstReportedModel || value == secondReportedModel -> Unit
                    secondReportedModel.isEmpty() -> secondReportedModel = value
                    // A match permits only its input code and one description.
                    // A third distinct report cannot agree with any such pair.
                    else -> conflicted = true
                }
            }
            lookup(code)?.let { if (!reportsAgree(it)) conflicted = true }
        }

        private fun reportsAgree(model: Model): Boolean =
            (firstReportedModel.isEmpty() || firstReportedModel == model.code || firstReportedModel == model.description) &&
                (secondReportedModel.isEmpty() || secondReportedModel == model.code || secondReportedModel == model.description)

        fun offer(attributes: Map<String, ByteArray>) {
            if (conflicted) return
            val fields = attributes.filterKeys { it.equals("model", true) }.values
            if (fields.isEmpty()) return
            if (fields.size != 1) { conflicted = true; return }
            val bytes = fields.single()
            if (bytes.size !in 1..64 || bytes.any { it.toInt() !in 33..126 }) { conflicted = true; return }
            val next = String(bytes, Charsets.US_ASCII)
            if (code.isEmpty()) code = next else if (code != next) conflicted = true
            lookup(code)?.let { if (!reportsAgree(it)) conflicted = true }
        }

        fun match(): Model? = if (conflicted) null else lookup(code)
        fun compatible(model: Model): Boolean = !conflicted && (code.isEmpty() || code == model.code) && reportsAgree(model)
        fun copy(): Evidence = Evidence().also {
            it.code = code; it.conflicted = conflicted
            it.firstReportedModel = firstReportedModel; it.secondReportedModel = secondReportedModel
        }
    }
}
