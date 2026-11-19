package io.github.kazands.networklens

/** Only the public ScannerCapabilities document, never jobs, images or admin URLs. */
internal object EsclInfo {
    const val MODEL = "eSCL scanner model"
    const val MANUFACTURER = "eSCL scanner manufacturer"
    private const val SCAN = "http://schemas.hp.com/imaging/escl/2011/05/03"
    private const val PWG = "http://www.pwg.org/schemas/2010/12/sm"
    fun applies(type: String): Boolean = LocalServices.canonicalType(type) in setOf("_uscan._tcp.", "_uscans._tcp.")
    fun parse(body: ByteArray): LocalMetadata.Report? {
        val root=BoundedXml.parse(body) ?: return null
        if(root.name!="ScannerCapabilities" || root.ns!=SCAN) return null
        fun value(name:String):String? {
            val nodes=root.children.filter {it.name==name && it.ns==PWG}
            if(nodes.size>1)return null
            val raw=nodes.singleOrNull()?.scalar() ?: return if(nodes.isEmpty()) "" else null
            if(raw.length>160 || raw.any {it.isISOControl()})return null
            return SafeText.label(raw,160)
        }
        val version=value("Version") ?: return null
        if(version !in setOf("2.0","2.1","2.5","2.6","2.63"))return null
        val model=value("MakeAndModel") ?: return null
        val manufacturer=value("Manufacturer") ?: return null
        if(!IdentityHints.specific(model))return null
        // An eSCL server can run on a computer sharing an attached scanner. These
        // values must never enter the host's catalogue or conflict evidence.
        return LocalMetadata.Report(details = mapOf(MODEL to model, MANUFACTURER to manufacturer,
            "Scanning identity" to "Reported by the eSCL service; may describe a scanner shared by this host").filterValues {it.isNotEmpty()})
    }
}
