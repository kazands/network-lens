package io.github.kazands.networklens

import android.content.Context
import java.io.IOException
import java.net.*

/** Android transport adapters. Parsed replies return through the coordinator's admission checks. */
internal class LanProtocolRunners(private val context: Context, private val net: NetworkState,
    private val resources: ScanResources, private val health: DiscoveryHealth,
    private val accepting: () -> Boolean, private val cancelled: () -> Boolean,
    private val deadline: () -> Long, private val inScope: (InetAddress) -> Boolean,
    private val blocked: (Exception) -> Unit) {
    private val datagrams: MutableSet<DatagramSocket> = resources.registry()
    private fun network() = net.network ?: throw IOException("Selected Wi-Fi is unavailable")
    fun equipment(method: NetworkEquipment.Method, range: IpRange?, resolved: (InetAddress, NetworkEquipment.Report) -> Unit): Thread? {
        if (net.ip.isEmpty() || range == null) { health.skipped(method.source,"No IPv4 Wi-Fi range selected"); return null }
        if (range.prefix == 32 && range.contains(net.ip)) { health.skipped(method.source,"No other IPv4 addresses selected"); return null }
        // Do not broadcast outside a custom scan range. Passive packets are still scope checked.
        val full = IpRange(net.ip,net.prefix)
        if (method == NetworkEquipment.Method.UBIQUITI && (range.first!=full.first || range.last!=full.last || net.prefix>=31)) {
            health.skipped(method.source,"Broadcast discovery needs the full Wi-Fi range"); return null
        }
        health.begin(method.source)
        return Thread({
            var socket: DatagramSocket? = null
            try {
                if(!accepting() || cancelled()) return@Thread
                net.check(context)
                val selected=DatagramSocket(null).also {socket=it;datagrams.add(it)}
                network().bindSocket(selected); selected.reuseAddress=false
                selected.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"),method.port));selected.broadcast=true
                val broadcast=InetAddress.getByName(IpRange.address(full.last + 1))
                val result=NetworkEquipment.discover(method,object:NetworkEquipment.Channel {
                    override fun send(bytes:ByteArray) {
                        if(!accepting() || cancelled())return
                        net.check(context);selected.send(DatagramPacket(bytes,bytes.size,broadcast,method.port));health.count(method.source,"requests")
                    }
                    override fun receive(timeout:Int):NetworkEquipment.Packet? {
                        net.check(context);selected.soTimeout=timeout
                        val bytes=ByteArray(4097);val packet=DatagramPacket(bytes,bytes.size)
                        try {selected.receive(packet)} catch(_:SocketTimeoutException) {return null}
                        return NetworkEquipment.Packet(packet.address,packet.port,bytes,packet.length)
                    }
                },deadline(),{accepting() && !cancelled()},inScope) {peer,report->
                    net.check(context)
                    if(accepting() && !cancelled() && inScope(peer))resolved(peer,report)
                }
                repeat(result.packets){health.count(method.source,"packets")}
                repeat(result.devices){health.count(method.source,"identity reports")}
                if(!cancelled()) {
                    if(result.limited || result.interrupted)health.truncated(method.source,"Discovery work or time limit reached")
                    else health.ok(method.source)
                }
            } catch(e:Exception) {
                if(accepting() && !cancelled()) {blocked(e);health.fail(method.source,if(e is BindException)"Discovery port already in use" else "Could not receive equipment discovery")}
            } finally {socket?.let {datagrams.remove(it);it.close()}}
        },method.source).also {resources.start(it)}
    }
    fun ssdp(ipv6: Boolean = false, resolved: (InetAddress, Map<String,String>) -> Unit): Thread = Thread({
        val source=SsdpDiscovery.SOURCE + if (ipv6) " · IPv6" else ""
        health.begin(source)
        var socket:DatagramSocket?=null
        try {
            if(!accepting() || cancelled()) return@Thread
            net.check(context)
            val selected=MulticastSocket().also {socket=it;datagrams.add(it)}
            network().bindSocket(selected)
            selected.networkInterface = NetworkInterface.getByName(net.iface) ?: throw IOException("Wi-Fi interface unavailable")
            selected.timeToLive = 1
            val group=InetAddress.getByName(if (ipv6) "ff02::c%" + net.iface else "239.255.255.250")
            val result=SsdpDiscovery.discover(object:SsdpDiscovery.Channel {
                override fun send(bytes:ByteArray) {
                    if(!accepting() || cancelled())return
                    net.check(context);selected.send(DatagramPacket(bytes,bytes.size,group,1900))
                }
                override fun receive(timeoutMillis:Int):SsdpDiscovery.Packet? {
                    selected.soTimeout=timeoutMillis
                    val bytes=ByteArray(4097);val packet=DatagramPacket(bytes,bytes.size)
                    try {selected.receive(packet)}catch(_:SocketTimeoutException){return null}
                    return SsdpDiscovery.Packet(packet.address,bytes,packet.length)
                }
            },deadline(),{accepting() && !cancelled()},inScope, ipv6 = ipv6) {peer,headers->
                net.check(context)
                if(accepting() && !cancelled() && inScope(peer)) resolved(peer,headers)
            }
            repeat(result.sent){health.count(source,"requests")}
            repeat(result.unique){health.count(source,"unique replies")}
            if(!cancelled()) {
                if(result.limited)health.truncated(source,"400-packet response limit reached")
                else if(result.interrupted)health.truncated(source,"Discovery window stopped early")
                else health.ok(source)
            }
        }catch(e:Exception){if(accepting() && !cancelled()){blocked(e);health.fail(source,"Discovery request failed")}}
        finally {socket?.let {datagrams.remove(it);it.close()}}
    },if (ipv6) "SSDP / UPnP · IPv6" else "service-discovery").also {resources.start(it)}
    fun omada(range: IpRange?, resolved: (InetAddress, OmadaDiscovery.Response) -> Unit): Thread? {
        val source = OmadaDiscovery.SOURCE
        val selectedRange = range
        if (net.ip.isEmpty() || selectedRange == null) { health.skipped(source, "No IPv4 Wi-Fi range selected"); return null }
        if (selectedRange.prefix == 32 && selectedRange.contains(net.ip)) {
            health.skipped(source, "No other IPv4 addresses selected"); return null
        }
        health.begin(source)
        return Thread({
            var socket: DatagramSocket? = null
            try {
                if (!accepting() || cancelled()) return@Thread
                net.check(context)
                val selected = DatagramSocket(null).also { socket = it; datagrams.add(it) }
                network().bindSocket(selected)
                // Broadcasts require a wildcard local address; Android binds this socket to the
                // selected Wi-Fi Network. Do not share/take a controller's existing listener.
                selected.reuseAddress = false
                selected.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), OmadaDiscovery.PORT))
                val result = OmadaDiscovery.listen(OmadaDiscovery.Channel { timeout ->
                    net.check(context)
                    selected.soTimeout = timeout
                    val bytes = ByteArray(OmadaDiscovery.MAX_PACKET + 1)
                    val packet = DatagramPacket(bytes, bytes.size)
                    try {
                        selected.receive(packet)
                        OmadaDiscovery.Packet(packet.address, packet.port, bytes, packet.length)
                    } catch (_: SocketTimeoutException) { null }
                }, deadline(), { accepting() && !cancelled() }, inScope) { peer, response ->
                    net.check(context)
                    resolved(peer, response)
                }
                repeat(result.packets) { health.count(source, "packets") }
                repeat(result.devices) { health.count(source, "devices") }
                if (!cancelled()) {
                    if (result.limited) health.truncated(source, "Announcement safety limit reached")
                    else if (result.interrupted) health.truncated(source, "Listening window stopped early")
                    else health.ok(source)
                }
            } catch (e: Exception) {
                if (accepting() && !cancelled()) {
                    blocked(e)
                    health.fail(source, if (e is BindException) "Discovery port already in use" else "Could not receive Omada announcements")
                }
            } finally { socket?.let { datagrams.remove(it); it.close() } }
        }, "omada-discovery").also { resources.start(it) }
    }

    fun tpLink(range: IpRange?, resolved: (InetAddress, Int, TpLinkDiscovery.Response) -> Unit): Thread? {
        val source = TpLinkDiscovery.SOURCE
        val selectedRange = range
        if (net.ip.isEmpty() || selectedRange == null) { health.skipped(source, "No IPv4 Wi-Fi range selected"); return null }
        val targets = try { TpLinkDiscovery.targets(net.ip, net.prefix, selectedRange) }
            catch (_: IllegalArgumentException) { health.fail(source, "Selected IPv4 range is unavailable"); return null }
        if (targets.isEmpty()) { health.skipped(source, "No other IPv4 addresses selected"); return null }
        health.begin(source)
        return Thread({
            var socket: DatagramSocket? = null
            try {
                if (!accepting() || cancelled()) return@Thread
                net.check(context)
                val selected = DatagramSocket(null).also { socket = it; datagrams.add(it) }
                network().bindSocket(selected)
                selected.bind(InetSocketAddress(InetAddress.getByName(net.ip), 0))
                selected.broadcast = true
                val result = TpLinkDiscovery.discover(object : TpLinkDiscovery.Channel {
                    override fun send(target: InetAddress, port: Int, bytes: ByteArray) {
                        if (!accepting() || cancelled()) return
                        net.check(context)
                        selected.send(DatagramPacket(bytes, bytes.size, target, port))
                        health.count(source, "requests")
                    }
                    override fun receive(timeoutMillis: Int): TpLinkDiscovery.Packet? {
                        selected.soTimeout = timeoutMillis
                        val bytes = ByteArray(TpLinkDiscovery.MAX_PACKET + 1)
                        val packet = DatagramPacket(bytes, bytes.size)
                        try { selected.receive(packet) } catch (_: SocketTimeoutException) { return null }
                        return TpLinkDiscovery.Packet(packet.address, packet.port, bytes, packet.length)
                    }
                }, targets, java.security.SecureRandom().nextInt(), deadline(),
                    { accepting() && !cancelled() }, inScope) { peer, port, response ->
                    net.check(context)
                    resolved(peer, port, response)
                }
                repeat(result.packets) { health.count(source, "packets") }
                repeat(result.devices) { health.count(source, "devices") }
                if (!cancelled()) {
                    if (result.limited) health.truncated(source, "512-packet / 128-device safety limit reached")
                    else if (result.interrupted) health.truncated(source, "Discovery window stopped early")
                    else health.ok(source)
                }
            } catch (e: Exception) {
                if (accepting() && !cancelled()) { blocked(e); health.fail(source, "Could not receive TP-Link discovery replies") }
            } finally { socket?.let { datagrams.remove(it); it.close() } }
        }, "tplink-discovery").also { resources.start(it) }
    }

    fun playStation(range: IpRange?, resolved: (InetAddress, PlayStationDiscovery.Response) -> Unit): Thread? {
        val source = PlayStationDiscovery.SOURCE
        if (net.ip.isEmpty() || net.prefix >= 31) { health.skipped(source, "IPv4 Wi-Fi broadcast is unavailable"); return null }
        return Thread({
            health.begin(source)
            var socket: DatagramSocket? = null
            try {
                if (!accepting() || cancelled()) return@Thread
                net.check(context)
                // Use a private client-port range; never reuse another app's bound socket.
                for (port in 9303..9319) {
                    val candidate = DatagramSocket(null)
                    try { candidate.bind(InetSocketAddress(InetAddress.getByName(net.ip), port)); socket = candidate; break }
                    catch (_: BindException) { candidate.close() }
                    catch (e: Exception) { candidate.close(); throw e }
                }
                val selected = socket ?: throw BindException("Discovery client ports unavailable")
                datagrams.add(selected); network().bindSocket(selected); selected.broadcast = true
                val broadcast = InetAddress.getByName(IpRange.address(IpRange(net.ip, net.prefix).last + 1))
                val result = PlayStationDiscovery.discover(object : PlayStationDiscovery.Channel {
                    override fun send(bytes: ByteArray) {
                        if (!accepting() || cancelled()) return
                        net.check(context)
                        selected.send(DatagramPacket(bytes, bytes.size, broadcast, PlayStationDiscovery.PORT))
                        health.count(source, "requests")
                    }
                    override fun receive(timeoutMillis: Int): PlayStationDiscovery.Packet? {
                        selected.soTimeout = timeoutMillis
                        val bytes = ByteArray(PlayStationDiscovery.MAX_PACKET + 1)
                        val packet = DatagramPacket(bytes, bytes.size)
                        try { selected.receive(packet) } catch (_: SocketTimeoutException) { return null }
                        return PlayStationDiscovery.Packet(packet.address, packet.port, bytes, packet.length)
                    }
                }, deadline(), { accepting() && !cancelled() }, inScope) { peer, response ->
                    net.check(context)
                    resolved(peer, response)
                }
                repeat(result.packets) { health.count(source, "packets") }
                repeat(result.devices) { health.count(source, "devices") }
                if (!cancelled()) {
                    if (result.limited) health.truncated(source, "64-packet / 32-device safety limit reached")
                    else if (result.interrupted) health.truncated(source, "Discovery window stopped early")
                    else health.ok(source)
                }
            } catch (e: Exception) {
                if (accepting() && !cancelled()) { blocked(e); health.fail(source, "Could not receive console discovery replies") }
            } finally { socket?.let { datagrams.remove(it); it.close() } }
        }, "playstation-discovery").also { resources.start(it) }
    }
}
