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
        FireTvInfo.FAMILY to Family("Amazon Fire TV"),
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
        "zha" to Family("Zigbee coordinator", "Discovery smart-home bridge"),
        "webostv" to Family("LG webOS device"), "braviatv" to Family("Sony BRAVIA device"),
        "vizio" to Family("VIZIO device"), "philips_js" to Family("Philips television service"),
        "roku" to Family("Roku device"), "directv" to Family("DIRECTV receiver"),
        "harmony" to Family("Logitech Harmony device"), "synology_dsm" to Family("Synology device"),
        "netgear" to Family("NETGEAR device"), "unifi_discovery" to Family("UniFi device"),
        "mikrotik" to Family("MikroTik device"), "ubiquiti" to Family("Ubiquiti device"),
        "fritz" to Family("FRITZ! network device"), "fritzbox" to Family("FRITZ! smart-home system"),
        "freebox" to Family("Freebox device"), "keenetic_ndms2" to Family("Keenetic device"),
        "huawei_lte" to Family("Huawei network device"),
        "brother" to Family("Brother printing device"), "hp_printer" to Family("HP printing device"),
        "syncthru" to Family("Samsung printing device"), "ipp" to Family("Printing service"),
        "roomba" to Family("iRobot device"), "romy" to Family("ROMY device"),
        "daikin" to Family("Daikin device"), "sensibo" to Family("Sensibo device"),
        "tado" to Family("tado° device"), "netatmo" to Family("Netatmo device"),
        "baf" to Family("Big Ass Fans device"), "modern_forms" to Family("Modern Forms device"),
        "airgradient" to Family("AirGradient device"), "airq" to Family("air-Q device"),
        "awair" to Family("Awair device"), "haven_iaq" to Family("HAVEN device"),
        "home_connect" to Family("Home Connect appliance"), "miele" to Family("Miele appliance"),
        "liebherr" to Family("Liebherr appliance"), "rachio" to Family("Rachio device"),
        "rainmachine" to Family("RainMachine device"), "hunterdouglas_powerview" to Family("PowerView system"),
        "velux" to Family("VELUX system"), "overkiz" to Family("Overkiz system"),
        "tradfri" to Family("IKEA smart-home system"), "deconz" to Family("deCONZ service"),
        "bosch_shc" to Family("Bosch Smart Home system"), "homee" to Family("homee system"),
        "isy994" to Family("ISY automation system"), "xiaomi_aqara" to Family("Aqara system"),
        "smlight" to Family("SMLIGHT device"), "zwave_js" to Family("Z-Wave JS service"),
        "zwave_me" to Family("Z-Wave.Me service"), "wled" to Family("WLED device"),
        "esphome" to Family("ESPHome device"), "blebox" to Family("BleBox device"),
        "heos" to Family("HEOS audio system"), "bluesound" to Family("BluOS audio system"),
        "yamaha_musiccast" to Family("Yamaha MusicCast device"), "denonavr" to Family("Denon audio device"),
        "wiim" to Family("WiiM device"), "linkplay" to Family("Linkplay audio device"),
        "soundtouch" to Family("Bose SoundTouch device"), "bang_olufsen" to Family("Bang & Olufsen device"),
        "devialet" to Family("Devialet device"), "volumio" to Family("Volumio player"),
        "kodi" to Family("Kodi media service"), "music_assistant" to Family("Music Assistant service"),
        "forked_daapd" to Family("DAAP music service"), "dlna_dmr" to Family("DLNA playback service"),
        "dlna_dms" to Family("DLNA media server"), "openhome" to Family("OpenHome audio service"),
        "cast" to Family("Google Cast device"), "androidtv_remote" to Family("Android TV remote service"),
        "homekit_controller" to Family("HomeKit device"), "homekit" to Family("HomeKit service"),
        "matter" to Family("Matter device"), "thread" to Family("Thread border-router service"),
        "wyoming" to Family("Wyoming voice service"), "nut" to Family("UPS monitoring service"),
        "enphase_envoy" to Family("Enphase energy system"), "victron_gx" to Family("Victron energy system"),
        "solaredge_modbus" to Family("SolarEdge device"), "smappee" to Family("Smappee device"),
        "openevse" to Family("OpenEVSE charging system"), "peblar" to Family("Peblar charging system"),
        "abode" to Family("abode system"), "altruist" to Family("Altruist device"),
        "arcam_fmj" to Family("Arcam device"), "bitvis" to Family("Bitvis service"),
        "bluetti_modbus" to Family("BLUETTI energy system"), "bsblan" to Family("BSB-LAN service"),
        "cambridge_audio" to Family("Cambridge Audio device"), "control4" to Family("Control4 system"),
        "deako" to Family("Deako device"), "devolo_home_control" to Family("devolo Home Control system"),
        "devolo_home_network" to Family("devolo network device"), "doorbird" to Family("DoorBird device"),
        "droplet" to Family("Droplet device"), "duco" to Family("DUCO device"),
        "e3dc" to Family("E3/DC energy system"), "eheimdigital" to Family("EHEIM Digital device"),
        "elmax" to Family("ELMAX system"), "energieleser" to Family("Energieleser device"),
        "escea" to Family("Escea device"), "flow_it" to Family("Flow IT service"),
        "frontier_silicon" to Family("Frontier Silicon audio device"), "gogogate2" to Family("Gogogate device"),
        "grandstream_home" to Family("Grandstream device"), "guardian" to Family("Guardian device"),
        "harman_luxury" to Family("Harman audio device"), "hdfury" to Family("HDfury device"),
        "hegel" to Family("Hegel device"), "hive" to Family("Hive system"),
        "homevolt" to Family("Homevolt device"), "hotspring" to Family("Hot Spring device"),
        "hr_energy_qube" to Family("HR Energy Qube device"), "hyperion" to Family("Hyperion service"),
        "imeon_inverter" to Family("IMEON energy system"), "indevolt" to Family("Indevolt device"),
        "indi_allsky" to Family("INDI AllSky service"), "iometer" to Family("IOmeter device"),
        "izone" to Family("iZone system"), "kaleidescape" to Family("Kaleidescape device"),
        "kiosker" to Family("Kiosker service"), "lametric" to Family("LaMetric device"),
        "lektrico" to Family("LEKTRI.CO charging system"), "libresync" to Family("LibreSync audio service"),
        "lookin" to Family("LOOKin device"), "loqed" to Family("LOQED device"),
        "lunatone" to Family("Lunatone device"), "lyngdorf" to Family("Lyngdorf device"),
        "motionmount" to Family("MotionMount device"), "my_pv" to Family("my-PV device"),
        "nam" to Family("Nettigo Air Monitor device"), "nrgkick" to Family("NRGkick device"),
        "onewire" to Family("1-Wire service"), "onkyo" to Family("Onkyo device"),
        "plugwise" to Family("Plugwise system"), "powerfox" to Family("powerfox device"),
        "powerfox_local" to Family("powerfox device"), "powersensor_au" to Family("PowerSensor device"),
        "prana" to Family("PRANA device"), "pure_energie" to Family("Pure Energie system"),
        "rabbitair" to Family("Rabbit Air device"), "russound_rio" to Family("Russound audio system"),
        "slide_local" to Family("Slide device"), "solarman" to Family("SOLARMAN device"),
        "songpal" to Family("Sony audio service"), "system_bridge" to Family("System Bridge service"),
        "systemnexa2" to Family("System Nexa device"), "tailwind" to Family("Tailwind device"),
        "technove" to Family("Technove device"), "trimlight" to Family("Trimlight device"),
        "upnp" to Family("UPnP network service"), "vegehub" to Family("VegeHub device"),
        "voice_support" to Family("Voice service"), "wattwaechter" to Family("Wattwaechter device"),
        "wilight" to Family("WiLight device"), "xbox" to Family("Xbox service")
    )
    fun family(domain: String): Family? = families[domain]
    internal fun reviewedFamilies(): Set<String> = families.keys
    private val genericServices = setOf("cast", "androidtv_remote", "homekit", "homekit_controller", "matter", "thread",
        "upnp", "dlna_dmr", "dlna_dms", "ipp", "voice_support")
    /** Generic capabilities must not hide a more specific, unambiguous ecosystem. */
    fun preferredFamily(domains: Collection<String>): Family? {
        val specific = domains.filterNot { it in genericServices }
        val selected = specific.ifEmpty { domains.toList() }
        if(selected.any { it !in families }) return null
        return selected.mapNotNull(::family).distinct().singleOrNull()
    }
    private val rulesByKind by lazy { HaMatchers.rules.groupBy { it.kind } }
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
        return rulesByKind[kind].orEmpty().asSequence().filter { it.conditions.all { c ->
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
