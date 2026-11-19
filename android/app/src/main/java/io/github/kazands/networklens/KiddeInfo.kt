package io.github.kazands.networklens

/** Observed WD-family fingerprint, not a retail-model or HomeKit compatibility claim.
 * Admission must already establish current, same-endpoint records. See docs/reference/KIDDE.md. */
internal object KiddeInfo {
    const val TYPE = "_hap._tcp."
    const val PRODUCT_ID = "kidde:wd-detector-family"
    private val instance = Regex("Kidde-WD-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
    private val technical = Regex("Kidde-WD-[0-9a-f]{12}(?:\\.local\\.?)?", RegexOption.IGNORE_CASE)

    fun matches(service: MdnsPacket.Service): Boolean {
        if (LocalServices.canonicalType(service.type) != TYPE || service.port !in 1..65535 ||
            service.duplicateModelKey || !instance.matches(service.name) || service.host.length > 64 || service.attributes.size > 128) return false
        if (!service.host.removeSuffix(".").equals(service.name + ".local", true)) return false
        // Reject duplicate case variants, malformed bytes and oversized values before decoding.
        val models = service.attributes.filterKeys { it.equals("md", true) }.values
        val model = models.singleOrNull() ?: return false
        return model.size == 2 && (model[0] == 87.toByte() || model[0] == 119.toByte()) &&
            (model[1] == 68.toByte() || model[1] == 100.toByte())
    }

    fun technicalName(value: String): Boolean = value.length <= 64 && technical.matches(value)
    fun identity(source: String) = DeviceCatalog.Match(DeviceCatalog.Product(
        PRODUCT_ID, "Kidde", "Kidde Smart Detector", DeviceCatalog.Category.SENSOR,
        setOf("wd"), familyOnly = true, displayType = "Safety detector", titleSource = DeviceCatalog.TitleSource.CATALOGUE),
        "Kidde WD-family advertisement · $source", "Matching Kidde-WD instance and host with TXT md=WD; exact model and sensor capabilities not reported")
}
