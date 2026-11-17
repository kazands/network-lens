package io.github.kazands.networklens

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** A matcher identifies an integration family, never an exact model or a physical device by itself. */
internal object DiscoveryMatchers {
    const val SOURCE = "Home Assistant discovery matchers"
    data class Condition(val field: String, val expression: String) { val regex = Regex(expression) }
    data class Rule(val kind: String, val domain: String, val conditions: List<Condition>)
    data class Family(val label: String, val hardware: String = "")
    private val families = mapOf(
        "wiz" to Family("WiZ-compatible device"), "yeelight" to Family("Yeelight light"),
        "govee" to Family("Govee device"), "tuya" to Family("Tuya-compatible device"),
        "hue" to Family("Philips Hue bridge", "Discovery smart-home bridge"),
        "lutron_caseta" to Family("Lutron system", "Discovery smart-home bridge"),
        "ecobee" to Family("ecobee device"), "sonos" to Family("Sonos audio system"),
        "nanoleaf" to Family("Nanoleaf device"), "lifx" to Family("LIFX device"),
        "wemo" to Family("WeMo device"), "shelly" to Family("Shelly device"),
        "samsungtv" to Family("Samsung media device"),
        "apple_tv" to Family("Apple media device"), "elgato" to Family("Elgato device"),
        "bond" to Family("Bond bridge", "Discovery smart-home bridge"),
        "homewizard" to Family("HomeWizard device"), "axis" to Family("Axis device"),
        "xiaomi_miio" to Family("Xiaomi miIO device"), "alexa_devices" to Family("Alexa service"),
        "plex" to Family("Plex media server"), "octoprint" to Family("OctoPrint server"),
        "zha" to Family("Zigbee coordinator", "Discovery smart-home bridge")
    )
    fun family(domain: String): Family? = families[domain]
    private fun normalize(values: Map<String, String>): Map<String, String>? {
        if (values.size > 128) return null
        val out = HashMap<String, String>()
        for ((key, value) in values) {
            if (key.length > 160 || value.length > 1024) return null
            val k = key.lowercase(Locale.ROOT); val v = value.lowercase(Locale.ROOT)
            if (out.containsKey(k) && out[k] != v) return null
            out[k] = v
        }
        return out
    }
    fun match(kind: String, values: Map<String, String>): Set<String> {
        val normalized = normalize(values) ?: return emptySet()
        return HaMatchers.rules.asSequence().filter { it.kind == kind && it.conditions.all { c ->
            normalized[c.field]?.let(c.regex::matches) == true
        } }.map { it.domain }.toSortedSet().take(16).toSet()
    }
    fun textAttributes(attrs: Map<String, ByteArray>): Map<String,String>? {
        if (attrs.size > 128) return null
        val out = LinkedHashMap<String,String>()
        for ((k,b) in attrs) {
            if (b.size > 1024 || k.length > 128) return null
            val s = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString() } catch (_: Exception) { return null }
            val key = k.lowercase(Locale.ROOT)
            if (out.containsKey(key) && out[key] != s) return null
            out[key]=s
        }
        return out
    }
    fun homekit(service: MdnsPacket.Service): Set<String> {
        if (!HomeKitInfo.applies(service.type)) return emptySet()
        val model = textAttributes(service.attributes)?.get("md") ?: return emptySet()
        return match("homekit", mapOf("model" to model))
    }
    fun mdns(service: MdnsPacket.Service): Set<String> {
        val attrs=textAttributes(service.attributes) ?: return emptySet()
        val type=LocalServices.canonicalType(service.type)
        if (type.isEmpty()) return emptySet()
        val values=attrs.mapKeys { "txt:"+it.key }.toMutableMap()
        values["type"]=type+"local."
        values["name"]=service.name+"."+type+"local."
        val matches=match("mdns",values).toMutableSet()
        return matches
    }
}
