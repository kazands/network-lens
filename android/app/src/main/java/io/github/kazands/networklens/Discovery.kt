package io.github.kazands.networklens

import android.content.Context
import android.content.res.Configuration
import android.net.nsd.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.ErrnoException
import android.system.OsConstants
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** User-started, bounded inventory scan. No Internet endpoint or background scheduler. */
class Discovery @JvmOverloads internal constructor(c: Context, private val net: NetworkState, private val echoFactory: EchoFactory,
    private val networkCheck: NetworkCheck = NetworkCheck { context, network -> network.check(context) }) {
    interface Listener {
        fun progress(done: Int, total: Int, devices: List<Device>)
        /** Consumers that coalesce updates can defer copying until delivery is needed. */
        fun progressSnapshot(done: Int, total: Int, devices: () -> List<Device>) = progress(done, total, devices())
        fun finished(devices: List<Device>, complete: Boolean, message: String)
    }
    fun interface EchoFactory {
        @Throws(IOException::class) fun open(network: NetworkState, target: InetAddress): IcmpEcho.Channel
    }
    fun interface NetworkCheck { fun check(context: Context, network: NetworkState) }
    constructor(c: Context, n: NetworkState) : this(c, n, EchoFactory(AndroidIcmp::open))

    private val context = c.applicationContext
    private val store = Store(context)
    private val token = store.token()
    private val cancelled = AtomicBoolean()
    private val found: ConcurrentMap<String, Device> = ConcurrentHashMap()
    private val freshResponders: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val echoAttempted = AtomicBoolean()
    @Volatile private var phase = "Finishing device details…"
    fun phase(): String = phase
    private val sockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    private val datagrams: MutableSet<DatagramSocket> = ConcurrentHashMap.newKeySet()
    private val workers = Executors.newFixedThreadPool(32)
    private val enrichment = DiscoveryWork()
    private val detailsQueued: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val confirmQueued: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val ipv6Candidates = ServiceAdmission(32)
    private val ipv6TcpFallback: ConcurrentMap<String, Int> = ConcurrentHashMap()
    private val ipv6EchoFinished: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val ipv6Pending = AtomicInteger()
    private val excludedIpv6: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var scanDeadline = Long.MAX_VALUE
    private val castRequests = ServiceAdmission(32)
    private val metadataRequests = ServiceAdmission(96, 32)
    private val metadataTimeouts = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val multicastNames = ServiceAdmission(128)
    private val wsPeers = ServiceAdmission(64)
    private val wsRecords = ServiceAdmission(256, 8)
    private val descriptions = DescriptionQueue(32, 3)
    private val health = DiscoveryHealth()
    @Volatile private var enriching = false
    @Volatile private var lastServiceChange = 0L
    private var nsdStarted = 0L
    private val endpoints = AtomicInteger()
    private val services = ServiceAdmission(256, 32)
    private val nsdTypes = HashSet<String>()
    private val mdnsQuestions = LinkedHashMap<String, ReverseQuestion>()
    private val mdnsRetries = MdnsRetries()
    private val mdnsAssembly = MdnsAssembly()
    private val nativeServiceEvidence = NativeServiceEvidence(mdnsAssembly)
    private val mdnsQuestionSuppression = MdnsQuestionSuppression()
    private val directServices: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val modernQueue: Queue<ModernRequest> = ArrayDeque()
    private val legacyBrowses: Queue<LegacyBrowse> = ArrayDeque()
    private val legacyActive = HashSet<LegacyBrowse>()
    private val modernActive = HashSet<ModernRequest>()
    private val resolverTimeouts = Handler(Looper.getMainLooper())

    private class ReverseQuestion(val ip: String) {
        val question = ReverseDns.question(ip)
        var attempts = 0
        var next = 0L
    }
    private class ModernRequest @JvmOverloads constructor(val info: NsdServiceInfo, val attempt: Int, val previousFailure: String = "") {
        var done = false
        var usable = false
        var registered = false
        var emptyUpdate = false
        var startedAt = 0L
        var timeout: Runnable? = null
        var settle: Runnable? = null
        var callback: NsdManager.ServiceInfoCallback? = null
    }
    private class LegacyBrowse(val type: String) {
        lateinit var listener: NsdManager.DiscoveryListener
        var timeout: Runnable? = null
        var stopping = false
    }
    private val nsdListeners = ArrayList<NsdManager.DiscoveryListener>()
    private val nsdCallbacks = ArrayList<NsdManager.ServiceInfoCallback>()
    private val queue: Queue<NsdServiceInfo> = ConcurrentLinkedQueue()
    private val resolving = AtomicBoolean()
    private val warning = AtomicReference<String?>()
    @Volatile private var accepting = false
    @Volatile private var range: IpRange? = null
    @Volatile private var scope = "Preparing scan…"
    fun scope(): String = scope
    fun sources(): Map<String, String> = health.sources()
    private var nsd: NsdManager? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var multicastThread: Thread? = null
    private var sadpThread: Thread? = null
    private var mdnsThread: Thread? = null
    private var mdnsV6Thread: Thread? = null
    private var wsThread: Thread? = null

    fun cancel() {
        cancelled.set(true)
        accepting = false
        enriching = false
        workers.shutdownNow()
        enrichment.shutdownNow()
        metadataTimeouts.shutdownNow()
        closeIo()
        cleanupNsd()
        mdnsAssembly.close()
        mdnsQuestionSuppression.close()
    }
    @Synchronized fun refreshNetwork(current: NetworkState): Boolean {
        if (!net.refresh(current)) return false
        if (accepting) observePhoneAddresses(current.addresses)
        return true
    }
    private fun observePhoneAddresses(addresses: List<String>) {
        for (address in addresses) try {
            val a = InetAddress.getByName(address)
            if (inScope(a)) observe(net.scoped(a), "This phone's Wi-Fi address")?.let { d -> synchronized(d) {
                d.self = true
                d.hint("Name", Build.MANUFACTURER + " " + Build.MODEL, "This phone", 100)
                d.hint("Manufacturer", Build.MANUFACTURER, "This phone", 100)
                d.hint("Model", Build.MODEL, "This phone", 100)
                d.hint("Device type", "This phone", "This phone", 100)
                d.identify("self:" + d.name, 100)
            } }
        } catch (_: Exception) {}
    }
    private fun closeIo() {
        for (socket in sockets) try { socket.close() } catch (_: IOException) {}
        for (socket in datagrams) socket.close()
    }
    fun scan(cidr: String?, listener: Listener) {
        Thread({
            if (!ACTIVE.compareAndSet(false, true)) {
                workers.shutdownNow(); enrichment.shutdownNow(); metadataTimeouts.shutdownNow()
                listener.finished(emptyList(), false, "Another scan is finishing. Try again shortly.")
                return@Thread
            }
            if (!store.startScanIfCurrent(token)) {
                ACTIVE.set(false); cancel()
                listener.finished(emptyList(), false, "This scan session was replaced.")
                return@Thread
            }
            var complete = false
            var started = false
            var message: String
            val done = AtomicInteger()
            var total = 0
            try {
                if (cancelled.get()) throw InterruptedException()
                net.check(context)
                var hosts: List<String> = ArrayList()
                if (net.ip.isNotEmpty()) {
                    val local = IpRange(net.ip, net.prefix)
                    range = local
                    if (!cidr.isNullOrBlankJava()) {
                        val p = cidr!!.javaTrim().javaSplit("/", -1)
                        if (p.size != 2) throw IllegalArgumentException("Enter an IPv4 range such as 192.168.1.0/24.")
                        val requested = IpRange(p[0], Integer.parseInt(p[1]))
                        range = requested
                        if (requested.first < local.first || requested.last > local.last) throw IllegalArgumentException("Choose a range inside your connected Wi-Fi network.")
                    }
                    hosts = range!!.hosts(1024)
                } else if (!cidr.isNullOrBlankJava()) throw IllegalArgumentException("This network has no IPv4 range. Use automatic discovery.")
                val selected = range
                scope = if (selected == null) "IPv6 advertised services + this phone's Wi-Fi addresses"
                    else IpRange.address(if (selected.prefix >= 31) selected.first else selected.first - 1) + "/" + selected.prefix + " · IPv4 + IPv6 advertised services"
                total = hosts.size; started = true; accepting = true; enriching = true
                health.begin("IPv4 checks"); health.skipped("UPnP descriptions", "No supported description advertised")
                for (kind in LocalMetadata.Kind.values()) health.skipped(kind.source, "No supported metadata service advertised")
                observePhoneAddresses(net.addresses)
                listener.progressSnapshot(0, total, ::snapshot)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(55)
                scanDeadline = deadline
                health.skipped(IPV6_SOURCE, "No separate IPv6 echo checks were needed")
                startNsd(); startMdns(); startSsdp(); startSadp(); startWsDiscovery()
                val echoTargets = hosts.any { it != net.ip }
                health.skipped(ICMP_SOURCE, if (echoTargets) "Echo checks are used for unconfirmed addresses" else "No other IPv4 addresses in this range")
                val count = total
                val jobs = ArrayList<Future<*>>()
                for (host in hosts) jobs.add(workers.submit {
                    if (cancelled.get()) return@submit
                    if (host != net.ip) probe(host, 160, 220)
                    val n = done.incrementAndGet()
                    if (n % 8 == 0 || n == count) listener.progressSnapshot(n, count, ::snapshot)
                })
                awaitJobs(jobs, deadline, "IPv4 checks")
                if (!cancelled.get()) {
                    val unanswered = ArrayList<String>()
                    val metadata = ArrayList<String>()
                    for (host in hosts) if (host != net.ip) {
                        val d = found[host]
                        if (!freshResponders.contains(host)) unanswered.add(host)
                        else if (d != null && !detailsQueued.contains(host)) synchronized(d) {
                            if (d.hostname.isEmpty() || d.mac.isEmpty()) metadata.add(host)
                        }
                    }
                    val retrySource = "Unanswered device checks"
                    if (unanswered.isNotEmpty() || metadata.isNotEmpty()) {
                        if (deadline - System.nanoTime() > TimeUnit.SECONDS.toNanos(8)) {
                            phase = "Checking unanswered devices…"; health.begin(retrySource)
                            listener.progressSnapshot(done.get(), count, ::snapshot)
                            val retry = ArrayList<Future<*>>()
                            val retried = AtomicInteger()
                            for (host in unanswered) retry.add(workers.submit {
                                if (cancelled.get()) return@submit
                                health.count(retrySource, "reachability retries"); probe(host, 250, 400)
                                if (retried.incrementAndGet() % 8 == 0) listener.progressSnapshot(done.get(), count, ::snapshot)
                            })
                            for (host in metadata) retry.add(workers.submit {
                                if (cancelled.get()) return@submit
                                found[host]?.let { d -> health.count(retrySource, "name/MAC retries"); enrichNetBios(host, d, 400) }
                                if (retried.incrementAndGet() % 8 == 0) listener.progressSnapshot(done.get(), count, ::snapshot)
                            })
                            awaitJobs(retry, deadline - TimeUnit.SECONDS.toNanos(5), retrySource)
                            if (!cancelled.get()) health.ok(retrySource)
                        } else health.truncated(retrySource, "Scan budget ended before retries could run")
                    } else health.skipped(retrySource, "All checked addresses already responded with details")
                    phase = "Finishing device details…"
                }
                if (!cancelled.get() && echoAttempted.get()) health.ok(ICMP_SOURCE)
                workers.shutdown()
                if (!cancelled.get()) {
                    if (done.get() == total) health.ok("IPv4 checks")
                    // Publish while resolutions settle, and reject late work after completion.
                    val cutoff = minOf(deadline, maxOf(nsdStarted + TimeUnit.SECONDS.toNanos(if (Build.VERSION.SDK_INT < 34) 26 else 12), System.nanoTime() + TimeUnit.SECONDS.toNanos(5)))
                    while (!cancelled.get() && (System.nanoTime() < cutoff || ipv6Pending.get() > 0 && System.nanoTime() < deadline)) {
                        val now = System.nanoTime()
                        if (now >= nsdStarted + TimeUnit.SECONDS.toNanos(10) && !health.pending() && !mdnsPending() && ipv6Pending.get() == 0 && now - lastServiceChange > TimeUnit.MILLISECONDS.toNanos(1500)) break
                        listener.progressSnapshot(done.get(), total, ::snapshot); Thread.sleep(250)
                    }
                    if (mdnsPending()) health.truncated(MDNS_SOURCE, "Local device details did not finish before the scan ended")
                    accepting = false
                    sadpThread?.let { it.join(500); if (it.isAlive) health.truncated("SADP device discovery", "Discovery window did not finish") }
                    wsThread?.let { it.join(500); if (it.isAlive) health.truncated(WSD_SOURCE, "Discovery window did not finish") }
                    health.closeBrowseWindow(); cleanupNsd(); enrichment.shutdown()
                    while (!cancelled.get() && !enrichment.isTerminated() && System.nanoTime() < deadline) {
                        listener.progressSnapshot(done.get(), total, ::snapshot); enrichment.awaitTermination(250, TimeUnit.MILLISECONDS)
                    }
                    if (!enrichment.isTerminated()) { health.truncated("Device enrichment", "Scan time limit reached"); enriching = false }
                    mdnsThread?.let { it.join(500); if (it.isAlive) health.truncated(MDNS_SOURCE, "Query window did not finish") }
                    mdnsV6Thread?.let { it.join(500); if (it.isAlive) health.truncated(MDNS_SOURCE, "IPv6 query window did not finish") }
                    multicastThread?.let { it.join(500); if (it.isAlive) health.truncated("SSDP / UPnP", "Discovery window did not finish") }
                    net.check(context)
                    finishDescriptions(); health.finish(false)
                    complete = done.get() == total && warning.get() == null && health.summary().isEmpty() && !cancelled.get()
                }
                message = if (cancelled.get()) "Scan stopped. Results are partial."
                    else warning.get() ?: health.summary().ifEmpty { "Scan finished. Silent or isolated devices may be missing." }
            } catch (e: Exception) {
                message = if (e is TimeoutException) "Scan time limit reached. Results are partial."
                    else if (cancelled.get()) "Scan stopped. Results are partial."
                    else e.message ?: "Could not finish this scan."
            } finally {
                accepting = false; enriching = false
                workers.shutdownNow(); enrichment.shutdownNow(); metadataTimeouts.shutdownNow(); closeIo(); cleanupNsd()
                health.finish(cancelled.get())
                if (started && done.get() != total) health.truncated("IPv4 checks", "Address checks did not finish")
                try {
                    workers.awaitTermination(2, TimeUnit.SECONDS); enrichment.awaitTermination(2, TimeUnit.SECONDS)
                    for (thread in listOf(multicastThread, sadpThread, mdnsThread, mdnsV6Thread, wsThread)) thread?.join(1000)
                } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
            var result: List<Device>
            try {
                readLegacyArp(); result = snapshot()
                if (!store.isCurrent(token)) { result = emptyList(); complete = false; message = "This session was reset. Earlier scan results were discarded." }
                else if (!started) result = store.devices(net.key)
                else if (!store.retainIfCurrent(token, net.key, result, ScanReport(scope, complete, message, done.get(), total, System.currentTimeMillis(), health.sources()))) { result = emptyList(); complete = false }
            } finally { ACTIVE.set(false) }
            listener.finished(result, complete, message)
        }, "device-discovery").start()
    }
    private fun String?.isNullOrBlankJava(): Boolean = this == null || javaTrim().isEmpty()
    private fun awaitJobs(jobs: List<Future<*>>, deadline: Long, source: String) {
        for (job in jobs) {
            if (cancelled.get()) return
            val left = deadline - System.nanoTime()
            if (left <= 0) { health.truncated(source, "Scan time limit reached"); throw TimeoutException() }
            try { job.get(left, TimeUnit.NANOSECONDS) }
            catch (e: TimeoutException) { health.truncated(source, "Scan time limit reached"); throw e }
        }
    }
    private fun inScope(a: InetAddress): Boolean {
        if (!net.local(a)) return false
        val selected = range
        return if (a is Inet4Address && selected != null) selected.contains(a.hostAddress) else true
    }
    private fun candidateAllowed(address: InetAddress, type: String): Boolean = inScope(address) ||
        address is Inet6Address && LocalServices.routedAccessory(type) && net.routed(address)

    private fun excludedV6(address: InetAddress) {
        if (address !is Inet6Address || excludedIpv6.size >= 128 || !excludedIpv6.add(net.scoped(address))) return
        health.count(IPV6_SOURCE, "excluded")
    }

    private fun checkV6(address: InetAddress) {
        if (!net.admitted(address)) throw IcmpEcho.Failure(IcmpEcho.Problem.ROUTE, "IPv6 route is no longer admitted")
        try { networkCheck.check(context, net) }
        catch (_: IllegalStateException) { throw IcmpEcho.Failure(IcmpEcho.Problem.ROUTE, "Selected Wi-Fi route changed") }
    }

    private fun confirmIpv6(d: Device, address: InetAddress, tcpPort: Int = 0) {
        if (!accepting || freshResponders.contains(d.ip) || cancelled.get()) return
        val admission = ipv6Candidates.admit(d.ip, "address")
        if (admission == ServiceAdmission.Result.LIMIT) {
            health.truncated(IPV6_SOURCE, "32-address confirmation limit reached")
            if (tcpPort > 0 && inScope(address)) confirmService(d, address, tcpPort)
            return
        }
        if (admission == ServiceAdmission.Result.INVALID) return
        if (tcpPort in 1..65535 && inScope(address)) {
            ipv6TcpFallback.putIfAbsent(d.ip, tcpPort)
            if (ipv6EchoFinished.contains(d.ip)) confirmService(d, address, ipv6TcpFallback.getValue(d.ip))
        }
        if (admission != ServiceAdmission.Result.ADMITTED) return
        ipv6Pending.incrementAndGet()
        health.workStarted(IPV6_SOURCE); health.count(IPV6_SOURCE, "candidates")
        if (!submitEnrichment(DiscoveryWork.Lane.IPV6, IPV6_SOURCE, Runnable {
            try {
                if (!enriching || cancelled.get()) return@Runnable
                val result = IcmpEcho.probeV6({
                    checkV6(address)
                    val channel = echoFactory.open(net, address)
                    object : IcmpEcho.Channel {
                        override fun identifier() = channel.identifier()
                        override fun send(packet: ByteArray) { checkV6(address); channel.send(packet) }
                        override fun receive(waitMillis: Int) = channel.receive(waitMillis)
                        override fun close() = channel.close()
                    }
                }, { cancelled.get() || !enriching || Thread.currentThread().isInterrupted || System.nanoTime() >= scanDeadline }, 2000, 2, 200)
                repeat(result.requests) { health.count(IPV6_SOURCE, "requests") }
                if (result.status == IcmpEcho.Status.REPLIED && enriching && !cancelled.get()) {
                    checkV6(address)
                    health.count(IPV6_SOURCE, "confirmed")
                    synchronized(d) {
                        d.detail("ICMPv6 response time", if (result.latencyMillis == 0L) "Less than 1 ms" else "${result.latencyMillis} ms")
                        if (net.routed(address)) d.detail("Network path", "Routed IPv6 through Wi-Fi")
                    }
                    confirmExisting(d, "Fresh ICMPv6 echo response")
                    synchronized(this@Discovery) { applyRouted(System.nanoTime(), d.ip) }
                } else if (result.status == IcmpEcho.Status.NO_REPLY) {
                    health.count(IPV6_SOURCE, "unconfirmed")
                } else if (result.status == IcmpEcho.Status.CANCELLED && !cancelled.get()) {
                    health.truncated(IPV6_SOURCE, "Scan window ended before IPv6 confirmation finished")
                }
                result.problem?.let {
                    health.count(IPV6_SOURCE, "errors")
                    if (it == IcmpEcho.Problem.REPLY_LIMIT) health.truncated(IPV6_SOURCE, result.reason()) else health.fail(IPV6_SOURCE, result.reason())
                }
                if (result.status in listOf(IcmpEcho.Status.NO_REPLY, IcmpEcho.Status.ERROR) && !cancelled.get()) {
                    ipv6EchoFinished.add(d.ip)
                    ipv6TcpFallback[d.ip]?.let { port -> if (inScope(address)) confirmService(d, address, port) }
                }
            } catch (e: Exception) {
                if (!cancelled.get()) { blocked(e); health.fail(IPV6_SOURCE, "IPv6 confirmation could not finish on the selected Wi-Fi") }
            } finally { ipv6Pending.decrementAndGet(); health.workFinished(IPV6_SOURCE) }
        })) { ipv6Pending.decrementAndGet(); health.workFinished(IPV6_SOURCE) }
    }
    private fun observe(ip: String, evidence: String): Device? = observe(ip, evidence, true)
    private fun observe(ip: String, evidence: String, fresh: Boolean): Device? {
        if (!accepting || cancelled.get()) return null
        val d = found.computeIfAbsent(ip) {
            if (endpoints.incrementAndGet() > 1200) {
                endpoints.decrementAndGet(); warning.compareAndSet(null, "The device limit was reached. Results are partial."); null
            } else Device().also { it.ip = ip; it.firstSeen = System.currentTimeMillis() }
        } ?: return null
        if (fresh) freshResponders.add(ip)
        synchronized(d) { d.lastSeen = System.currentTimeMillis(); d.seen = true; d.gateway = ip == net.gateway; d.evidence(evidence) }
        return d
    }
    private fun probe(host: String, connectTimeout: Int, nameTimeout: Int) {
        if (cancelled.get()) return
        if (useFreshResponse(host, connectTimeout, nameTimeout)) return
        val echo = echo(host, if (connectTimeout == 160) 200 else 300)
        if (cancelled.get() || echo.status == IcmpEcho.Status.CANCELLED) return
        val d = if (echo.status == IcmpEcho.Status.REPLIED) observe(host, "ICMP echo response") else null
        if (d != null) {
            synchronized(d) { d.detail("ICMP response time", if (echo.latencyMillis == 0L) "Less than 1 ms" else "" + echo.latencyMillis + " ms") }
            queryDeviceName(d); queueDetails(d, connectTimeout, nameTimeout); return
        }
        if (useFreshResponse(host, connectTimeout, nameTimeout)) return
        for (port in PORTS) {
            if (cancelled.get()) return
            if (useFreshResponse(host, connectTimeout, nameTimeout)) return
            val result = confirmTcp(peerAddress(host), port, connectTimeout, "IPv4 checks")
            if (result.status == TcpCheck.Status.CANCELLED) return
            if (result.status != TcpCheck.Status.CONFIRMED) continue
            observe(host, "TCP connection on port " + port + " (confirmed twice)")?.let { item ->
                synchronized(item) { item.latency = result.latency; item.port(port); item.detail("Presence check", "Two new TCP connections, at least 200 ms apart") }
                queryDeviceName(item); queueDetails(item, 0, nameTimeout)
            }
            return
        }
        if (!cancelled.get() && !useFreshResponse(host, connectTimeout, nameTimeout)) enrichNetBios(host, null, nameTimeout)
    }
    /** Only a response already validated by this scan can replace another presence probe. */
    private fun useFreshResponse(host: String, connectTimeout: Int, nameTimeout: Int): Boolean {
        if (cancelled.get() || !enriching || !freshResponders.contains(host)) return false
        val device = found[host] ?: return false
        if (!inScope(peerAddress(host))) return false
        health.count("IPv4 checks", "already answered this scan")
        queryDeviceName(device)
        // Presence reuse does not discard the independent name/MAC/port work.
        if (!device.self) queueDetails(device, connectTimeout, nameTimeout)
        return true
    }
    private fun peerAddress(host: String): InetAddress = try { InetAddress.getByName(host) }
        catch (e: UnknownHostException) { throw IllegalArgumentException("Invalid scan address", e) }
    private fun confirmTcp(address: InetAddress, port: Int, timeout: Int, source: String): TcpCheck.Result {
        val result = TcpCheck.confirm({
            if (!inScope(address)) throw IOException("TCP target is outside this scan")
            val socket = Socket(); sockets.add(socket)
            try {
                net.network!!.bindSocket(socket)
                val start = System.nanoTime(); socket.connect(InetSocketAddress(address, port), timeout)
                if (!socket.isConnected || socket.isClosed || address != socket.inetAddress || socket.port != port) throw IOException("TCP connection was not established to the requested peer")
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).toInt()
            } finally { sockets.remove(socket); try { socket.close() } catch (_: IOException) {} }
        }, { cancelled.get() || !enriching })
        if (result.status == TcpCheck.Status.CONFIRMED) health.count(source, "TCP confirmations")
        else if (result.status == TcpCheck.Status.UNCONFIRMED) {
            if (result.connections > 0) health.count(source, "TCP connections not reconfirmed")
            result.failure?.let { if (refused(it)) health.count(source, "TCP refusals not used as presence"); blocked(it) }
        }
        return result
    }
    private fun echo(host: String, timeout: Int): IcmpEcho.Result {
        echoAttempted.set(true); health.begin(ICMP_SOURCE)
        health.count(ICMP_SOURCE, "probes")
        val result = IcmpEcho.probe({
            val target = InetAddress.getByName(host)
            if (!inScope(target)) throw IcmpEcho.Failure(IcmpEcho.Problem.SETUP, "Target is outside scan scope")
            echoFactory.open(net, target)
        }, { cancelled.get() || Thread.currentThread().isInterrupted }, timeout, 2, 120)
        repeat(result.requests) { health.count(ICMP_SOURCE, "requests") }
        if (result.requests > 1) health.count(ICMP_SOURCE, "retries")
        if (result.status == IcmpEcho.Status.REPLIED) health.count(ICMP_SOURCE, "replies")
        if (result.status == IcmpEcho.Status.NO_REPLY) health.count(ICMP_SOURCE, "no echo reply")
        if (result.problem != null) {
            health.count(ICMP_SOURCE, "errors")
            if (result.problem == IcmpEcho.Problem.REPLY_LIMIT) health.truncated(ICMP_SOURCE, result.reason()) else health.fail(ICMP_SOURCE, result.reason())
        }
        return result
    }
    private fun enrichNetBios(host: String, existing: Device?, timeout: Int) {
        if (cancelled.get()) return
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(); datagrams.add(socket); net.network!!.bindSocket(socket); socket.soTimeout = timeout
            val id = ThreadLocalRandom.current().nextInt(65536)
            val query = NetBios.query(id); socket.connect(InetAddress.getByName(host), 137); socket.send(DatagramPacket(query, query.size))
            val bytes = ByteArray(4096); val response = DatagramPacket(bytes, bytes.size); socket.receive(response)
            val status = NetBios.parse(bytes, response.length, id) ?: return
            freshResponders.add(host)
            val d = existing ?: observe(host, "NetBIOS node-status response")
            if (d != null) {
                synchronized(d) {
                    d.hint("Name", status.name, "NetBIOS hostname", 50); d.hint("Hostname", status.name, "NetBIOS node status", 55)
                    d.hardware(status.mac, "NetBIOS reported adapter address"); d.service("NetBIOS node status"); d.evidence("NetBIOS node-status response")
                    if (status.mac.isNotEmpty()) d.identify("nbstat:" + status.mac, 60)
                }
                queryDeviceName(d)
            }
        } catch (e: Exception) { blocked(e) }
        finally { socket?.let { datagrams.remove(it); it.close() } }
    }

    private fun startSsdp() {
        multicastThread = Thread({
            health.begin("SSDP / UPnP")
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(); datagrams.add(socket); net.network!!.bindSocket(socket); socket.soTimeout = 300
                val query = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
                val group = InetAddress.getByName("239.255.255.250")
                val start = System.nanoTime(); val end = start + TimeUnit.SECONDS.toNanos(5)
                var replies = 0; var sent = 0
                val seen = HashSet<String>()
                while (accepting && !cancelled.get() && System.nanoTime() < end && replies < 400) {
                    if (sent < 2 && System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos((sent * 1200).toLong())) {
                        socket.send(DatagramPacket(query, query.size, group, 1900)); sent++; health.count("SSDP / UPnP", "requests")
                    }
                    val data = ByteArray(4096); val p = DatagramPacket(data, data.size)
                    try { socket.receive(p) } catch (_: SocketTimeoutException) { continue }
                    replies++
                    if (!inScope(p.address)) continue
                    val headers = Ssdp.parse(data, p.length)
                    if (headers.isEmpty()) continue
                    val key = p.address.hostAddress + "|" + headers.getOrDefault("st", "") + "|" + headers.getOrDefault("usn", "") + "|" + headers.getOrDefault("location", "")
                    if (!seen.add(key)) continue
                    health.count("SSDP / UPnP", "unique replies")
                    observe(net.scoped(p.address), "SSDP / UPnP discovery response")?.let { d ->
                        synchronized(d) {
                            d.service(headers.getOrDefault("st", "SSDP / UPnP"))
                            d.detail("UPnP server", headers.getOrDefault("server", ""))
                            d.detail("UPnP identifier", headers.getOrDefault("usn", ""))
                            d.identify(headers.getOrDefault("usn", ""), 70)
                        }
                        description(d, p.address, headers["location"]); queryDeviceName(d)
                    }
                }
                if (replies >= 400) health.truncated("SSDP / UPnP", "400-packet response limit reached") else if (!cancelled.get()) health.ok("SSDP / UPnP")
            } catch (e: Exception) { if (!cancelled.get()) { blocked(e); health.fail("SSDP / UPnP", "Discovery request failed") } }
            finally { socket?.let { datagrams.remove(it); it.close() } }
        }, "service-discovery").also { it.start() }
    }
    private fun startSadp() {
        val source = "SADP device discovery"
        if (net.ip.isEmpty()) { health.skipped(source, "IPv4 Wi-Fi is unavailable"); return }
        sadpThread = Thread({
            health.begin(source)
            var socket: MulticastSocket? = null
            try {
                if (!accepting || cancelled.get()) return@Thread
                val iface = NetworkInterface.getByName(net.iface) ?: throw IOException("Wi-Fi interface unavailable")
                socket = MulticastSocket(null); datagrams.add(socket); socket.reuseAddress = true; socket.bind(InetSocketAddress(37020))
                net.network!!.bindSocket(socket); socket.networkInterface = iface; socket.timeToLive = 1; socket.soTimeout = 300
                val group = InetAddress.getByName("239.255.255.250"); socket.joinGroup(InetSocketAddress(group, 37020), iface)
                val id = UUID.randomUUID().toString(); val query = Sadp.query(id)
                val start = System.nanoTime(); val end = start + TimeUnit.SECONDS.toNanos(5)
                var replies = 0; var sent = 0
                val seen = HashSet<String>()
                while (accepting && !cancelled.get() && System.nanoTime() < end && replies < 128) {
                    if (sent < 2 && System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos((sent * 1200).toLong())) {
                        socket.send(DatagramPacket(query, query.size, group, 37020)); sent++; health.count(source, "requests")
                    }
                    val bytes = ByteArray(Sadp.MAX_PACKET + 1); val response = DatagramPacket(bytes, bytes.size)
                    try { socket.receive(response) } catch (_: SocketTimeoutException) { continue }
                    replies++
                    if (response.port != 37020 || !inScope(response.address)) continue
                    val values = Sadp.parse(bytes, response.length, id, response.address)
                    if (values.isNotEmpty() && seen.add(response.address.hostAddress)) {
                        if (seen.size > 64) { health.truncated(source, "64-device safety limit reached"); break }
                        health.count(source, "devices"); sadpResolved(response.address, values)
                    }
                }
                if (!cancelled.get()) {
                    if (replies >= 128) health.truncated(source, "128-packet safety limit reached")
                    else if (System.nanoTime() >= end) health.ok(source)
                    else health.truncated(source, "Discovery window stopped early")
                }
            } catch (e: Exception) { if (accepting && !cancelled.get()) { blocked(e); health.fail(source, "Could not receive device inquiries") } }
            finally { socket?.let { datagrams.remove(it); it.close() } }
        }, "sadp-discovery").also { it.start() }
    }
    private fun sadpResolved(responder: InetAddress, values: Map<String, String>) {
        if (!inScope(responder)) return
        val d = observe(net.scoped(responder), "Correlated SADP inquiry response") ?: return
        synchronized(d) {
            val source = "SADP reported device data"
            d.service("Hikvision SADP discovery")
            d.hint("Model", values["DeviceDescription"], source, 85)
            d.hint("Name", values["DeviceDescription"], "SADP reported model", 45)
            d.hint("Firmware", values["SoftwareVersion"], source, 85)
            d.hardware(values["MAC"], "SADP reported adapter address")
            d.port(Sadp.port(values["HttpPort"])); d.port(Sadp.port(values["CommandPort"]))
            d.detail("SADP device family", values["SpecificDeviceType"])
            val channels = values["DigitalChannelNum"]
            if (channels != null && channels.matches(Regex("[0-9]{1,4}"))) {
                d.detail("Video channels (advertised)", channels)
                if (Integer.parseInt(channels) > 0) d.hint("Device type", "Video surveillance device", "SADP advertised video channels", 75)
            }
        }
        queryDeviceName(d)
    }
    private fun startWsDiscovery() {
        if (net.ip.isEmpty()) { health.skipped(WSD_SOURCE, "IPv4 discovery unavailable"); return }
        wsThread = Thread({
            var socket: MulticastSocket? = null
            health.begin(WSD_SOURCE)
            try {
                if (!accepting || cancelled.get()) return@Thread
                val iface = NetworkInterface.getByName(net.iface) ?: throw IOException("Wi-Fi interface unavailable")
                socket = MulticastSocket(null); datagrams.add(socket); socket.bind(InetSocketAddress(0))
                net.network!!.bindSocket(socket); socket.networkInterface = iface; socket.timeToLive = 1; socket.soTimeout = 250
                val versions = WsDiscovery.Version.values()
                val ids = arrayOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
                val requests = arrayOf(WsDiscovery.query(versions[0], ids[0]), WsDiscovery.query(versions[1], ids[1]))
                val group = InetAddress.getByName("239.255.255.250")
                val start = System.nanoTime(); val end = start + TimeUnit.SECONDS.toNanos(5)
                var sent = 0; var packets = 0
                while (accepting && !cancelled.get() && System.nanoTime() < end && packets < 256) {
                    if (sent < 2 && System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos((sent * 1200).toLong())) {
                        for (request in requests) {
                            if (!accepting || cancelled.get()) break
                            socket.send(DatagramPacket(request, request.size, group, 3702)); health.count(WSD_SOURCE, "requests")
                        }
                        sent++
                    }
                    val bytes = ByteArray(WsDiscovery.MAX_PACKET + 1); val reply = DatagramPacket(bytes, bytes.size)
                    try { socket.receive(reply) } catch (_: SocketTimeoutException) { continue }
                    packets++
                    if (!inScope(reply.address)) { health.count(WSD_SOURCE, "ignored packets"); continue }
                    var matched = false
                    for (i in versions.indices) if (wsPacket(reply.address, bytes, reply.length, versions[i], ids[i]) > 0) { matched = true; break }
                    health.count(WSD_SOURCE, if (matched) "correlated replies" else "ignored packets")
                }
                if (!cancelled.get()) {
                    if (packets >= 256) health.truncated(WSD_SOURCE, "256-packet safety limit reached")
                    else if (System.nanoTime() >= end) health.ok(WSD_SOURCE)
                    else health.truncated(WSD_SOURCE, "Discovery window stopped early")
                }
            } catch (e: Exception) { if (accepting && !cancelled.get()) { blocked(e); health.fail(WSD_SOURCE, "Could not receive local discovery replies") } }
            finally { socket?.let { datagrams.remove(it); it.close() } }
        }, "ws-discovery").also { it.start() }
    }
    private fun wsPacket(responder: InetAddress, bytes: ByteArray, length: Int, version: WsDiscovery.Version, id: String): Int {
        if (!accepting || cancelled.get() || !inScope(responder)) return 0
        val records = WsDiscovery.parse(bytes, length, version, id, responder)
        val matched = records.size; val ip = net.scoped(responder)
        for (record in records) {
            val peer = wsPeers.admit(ip, "WSD")
            if (peer == ServiceAdmission.Result.LIMIT) { health.truncated(WSD_SOURCE, "64-device safety limit reached"); break }
            val fingerprint = try {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                val digest = hash.digest(record.fingerprint().toByteArray(StandardCharsets.UTF_8))
                val hex = StringBuilder()
                for (b in digest) hex.append(String.format(Locale.ROOT, "%02x", b.toInt() and 255))
                hex.toString()
            } catch (e: java.security.NoSuchAlgorithmException) { throw AssertionError(e) }
            val admission = wsRecords.admit(fingerprint, ip)
            if (admission == ServiceAdmission.Result.LIMIT) { health.truncated(WSD_SOURCE, "8-per-device / 256-total record limit reached"); continue }
            if (admission != ServiceAdmission.Result.ADMITTED) continue
            val d = observe(ip, "Correlated WS-Discovery ProbeMatches response") ?: continue
            if (peer == ServiceAdmission.Result.ADMITTED) health.count(WSD_SOURCE, "devices")
            synchronized(d) {
                d.service("WS-Discovery")
                if (!d.self) {
                    for (name in record.names) d.hint("Name", name, "ONVIF advertised name scope", 85)
                    if (record.hardware.isNotEmpty()) d.detail("ONVIF hardware (advertised)", record.hardware.joinToString("; "))
                    for (mac in record.macs) d.hardware(mac, "ONVIF advertised MAC scope; may be another interface")
                    if (record.kind.isNotEmpty()) {
                        d.service("WS-Discovery: " + record.kind)
                        d.hint("Device type", record.kind, "WS-Discovery advertised service type", if (record.kind == "Printer service") 80 else if (record.kind == "Video surveillance device") 75 else 40)
                    }
                    for (port in record.ports) d.port(port)
                    if (record.identity.isNotEmpty()) d.link(record.identity)
                }
            }
            lastServiceChange = System.nanoTime(); queryDeviceName(d)
        }
        return matched
    }
    private fun blocked(e: Throwable) {
        var t: Throwable? = e
        while (t != null) {
            if (t is SecurityException || t is ErrnoException && (t.errno == OsConstants.EACCES || t.errno == OsConstants.EPERM)) {
                warning.compareAndSet(null, "Local-network access was blocked by Android or a VPN. Check the app's network permission and VPN settings.")
                health.fail("Local network access", "Android or a VPN blocked discovery"); return
            }
            t = t.cause
        }
    }
    fun snapshot(): List<Device> {
        val out = ArrayList<Device>()
        for (d in found.values) if (freshResponders.contains(d.ip) && net.contains(d)) out.add(d.copy())
        out.sortWith { a, b -> Address.compare(a.ip, b.ip) }
        return out
    }
    private fun queryDeviceName(d: Device) {
        // Router PTR/DHCP records can outlive clients; use current peer mDNS only.
        queueMdnsName(d)
    }
    private fun submitEnrichment(source: String, action: Runnable): Boolean = submitEnrichment(DiscoveryWork.Lane.NAMES, source, action)
    private fun submitEnrichment(lane: DiscoveryWork.Lane, source: String, action: Runnable): Boolean {
        if (enrichment.submit(lane) { if (enriching && !cancelled.get()) action.run() }) return true
        if (!cancelled.get()) health.truncated(source, "Device detail queue limit reached")
        return false
    }
    private fun queueDetails(d: Device, connectTimeout: Int, nameTimeout: Int) {
        maybeCast(d)
        if (!enriching || cancelled.get() || !detailsQueued.add(d.ip)) return
        val source = "Device port and name checks"; health.workStarted(source)
        if (!submitEnrichment(DiscoveryWork.Lane.DETAILS, source, Runnable {
            try {
                enrichNetBios(d.ip, d, nameTimeout)
                if (connectTimeout > 0) for (port in PORTS) {
                    if (!enriching || cancelled.get()) return@Runnable
                    val socket = Socket(); sockets.add(socket)
                    try {
                        net.network!!.bindSocket(socket); val start = System.nanoTime()
                        socket.connect(InetSocketAddress(d.ip, port), connectTimeout)
                        synchronized(d) {
                            if (!enriching || cancelled.get()) return@Runnable
                            d.port(port); d.latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).toInt()
                        }
                        break
                    } catch (e: Exception) { if (refused(e)) break; blocked(e) }
                    finally { sockets.remove(socket); try { socket.close() } catch (_: IOException) {} }
                }
                val retry = synchronized(d) { d.hostname.isEmpty() || d.mac.isEmpty() }
                if (retry && enriching && !cancelled.get()) enrichNetBios(d.ip, d, minOf(400, nameTimeout + 180))
            } finally { health.workFinished(source) }
        })) health.workFinished(source)
    }
    private fun confirmService(d: Device, address: InetAddress, port: Int) {
        if (!accepting || freshResponders.contains(d.ip) || port < 1 || port > 65535 || !confirmQueued.add(d.ip)) return
        val source = "Service address checks"; health.workStarted(source)
        if (!submitEnrichment(DiscoveryWork.Lane.CONFIRM, source, Runnable {
            try {
                if (!enriching || cancelled.get()) return@Runnable
                net.check(context)
                val result = confirmTcp(address, port, 400, source)
                if (result.status == TcpCheck.Status.CONFIRMED) {
                    synchronized(d) { d.latency = result.latency; d.detail("Presence check", "Two new TCP connections, at least 200 ms apart") }
                    synchronized(d) { d.port(port) }
                    confirmExisting(d, "Connection to advertised service (confirmed twice)")
                }
            } catch (e: Exception) { blocked(e) }
            finally { health.workFinished(source) }
        })) health.workFinished(source)
    }
    private fun confirmExisting(d: Device, evidence: String) {
        if (!enriching || cancelled.get()) return
        freshResponders.add(d.ip)
        synchronized(d) { d.lastSeen = System.currentTimeMillis(); d.seen = true; d.evidence(evidence) }
        if (inScope(peerAddress(d.ip))) { queryDeviceName(d); maybeCast(d) }
    }
    private fun maybeCast(d: Device) {
        if (!enriching || cancelled.get() || !freshResponders.contains(d.ip)) return
        synchronized(d) { if (d.self || d.services.none { canonicalType(it) == "_googlecast._tcp." } || !CastInfo.needsName(d.name)) return }
        val source = "Cast device names"; val admitted = castRequests.admit(d.ip, "name")
        if (admitted == ServiceAdmission.Result.LIMIT) { health.truncated(source, "32-device name request limit reached"); return }
        if (admitted != ServiceAdmission.Result.ADMITTED) return
        health.workStarted(source)
        if (!submitEnrichment(DiscoveryWork.Lane.CAST, source, Runnable {
            val socket = Socket(); sockets.add(socket); val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500)
            try {
                synchronized(d) { if (!CastInfo.needsName(d.name)) return@Runnable }
                if (!enriching || cancelled.get()) return@Runnable
                net.check(context)
                val peer = InetAddress.getByName(d.ip)
                if (!inScope(peer)) return@Runnable
                net.network!!.bindSocket(socket); socket.connect(InetSocketAddress(peer, 8008), 400)
                socket.getOutputStream().write(CastInfo.request(peer)); health.count(source, "requests")
                val input = socket.getInputStream()
                val bounded = object : FilterInputStream(input) {
                    var bytes = 0
                    private fun remaining(n: Int) {
                        bytes += n
                        if (bytes > CastInfo.MAX_BODY + 8192 || System.nanoTime() >= deadline || !enriching || cancelled.get()) throw IOException("Cast response limit")
                        val left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                        socket.soTimeout = maxOf(1, minOf(400, left)).toInt()
                    }
                    override fun read(): Int {
                        remaining(0); val n = input.read(); if (n >= 0) remaining(1); return n
                    }
                    override fun read(b: ByteArray, o: Int, n: Int): Int {
                        remaining(0); val got = input.read(b, o, minOf(n, 2048)); if (got > 0) remaining(got); return got
                    }
                }
                val name = CastInfo.name(UpnpDescription.readResponse(bounded))
                if (name.isNotEmpty()) synchronized(d) {
                    if (enriching && !cancelled.get() && CastInfo.needsName(d.name)) { d.hint("Name", name, "Cast device reported name", 70); health.count(source, "names") }
                } else health.count(source, "no extra name")
            } catch (e: Exception) { blocked(e); health.count(source, "unavailable") }
            finally { sockets.remove(socket); try { socket.close() } catch (_: IOException) {}; health.workFinished(source) }
        })) health.workFinished(source)
    }
    /** Called only with current wire records, never Android's cached/native service metadata. */
    private fun requestMetadata(d: Device, service: MdnsPacket.Service) {
        if (!enriching || cancelled.get() || d.self || !freshResponders.contains(d.ip)) return
        val peer = peerAddress(d.ip)
        if (!inScope(peer)) return
        val request = LocalMetadata.request(service) ?: return
        val source = request.kind.source
        // One attempt per peer/protocol: changing TXT paths or ports cannot grow work.
        val admission = metadataRequests.admit(d.ip, request.kind.name)
        if (admission == ServiceAdmission.Result.LIMIT) { health.truncated(source, "32-per-method / 96-total metadata limit reached"); return }
        if (admission != ServiceAdmission.Result.ADMITTED) return
        health.workStarted(source)
        if (!submitEnrichment(DiscoveryWork.Lane.METADATA, source, Runnable {
            val socket = Socket(); sockets.add(socket)
            val deadline = minOf(scanDeadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(2))
            try {
                if (!enriching || cancelled.get()) return@Runnable
                if (System.nanoTime() >= deadline) { health.truncated(source, "Scan time limit reached"); return@Runnable }
                net.check(context)
                if (!inScope(peer) || !freshResponders.contains(d.ip)) return@Runnable
                net.network!!.bindSocket(socket)
                health.count(source, "requests")
                val report = MetadataHttp.exchange(socket, peer, request, deadline, metadataTimeouts) { enriching && !cancelled.get() }
                if (!enriching || cancelled.get()) return@Runnable
                net.check(context)
                if (!inScope(peer) || !freshResponders.contains(d.ip)) return@Runnable
                synchronized(d) {
                    if (enriching && !cancelled.get()) {
                        applyMetadata(d, report, source + " (reported)")
                        health.count(source, if (report.hints.values.any { it.isNotEmpty() } || report.mac.isNotEmpty() || report.details.values.any { it.isNotEmpty() }) "metadata replies" else "no extra details")
                    }
                }
            } catch (e: Exception) {
                blocked(e)
                if (!cancelled.get()) {
                    MetadataHttp.recordFailure(health, source, e, !enriching || System.nanoTime() >= deadline)
                }
            } finally {
                sockets.remove(socket); try { socket.close() } catch (_: IOException) {}
                health.workFinished(source)
            }
        })) health.workFinished(source)
    }
    private fun applyMetadata(d: Device, report: LocalMetadata.Report, source: String) {
        for ((field, value) in report.hints) d.hint(field, value, source, if (field == "Name") 90 else 80)
        for ((field, value) in report.details) d.detail(field, value)
        if (report.mac.isNotEmpty()) d.hardware(report.mac, source + " MAC address; may be another interface")
    }
    private fun description(d: Device, responder: InetAddress, location: String?) {
        if (!enriching || !inScope(responder) || d.self) return
        val target = UpnpDescription.target(location, responder)
        if (target == null) { if (location != null) health.count("UPnP descriptions", "unsupported URLs"); return }
        val offer = descriptions.offer(d.ip, target)
        if (offer == DescriptionQueue.Offer.LIMIT) { health.truncated("UPnP descriptions", "32-device / 3-location description limit reached"); return }
        if (offer != DescriptionQueue.Offer.START) return
        health.begin("UPnP descriptions")
        submitEnrichment(DiscoveryWork.Lane.DESCRIPTIONS, "UPnP descriptions", Runnable {
            while (enriching && !cancelled.get()) {
                val next = descriptions.next(d.ip) ?: break
                fetchDescription(d, responder, next)
            }
        })
    }
    private fun fetchDescription(d: Device, responder: InetAddress, target: URI) {
        val socket = Socket(); sockets.add(socket); val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        health.count("UPnP descriptions", "requests")
        try {
            if (!enriching || cancelled.get()) return
            net.check(context); net.network!!.bindSocket(socket)
            socket.connect(InetSocketAddress(responder, if (target.port < 0) 80 else target.port), 500)
            socket.getOutputStream().write(UpnpDescription.request(target))
            val input = socket.getInputStream()
            val bounded = object : FilterInputStream(input) {
                private fun remaining() {
                    val left = deadline - System.nanoTime()
                    if (left <= 0 || !enriching || cancelled.get()) throw SocketTimeoutException()
                    socket.soTimeout = maxOf(1, minOf(500, TimeUnit.NANOSECONDS.toMillis(left))).toInt()
                }
                override fun read(): Int { remaining(); return super.read() }
                override fun read(b: ByteArray, o: Int, n: Int): Int { remaining(); return input.read(b, o, n) }
            }
            val values = UpnpDescription.parse(UpnpDescription.readResponse(bounded))
            if (values.isEmpty()) throw IOException("No supported description fields")
            synchronized(d) {
                if (!enriching || cancelled.get()) return
                val source = "UPnP device description"
                d.hint("Name", values["friendlyName"], source + " friendly name", 80)
                d.hint("Manufacturer", values["manufacturer"], source, 70); d.hint("Model", values["modelName"], source, 70)
                d.detail("UPnP model number", values["modelNumber"]); d.detail("UPnP serial number", values["serialNumber"])
                d.detail("UPnP device type", values["deviceType"]); d.detail("UPnP description status", "Description received")
            }
            descriptions.record(d.ip, true); health.count("UPnP descriptions", "received")
        } catch (e: Exception) {
            if (enriching && !cancelled.get()) {
                blocked(e); descriptions.record(d.ip, false); health.count("UPnP descriptions", "failed attempts")
                if (!descriptions.hasSuccess(d.ip)) {
                    val reason = if (e is SocketTimeoutException) "Description request timed out" else if (e is ConnectException) "Description connection failed" else "Description unavailable or unsupported"
                    synchronized(d) { d.detail("UPnP description status", reason) }
                }
            }
        } finally { sockets.remove(socket); try { socket.close() } catch (_: IOException) {} }
    }
    private fun finishDescriptions() {
        if (descriptions.peers() == 0) return
        val failed = descriptions.failedPeers()
        if (failed > 0) health.fail("UPnP descriptions", "" + failed + " responding device(s) supplied no usable description; see device details")
        else health.ok("UPnP descriptions")
    }
    @Synchronized private fun startNsd() {
        if (!accepting) return
        val now = System.nanoTime(); nsdStarted = now; lastServiceChange = now
        try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicast = wm.createMulticastLock("device-discovery").also { it.setReferenceCounted(false); it.acquire() }
        } catch (e: Exception) { health.fail("Multicast reception", "Android could not enable multicast reception"); blocked(e) }
        try {
            nsd = NSD_HANDLE.acquire({
                // Android 8 can hold a Context's entire service-cache monitor while
                // constructing NSD. A timed-out constructor must not block UI/audio
                // or connectivity lookups through the application's Context cache.
                val serviceContext = context.createConfigurationContext(Configuration(context.resources.configuration))
                serviceContext.getSystemService(Context.NSD_SERVICE) as NsdManager?
            },
                { cancelled.get() || !accepting }, 1500)
            if (!accepting || cancelled.get()) return
            if (nsd == null) throw IllegalStateException()
            for (type in LocalServices.INITIAL) discoverType(type)
        } catch (e: Exception) {
            if (!cancelled.get()) {
                health.fail("mDNS", if (e is TimeoutException) "Android service initialization timed out; direct queries continue" else "Android service discovery unavailable; direct queries continue")
                blocked(e)
                // Direct multicast discovery does not depend on Android NSD.
                nsdTypes.addAll(LocalServices.INITIAL)
            }
        }
    }
    @Synchronized private fun discoverType(type: String) {
        if (!accepting || nsdTypes.contains(type)) return
        if (nsdTypes.size >= 48) { health.truncated(MDNS_SOURCE, "48-service-type limit reached"); return }
        nsdTypes.add(type); health.starting(type)
        if (nsd == null) { health.startFailed(type); return }
        val browse = if (Build.VERSION.SDK_INT < 34) LegacyBrowse(type) else null
        val listener = object : NsdManager.DiscoveryListener {
            // Legacy callbacks can hold Android's listener-map lock. Post before taking our monitor.
            private fun deliver(task: Runnable) { if (browse != null) resolverTimeouts.post(task) else task.run() }
            override fun onDiscoveryStarted(t: String) {
                deliver {
                    if (accepting) {
                        health.started(type)
                        if (browse != null) {
                            browse.timeout = Runnable { stopLegacyBrowse(browse) }
                            resolverTimeouts.postDelayed(browse.timeout!!, 3000)
                        }
                    }
                }
            }
            override fun onDiscoveryStopped(t: String) { if (browse != null) deliver { finishLegacyBrowse(browse, true) } }
            override fun onStartDiscoveryFailed(t: String, error: Int) {
                deliver { if (accepting) { health.startFailed(type); health.fail("mDNS " + type, "Android discovery error " + error); if (browse != null) finishLegacyBrowse(browse, false) } }
            }
            override fun onStopDiscoveryFailed(t: String, error: Int) { deliver { if (accepting) health.fail("mDNS " + type, "Android could not stop discovery; error " + error) } }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) { if (admitService(type, info)) deliver { serviceFound(type, info) } }
        }
        if (browse != null) { browse.listener = listener; legacyBrowses.add(browse); startLegacyBrowses() }
        else {
            nsdListeners.add(listener)
            try { nsd!!.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, net.network, java.util.concurrent.Executor { it.run() }, listener) }
            catch (e: Exception) { health.startFailed(type); blocked(e) }
        }
    }
    private fun admitService(type: String, info: NsdServiceInfo): Boolean {
        if (!accepting) return false
        if (directServices.contains(wireServiceKey(info.serviceName, type))) return false
        // Bound admission before posting callbacks from untrusted advertisements.
        val result = services.admit(info.serviceName?.lowercase(Locale.ROOT), type)
        if (result == ServiceAdmission.Result.LIMIT) {
            health.count("mDNS " + type, "limited"); health.truncated("mDNS " + type, "32-per-type / 256-total service limit reached"); return false
        }
        if (result == ServiceAdmission.Result.INVALID) { health.truncated("mDNS " + type, "Advertised service exceeded text limits"); return false }
        if (result != ServiceAdmission.Result.ADMITTED) return false
        health.count("mDNS " + type, "found"); lastServiceChange = System.nanoTime(); health.resolving(serviceKey(info), type)
        return true
    }
    @Synchronized private fun serviceFound(type: String, info: NsdServiceInfo) {
        if (!accepting) return
        if (Build.VERSION.SDK_INT >= 34) { modernQueue.add(ModernRequest(info, 0)); resolveModern() }
        else { queue.add(info); resolveNext() }
    }
    /** Older Android limits outstanding NSD requests. Leave room for registration and resolution. */
    @Synchronized private fun startLegacyBrowses() {
        if (!accepting || nsd == null) return
        while (legacyActive.size < 6 && legacyBrowses.isNotEmpty()) {
            val browse = legacyBrowses.remove(); legacyActive.add(browse); nsdListeners.add(browse.listener)
            try {
                if (Build.VERSION.SDK_INT >= 33) nsd!!.discoverServices(browse.type, NsdManager.PROTOCOL_DNS_SD, net.network, java.util.concurrent.Executor { it.run() }, browse.listener)
                else nsd!!.discoverServices(browse.type, NsdManager.PROTOCOL_DNS_SD, browse.listener)
            } catch (e: Exception) { health.startFailed(browse.type); blocked(e); finishLegacyBrowse(browse, false) }
        }
    }
    @Synchronized private fun stopLegacyBrowse(browse: LegacyBrowse) {
        if (!accepting || !legacyActive.contains(browse) || browse.stopping) return
        browse.stopping = true
        try { nsd!!.stopServiceDiscovery(browse.listener) }
        catch (e: Exception) { health.fail("mDNS " + browse.type, "Android could not stop discovery"); blocked(e) }
    }
    @Synchronized private fun finishLegacyBrowse(browse: LegacyBrowse, success: Boolean) {
        if (!legacyActive.remove(browse)) return
        nsdListeners.remove(browse.listener); browse.timeout?.let { resolverTimeouts.removeCallbacks(it) }
        if (accepting) { if (success) health.ok("mDNS " + browse.type); resolverTimeouts.post { startLegacyBrowses() } }
    }
    @Synchronized private fun resolveModern() {
        if (Build.VERSION.SDK_INT < 34 || !accepting) return
        while (modernActive.size < 12 && modernQueue.isNotEmpty()) {
            val request = modernQueue.remove(); modernActive.add(request)
            request.callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistered() { synchronized(this@Discovery) { if (accepting && !request.done) request.registered = true } }
                override fun onServiceInfoCallbackRegistrationFailed(error: Int) { finishModern(request, false, "Android registration error " + error) }
                override fun onServiceInfoCallbackUnregistered() {}
                override fun onServiceLost() { synchronized(this@Discovery) { finishModern(request, request.usable, "Service disappeared before resolution") } }
                override fun onServiceUpdated(info: NsdServiceInfo) { modernUpdated(request, info) }
            }
            request.startedAt = System.nanoTime()
            request.timeout = Runnable { synchronized(this@Discovery) {
                val state = if (request.emptyUpdate) "updates had no local addresses" else if (request.registered) "registered; no addresses" else "registration unconfirmed"
                finishModern(request, request.usable, "No usable service data after " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - request.startedAt) + " ms (" + state + ")")
            } }
            resolverTimeouts.postDelayed(request.timeout!!, 4000); nsdCallbacks.add(request.callback!!)
            try { nsd!!.registerServiceInfoCallback(request.info, java.util.concurrent.Executor { it.run() }, request.callback!!) }
            catch (e: Exception) { blocked(e); finishModern(request, false, "Android registration threw " + e.javaClass.simpleName) }
        }
    }
    @Synchronized private fun modernUpdated(request: ModernRequest, info: NsdServiceInfo) {
        if (!accepting || request.done) return
        if (info.hostAddresses.isEmpty() || info.network != null && net.network != info.network) { request.emptyUpdate = true; return }
        if (info.hostAddresses.none { candidateAllowed(it, info.serviceType) }) {
            info.hostAddresses.forEach(::excludedV6); request.emptyUpdate = true; return
        }
        resolved(info); request.usable = true
        // Continuous callback briefly accepts later TXT/address updates within the fixed hard deadline.
        if (request.settle == null) {
            request.settle = Runnable { finishModern(request, true, "") }
            resolverTimeouts.postDelayed(request.settle!!, 1000)
        }
    }
    @Synchronized private fun finishModern(request: ModernRequest, success: Boolean, reason: String) {
        if (!accepting || request.done) return
        request.done = true; request.timeout?.let { resolverTimeouts.removeCallbacks(it) }; request.settle?.let { resolverTimeouts.removeCallbacks(it) }
        modernActive.remove(request); nsdCallbacks.remove(request.callback)
        try { nsd!!.unregisterServiceInfoCallback(request.callback!!) } catch (_: Exception) {}
        val type = canonicalType(request.info.serviceType); val key = serviceKey(request.info)
        val recovered = !success && directServices.contains(wireServiceKey(request.info.serviceName, type))
        if (success || recovered) { health.resolved(key); health.count("mDNS " + type, if (recovered) "live reply recovery" else "resolved"); lastServiceChange = System.nanoTime() }
        else if (request.attempt == 0) {
            if (request.registered && nsdTypes.contains(type)) mdnsRetries.request(type)
            modernQueue.add(ModernRequest(request.info, 1, reason)); health.count("mDNS " + type, "retried")
        } else {
            // A live snapshot must not observe Failed before its prior-attempt
            // reason and terminal counter have been published.
            synchronized(health) {
                nativeServiceEvidence.failed(request.info.serviceName, type, System.nanoTime())?.let { health.diagnostic("mDNS " + type, it) }
                health.resolveFailed(key, reason)
                if (request.previousFailure.isNotEmpty()) health.fail("mDNS " + type, "First attempt: " + request.previousFailure)
                health.count("mDNS " + type, "failed")
            }
        }
        resolverTimeouts.post { resolveModern() }
    }
    @Synchronized private fun queueMdnsName(d: Device) {
        val address = Address.bytes(d.ip)
        if (!accepting || address == null || address.size != 4 && address.size != 16 || mdnsQuestions.containsKey(d.ip)) return
        synchronized(d) { if (d.self || d.hostname.isNotEmpty()) return }
        val admission = multicastNames.admit(d.ip, "PTR")
        if (admission == ServiceAdmission.Result.LIMIT) { health.truncated(MDNS_SOURCE, "128-address hostname-query limit reached"); return }
        if (admission != ServiceAdmission.Result.ADMITTED) return
        mdnsQuestions[d.ip] = ReverseQuestion(d.ip)
    }
    @Synchronized private fun mdnsPending(): Boolean = mdnsQuestions.values.any { it.attempts < 2 } || mdnsAssembly.pending(System.nanoTime())
    private fun startMdns() {
        val ipv6 = net.addresses.any { Address.bytes(it)?.size == 16 }
        if (net.ip.isNotEmpty()) mdnsThread = startMdnsFamily(false)
        if (ipv6) mdnsV6Thread = startMdnsFamily(true)
    }
    private fun startMdnsFamily(ipv6: Boolean): Thread {
        val thread = Thread({
            var socket: MulticastSocket? = null; health.begin(MDNS_SOURCE)
            try {
                val iface = NetworkInterface.getByName(net.iface) ?: throw IOException("Wi-Fi interface unavailable")
                socket = MulticastSocket(null); datagrams.add(socket); socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName(if (ipv6) "::" else "0.0.0.0"), 5353))
                net.network!!.bindSocket(socket); socket.networkInterface = iface; socket.timeToLive = 255; socket.soTimeout = 200
                val group = InetAddress.getByName(if (ipv6) "ff02::fb%" + net.iface else "224.0.0.251")
                socket.joinGroup(InetSocketAddress(group, 5353), iface)
                val start = System.nanoTime(); var nextBatch = 0L; var enumerations = 0
                val traffic = MdnsTraffic(health, ipv6)
                val typeAttempts = HashMap<String, Int>(); val typeNext = HashMap<String, Long>()
                while (accepting && !cancelled.get()) {
                    val now = System.nanoTime()
                    if (enumerations < 2 && now - start >= TimeUnit.SECONDS.toNanos((enumerations * 2).toLong())) {
                        val question = MdnsPacket.query(Collections.singletonList(MdnsPacket.ENUMERATION))
                        socket.send(DatagramPacket(question, question.size, group, 5353)); enumerations++; health.count(MDNS_SOURCE, "type queries")
                        health.diagnostic(MDNS_SOURCE, DiscoveryHealth.Diagnostic.MDNS_SENT)
                    }
                    if (now >= nextBatch) {
                        drainMdns(now)
                        val batch = mdnsBatch(ipv6, now, typeAttempts, typeNext)
                        if (batch.questions.isNotEmpty()) {
                            val known = mdnsAssembly.knownAnswers(ipv6, batch.questions, now)
                            for (query in MdnsPacket.queryPackets(batch.questions, known)) {
                                if (!accepting || cancelled.get()) break
                                socket.send(DatagramPacket(query, query.size, group, 5353))
                                health.diagnostic(MDNS_SOURCE, DiscoveryHealth.Diagnostic.MDNS_SENT)
                                val answers = ((query[6].toInt() and 255) shl 8) or (query[7].toInt() and 255)
                                if (answers > 0) health.diagnostic(MDNS_SOURCE, DiscoveryHealth.Diagnostic.MDNS_KNOWN_ANSWERS, answers)
                            }
                            health.count(MDNS_SOURCE, "hostname batches"); if (batch.retry) health.count(MDNS_SOURCE, "service retry batches")
                        }
                        val followUps = mdnsAssembly.due(ipv6, now, 4).filter { shouldAsk(ipv6, it, now) }
                        if (followUps.isNotEmpty()) {
                            val query = MdnsPacket.followUp(followUps)
                            socket.send(DatagramPacket(query, query.size, group, 5353))
                            health.diagnostic(MDNS_SOURCE, DiscoveryHealth.Diagnostic.MDNS_SENT)
                            health.count("mDNS service details", "query batches")
                        }
                        if (mdnsAssembly.limited) health.truncated("mDNS service details", "Current-record or follow-up limit reached")
                        nextBatch = now + TimeUnit.MILLISECONDS.toNanos(250)
                    }
                    val bytes = ByteArray(MdnsPacket.MAX_PACKET + 1); val packet = DatagramPacket(bytes, bytes.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val response = traffic.receive(bytes, packet.length, packet.port, inScope(packet.address)) { questions ->
                        if (accepting && !cancelled.get()) mdnsQuestionSuppression.observe(packet.address.address.size == 16, questions, System.nanoTime())
                    }
                    if (traffic.limited) break
                    if (response != null) mdnsResolved(packet.address, response)
                }
                if (!cancelled.get()) health.ok(MDNS_SOURCE)
            } catch (e: Exception) { if (accepting && !cancelled.get()) { blocked(e); health.fail(MDNS_SOURCE, "Could not receive local multicast queries") } }
            finally { socket?.let { datagrams.remove(it); it.close() } }
        }, if (ipv6) "mdns-ipv6-discovery" else "mdns-query-discovery")
        thread.start(); return thread
    }
    private class MdnsBatch(names: List<String>, val retry: Boolean) {
        val questions: List<String> = Collections.unmodifiableList(names)
    }
    private fun shouldAsk(ipv6: Boolean, question: MdnsPacket.Question, now: Long): Boolean {
        if (!mdnsQuestionSuppression.suppress(ipv6, question, now)) return true
        health.diagnostic(MDNS_SOURCE, DiscoveryHealth.Diagnostic.MDNS_COALESCED)
        return false
    }
    @Synchronized private fun mdnsBatch(ipv6: Boolean, now: Long, typeAttempts: MutableMap<String, Int>, typeNext: MutableMap<String, Long>): MdnsBatch {
        val questions = ArrayList<String>()
        val retries = mdnsRetries.due(ipv6, now, 8)
        for (type in retries) if (shouldAsk(ipv6, MdnsPacket.Question((type + "local").split('.'), 12), now)) questions.add(type + "local")
        // Recovery queries do not consume normal reverse/type attempts.
        if (retries.isNotEmpty()) return MdnsBatch(questions, true)
        for (q in mdnsQuestions.values) if ((Address.bytes(q.ip)!!.size == 16) == ipv6 && q.attempts < 2 && now >= q.next) {
            if (shouldAsk(ipv6, MdnsPacket.Question(q.question.split('.'), 12), now)) questions.add(q.question)
            q.attempts++; q.next = now + TimeUnit.MILLISECONDS.toNanos(1200)
            if (questions.size == 8) break
        }
        for (type in TreeSet(nsdTypes)) {
            if (questions.size == 8) break
            if (typeAttempts.getOrDefault(type, 0) < 2 && now >= typeNext.getOrDefault(type, 0L)) {
                if (shouldAsk(ipv6, MdnsPacket.Question((type + "local").split('.'), 12), now)) questions.add(type + "local")
                typeAttempts[type] = typeAttempts.getOrDefault(type, 0) + 1
                typeNext[type] = now + TimeUnit.MILLISECONDS.toNanos(1200)
            }
        }
        return MdnsBatch(questions, false)
    }
    @Synchronized private fun mdnsResolved(responder: InetAddress, result: MdnsPacket.Result) {
        if (!accepting || !inScope(responder)) return
        if (result.freshResponse || result.serviceTypes.isNotEmpty()) observe(net.scoped(responder), "Fresh mDNS response")?.let { queryDeviceName(it); maybeCast(it) }
        val peer = net.scoped(responder)
        for (type in result.serviceTypes) {
            val known = nsdTypes.contains(type); discoverType(type)
            if (!known && nsdTypes.contains(type)) { health.count(MDNS_SOURCE, "new types"); lastServiceChange = System.nanoTime() }
        }
        // Assemble only this scan's unexpired records from the same endpoint.
        // A proxy's records for another address cannot identify that endpoint.
        val recovered = mdnsAssembly.accept(responder, result, System.nanoTime())
        if (result.records.isNotEmpty()) { health.begin("mDNS service details"); health.ok("mDNS service details") }
        if (mdnsAssembly.limited) health.truncated("mDNS service details", "Current-record or follow-up limit reached")
        applyRecovered(peer, recovered)
        applyRouted(System.nanoTime())
        val ip = net.scoped(responder); val question = mdnsQuestions[ip] ?: return
        if (question.attempts == 0) return
        val hostname = result.hostnames[question.question]; val d = found[ip]
        if (hostname != null && d != null) {
            synchronized(d) {
                if (d.self) return
                d.hint("Hostname", hostname, "mDNS reverse hostname", 55); d.hint("Name", hostname, "mDNS reverse hostname", 50)
                d.evidence("Matching mDNS reverse-PTR reply")
            }
            freshResponders.add(ip); mdnsQuestions.remove(ip); health.count(MDNS_SOURCE, "hostnames"); lastServiceChange = System.nanoTime()
        }
    }
    @Synchronized private fun drainMdns(now: Long) {
        if (!accepting || cancelled.get()) return
        for ((sender, services) in mdnsAssembly.ready(now)) {
            if (!inScope(sender)) continue
            val peer = net.scoped(sender)
            if (freshResponders.contains(peer)) applyRecovered(peer, services)
        }
        applyRouted(now)
    }
    private fun applyRouted(now: Long, only: String? = null) {
        if (!enriching || cancelled.get()) return
        for (candidate in mdnsAssembly.routedCandidates(now)) {
            if (!inScope(candidate.sender) || net.local(candidate.address)) continue
            val ip = net.scoped(candidate.address)
            if (only != null && ip != only) continue
            if (!net.routed(candidate.address)) { excludedV6(candidate.address); continue }
            val d = found[ip] ?: observe(ip, "Routed service candidate (not presence proof)", false) ?: continue
            if (freshResponders.contains(ip)) {
                applyService(d, candidate.service, "Wi-Fi proxy advertisement")
                synchronized(d) {
                    d.detail("Network path", "Routed IPv6 through Wi-Fi")
                    d.detail("Service details source", "Advertised on Wi-Fi; endpoint separately confirmed by ICMPv6")
                    d.evidence("Current routed accessory records; separate endpoint echo reply")
                }
            } else confirmIpv6(d, candidate.address)
        }
    }
    private fun applyRecovered(peer: String, recovered: List<MdnsPacket.Service>) {
        for (service in recovered) {
            if (!nsdTypes.contains(service.type)) continue
            val key = wireServiceKey(service.name, service.type)
            if (!directServices.contains(key)) {
                val admission = services.admit(service.name.lowercase(Locale.ROOT), service.type)
                if (admission == ServiceAdmission.Result.LIMIT) { health.truncated("mDNS " + service.type, "32-per-type / 256-total service limit reached"); continue }
                if (admission == ServiceAdmission.Result.INVALID) continue
                if (admission == ServiceAdmission.Result.ADMITTED) health.count("mDNS " + service.type, "found")
                directServices.add(key); health.count("mDNS " + service.type, "live service records")
            }
            found[peer]?.let { device ->
                applyCurrentService(device, service)
                synchronized(device) { device.evidence(if (HomeKitInfo.applies(service.type)) "Current HomeKit service records from this address" else "Current service records from this address") }
                lastServiceChange = System.nanoTime()
            }
        }
    }
    @Synchronized private fun resolveNext() {
        if (!accepting || !resolving.compareAndSet(false, true)) return
        val next = queue.poll()
        if (next == null) { resolving.set(false); return }
        val key = serviceKey(next)
        try {
            nsd!!.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                    resolverTimeouts.post {
                        if (accepting) synchronized(this@Discovery) {
                            if (directServices.contains(wireServiceKey(next.serviceName, canonicalType(next.serviceType)))) {
                                health.resolved(key); health.count("mDNS " + canonicalType(next.serviceType), "live reply recovery")
                            } else health.resolveFailed(key)
                        }
                        resolving.set(false); resolveNext()
                    }
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    resolverTimeouts.post {
                        if (accepting) { resolved(info); if (info.host != null) health.resolved(key) else health.resolveFailed(key); lastServiceChange = System.nanoTime() }
                        resolving.set(false); resolveNext()
                    }
                }
            })
        } catch (_: Exception) { health.resolveFailed(key); resolving.set(false); resolverTimeouts.post { resolveNext() } }
    }
    @Synchronized private fun resolved(service: NsdServiceInfo) {
        if (!accepting) return
        if (Build.VERSION.SDK_INT >= 33 && service.network != null && net.network != service.network) return
        val addresses = ArrayList<InetAddress>()
        if (Build.VERSION.SDK_INT >= 34) addresses.addAll(service.hostAddresses) else service.host?.let { addresses.add(it) }
        for (address in addresses) {
            if (!candidateAllowed(address, service.serviceType)) { excludedV6(address); continue }
            val d = observe(net.scoped(address), "Service address candidate (not presence proof)", false) ?: continue
            // Native callbacks supply candidates only. Identity comes exclusively
            // from the shared, expiry-aware current-wire assembly above.
            if (freshResponders.contains(d.ip)) { queryDeviceName(d); maybeCast(d) }
            else if (address is Inet6Address) confirmIpv6(d, address,
                if (inScope(address) && LocalServices.tcpConfirmation(service.serviceType)) service.port else 0)
            else { queueMdnsName(d); if (LocalServices.tcpConfirmation(canonicalType(service.serviceType))) confirmService(d, address, service.port) }
        }
    }
    private fun applyCurrentService(d: Device, service: MdnsPacket.Service) {
        applyService(d, service, "mDNS current")
    }
    private fun applyService(d: Device, service: MdnsPacket.Service, prefix: String) {
        synchronized(d) {
            val type = service.type; val name = service.name; val source = prefix + " " + type
            d.service(type); d.port(service.port)
            if (service.host.isNotEmpty()) d.link("mdns-host:" + service.host.lowercase(Locale.ROOT))
            if (!d.self) d.hint("Name", name, source + " instance", if (type.contains("http")) 25 else if (type.contains("androidtvremote2")) 85 else 65)
            if (!d.self) d.hint("Hostname", service.host, source + " host", 60)
            val attrs = service.attributes
            val model = if (type == "_meshcop._udp.") ""
                else if (HomeKitInfo.applies(type)) attribute(attrs, "md", "model")
                else if (type.contains("raop")) attribute(attrs, "am", "model", "md")
                else if (type.contains("companion-link")) attribute(attrs, "rpMd", "model")
                else if (type.contains("ipp") || type.contains("printer") || type.contains("pdl-datastream") || type.contains("scanner")) attribute(attrs, "ty", "usb_MDL", "mdl", "model", "product")
                else attribute(attrs, "model", "md", "ty", "product", "usb_MDL", "mdl")
            val manufacturer = if (type == "_meshcop._udp.") "" else attribute(attrs, "manufacturer", "usb_MFG", "mfg")
            d.hint("Model", model, source + " TXT", if (type.contains("device-info")) 20 else 80)
            d.hint("Manufacturer", manufacturer, source + " TXT", 80)
            if (!d.self) d.hint("Name", attribute(attrs, "fn", "friendlyname"), source + " friendly name", 90)
            val id = attribute(attrs, "id")
            if (id.isNotEmpty()) { d.identify(type + "|" + id, 80); val key = explicitId(type, attrs["id"]); if (key.isNotEmpty()) d.link(key) }
            else d.identify(type + "|" + name, 20)
            val mac = attribute(attrs, "mac", "macaddress")
            if (mac.isNotEmpty()) d.hardware(mac, "mDNS advertised MAC address")
            if (type.contains("airplay")) {
                d.hardware(attribute(attrs, "deviceid"), "AirPlay advertised device ID; may be another interface"); d.hint("Device type", "AirPlay device", source, 60)
            } else if (type.contains("raop")) {
                val at = name.indexOf('@')
                if (at == 12) d.hardware(name.substring(0, at), "RAOP advertised hardware ID; may be another interface")
                d.hint("Device type", "Audio device", source, 60)
            } else if (type.contains("ipp") || type.contains("printer") || type.contains("pdl-datastream") || type.contains("scanner")) d.hint("Device type", "Printer service", source, 80)
            else if (type.contains("cast")) d.hint("Device type", "Cast device", source, 70)
            else if (type.contains("smb")) d.hint("Device type", "File-sharing device", source, 40)
            d.hint("Firmware", attribute(attrs, "fv", "fwversion", "firmware"), source + " TXT", 80)
            d.hint("OS", attribute(attrs, "osvers", "os"), source + " TXT", 80)
            applyMetadata(d, LocalMetadata.advertisement(type, attrs), source + " TXT")
            if (!d.self && HomeKitInfo.applies(type)) {
                val homeKit = HomeKitInfo.parse(type, attrs)
                d.hint("Device type", homeKit.kind, source + " category", if (homeKit.kind == "HomeKit accessory") 35 else 80)
                d.detail("HomeKit setup (advertised)", homeKit.setup)
            } else if (!d.self && type == "_matter._tcp.") d.hint("Device type", "Matter device", source + " service", 35)
            else if (!d.self && type == "_matterc._udp.") d.hint("Device type", "Matter setup service", source + " service", 35)
            else if (!d.self && type == "_meshcop._udp.") d.hint("Device type", "Thread border router service", source + " service", 35)
        }
        maybeCast(d)
        requestMetadata(d, service)
    }
    private fun readLegacyArp() {
        if (Build.VERSION.SDK_INT >= 29) return
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { r ->
                for (n in 0 until 1200) {
                    val line = r.readLine() ?: break
                    if (line.length > 512) continue
                    val fields = line.javaTrim().javaSplit("\\s+")
                    if (fields.size < 6 || fields[5] != net.iface || fields[2] != "0x2") continue
                    found[fields[0]]?.hardware(fields[3], "Android ARP cache (Android 8–9)")
                }
            }
        } catch (_: Exception) {}
    }
    @Synchronized private fun cleanupNsd() {
        for ((type, later) in nativeServiceEvidence.finish(System.nanoTime())) {
            if (later.observed > 0) health.diagnostic("mDNS " + type, DiscoveryHealth.Diagnostic.NSD_WIRE_LATER_OBSERVED, later.observed)
            if (later.complete > 0) health.diagnostic("mDNS " + type, DiscoveryHealth.Diagnostic.NSD_WIRE_LATER_COMPLETE, later.complete)
        }
        mdnsQuestionSuppression.close()
        resolverTimeouts.removeCallbacksAndMessages(null)
        for (browse in legacyBrowses) health.truncated("mDNS " + browse.type, if (cancelled.get()) "Scan stopped before this type was checked" else "Scan window ended before this type was checked")
        legacyBrowses.clear(); legacyActive.clear()
        for (request in modernActive) request.done = true
        modernActive.clear(); modernQueue.clear()
        nsd?.let { manager ->
            for (listener in nsdListeners) try { manager.stopServiceDiscovery(listener) } catch (_: Exception) {}
            nsdListeners.clear()
            if (Build.VERSION.SDK_INT >= 34) for (callback in nsdCallbacks) try { manager.unregisterServiceInfoCallback(callback) } catch (_: Exception) {}
            nsdCallbacks.clear()
        }
        queue.clear(); multicast?.let { if (it.isHeld) it.release() }
    }
    companion object {
        private val NSD_HANDLE = ServiceHandle<NsdManager>()
        private val ACTIVE = AtomicBoolean()
        private val PORTS = intArrayOf(80, 443, 22, 445, 62078, 631, 8008)
        private const val MDNS_SOURCE = MdnsTraffic.SOURCE
        private const val WSD_SOURCE = "WS-Discovery"
        private const val ICMP_SOURCE = "ICMP echo"
        private const val IPV6_SOURCE = "IPv6 service checks"
        @JvmStatic private fun canonicalType(type: String?): String = LocalServices.canonicalType(type)
        @JvmStatic private fun serviceKey(info: NsdServiceInfo): String = info.serviceName + "|" + canonicalType(info.serviceType)
        private fun wireServiceKey(name: String?, type: String): String = SafeText.label(name, 128).lowercase(Locale.ROOT) + "|" + type
        @JvmStatic private fun explicitId(type: String, id: ByteArray?): String {
            if (id == null || id.isEmpty() || id.size > 512 || type.isEmpty()) return ""
            try {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                hash.update(type.toByteArray(StandardCharsets.UTF_8)); hash.update(0.toByte())
                val digest = hash.digest(id); val text = StringBuilder("id:")
                for (b in digest) text.append(String.format(Locale.ROOT, "%02x", b.toInt() and 255))
                return text.toString()
            } catch (e: java.security.NoSuchAlgorithmException) { throw AssertionError(e) }
        }
        @JvmStatic private fun attribute(attributes: Map<String, ByteArray>, vararg keys: String): String {
            for (key in keys) {
                var best = ""; var examined = 0
                for ((entryKey, value) in attributes) {
                    if (++examined > 128) break
                    if (key.equals(entryKey, true) && value.size <= 512) {
                        val text = SafeText.label(String(value, StandardCharsets.UTF_8), 160)
                        if (text.isNotEmpty() && (best.isEmpty() || text < best)) best = text
                    }
                }
                if (best.isNotEmpty()) return best
            }
            return ""
        }
        @JvmStatic fun refused(e: Throwable): Boolean {
            var t: Throwable? = e
            while (t != null) { if (t is ErrnoException && t.errno == OsConstants.ECONNREFUSED) return true; t = t.cause }
            return false
        }
    }
}
