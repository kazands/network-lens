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
        val hardware = try { context.resources.openRawResource(R.raw.android_hardware).use(AndroidHardwareIndex::read) }
            catch (_: IOException) { AndroidHardwareIndex.EMPTY }
            catch (_: RuntimeException) { AndroidHardwareIndex.EMPTY }
        DeviceCatalog.installHardware(hardware)
        MatterInfo.index = try { context.resources.openRawResource(R.raw.matter_models).use(MatterInfo.Index::read) }
            catch (_: IOException) { MatterInfo.Index.EMPTY }
            catch (_: RuntimeException) { MatterInfo.Index.EMPTY }
        ready = true
    }
}
