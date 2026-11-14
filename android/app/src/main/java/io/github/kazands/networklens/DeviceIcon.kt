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
        private val SURVEILLANCE = Spec(R.drawable.ic_device_cctv, "Video surveillance device")
        private val UNKNOWN = Spec(R.drawable.ic_device_network, "Device type unknown")
        private val LIGHT = Spec(R.drawable.ic_device_lightbulb, "HomeKit light")
        private val LOCK = Spec(R.drawable.ic_device_lock, "HomeKit lock")
        private val OUTLET = Spec(R.drawable.ic_device_plug, "HomeKit outlet")
        private val SWITCH = Spec(R.drawable.ic_device_switch, "HomeKit switch")
        private val THERMOSTAT = Spec(R.drawable.ic_device_thermostat, "HomeKit thermostat")
        private val SENSOR = Spec(R.drawable.ic_device_sensor, "HomeKit sensor")
        private val DOORBELL = Spec(R.drawable.ic_device_doorbell, "HomeKit video doorbell")
        private val FAN = Spec(R.drawable.ic_device_fan, "HomeKit fan")
        private val LAPTOP = Spec(R.drawable.ic_device_laptop, "Locally recognized laptop")
        private val DESKTOP = Spec(R.drawable.ic_device_monitor, "Locally recognized desktop computer")
        private val TABLET = Spec(R.drawable.ic_device_tablet, "Locally recognized tablet")
        private val SMARTPHONE = Spec(R.drawable.ic_device_smartphone, "Locally recognized smartphone")
        private val SMART_SPEAKER = Spec(R.drawable.ic_device_speaker, "Locally recognized smart speaker")
        private val TV_PLAYER = Spec(R.drawable.ic_device_tv_player, "Locally recognized TV media player")
        private val HANDHELD = Spec(R.drawable.ic_device_smartphone, "Locally recognized portable media player")
        private val RECORDER = Spec(R.drawable.ic_device_recorder, "Network video recorder")
        private val CONSOLE = Spec(R.drawable.ic_device_game_console, "Game console")

        internal fun categoryLabel(group: Device): String = when (val label = forGroup(group).label) {
            "This phone" -> "This phone"
            "Router" -> "Wi-Fi gateway"
            "Device type unknown" -> ""
            "Audio service" -> "Audio device"
            "Casting service" -> "Casting device"
            "File sharing" -> "File-sharing service"
            else -> label
        }

        @JvmStatic fun forGroup(group: Device): Spec {
            val endpoints = group.endpoints()
            if (endpoints.any { it.self }) return PHONE
            if (endpoints.any { it.gateway }) return ROUTER
            val product = group.groupProduct()?.product
            if (product?.category == DeviceCatalog.Category.RECORDER) return RECORDER
            if (endpoints.any { it.kind.lowercase(Locale.ROOT).contains("surveillance") }) return SURVEILLANCE
            for (d in endpoints) when (d.kind) {
                "HomeKit light" -> return LIGHT
                "HomeKit lock" -> return LOCK
                "HomeKit outlet" -> return OUTLET
                "HomeKit switch" -> return SWITCH
                "HomeKit thermostat" -> return THERMOSTAT
                "HomeKit heater", "HomeKit air conditioner" -> return Spec(R.drawable.ic_device_thermostat, d.kind)
                "HomeKit sensor" -> return SENSOR
                "HomeKit video doorbell" -> return DOORBELL
                "HomeKit camera" -> return Spec(R.drawable.ic_device_cctv, d.kind)
                "HomeKit fan" -> return FAN
            }
            product?.let {
                val specific = when (it.category) {
                    DeviceCatalog.Category.LAPTOP -> LAPTOP
                    DeviceCatalog.Category.DESKTOP -> DESKTOP
                    DeviceCatalog.Category.TABLET -> TABLET
                    DeviceCatalog.Category.PHONE -> SMARTPHONE
                    DeviceCatalog.Category.SPEAKER -> SMART_SPEAKER
                    DeviceCatalog.Category.TV_PLAYER -> TV_PLAYER
                    DeviceCatalog.Category.HANDHELD -> HANDHELD
                    DeviceCatalog.Category.RECORDER -> RECORDER
                    DeviceCatalog.Category.CONSOLE -> CONSOLE
                    DeviceCatalog.Category.UNSPECIFIED -> null
                }
                if (specific != null) return specific
            }
            for (d in endpoints) {
                val kind = d.kind.lowercase(Locale.ROOT)
                val services = d.services.joinToString(" ").lowercase(Locale.ROOT)
                if (kind.contains("printer") || services.contains("_ipp.") || services.contains("_ipps.") || services.contains("_printer.")) return PRINTER
                if (kind.contains("audio") || services.contains("_raop.") || services.contains("_spotify-connect.")) return AUDIO
                if (kind.contains("cast") || kind.contains("airplay") || services.contains("_googlecast.") || services.contains("_airplay.")) return CAST
                if (kind.contains("file-sharing") || services.contains("_smb.")) return FILES
            }
            for (d in endpoints) {
                if (d.kind.startsWith("HomeKit ") || d.kind.startsWith("Matter ")) return Spec(R.drawable.ic_device_network, d.kind)
                if (d.kind == "Thread border router service") return Spec(R.drawable.ic_device_router, d.kind)
            }
            return UNKNOWN
        }
    }
}

