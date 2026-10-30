package io.github.kazands.networklens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Foreground SSID observation; no coordinates or persisted network names. */
class WifiName(private val context: Context, private val changed: Runnable) {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val names = LinkedHashMap<Network, String>()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var generation = 0

    fun allowed(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun start() {
        if (callback != null || !allowed()) return
        val token = ++generation
        val next = if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, cap: NetworkCapabilities) { update(network, cap, token) }
                override fun onLost(network: Network) { lost(network, token) }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, cap: NetworkCapabilities) { update(network, cap, token) }
                override fun onLost(network: Network) { lost(network, token) }
            }
        }
        callback = next
        try {
            manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), next, Handler(Looper.getMainLooper()))
        } catch (_: RuntimeException) { callback = null; names.clear() }
    }

    private fun update(network: Network, cap: NetworkCapabilities, token: Int) {
        if (callback == null || generation != token || !allowed() || !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            !cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return
        var value = ""
        if (Build.VERSION.SDK_INT >= 29) (cap.transportInfo as? WifiInfo)?.let { value = clean(it.ssid) }
        if (names.size >= 8 && !names.containsKey(network)) names.remove(names.keys.first())
        names[network] = value
        changed.run()
    }
    private fun lost(network: Network, token: Int) {
        if (callback == null || generation != token) return
        names.remove(network)
        changed.run()
    }

    fun get(network: Network?): String {
        if (callback == null || network == null || !allowed()) return ""
        if (Build.VERSION.SDK_INT < 29) return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            clean(wifi.connectionInfo.ssid)
        } catch (_: RuntimeException) { "" }
        return names[network] ?: ""
    }

    fun stop() {
        ++generation
        val prior = callback
        callback = null
        names.clear()
        if (prior != null) try { manager.unregisterNetworkCallback(prior) } catch (_: RuntimeException) {}
    }

    companion object {
        @JvmStatic fun clean(raw: String?): String {
            if (raw == null || raw == WifiManager.UNKNOWN_SSID) return ""
            val value = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\""))
                raw.substring(1, raw.length - 1) else raw
            return SafeText.label(value, 64)
        }
    }
}

