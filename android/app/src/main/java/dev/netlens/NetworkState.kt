package dev.netlens

import android.content.Context
import android.net.*
import android.os.Handler
import android.os.Looper
import android.os.Build
import java.net.*
import java.util.TreeSet
import java.util.UUID

/** Only observed in-process Wi-Fi sessions can own current results. */
class NetworkState {
    @JvmField var network: Network? = null
    @JvmField var ip = ""
    @JvmField var gateway = ""
    @JvmField var iface = ""
    @JvmField var key = ""
    @JvmField var dns = ""
    @JvmField var prefix = 0
    @JvmField val addresses: MutableList<String> = ArrayList()
    @JvmField val prefixes: MutableMap<String, Int> = LinkedHashMap()
    private data class Scope(val prefixes: Map<String, Int>, val networks: Set<String>, val own: Set<String>, val routes: Ipv6Routes)
    @Volatile private var liveScope: Scope? = null

    private fun snapshot(): Scope = liveScope ?: snapshot(prefixes, addresses)

    fun sameConnection(other: NetworkState?): Boolean =
        other != null && network == other.network && key == other.key

    fun sameScope(other: NetworkState?): Boolean =
        sameConnection(other) && snapshot().networks == other!!.snapshot().networks && snapshot().routes.same(other.snapshot().routes)

    /** Additions and privacy-address rotation preserve existing work; withdrawal does not. */
    fun retainsScope(other: NetworkState?): Boolean =
        sameConnection(other) && other!!.snapshot().networks.containsAll(snapshot().networks) && snapshot().routes.retains(other.snapshot().routes)

    @Synchronized fun refresh(other: NetworkState): Boolean {
        if (!retainsScope(other)) return false
        liveScope = other.snapshot()
        return true
    }

    fun contains(device: Device): Boolean {
        val address = Address.bytes(device.ip)?.let { InetAddress.getByAddress(it) } ?: return false
        return admitted(address) && (!device.self || owns(device.ip))
    }
    fun owns(ip: String): Boolean = snapshot().own.contains(numeric(ip))
    fun routed(address: InetAddress): Boolean = !local(address) && snapshot().routes.gateway(address) != null
    fun admitted(address: InetAddress): Boolean = local(address) || routed(address)

    fun local(address: InetAddress?): Boolean {
        if (address == null || address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return false
        val value = address.hostAddress!!
        for ((local, prefix) in snapshot().prefixes) if (Address.inPrefix(value, local, prefix)) return true
        return false
    }

    fun scoped(address: InetAddress): String {
        var value = address.hostAddress!!
        if (address is Inet6Address) {
            value = value.javaSplit("%", 2)[0]
            if (address.isLinkLocalAddress) value += "%" + iface
        }
        return value
    }

    fun check(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(network)
        val properties = manager.getLinkProperties(network)
        if (properties == null || capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            throw IllegalStateException("Wi-Fi disconnected. Start a new scan after reconnecting.")
        }
        if (!retainsScope(fromProperties(context, network!!, properties))) {
            throw IllegalStateException("Wi-Fi addressing changed. Start a new scan.")
        }
    }

    companion object {
        private val sessions = HashMap<Network, String>()
        private var sessionObserver: ConnectivityManager.NetworkCallback? = null

        private fun session(context: Context, network: Network): String = synchronized(NetworkState::class.java) {
            if (sessionObserver == null) {
                val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val observer = object : ConnectivityManager.NetworkCallback() {
                    override fun onLost(lost: Network) { synchronized(NetworkState::class.java) { sessions.remove(lost) } }
                }
                manager.registerNetworkCallback(NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),
                    observer, Handler(Looper.getMainLooper()))
                sessionObserver = observer
            }
            sessions.getOrPut(network) { UUID.randomUUID().toString() }
        }

        private fun numeric(value: String): String = Address.bytes(value)?.joinToString("") { "%02x".format(it.toInt() and 255) } ?: value

        private fun snapshot(prefixes: Map<String, Int>, own: List<String>, routes: Ipv6Routes = Ipv6Routes(emptyList(), "")): Scope {
            val copy = LinkedHashMap(prefixes)
            val networks = copy.mapNotNull { (address, prefix) ->
                val bytes = Address.bytes(address) ?: return@mapNotNull null
                if (prefix !in 0..bytes.size * 8) return@mapNotNull null
                for (bit in prefix until bytes.size * 8) bytes[bit / 8] = (bytes[bit / 8].toInt() and (0x80 ushr (bit % 8)).inv()).toByte()
                bytes.joinToString("") { "%02x".format(it.toInt() and 255) } + "/" + prefix
            }.toSet()
            return Scope(copy, networks, own.map(::numeric).toSet(), routes)
        }

        /** Only changes that replace the IPv4 attachment replace the connection key. */
        @JvmStatic private fun attachment(properties: LinkProperties): String {
            val parts = TreeSet<String>()
            parts.add("interface:" + properties.interfaceName)
            for (address in properties.linkAddresses) if (address.address is Inet4Address)
                parts.add("address:" + address.address.hostAddress + "/" + address.prefixLength)
            for (route in properties.routes) if (route.isDefaultRoute && route.gateway is Inet4Address)
                parts.add("gateway:" + route.gateway + "|" + route.getInterface())
            return parts.toString()
        }

        private fun connectionKey(context: Context, network: Network, properties: LinkProperties): String =
            "Wi-Fi session v4|" + session(context, network) + "|" + network.networkHandle + "|" + attachment(properties)

        @JvmStatic private fun fromProperties(context: Context, network: Network, properties: LinkProperties): NetworkState {
            val state = NetworkState()
            state.network = network
            state.iface = properties.interfaceName ?: ""
            for (address in properties.linkAddresses.sortedBy { it.address.hostAddress }) {
                val value = address.address.hostAddress!!
                state.addresses.add(value)
                state.prefixes[value] = address.prefixLength
                if (state.ip.isEmpty() && address.address is Inet4Address) {
                    state.ip = value; state.prefix = address.prefixLength
                }
            }
            for (route in properties.routes.sortedBy { it.toString() }) {
                val gateway = route.gateway
                if (route.isDefaultRoute && gateway != null && !gateway.isAnyLocalAddress) {
                    if (state.gateway.isEmpty() || gateway is Inet4Address) state.gateway = gateway.hostAddress!!
                }
            }
            for (address in properties.dnsServers) state.dns += (if (state.dns.isEmpty()) "" else ", ") + address.hostAddress
            if (state.addresses.isEmpty()) throw IllegalStateException("Wi-Fi has no usable IP address yet. Try again shortly.")
            val routes = properties.routes.mapNotNull { route ->
                val destination = route.destination
                if (destination.address !is Inet6Address || destination.prefixLength == 0) null
                else Ipv6Routes.Rule(destination.address.hostAddress!!, destination.prefixLength,
                    route.gateway?.hostAddress ?: "", route.getInterface() ?: "",
                    Build.VERSION.SDK_INT < 33 || route.type == RouteInfo.RTN_UNICAST)
            }
            state.liveScope = snapshot(state.prefixes, state.addresses, Ipv6Routes(routes, state.iface))
            state.key = connectionKey(context, network, properties)
            return state
        }

        @JvmStatic fun current(context: Context): NetworkState {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (network in manager.allNetworks) {
                val capabilities = manager.getNetworkCapabilities(network)
                if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
                val properties = manager.getLinkProperties(network) ?: continue
                return fromProperties(context, network, properties)
            }
            throw IllegalStateException("Connect to Wi-Fi to find devices. Cellular networks are not scanned.")
        }
    }
}

