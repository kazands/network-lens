package io.github.kazands.networklens

/** Public REST identity only. No WebSocket, pairing token, keys or application commands. */
internal object SamsungInfo {
    fun parse(body: ByteArray): LocalMetadata.Report? {
        val fields = BoundedJson.paths(body, setOf(
            listOf("device", "type"), listOf("device", "modelName"), listOf("device", "name"),
            listOf("device", "firmwareVersion"))) ?: return null
        if (fields[listOf("device", "type")] != "Samsung SmartTV") return null
        val model = SafeText.label(fields[listOf("device", "modelName")], 160)
        if (!IdentityHints.specific(model)) return null
        return LocalMetadata.Report(mapOf("Model" to model,
            "Name" to SafeText.label(fields[listOf("device", "name")], 160), "Device type" to "Samsung television",
            "Firmware" to SafeText.label(fields[listOf("device", "firmwareVersion")], 160)))
    }
}
