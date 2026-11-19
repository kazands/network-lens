package io.github.kazands.networklens

/** Scan-local diagnostic observations, independent of recognition/display admission. Never raw payloads. */
internal class DiagnosticEvidence {
    data class Entry(val source: String, val field: String, val value: String, val disposition: String)
    private val entries = linkedSetOf<Entry>()
    private var omitted = 0L
    @Synchronized fun record(source: String, field: String, value: String?, disposition: String = "Returned observation; not a recognition decision") {
        if (value.isNullOrEmpty()) return
        val limited = source.length > 128 || field.length > 128 || value.length > 256
        val entry = Entry(SafeText.label(source, 128), SafeText.label(field, 128), SafeText.label(value, 256),
            disposition + if (limited) "; text shortened by diagnostic limit" else "")
        if (entry in entries) return
        if (entries.size < LIMIT) entries.add(entry) else omitted = (omitted + 1).coerceAtMost(Long.MAX_VALUE - 1)
    }
    @Synchronized fun snapshot() = entries.toList()
    @Synchronized fun omissions() = omitted
    @Synchronized fun copy() = DiagnosticEvidence().also { it.entries.addAll(entries); it.omitted = omitted }

    fun txt(source: String, values: Map<String, ByteArray>) {
        for ((key, bytes) in values) {
            val safe = key.lowercase() in TXT_PUBLIC
            val value = if (safe) bytes.toString(Charsets.UTF_8) else "[value withheld; ${bytes.size} bytes]"
            record(source, "TXT " + fieldName(key), value, if (safe) "Returned TXT; interpretation is listed separately" else "Unreviewed or identifying TXT value withheld")
        }
    }
    fun json(source: String, body: ByteArray, publicPaths: Set<String>) {
        val tree = BoundedJson.document(body)
        if (tree == null) { record(source, "Response capture", "JSON unsupported, invalid or over parser limits; ${body.size} bytes; body not retained"); return }
        fun visit(value: BoundedJson.Value, path: List<String>) {
            val label = path.joinToString("/") { fieldName(it) }
            when (value) {
                is BoundedJson.ObjectValue -> value.values.forEach { (key, child) -> visit(child, path + key) }
                is BoundedJson.ArrayValue -> record(source, label, "[${value.values.size} array items; values not retained]", "Array content omitted")
                else -> {
                    val text = when (value) {
                        is BoundedJson.Scalar -> value.text
                        is BoundedJson.BooleanValue -> value.value.toString()
                        else -> "null"
                    }
                    val keep = path.joinToString("/") in publicPaths
                    record(source, label, if (keep) text.ifEmpty { "[empty]" } else "[value withheld; ${text.length} characters]",
                        if (keep) "Returned field; interpretation is listed separately" else "Unreviewed or private value withheld")
                }
            }
        }
        visit(tree, emptyList())
    }
    fun xml(source: String, body: ByteArray) {
        val tree = BoundedXml.parse(body)
        if (tree == null) { record(source, "Response capture", "XML unsupported, invalid or over parser limits; ${body.size} bytes; body not retained"); return }
        fun visit(node: BoundedXml.Node, path: String) {
            val here = path + "/" + fieldName(node.name)
            // Only description fields are retained, never URLs, IDs, arbitrary XML attributes or tokens.
            node.scalar()?.let { value -> if (value.isNotBlank()) record(source, here,
                if (node.name in XML_PUBLIC) value else "[value withheld; ${value.length} characters]",
                "Returned XML; child/service identity is not host identity") }
            for ((key, value) in node.attrs) record(source, here + "/@" + fieldName(key), "[value withheld; ${value.length} characters]", "XML attribute withheld")
            node.children.forEach { visit(it, here) }
        }
        visit(tree, "")
    }
    companion object {
        const val LIMIT = 128
        private val KEY = Regex("[A-Za-z_][A-Za-z0-9_.:-]{0,63}")
        fun fieldName(key: String) = key.takeIf { KEY.matches(it) } ?: "[unusual field name withheld]"
        private val TXT_PUBLIC = setOf("model", "md", "ty", "product", "usb_mdl", "mdl", "manufacturer", "usb_mfg", "mfg", "am", "rpmd", "mn", "vn", "fv", "fwversion", "firmware", "osvers", "os", "version", "board", "platform", "ca", "sf", "ci", "ff", "ft", "features", "flags", "pv", "srcvers", "protovers", "vp", "dt")
        private val XML_PUBLIC = setOf("manufacturer", "modelName", "modelNumber", "modelDescription", "deviceType", "serviceType", "MakeAndModel", "Manufacturer", "ModelName", "Version")
        fun paths(kind: LocalMetadata.Kind): Set<String> = when (kind) {
            LocalMetadata.Kind.CAST -> setOf("device_info/model_name", "device_info/manufacturer")
            LocalMetadata.Kind.SAMSUNG -> setOf("device/type", "device/modelName", "device/model", "device/firmwareVersion", "device/OS", "device/resolution", "device/TokenAuthSupport", "device/VoiceSupport")
            LocalMetadata.Kind.SHELLY -> setOf("model", "type", "ver", "fw", "gen", "app", "auth", "auth_en")
            LocalMetadata.Kind.WLED -> setOf("ver", "vid", "arch", "brand", "product", "uptime")
            LocalMetadata.Kind.SPOTIFY -> setOf("modelDisplayName", "brandDisplayName", "deviceType", "status", "spotifyError", "version", "libraryVersion", "accountReq")
            else -> emptySet()
        }
    }
}
