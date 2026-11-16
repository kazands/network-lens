package io.github.kazands.networklens

import java.util.Locale

/** Pictograms describe reported roles, never ownership or safety. */
class DeviceIcon private constructor() {
    class Spec internal constructor(@JvmField val drawable: Int, @JvmField val label: String)

    companion object {
        private val PHONE = Spec(R.drawable.ic_device_smartphone, "This phone")
        private val ROUTER = Spec(R.drawable.ic_device_router, "Router")
        private val PRINTER = Spec(R.drawable.ic_device_printer, "Printer service")
        private val AUDIO = Spec(R.drawable.ic_device_speaker, "Audio service")
        private val CAST = Spec(R.drawable.ic_device_cast, "Casting service")
        private val FILES = Spec(R.drawable.ic_device_file_share, "File sharing")
        private val UNKNOWN = Spec(R.drawable.ic_device_network, "Device type unknown")
        private val LAPTOP = Spec(R.drawable.ic_device_laptop, "Locally recognized laptop")
        private val DESKTOP = Spec(R.drawable.ic_device_monitor, "Locally recognized desktop computer")
        private val TABLET = Spec(R.drawable.ic_device_tablet, "Locally recognized tablet")
        private val SMARTPHONE = Spec(R.drawable.ic_device_smartphone, "Locally recognized smartphone")
        private val SMART_SPEAKER = Spec(R.drawable.ic_device_speaker, "Locally recognized smart speaker")
        private val TV_PLAYER = Spec(R.drawable.ic_device_tv_player, "Locally recognized TV media player")
        private val HANDHELD = Spec(R.drawable.ic_device_smartphone, "Locally recognized portable media player")
        private val RECORDER = Spec(R.drawable.ic_device_recorder, "Network video recorder")
        private val CONSOLE = Spec(R.drawable.ic_device_game_console, "Game console")
        private val RECOGNIZED_THERMOSTAT = Spec(R.drawable.ic_device_thermostat, "Thermostat")

        internal fun categoryLabel(group: Device): String {
            if (!group.endpoints().any { it.self || it.gateway }) group.classification().category?.let { return it.label }
            return when (val label = forGroup(group).label) {
                "This phone" -> "This phone"
                "Router" -> "Wi-Fi gateway"
                "Device type unknown" -> ""
                "Casting service" -> "Casting device"
                "File sharing" -> "File-sharing service"
                else -> label
            }
        }

        @JvmStatic fun forGroup(group: Device): Spec {
            val endpoints = group.endpoints()
            if (endpoints.any { it.self }) return PHONE
            if (endpoints.any { it.gateway }) return ROUTER
            val classification = group.classification()
            if (classification.conflicted) return Spec(R.drawable.ic_device_network, "Conflicting device types")
            classification.category?.let { category ->
                return when (category) {
                    DeviceCatalog.Category.LAPTOP -> LAPTOP
                    DeviceCatalog.Category.DESKTOP -> DESKTOP
                    DeviceCatalog.Category.TABLET -> TABLET
                    DeviceCatalog.Category.PHONE -> SMARTPHONE
                    DeviceCatalog.Category.SPEAKER -> if (group.groupRecognition() != null) SMART_SPEAKER else Spec(R.drawable.ic_device_speaker, "Speaker")
                    DeviceCatalog.Category.TV_PLAYER -> TV_PLAYER
                    DeviceCatalog.Category.HANDHELD -> HANDHELD
                    DeviceCatalog.Category.RECORDER -> RECORDER
                    DeviceCatalog.Category.CONSOLE -> CONSOLE
                    DeviceCatalog.Category.THERMOSTAT -> RECOGNIZED_THERMOSTAT
                    DeviceCatalog.Category.TV -> Spec(R.drawable.ic_device_monitor, category.label)
                    DeviceCatalog.Category.CAMERA -> Spec(R.drawable.ic_device_cctv, category.label)
                    DeviceCatalog.Category.DOORBELL -> Spec(R.drawable.ic_device_doorbell, category.label)
                    DeviceCatalog.Category.LIGHT -> Spec(R.drawable.ic_device_lightbulb, category.label)
                    DeviceCatalog.Category.LOCK -> Spec(R.drawable.ic_device_lock, category.label)
                    DeviceCatalog.Category.OUTLET -> Spec(R.drawable.ic_device_plug, category.label)
                    DeviceCatalog.Category.SWITCH -> Spec(R.drawable.ic_device_switch, category.label)
                    DeviceCatalog.Category.SENSOR -> Spec(R.drawable.ic_device_sensor, category.label)
                    DeviceCatalog.Category.FAN, DeviceCatalog.Category.AIR_PURIFIER -> Spec(R.drawable.ic_device_fan, category.label)
                    DeviceCatalog.Category.HEATER, DeviceCatalog.Category.AIR_CONDITIONER,
                    DeviceCatalog.Category.HUMIDIFIER, DeviceCatalog.Category.DEHUMIDIFIER -> Spec(R.drawable.ic_device_thermostat, category.label)
                    DeviceCatalog.Category.AMPLIFIER, DeviceCatalog.Category.AUDIO_RECEIVER -> Spec(R.drawable.ic_device_speaker, category.label)
                    DeviceCatalog.Category.ROUTER, DeviceCatalog.Category.RANGE_EXTENDER -> Spec(R.drawable.ic_device_router, category.label)
                    DeviceCatalog.Category.SECURITY_SYSTEM -> Spec(R.drawable.ic_device_lock, category.label)
                    DeviceCatalog.Category.BRIDGE, DeviceCatalog.Category.GARAGE_DOOR, DeviceCatalog.Category.DOOR,
                    DeviceCatalog.Category.WINDOW, DeviceCatalog.Category.WINDOW_COVERING, DeviceCatalog.Category.SPRINKLER,
                    DeviceCatalog.Category.FAUCET, DeviceCatalog.Category.SHOWER, DeviceCatalog.Category.REMOTE -> Spec(R.drawable.ic_device_network, category.label)
                    DeviceCatalog.Category.UNSPECIFIED -> UNKNOWN
                }
            }
            // Service fallback is deterministic across endpoints, never a physical type claim.
            val kinds = endpoints.map { it.kind.lowercase(Locale.ROOT) }
            val services = endpoints.flatMap { it.services }.map { it.lowercase(Locale.ROOT) }
            if (kinds.any { it.contains("printer") } || services.any { it in listOf("_ipp._tcp.", "_ipps._tcp.", "_printer._tcp.") }) return PRINTER
            if (kinds.any { it.contains("audio") } || services.any { it in listOf("_raop._tcp.", "_spotify-connect._tcp.") }) return AUDIO
            if (kinds.any { it.contains("cast") || it.contains("airplay") } || services.any { it in listOf("_googlecast._tcp.", "_airplay._tcp.") }) return CAST
            if (kinds.any { it.contains("file-sharing") } || "_smb._tcp." in services) return FILES
            if ("_companion-link._tcp." in services) return Spec(R.drawable.ic_device_network, "Apple companion service")
            if (AppleDeviceInfo.SERVICE.lowercase(Locale.ROOT) in services) return Spec(R.drawable.ic_device_network, AppleDeviceInfo.SERVICE)
            if ("cast speaker group" in services) return Spec(R.drawable.ic_device_network, "Speaker group service")
            val accessory = endpoints.map { it.kind }.sorted().firstOrNull { it.startsWith("HomeKit ") || it.startsWith("Matter ") }
            if (accessory != null) return Spec(R.drawable.ic_device_network, accessory)
            if (endpoints.any { it.kind == "Thread border router service" }) return Spec(R.drawable.ic_device_router, "Thread border router service")
            return UNKNOWN
        }
    }
}
