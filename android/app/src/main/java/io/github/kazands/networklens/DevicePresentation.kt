package io.github.kazands.networklens

/** Identity-only display choices; original names/models remain in endpoint details and search. */
internal object DevicePresentation {
    data class Labels(val title: String, val subtitle: String)
    fun labels(name: String, hostname: String, model: String, role: String, product: DeviceCatalog.Product?,
               self: Boolean, gateway: Boolean): Labels {
        val clean = SafeText.label(name, 160)
        if (self) return Labels(clean.ifEmpty { "This Android device" }, "This phone")
        if (gateway) return Labels(clean.ifEmpty { "Router" }, "Wi-Fi gateway")
        val networkName = reportedName(clean, hostname)
        if (product != null) {
            val custom = networkName.takeIf { IdentityHints.specificName(it) && !product.redundantName(it) }.orEmpty()
            val category = if (product.category == DeviceCatalog.Category.UNSPECIFIED) role else product.displayType.ifEmpty { product.category.label }
            return Labels(product.title, listOf(category, custom).filter { it.isNotEmpty() }.joinToString(" · "))
        }
        val named = networkName.takeIf(IdentityHints::specificName)
        val reportedModel = model.takeIf(IdentityHints::specific)
        val title = named ?: reportedModel ?: "Unidentified device"
        return Labels(title, role.ifEmpty { when {
            named != null -> "Reported device name"
            reportedModel != null -> "Reported model"
            else -> ""
        } })
    }
    private fun reportedName(name: String, hostname: String): String {
        if (hostname.isEmpty() || !name.equals(hostname, true) || !name.endsWith(".local", true)) return name
        val host = name.dropLast(6)
        if (!host.matches(Regex("[a-zA-Z0-9_-]{1,63}"))) return name
        val words = host.replace('-', ' ')
        // Typography only: this does not create a product, brand or hardware category.
        return if (words.startsWith("thinkstation ", true)) "ThinkStation " + words.substring(13)
            else words.replaceFirstChar { it.titlecase() }
    }
}
