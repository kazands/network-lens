package io.github.kazands.networklens

import android.content.Context
import android.content.res.Configuration

/** Process-local state; no app data or preferences are written to storage. */
class Store(c: Context) {
    private val context: Context = c.applicationContext

    init { synchronized(DATA) { if (!legacyRemoved) migrate() } }
    fun token(): Long = DATA.token()
    fun isCurrent(token: Long): Boolean = DATA.isCurrent(token)

    /** Delete the old preference file and backup in both storage domains, without a disk marker. */
    fun migrate(): Boolean = synchronized(DATA) {
        val credential = try { context.deleteSharedPreferences("network_lens") } catch (_: RuntimeException) { false }
        val device = try { context.createDeviceProtectedStorageContext().deleteSharedPreferences("network_lens") } catch (_: RuntimeException) { false }
        legacyRemoved = credential && device
        legacyRemoved
    }
    fun ready(): Boolean = synchronized(DATA) { legacyRemoved }

    fun devices(key: String): MutableList<Device> = synchronized(DATA) {
        val copy = ArrayList<Device>()
        if (network == key) for (d in inventory) copy.add(d.copy())
        copy
    }
    fun startScanIfCurrent(token: Long): Boolean = ready() && DATA.writeIfCurrent(token, Runnable { empty() })
    fun retainIfCurrent(token: Long, key: String, found: List<Device>, complete: Boolean): Boolean =
        retainIfCurrent(token, key, found, ScanReport("", complete, "", 0, 0, System.currentTimeMillis()))

    fun retainIfCurrent(token: Long, key: String, found: List<Device>, report: ScanReport): Boolean =
        DATA.writeIfCurrent(token, Runnable {
            val copy = ArrayList<Device>()
            for (d in found) {
                if (copy.size >= 1200) break
                copy.add(d.copy())
            }
            inventory = copy
            latest = report
            network = key
        })

    fun report(key: String): ScanReport? = synchronized(DATA) { if (network == key) latest else null }
    fun belongsToOtherNetwork(key: String): Boolean = synchronized(DATA) { network.isNotEmpty() && network != key }
    fun connection(): NetworkState? = synchronized(DATA) { connection }
    fun observeConnection(value: NetworkState?) { synchronized(DATA) { connection = value } }
    fun pruneRetiredSelf(current: NetworkState) { synchronized(DATA) {
        if (network == current.key) inventory = inventory.filter { !it.self || current.owns(it.ip) }.map { it.copy() }
    } }
    /** Fence an invalidated scope's callbacks while retaining only unaffected observations. */
    fun replaceScope(key: String, kept: List<Device>, report: ScanReport) {
        DATA.invalidateAndClear(Runnable { network = key; inventory = kept.map { it.copy() }; latest = report })
    }
    fun last(key: String): Long = synchronized(DATA) { if (network == key) latest?.finished ?: 0 else 0 }
    fun dark(): Boolean = synchronized(DATA) {
        appearance ?: ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    }
    fun dark(value: Boolean) { synchronized(DATA) { appearance = value } }
    fun clear() { DATA.invalidateAndClear(Runnable { empty() }) }

    companion object {
        private val DATA = DataGuard()
        private var network = ""
        private var connection: NetworkState? = null
        private var inventory: List<Device> = emptyList()
        private var latest: ScanReport? = null
        private var appearance: Boolean? = null
        private var legacyRemoved = false
        private fun empty() { inventory = emptyList(); latest = null; network = "" }
    }
}

