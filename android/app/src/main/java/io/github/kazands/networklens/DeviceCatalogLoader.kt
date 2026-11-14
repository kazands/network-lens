package io.github.kazands.networklens

import android.content.Context
import java.io.IOException

/** Load bundled public facts on the presentation worker; never query a cloud service. */
internal object DeviceCatalogLoader {
    @Volatile private var ready = false
    @Synchronized fun load(context: Context) {
        if (ready) return
        val index = try { context.resources.openRawResource(R.raw.product_models).use { raw -> java.util.zip.GZIPInputStream(raw).use(ProductModelIndex::read) } }
            catch (_: IOException) { ProductModelIndex.EMPTY }
            catch (_: RuntimeException) { ProductModelIndex.EMPTY }
        DeviceCatalog.install(index)
        ready = true
    }
}
