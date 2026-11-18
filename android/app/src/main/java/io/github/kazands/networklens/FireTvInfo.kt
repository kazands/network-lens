package io.github.kazands.networklens

/** WhisperPlay names and reviewed offline device-type identities.
 * References and limits: docs/FIRE-TV-RECOGNITION.md. */
internal object FireTvInfo {
    const val TYPE = "_amzn-wplay._tcp."
    const val FAMILY = "amazon_fire_tv"
    private val manager = Regex("amzn\\.dmgr:[A-Fa-f0-9]{32}:[A-Za-z0-9+/=_-]+:[0-9]+")
    private val deviceType = Regex("A[A-Z0-9]{5,31}")
    data class Report(val name: String, val typeCode: String)
    const val PRODUCT_PREFIX = "amazon:type:"
    // One reviewed public device-type fact, not the instance UUID or an account identifier.
    // MIT source and modification record: android/fire-tv-provenance.json.
    private val stick4k = DeviceCatalog.Product(
        PRODUCT_PREFIX + "AKPGW064GI9HE", "Amazon", "Amazon Fire TV Stick 4K", DeviceCatalog.Category.TV_PLAYER,
        setOf("fire tv", "amazon fire tv", "fire tv stick 4k", "amazon fire tv stick 4k", "aftmm"),
        familyOnly = true, reference = "ioBroker.alexa2@6471cf116653e50b6cbc6c4dd54c2d52bfea4190 · Amazon device-type table")
    fun product(code: String): DeviceCatalog.Product? = stick4k.takeIf { code == "AKPGW064GI9HE" }

    fun parse(service: MdnsPacket.Service): Report? {
        if (LocalServices.canonicalType(service.type) != TYPE || service.port !in 1..65535 ||
            service.name.length > 128 || !manager.matches(service.name)) return null
        val attrs = DiscoveryMatchers.textAttributes(service.attributes) ?: return null
        val name = attrs["n"].orEmpty().let {
            if (it.length <= 160 && it == SafeText.label(it, 160) && IdentityHints.specificName(it)) it else ""
        }
        // This is a catalogue key, not a reported retail-model string or hardware address.
        val code = attrs["ad"].orEmpty().takeIf(deviceType::matches).orEmpty()
        return Report(name, code)
    }
}
