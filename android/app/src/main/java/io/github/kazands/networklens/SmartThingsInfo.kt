package io.github.kazands.networklens

/** Conservative current-advertisement interpretation; see docs/reference/SMARTTHINGS.md. */
internal object SmartThingsInfo {
    const val TYPE = "_smartthings._tcp."
    const val HEDGE_TYPE = "_smartthings-hedge._tcp."
    const val PRODUCT_ID = "smartthings:hub-family"
    private val identifier = Regex("[0-9a-fA-F]{16}")
    private val hubHost = Regex("hubv2-(0?[0-9a-f]{16})\\.local\\.?", RegexOption.IGNORE_CASE)
    private val technical = Regex("(?:hub-[0-9a-f]{32}|hubv2-0?[0-9a-f]{16}|[0-9a-f]{16})(?:\\.local\\.?)?", RegexOption.IGNORE_CASE)

    fun matches(service: MdnsPacket.Service): Boolean {
        if (LocalServices.canonicalType(service.type) != TYPE || service.port !in 1..65535 ||
            !identifier.matches(service.name) || service.host.length > 64) return false
        val hostId = hubHost.matchEntire(service.host)?.groupValues?.get(1) ?: return false
        // The observed hostname has an optional leading zero, not a hardware MAC.
        return hostId.takeLast(16).equals(service.name, true)
    }
    fun technicalName(value: String): Boolean = value.length <= 64 && technical.matches(value)
    fun acceptsManufacturer(value: String): Boolean = value in setOf("smartthings", "samsung", "aeotec")
    fun identity(source: String) = DeviceCatalog.Match(DeviceCatalog.Product(
        PRODUCT_ID, "SmartThings", "SmartThings Hub", DeviceCatalog.Category.HUB,
        setOf("smartthings hub", "samsung smartthings hub"), familyOnly = true, titleSource = DeviceCatalog.TitleSource.CATALOGUE),
        "SmartThings hub advertisement · $source", "SmartThings service with matching hub hostname and instance ID; generation not verified")
}
