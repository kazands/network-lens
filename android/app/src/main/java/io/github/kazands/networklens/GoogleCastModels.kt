package io.github.kazands.networklens

/** Exact first-party product names, not a manufacturer inference from the Cast service.
 * Public names and physical types: docs/reference/DEVICE-IDENTITIES.md#google-cast-models.
 * Missing reported fields stay missing; ambiguous generations retain a family title. */
internal object GoogleCastModels {
    private fun product(id: String, title: String, category: DeviceCatalog.Category,
                        vararg aliases: String, family: Boolean = false) = DeviceCatalog.Product(
        "google:$id", "Google", title, category,
        (listOf(title) + aliases).mapTo(HashSet(), DeviceCatalog::key), familyOnly = family, titleSource = DeviceCatalog.TitleSource.CATALOGUE)

    private val products = listOf(
        product("nest-mini", "Google Nest Mini", DeviceCatalog.Category.SPEAKER,
            "Nest Mini", "Google Nest Mini (2nd gen)"),
        product("nest-audio", "Google Nest Audio", DeviceCatalog.Category.SPEAKER, "Nest Audio"),
        product("home", "Google Home", DeviceCatalog.Category.SPEAKER),
        product("home-mini", "Google Home Mini", DeviceCatalog.Category.SPEAKER,
            "Google Home Mini (1st gen)"),
        product("home-max", "Google Home Max", DeviceCatalog.Category.SPEAKER),
        product("home-speaker", "Google Home Speaker", DeviceCatalog.Category.SPEAKER,
            "Google Home Speaker (2026)"),
        product("nest-hub", "Google Nest Hub", DeviceCatalog.Category.SMART_DISPLAY,
            "Nest Hub", "Google Home Hub", "Google Nest Hub (2nd gen)", "Nest Hub (2nd gen)", family = true),
        product("nest-hub-max", "Google Nest Hub Max", DeviceCatalog.Category.SMART_DISPLAY, "Nest Hub Max"),
        product("chromecast", "Google Chromecast", DeviceCatalog.Category.TV_PLAYER,
            "Chromecast", family = true),
        product("chromecast-ultra", "Google Chromecast Ultra", DeviceCatalog.Category.TV_PLAYER, "Chromecast Ultra"),
        product("chromecast-google-tv", "Google Chromecast with Google TV", DeviceCatalog.Category.TV_PLAYER,
            "Chromecast with Google TV", family = true),
        product("tv-streamer", "Google TV Streamer", DeviceCatalog.Category.TV_PLAYER,
            "Google TV Streamer (4K)", family = true),
        product("chromecast-audio", "Google Chromecast Audio", DeviceCatalog.Category.AUDIO_RECEIVER, "Chromecast Audio"),
        product("nest-wifi-point", "Google Nest Wifi Point", DeviceCatalog.Category.ACCESS_POINT, "Nest Wifi Point"),
        product("pixel-tablet", "Google Pixel Tablet", DeviceCatalog.Category.TABLET, "Pixel Tablet")
    )
    // Reject an accidentally ambiguous alias rather than selecting a product by table order.
    private val byModel = products.flatMap { product -> product.models.map { it to product } }
        .groupBy({ it.first }, { it.second })

    fun lookup(model: String): DeviceCatalog.Product? =
        if (model.length in 1..160) byModel[DeviceCatalog.key(model)]?.singleOrNull() else null
}
