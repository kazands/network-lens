package io.github.kazands.networklens

import android.content.Context
import java.io.IOException

/** One immutable bundled snapshot per process; load on a worker. */
class MacVendors private constructor() {
    companion object {
        @Volatile private var index = VendorIndex.EMPTY
        @Volatile private var ready = false

        @JvmStatic @Synchronized fun load(context: Context) {
            if (ready) return
            try {
                context.resources.openRawResource(R.raw.ieee_mac_vendors).use { index = VendorIndex.read(it) }
            } catch (_: IOException) { index = VendorIndex.EMPTY }
            catch (_: RuntimeException) { index = VendorIndex.EMPTY }
            ready = true
        }
        @JvmStatic fun ready(): Boolean = ready
        @JvmStatic fun lookup(mac: String?): String = index.lookup(mac)
        @JvmStatic fun snapshot(): Long = index.snapshot
    }
}

