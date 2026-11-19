package io.github.kazands.networklens

/** Reviewed exact models, never prefix-based guesses. Sources: docs/reference/TP-LINK.md. */
internal object TpLinkModels {
    private data class Family(val series: String, val label: String, val category: DeviceCatalog.Category, val models: List<String>)
    private val families = listOf(
        Family("Kasa", "Smart plug", DeviceCatalog.Category.OUTLET,
            listOf("HS100", "HS103", "HS105", "HS110", "KP100", "KP105", "KP115", "KP125", "EP10", "EP25")),
        Family("Kasa", "Light switch", DeviceCatalog.Category.SWITCH, listOf("HS200", "HS210", "KS200", "KS200M", "KS205")),
        Family("Kasa", "Dimmer switch", DeviceCatalog.Category.SWITCH, listOf("HS220", "KS220", "KS220M", "KS225", "KS230")),
        Family("Tapo", "Smart plug", DeviceCatalog.Category.OUTLET,
            listOf("P100", "P100M", "P105", "P110", "P110M", "P115", "P125", "P125M")),
        Family("Tapo", "Light switch", DeviceCatalog.Category.SWITCH, listOf("S500", "S505")),
        Family("Tapo", "Dimmer switch", DeviceCatalog.Category.SWITCH, listOf("S500D", "S505D"))
    )
    private val pattern = Regex("(?:TP-LINK )?(?:(KASA|TAPO) )?([A-Z]+[0-9]+[A-Z]*)( KIT)?(?:\\([A-Z]{2,3}\\))?")
    fun sameModel(id: String, model: String): Boolean = lookup(model)?.id == id
    fun lookup(model: String): DeviceCatalog.Product? {
        val match = pattern.matchEntire(model.uppercase(java.util.Locale.ROOT)) ?: return null
        val code = match.groupValues[2]
        val family = families.singleOrNull { code in it.models } ?: return null
        if (match.groupValues[1].isNotEmpty() && !match.groupValues[1].equals(family.series, true) ||
            match.groupValues[3].isNotEmpty() && code !in setOf("HS210", "KS230")) return null
        return DeviceCatalog.Product("tplink:${DeviceCatalog.key(code)}", "TP-Link", "TP-Link ${family.series} $code",
            family.category, setOf(DeviceCatalog.key(code)), manufacturerRequired = true, displayType = family.label, titleSource = DeviceCatalog.TitleSource.CATALOGUE)
    }
}
