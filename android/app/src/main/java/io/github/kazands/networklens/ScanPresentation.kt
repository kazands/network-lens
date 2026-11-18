package io.github.kazands.networklens

/** Worker-to-UI value boundary. Neither input mutation nor a consumer can modify a retained snapshot. */
internal class ScanPresentation private constructor(private val endpoints: List<Device>, private val grouped: List<Device>) {
    fun devices(): List<Device> = endpoints.map { it.copy() }
    fun groups(): List<Device> = grouped.map { it.copy() }
    companion object {
        fun capture(devices: List<Device>): ScanPresentation {
            val owned = devices.map { it.copy() }
            return ScanPresentation(owned, Device.grouped(owned))
        }
    }
}
