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
    private val networkCheck: NetworkCheck = NetworkCheck { context, network -> network.check(context) },
    private val metadataReader: MetadataReader? = null) {
    interface Listener {
        fun progress(done: Int, total: Int, devices: List<Device>)
        /** Consumers that coalesce updates can defer copying until delivery is needed. */
        fun progressSnapshot(done: Int, total: Int, devices: () -> List<Device>) = progress(done, total, devices())
        fun finished(devices: List<Device>, complete: Boolean, message: String)
    }
    fun interface EchoFactory {
        @Throws(IOException::class) fun open(network: NetworkState, target: InetAddress): IcmpEcho.Channel
    }
    internal fun interface MetadataReader { fun read(peer: InetAddress, request: LocalMetadata.Request, deadline: Long, active: () -> Boolean): LocalMetadata.Report }
    fun interface NetworkCheck { fun check(context: Context, network: NetworkState) }
    constructor(c: Context, n: NetworkState) : this(c, n, EchoFactory(AndroidIcmp::open))

    private val context = c.applicationContext
    private val store = Store(context)
    private val token = store.token()
    private val lifecycle = Any()
    private val cancelled = AtomicBoolean()
    private val found: ConcurrentMap<String, Device> = ConcurrentHashMap()
    private val freshResponders: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val echoAttempted = AtomicBoolean()
    @Volatile private var phase = "Finishing device details…"
    fun phase(): String = phase
    private val resources = ScanResources()
    private val sockets: MutableSet<Socket> = resources.registry()
    private val datagrams: MutableSet<DatagramSocket> = resources.registry()
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
    private val metadataPlans = MetadataPlans()
    private val appleRequests = ServiceAdmission(32)
    private val envisalinkRequests = ServiceAdmission(1200)
    private val ssdpMatchers = SsdpMatchers()
    private val eapRequests = ServiceAdmission(32)
    private val castReceivers = ConcurrentHashMap.newKeySet<String>()
    private val audioAdvertisements = ConcurrentHashMap.newKeySet<String>()
    private val broadAudioRequests = ConcurrentHashMap.newKeySet<String>()
    private val audioRequests = ConcurrentHashMap.newKeySet<String>()
    private val metadataTimeouts = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val multicastNames = ServiceAdmission(128)
    private val wsPeers = ServiceAdmission(64)
    private val wsRecords = ServiceAdmission(256, 8)
    private val descriptions = DescriptionQueue(32, 3)
    private val health = DiscoveryHealth()
    private val protocolRunners by lazy { LanProtocolRunners(context, net, resources, health,
        { accepting }, { cancelled.get() }, { scanDeadline }, ::inScope, ::blocked) }
    @Volatile private var enriching = false
    @Volatile private var lastServiceChange = 0L
    private var nsdStarted = 0L
    private val endpoints = AtomicInteger()
    private val services = ServiceAdmission(256, 32)
    private val nsdTypes = HashSet<String>()
    private val mdnsTypesPending = arrayOf(AtomicBoolean(), AtomicBoolean())
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
    private var multicast: Closeable? = null
    private var multicastThread: Thread? = null
    private var sadpThread: Thread? = null
    private var mdnsThread: Thread? = null
    private var mdnsV6Thread: Thread? = null
    private var wsThread: Thread? = null
    private var playStationThread: Thread? = null
    private var tpLinkThread: Thread? = null
    private var omadaThread: Thread? = null
    private val lanThreads = ArrayList<Thread>()

    init {
        resources.own(Closeable { workers.shutdownNow() })
        resources.own(Closeable { enrichment.shutdownNow() })
        resources.own(Closeable { metadataTimeouts.shutdownNow() })
        resources.own(Closeable { cleanupNsd() })
        resources.own(Closeable { mdnsAssembly.close(); mdnsQuestionSuppression.close() })
        resources.own(Closeable { metadataPlans.close() })
    }

    fun cancel() {
        synchronized(lifecycle) {
            cancelled.set(true)
            accepting = false
            enriching = false
        }
        finishMetadataLookups(true)
        closeIo()
    }
    private fun beginScan(plan: ScanPlan) = synchronized(lifecycle) {
        if (cancelled.get() || !resources.active()) throw InterruptedException("Scan stopped")
        range = plan.range
        scope = plan.scope
        accepting = true
        enriching = true
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
    private fun closeIo() { resources.close() }
    private fun selectedNetwork() = net.network ?: throw IOException("Selected Wi-Fi is unavailable")
    fun scan(cidr: String?, listener: Listener) {
        Thread({
            if (!ACTIVE.compareAndSet(false, true)) {
                cancel()
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
                networkCheck.check(context, net)
                val plan = ScanPlan.create(net.ip, net.prefix, cidr)
                val hosts = plan.hosts
                beginScan(plan)
                total = hosts.size; started = true
                health.begin("IPv4 checks")
                if (plan.sweepOmitted) health.truncated("IPv4 checks", "Address sweep not run: network exceeds 1,024 addresses; service discovery continues. Choose a smaller range to check individual addresses.")
                health.skipped("UPnP descriptions", "No supported description advertised")
                health.skipped(AppleDeviceInfo.SOURCE, "No open Apple service port observed")
                health.skipped(EnvisalinkInfo.SOURCE, "No eligible current IPv4 endpoint checked")
                health.skipped(EapInfo.SOURCE, "No HTTP identity candidate observed")
                for (kind in LocalMetadata.Kind.values()) health.skipped(kind.source, "No supported metadata service advertised")
                observePhoneAddresses(net.addresses)
                listener.progressSnapshot(0, total, ::snapshot)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(55)
                scanDeadline = deadline
                health.skipped(IPV6_SOURCE, "No separate IPv6 echo checks were needed")
                startNsd(); startMdns(); startSsdp(); startSadp(); startWsDiscovery(); startPlayStation(); startTpLink(); startOmada(); startLanIdentities(); startTuyaAnnouncements(); startLifxIdentity(); startEasySmartDiscovery()
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
                    while (!cancelled.get() && (System.nanoTime() < cutoff || (ipv6Pending.get() > 0 || nativeBrowsingPending() || mdnsTypesPending.any { it.get() }) && System.nanoTime() < deadline - TimeUnit.SECONDS.toNanos(2))) {
                        val now = System.nanoTime()
                        if (now >= nsdStarted + TimeUnit.SECONDS.toNanos(10) && omadaThread?.isAlive != true && !nativeBrowsingPending() && !health.pending() && !mdnsPending() && ipv6Pending.get() == 0 && now - lastServiceChange > TimeUnit.MILLISECONDS.toNanos(1500)) break
                        listener.progressSnapshot(done.get(), total, ::snapshot); Thread.sleep(250)
                    }
                    if (mdnsPending()) health.truncated(MDNS_SOURCE, "Local device details did not finish before the scan ended")
                    accepting = false
                    sadpThread?.let { it.join(500); if (it.isAlive) health.truncated("SADP device discovery", "Discovery window did not finish") }
                    wsThread?.let { it.join(500); if (it.isAlive) health.truncated(WSD_SOURCE, "Discovery window did not finish") }
                    playStationThread?.let { it.join(500); if (it.isAlive) health.truncated(PlayStationDiscovery.SOURCE, "Discovery window did not finish") }
                    tpLinkThread?.let { it.join(500); if (it.isAlive) health.truncated(TpLinkDiscovery.SOURCE, "Discovery window did not finish") }
                    omadaThread?.let { it.join(500); if (it.isAlive) health.truncated(OmadaDiscovery.SOURCE, "Listening window did not finish") }
                    for (thread in lanThreads) { thread.join(200); if (thread.isAlive) health.truncated(thread.name, "Discovery window did not finish") }
                    health.closeBrowseWindow(); cleanupNsd(); enrichment.shutdown()
                    while (!cancelled.get() && !enrichment.isTerminated() && System.nanoTime() < deadline) {
                        listener.progressSnapshot(done.get(), total, ::snapshot); enrichment.awaitTermination(250, TimeUnit.MILLISECONDS)
                    }
                    if (!enrichment.isTerminated()) { health.truncated("Device enrichment", "Scan time limit reached"); enriching = false }
                    mdnsThread?.let { it.join(500); if (it.isAlive) health.truncated(MDNS_SOURCE, "Query window did not finish") }
                    mdnsV6Thread?.let { it.join(500); if (it.isAlive) health.truncated(MDNS_SOURCE, "IPv6 query window did not finish") }
                    multicastThread?.let { it.join(500); if (it.isAlive) health.truncated("SSDP / UPnP", "Discovery window did not finish") }
                    net.check(context)
                    finishMetadataLookups(false); finishDescriptions(); health.finish(false)
                    complete = done.get() == total && warning.get() == null && health.summary().isEmpty() && !cancelled.get()
                }
                message = if (cancelled.get()) "Scan stopped. Results collected so far are shown."
                    else warning.get() ?: health.summary().ifEmpty { "Scan finished." }
            } catch (e: Exception) {
                message = if (e is TimeoutException) "Scan time limit reached."
                    else if (cancelled.get()) "Scan stopped. Results collected so far are shown."
                    else e.message ?: "Could not finish this scan."
            } finally {
                accepting = false; enriching = false
                finishMetadataLookups(cancelled.get())
                closeIo()
                try { cleanupNsd() } catch (_: Exception) {
                    health.fail("Scan cleanup", "Native discovery cleanup failed")
                    complete = false
                    if (!cancelled.get()) message = health.summary()
                }
                health.finish(cancelled.get())
                if (started && done.get() != total) health.truncated("IPv4 checks", "Address checks did not finish")
                try {
                    workers.awaitTermination(2, TimeUnit.SECONDS); enrichment.awaitTermination(2, TimeUnit.SECONDS)
                    if (!resources.awaitThreads(2, TimeUnit.SECONDS)) {
                        health.truncated("Scan cleanup", "A protocol worker did not stop within the cleanup budget")
                        complete = false
                        if (!cancelled.get()) message = health.summary()
                    }
                    if (resources.closeFailures > 0) {
                        health.fail("Scan cleanup", "A resource could not be closed")
                        complete = false
                        if (!cancelled.get()) message = health.summary()
                    }
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
                endpoints.decrementAndGet(); warning.compareAndSet(null, "The device limit was reached."); null
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
                if (port == AppleDeviceInfo.PORT) requestAppleModel(item)
                if (port == 80) requestEapModel(item)
                if (port == 8001) requestSamsung(item)
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
                selectedNetwork().bindSocket(socket)
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
            socket = DatagramSocket(); datagrams.add(socket); selectedNetwork().bindSocket(socket); socket.soTimeout = timeout
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

    private fun startOmada() { omadaThread = protocolRunners.omada(range, ::omadaResolved) }

    private fun omadaResolved(peer: InetAddress, response: OmadaDiscovery.Response) {
        if (!accepting || cancelled.get() || !inScope(peer)) return
        val d = observe(net.scoped(peer), "Current Omada discovery announcement") ?: return
        synchronized(d) {
            if (!accepting || cancelled.get() || d.self) return
            d.hint("Model", response.model, OmadaDiscovery.MODEL_SOURCE, 80)
            d.hint("Name", response.name, "Omada discovery reported name", 75)
            d.hint("Device type", response.kind, "Omada discovery device family", 80)
            d.hint("Firmware", response.firmware, "Omada discovery reported firmware", 80)
            d.hardware(response.mac, "Omada discovery reported MAC address")
            d.detail("Omada hardware version (reported)", response.hardware)
            d.detail("Omada protocol version (reported)", response.version)
            d.service(OmadaDiscovery.SOURCE)
            d.detail("Omada discovery transport", "UDP 29810 · receive only")
        }
    }

    private fun startEasySmartDiscovery() {
        val source=EasySmartDiscovery.SOURCE
        val selectedRange=range
        if(net.ip.isEmpty() || selectedRange==null) {health.skipped(source,"IPv4 Wi-Fi unavailable");return}
        val targets=TpLinkDiscovery.targets(net.ip,net.prefix,selectedRange)
        if (targets.isEmpty()) { health.skipped(source,"No other IPv4 addresses selected"); return }
        val thread=Thread({
            var socket:DatagramSocket?=null
            health.begin(source)
            try {
                if(!accepting || cancelled.get()) return@Thread
                net.check(context)
                val selected=DatagramSocket(null).also {socket=it;datagrams.add(it)}
                selected.reuseAddress=false;selectedNetwork().bindSocket(selected)
                selected.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"),29809));selected.broadcast=true;selected.soTimeout=200
                val random=java.security.SecureRandom();val client=ByteArray(6).also {random.nextBytes(it);it[0]=2}
                val first=random.nextInt(65536);val sequences=linkedSetOf<Int>()
                val start=System.nanoTime();val end=minOf(scanDeadline,start+TimeUnit.SECONDS.toNanos(6))
                var round=0;var packets=0;var limited=false;val peers=linkedMapOf<String,MutableSet<LocalMetadata.Report>>()
                while(accepting && !cancelled.get() && System.nanoTime()<end) {
                    if(packets>=512) {limited=true;break}
                    if(round<2 && System.nanoTime()>=start+TimeUnit.SECONDS.toNanos(round.toLong())) {
                        val sequence=(first+round) and 65535;sequences.add(sequence);val query=EasySmartDiscovery.query(client,sequence)
                        for(target in targets) {
                            if(!accepting || cancelled.get() || System.nanoTime()>=end) break
                            net.check(context);selected.send(DatagramPacket(query,query.size,target,29808));health.count(source,"requests")
                        }
                        round++
                    }
                    net.check(context)
                    val bytes=ByteArray(4097);val packet=DatagramPacket(bytes,bytes.size)
                    try {selected.receive(packet)} catch(_:SocketTimeoutException) {continue}
                    packets++;health.count(source,"packets")
                    if(!accepting || cancelled.get() || System.nanoTime()>=end || !inScope(packet.address))continue
                    val report=EasySmartDiscovery.parse(LanIdentity.Packet(packet.address,packet.port,bytes,packet.length),client,sequences) ?: continue
                    val ip=net.scoped(packet.address)
                    val reports=peers[ip] ?: if(peers.size<128) linkedSetOf<LocalMetadata.Report>().also {peers[ip]=it} else {limited=true;continue}
                    if(report in reports)continue
                    if(reports.size>=4) {limited=true;continue};reports.add(report)
                    val d=observe(ip,"Current Easy Smart discovery reply") ?: continue
                    synchronized(d) {if(accepting && !cancelled.get() && !d.self) {
                        applyMetadata(d,report,EasySmartDiscovery.MODEL_SOURCE);d.service(source)
                    }}
                    health.count(source,"devices")
                }
                if(!cancelled.get()) {if(limited || round<2) health.truncated(source,"Discovery work or time limit reached") else health.ok(source)}
            } catch(e:Exception) {
                if(accepting && !cancelled.get()) {blocked(e);health.fail(source,if(e is BindException) "Discovery port already in use" else "Could not receive discovery replies")}
            } finally {socket?.let {datagrams.remove(it);it.close()}}
        },source)
        lanThreads.add(thread);resources.start(thread)
    }

    private fun startLifxIdentity() {
        val source=LifxIdentity.SOURCE
        val selectedRange=range
        val attached=if(net.ip.isNotEmpty()) IpRange(net.ip,net.prefix) else null
        if(attached==null || selectedRange==null || net.prefix>=31 || selectedRange.first!=attached.first || selectedRange.last!=attached.last) {
            health.skipped(source,"Requires an automatic IPv4 subnet scan");return
        }
        val thread=Thread({
            var socket:DatagramSocket?=null
            health.begin(source)
            try {
                if(!accepting || cancelled.get()) return@Thread
                net.check(context)
                val selected=DatagramSocket(null).also {socket=it;datagrams.add(it)}
                selectedNetwork().bindSocket(selected);selected.bind(InetSocketAddress(InetAddress.getByName(net.ip),0));selected.broadcast=true
                val transaction=java.security.SecureRandom().nextInt().let {if(it==0)1 else it}
                val result=LifxIdentity.discover(object:LifxIdentity.Channel {
                    override fun send(peer:InetAddress,port:Int,bytes:ByteArray) {
                        if(!accepting || cancelled.get()) return
                        net.check(context);selected.send(DatagramPacket(bytes,bytes.size,peer,port));health.count(source,"requests")
                    }
                    override fun receive(timeoutMillis:Int):LanIdentity.Packet? {
                        net.check(context);selected.soTimeout=timeoutMillis
                        val bytes=ByteArray(4097);val packet=DatagramPacket(bytes,bytes.size)
                        try {selected.receive(packet)} catch(_:SocketTimeoutException) {return null}
                        return LanIdentity.Packet(packet.address,packet.port,bytes,packet.length)
                    }
                },InetAddress.getByName(IpRange.address(attached.last+1)),transaction,scanDeadline,{accepting && !cancelled.get()},::inScope) {peer,report->
                    net.check(context)
                    if(accepting && !cancelled.get() && inScope(peer)) observe(net.scoped(peer),"Current LIFX GetVersion reply")?.let {d->synchronized(d) {
                        if(accepting && !cancelled.get() && !d.self) {d.discoveryFamilies(setOf(report.family),source);d.service(source);applyMetadata(d,report.metadata,source)}
                    }}
                }
                repeat(result.packets) {health.count(source,"packets")};repeat(result.devices) {health.count(source,"devices")}
                if(!cancelled.get()) {if(result.limited || result.interrupted) health.truncated(source,"Discovery work or time limit reached") else health.ok(source)}
            } catch(e:Exception) {if(accepting && !cancelled.get()) {blocked(e);health.fail(source,"Could not receive discovery replies")}}
            finally {socket?.let {datagrams.remove(it);it.close()}}
        },source)
        lanThreads.add(thread);resources.start(thread)
    }

    private fun startTuyaAnnouncements() {
        for (port in listOf(6666,6667,7000)) {
            val source="Tuya announcements · UDP $port"
            if (net.ip.isEmpty()) {health.skipped(source,"IPv4 Wi-Fi unavailable");continue}
            if (range?.let { it.prefix == 32 && it.contains(net.ip) } == true) {
                health.skipped(source,"No other IPv4 addresses selected");continue
            }
            val thread=Thread({
                var socket: DatagramSocket?=null
                health.begin(source)
                try {
                    if (!accepting || cancelled.get()) return@Thread
                    net.check(context)
                    val selected=DatagramSocket(null).also {socket=it;datagrams.add(it)}
                    selected.reuseAddress=false;selectedNetwork().bindSocket(selected)
                    selected.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"),port));selected.soTimeout=200
                    val end=minOf(scanDeadline,System.nanoTime()+TimeUnit.SECONDS.toNanos(6))
                    var packets=0;val peers=linkedSetOf<String>();var limited=false
                    while(accepting && !cancelled.get() && System.nanoTime()<end) {
                        if(packets>=512) {limited=true;break}
                        net.check(context)
                        val bytes=ByteArray(4097);val packet=DatagramPacket(bytes,bytes.size)
                        try {selected.receive(packet)} catch(_: SocketTimeoutException) {continue}
                        packets++;health.count(source,"packets")
                        if(!accepting || cancelled.get() || System.nanoTime()>=end || !inScope(packet.address)) continue
                        val report=TuyaAnnouncement.parse(bytes.copyOf(packet.length),packet.address) ?: continue
                        val ip=net.scoped(packet.address)
                        if(ip in peers) continue
                        if(peers.size>=128) {limited=true;continue}
                        peers.add(ip)
                        val d=observe(ip,"Current Tuya discovery announcement") ?: continue
                        synchronized(d) {if(accepting && !cancelled.get() && !d.self) {
                            d.discoveryFamilies(setOf(report.family),source);d.service("Tuya discovery")
                            applyMetadata(d,report.metadata,source)
                        }}
                        health.count(source,"devices")
                    }
                    if(!cancelled.get()) {if(limited) health.truncated(source,"Announcement work limit reached") else health.ok(source)}
                } catch(e:Exception) {
                    if(accepting && !cancelled.get()) {blocked(e);health.fail(source,if(e is BindException) "Discovery port already in use" else "Could not receive announcements")}
                } finally {socket?.let {datagrams.remove(it);it.close()}}
            },source)
            lanThreads.add(thread);resources.start(thread)
        }
    }

    private fun startLanIdentities() {
        val selectedRange = range
        val attached = if (net.ip.isNotEmpty()) IpRange(net.ip, net.prefix) else null
        for (method in LanIdentity.Method.values()) {
            // These multicast/broadcast protocols have no subnet selector. Do not transmit beyond a narrowed scan.
            if (attached == null || selectedRange == null || net.prefix >= 31 ||
                selectedRange.first != attached.first || selectedRange.last != attached.last) {
                health.skipped(method.source, "Requires an automatic IPv4 subnet scan"); continue
            }
            val thread = Thread({
                var socket: MulticastSocket? = null
                health.begin(method.source)
                try {
                    if (!accepting || cancelled.get()) return@Thread
                    net.check(context)
                    val iface = NetworkInterface.getByName(net.iface) ?: throw IOException("Wi-Fi interface unavailable")
                    val selected = MulticastSocket(null).also { socket=it; datagrams.add(it) }
                    selected.reuseAddress=false
                    selectedNetwork().bindSocket(selected)
                    selected.bind(InetSocketAddress(if (method.listenPort == 0) InetAddress.getByName(net.ip) else InetAddress.getByName("0.0.0.0"), method.listenPort))
                    selected.networkInterface=iface; selected.timeToLive=1; selected.broadcast=true
                    val group = InetAddress.getByName("239.255.255.250")
                    val target = if (method == LanIdentity.Method.WIZ) InetAddress.getByName(IpRange.address(attached.last+1)) else group
                    if (method == LanIdentity.Method.GOVEE) selected.joinGroup(InetSocketAddress(group,4002),iface)
                    val result = LanIdentity.discover(method, object : LanIdentity.Channel {
                        override fun send(bytes: ByteArray) {
                            if (!accepting || cancelled.get()) return
                            net.check(context)
                            selected.send(DatagramPacket(bytes,bytes.size,target,method.port)); health.count(method.source,"requests")
                        }
                        override fun receive(timeoutMillis: Int): LanIdentity.Packet? {
                            net.check(context); selected.soTimeout=timeoutMillis
                            val bytes=ByteArray(4097); val packet=DatagramPacket(bytes,bytes.size)
                            try { selected.receive(packet) } catch (_: SocketTimeoutException) { return null }
                            return LanIdentity.Packet(packet.address,packet.port,bytes,packet.length)
                        }
                    },scanDeadline,{ accepting && !cancelled.get() },::inScope) { peer,report ->
                        net.check(context)
                        if (accepting && !cancelled.get() && inScope(peer)) {
                            val d=observe(net.scoped(peer), "Current " + method.source + " reply")
                            if (d != null) synchronized(d) {
                                if (accepting && !cancelled.get() && !d.self) {
                                    d.discoveryFamilies(setOf(report.family),method.source)
                                    d.service(method.source); applyMetadata(d,report.metadata,method.source + " (reported)")
                                }
                            }
                        }
                    }
                    repeat(result.packets) {health.count(method.source,"packets")}
                    repeat(result.devices) {health.count(method.source,"devices")}
                    if (!cancelled.get()) {
                        if (result.limited || result.interrupted) health.truncated(method.source,"Discovery work or time limit reached")
                        else health.ok(method.source)
                    }
                } catch(e: Exception) {
                    if (accepting && !cancelled.get()) {blocked(e);health.fail(method.source,if(e is BindException) "Discovery port already in use" else "Could not receive discovery replies")}
                } finally {socket?.let {datagrams.remove(it);it.close()}}
            },method.source)
            lanThreads.add(thread); resources.start(thread)
        }
    }

    private fun startTpLink() { tpLinkThread = protocolRunners.tpLink(range, ::tpLinkResolved) }
    /** Current, scope-checked socket replies only. Advertised addresses never redirect discovery. */
    private fun tpLinkResolved(peer: InetAddress, port: Int, response: TpLinkDiscovery.Response) {
        if (!accepting || cancelled.get() || !inScope(peer)) return
        val d = observe(net.scoped(peer), "Current TP-Link discovery reply") ?: return
        synchronized(d) {
            if (!accepting || cancelled.get() || d.self) return
            d.hint("Model", response.model, TpLinkDiscovery.MODEL_SOURCE, 80)
            d.hint("Name", response.name, "TP-Link discovery alias", 75)
            d.hint("Firmware", response.firmware, "TP-Link discovery reported firmware", 80)
            d.hardware(response.mac, "TP-Link discovery reported MAC address")
            d.detail("TP-Link hardware version (reported)", response.hardware)
            d.detail("TP-Link device family (reported)", response.family)
            // UDP discovery ports are not advertised TCP services; do not feed TCP probes.
            d.service(TpLinkDiscovery.SOURCE)
            d.detail("TP-Link discovery transport", "UDP " + port)
        }
    }

    private fun startPlayStation() { playStationThread = protocolRunners.playStation(range, ::playStationResolved) }
    /** Only the current selected-Wi-Fi discovery socket supplies these parsed responses. */
    private fun playStationResolved(peer: InetAddress, response: PlayStationDiscovery.Response) {
        if (!accepting || cancelled.get() || !inScope(peer)) return
        val d = observe(net.scoped(peer), "Current PlayStation discovery reply") ?: return
        synchronized(d) {
            if (!accepting || cancelled.get()) return
            d.hint("Model", "PS5", "PlayStation discovery host-type", 80)
            if (!d.self) d.hint("Name", response.name, "PlayStation discovery host-name", 75)
            d.service("PlayStation discovery")
            d.detail("PlayStation state (reported)", if (response.standby) "Standby" else "Ready")
        }
    }
    private fun startSsdp() {
        multicastThread = protocolRunners.ssdp { peer, headers ->
            observe(net.scoped(peer), "SSDP / UPnP discovery response")?.let { d ->
                synchronized(d) {
                    if (!accepting || cancelled.get()) return@ssdp
                    applyFamilies(d, DiscoveryMatchers.match("ssdp", headers), "SSDP response")
                    d.service(headers.getOrDefault("st", "SSDP / UPnP"))
                    d.detail("UPnP server", headers.getOrDefault("server", ""))
                    d.detail("UPnP identifier", headers.getOrDefault("usn", ""))
                    d.identify(headers.getOrDefault("usn", ""), 70)
                }
                description(d, peer, headers["location"], headers); queryDeviceName(d)
            }
        }
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
                selectedNetwork().bindSocket(socket); socket.networkInterface = iface; socket.timeToLive = 1; socket.soTimeout = 300
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
        }, "sadp-discovery").also { resources.start(it) }
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
                selectedNetwork().bindSocket(socket); socket.networkInterface = iface; socket.timeToLive = 1; socket.soTimeout = 250
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
        }, "ws-discovery").also { resources.start(it) }
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
        Device.audioRows(out)
        out.sortWith { a, b -> Address.compare(a.ip, b.ip) }
        return out
    }
    private fun queryDeviceName(d: Device) {
        // Router PTR/DHCP records can outlive clients; use current peer mDNS only.
        queueMdnsName(d)
    }
    private fun submitEnrichment(source: String, action: Runnable): Boolean = submitEnrichment(DiscoveryWork.Lane.NAMES, source, action)
    private fun submitEnrichment(lane: DiscoveryWork.Lane, source: String, action: Runnable): Boolean = submitEnrichment(lane, source, "", action)
    private fun submitEnrichment(lane: DiscoveryWork.Lane, source: String, owner: String, action: Runnable): Boolean {
        if (enrichment.submit(lane, owner) { if (enriching && !cancelled.get()) action.run() }) return true
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
                val checked = HashSet<Int>()
                if (connectTimeout > 0) for (port in PORTS) {
                    if (!enriching || cancelled.get()) return@Runnable
                    checked.add(port)
                    if (checkDetailPort(d, port, connectTimeout)) break
                }
                // Identity opportunities remain independent of the first responding general service.
                for (port in IdentityProbePlan.remaining(checked)) {
                    if (!enriching || cancelled.get()) return@Runnable
                    checkDetailPort(d, port, maxOf(160, connectTimeout))
                }
                // Receive-only identity uses its own single connection, even when HTTP answered first.
                checkEnvisalink(d)
                val retry = synchronized(d) { d.hostname.isEmpty() || d.mac.isEmpty() }
                if (retry && enriching && !cancelled.get()) enrichNetBios(d.ip, d, minOf(400, nameTimeout + 180))
            } finally { health.workFinished(source) }
        })) health.workFinished(source)
    }
    private fun checkDetailPort(d: Device, port: Int, timeout: Int): Boolean {
        if (!enriching || cancelled.get() || d.self || !freshResponders.contains(d.ip) || !inScope(peerAddress(d.ip))) return false
        if (synchronized(d) { port in d.ports }) {
            if (port == AppleDeviceInfo.PORT) requestAppleModel(d)
            if (port == 80) requestEapModel(d)
            if (port == 8001) requestSamsung(d)
            return true
        }
        val socket = Socket(); sockets.add(socket)
        try {
            net.check(context); selectedNetwork().bindSocket(socket)
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(d.ip, port), timeout)
            synchronized(d) {
                if (!enriching || cancelled.get()) return false
                d.port(port); d.latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).toInt()
            }
            if (port == AppleDeviceInfo.PORT) requestAppleModel(d)
            if (port == 80) requestEapModel(d)
            if (port == 8001) requestSamsung(d)
            return true
        } catch (e: Exception) { blocked(e); return false }
        finally { sockets.remove(socket); try { socket.close() } catch (_: IOException) {} }
    }

    private fun currentEapPeer(d: Device): Boolean = enriching && !cancelled.get() && !d.self &&
        freshResponders.contains(d.ip) && found[d.ip] === d && inScope(peerAddress(d.ip)) && synchronized(d) { 80 in d.ports }

    private fun requestEapModel(d: Device) {
        if (!currentEapPeer(d)) return
        val source = EapInfo.SOURCE
        synchronized(d) { d.detail("Network equipment identity request", EapInfo.TRANSPORT) }
        val admission = eapRequests.admit(d.ip, source)
        if (admission == ServiceAdmission.Result.LIMIT) {
            health.truncated(source, "32-device identity check limit reached"); return
        }
        if (admission != ServiceAdmission.Result.ADMITTED) return
        synchronized(d) { d.detail(source, "Waiting or checking") }
        health.workStarted(source)
        if (!submitEnrichment(DiscoveryWork.Lane.METADATA, source, d.ip, Runnable {
            val deadline = minOf(scanDeadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(2))
            try {
                val model = EapInfo.read(peerAddress(d.ip), deadline, metadataTimeouts, { currentEapPeer(d) }, {
                    net.check(context)
                    val socket = Socket(); sockets.add(socket)
                    try { selectedNetwork().bindSocket(socket); health.count(source, "requests"); socket }
                    catch (e: Exception) { sockets.remove(socket); socket.close(); throw e }
                }, { sockets.remove(it) })
                if (applyEapReport(d, model)) health.count(source, if (model == null) "no EAP identity" else "models received")
            } catch (e: Exception) {
                if (currentEapPeer(d)) {
                    blocked(e)
                    val failure = AndroidMetadataFailure.classify(e, System.nanoTime() >= deadline)
                    synchronized(d) { d.detail(source, failure.reason.detail) }
                    MetadataHttp.recordFailure(health, source, failure, false)
                }
            } finally {
                synchronized(d) { if (d.details[source] == "Waiting or checking") d.detail(source, "Lookup did not finish") }
                health.workFinished(source)
            }
        })) {
            synchronized(d) { d.detail(source, "Not attempted: work queue limit reached") }
            health.workFinished(source)
        }
    }
    private fun applyEapReport(d: Device, model: String?): Boolean = synchronized(d) {
        if (!currentEapPeer(d) || d.details[EapInfo.SOURCE] != "Waiting or checking") return@synchronized false
        d.detail(EapInfo.SOURCE, if (model == null) "No supported EAP identity reported" else "Public model received")
        if (model != null) {
            d.hint("Model", model, EapInfo.MODEL_SOURCE, 80)
            d.hint("Device type", "TP-Link EAP access point", EapInfo.MODEL_SOURCE, 80)
            d.evidence("Current TP-Link EAP public identity response")
        }
        true
    }

    private fun currentEnvisalinkPeer(d: Device): Boolean = enriching && !cancelled.get() && !metadataFinalized.get() &&
        !d.self && !d.ip.contains(':') && freshResponders.contains(d.ip) && found[d.ip] === d && inScope(peerAddress(d.ip))

    /** Runs in the bounded DETAILS lane. Never pre-connect, retry, authenticate or send TPI data. */
    private fun checkEnvisalink(d: Device) {
        if (!currentEnvisalinkPeer(d)) return
        val source = EnvisalinkInfo.SOURCE
        when (envisalinkRequests.admit(d.ip, source)) {
            ServiceAdmission.Result.ADMITTED -> {}
            ServiceAdmission.Result.LIMIT -> { health.truncated(source, "Endpoint work limit reached"); return }
            else -> return
        }
        synchronized(d) { d.detail(source, "Waiting or checking") }
        health.workStarted(source)
        val deadline = minOf(scanDeadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750))
        try {
            val report = metadataSocket(peerAddress(d.ip), deadline, { currentEnvisalinkPeer(d) }) { socket ->
                health.count(source, "connections attempted")
                EnvisalinkInfo.exchange(socket, peerAddress(d.ip), deadline, metadataTimeouts) { currentEnvisalinkPeer(d) }
            }
            if (applyEnvisalinkReport(d, report)) health.count(source, when (report) {
                EnvisalinkInfo.Report.IDENTIFIED -> "families identified"
                EnvisalinkInfo.Report.UNAVAILABLE -> "service unavailable"
                else -> "no identifying greeting"
            })
        } catch (e: Exception) {
            blocked(e)
            synchronized(d) {
                if (currentEnvisalinkPeer(d) && d.details[source] == "Waiting or checking") {
                    val failure = AndroidMetadataFailure.classify(e, System.nanoTime() >= deadline)
                    d.detail(source, failure.reason.detail)
                    MetadataHttp.recordFailure(health, source, failure, false)
                }
            }
        } finally {
            synchronized(d) { if (d.details[source] == "Waiting or checking") d.detail(source, "Lookup did not finish") }
            health.workFinished(source)
        }
    }
    private fun applyEnvisalinkReport(d: Device, report: EnvisalinkInfo.Report): Boolean = synchronized(d) {
        if (!currentEnvisalinkPeer(d) || d.details[EnvisalinkInfo.SOURCE] != "Waiting or checking") return@synchronized false
        if (report == EnvisalinkInfo.Report.UNAVAILABLE) d.details.remove(EnvisalinkInfo.SOURCE)
        else {
            d.port(EnvisalinkInfo.PORT)
            d.detail(EnvisalinkInfo.SOURCE, report.text)
        }
        if (report == EnvisalinkInfo.Report.IDENTIFIED) {
            d.recognizeEnvisalink()
            d.service(EnvisalinkInfo.SERVICE)
            d.evidence("Current Envisalink DSC TPI greeting from this address")
        }
        true
    }

    private fun requestAppleModel(d: Device) {
        if (!enriching || cancelled.get() || d.self || !freshResponders.contains(d.ip) ||
            found[d.ip] !== d || !inScope(peerAddress(d.ip)) || synchronized(d) { AppleDeviceInfo.PORT !in d.ports }) return
        val source = AppleDeviceInfo.SOURCE
        val admission = appleRequests.admit(d.ip, source)
        if (admission == ServiceAdmission.Result.LIMIT) {
            synchronized(d) { d.detail(source, "Not attempted: 32-device work limit reached") }
            health.truncated(source, "32-device model request limit reached"); return
        }
        if (admission != ServiceAdmission.Result.ADMITTED) return
        synchronized(d) { d.detail(source, "Waiting or checking") }
        health.workStarted(source)
        if (!submitEnrichment(DiscoveryWork.Lane.METADATA, source, d.ip, Runnable {
            val deadline = minOf(scanDeadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(2))
            try {
                if (!enriching || cancelled.get() || !inScope(peerAddress(d.ip))) return@Runnable
                val report = readOnlyMetadata(peerAddress(d.ip), deadline, { enriching && !cancelled.get() }) { socket ->
                    health.count(source, "requests")
                    AppleDeviceInfo.exchange(socket, peerAddress(d.ip), deadline, metadataTimeouts) { enriching && !cancelled.get() }
                }
                if (applyAppleReport(d, report)) health.count(source, when (report.status) {
                    AppleDeviceInfo.Status.MODEL -> "models received"
                    AppleDeviceInfo.Status.WITHHELD -> "models withheld"
                    AppleDeviceInfo.Status.OTHER -> "other services"
                    AppleDeviceInfo.Status.UNAVAILABLE -> "verified services without model"
                })
                report.failure?.let { MetadataHttp.recordFailure(health, source, MetadataHttp.Failure(it), false) }
            } catch (e: Exception) {
                blocked(e)
                synchronized(d) {
                    if (enriching && !cancelled.get() && d.details[source] == "Waiting or checking") {
                        val failure = AndroidMetadataFailure.classify(e, System.nanoTime() >= deadline)
                        d.detail(source, failure.reason.detail)
                        MetadataHttp.recordFailure(health, source, failure, false)
                    }
                }
            } finally {
                synchronized(d) { if (d.details[source] == "Waiting or checking") d.detail(source, "Lookup did not finish") }
                health.workFinished(source)
            }
        })) {
            synchronized(d) { d.detail(source, "Not attempted: work queue limit reached") }
            health.workFinished(source)
        }
    }
    private fun applyAppleReport(d: Device, report: AppleDeviceInfo.Report): Boolean = synchronized(d) {
        if (!enriching || cancelled.get() || d.self || !freshResponders.contains(d.ip) || found[d.ip] !== d ||
            !inScope(peerAddress(d.ip)) || d.details[AppleDeviceInfo.SOURCE] != "Waiting or checking") return@synchronized false
        d.detail(AppleDeviceInfo.SOURCE, report.status.text + (report.failure?.let { "; " + it.detail } ?: ""))
        if (report.status != AppleDeviceInfo.Status.OTHER) {
            d.service(AppleDeviceInfo.SERVICE)
            d.evidence("Current Apple QueryType response from this address")
            if (report.status == AppleDeviceInfo.Status.MODEL) {
                d.recognizeAppleModel(report.model)
                d.hint("Model", report.model, AppleDeviceInfo.MODEL_SOURCE, 80)
            }
        }
        true
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
        if (synchronized(d) { AppleDeviceInfo.PORT in d.ports }) requestAppleModel(d)
        if (synchronized(d) { 80 in d.ports }) requestEapModel(d)
        if (synchronized(d) { 8001 in d.ports }) requestSamsung(d)
        if (inScope(peerAddress(d.ip))) { queryDeviceName(d); maybeCast(d) }
    }
    private fun maybeCast(d: Device) {
        if (!enriching || cancelled.get() || !freshResponders.contains(d.ip) || !castReceivers.contains(d.ip)) return
        synchronized(d) { if (d.self || d.services.none { canonicalType(it) == "_googlecast._tcp." }) return }
        // The fixed metadata endpoint is independent of a Cast service's playback port.
        // A useful room name does not mean that its model/manufacturer are already known.
        requestMetadata(d, MdnsPacket.Service("", "_googlecast._tcp.").apply { port = 8008 })
    }
    /** Terminalize accepted lookups, including queued tasks that shutdownNow will discard. */
    private val metadataFinalized = AtomicBoolean()
    private fun finishMetadataLookups(stopped: Boolean) {
        if (!metadataFinalized.compareAndSet(false, true)) return
        metadataPlans.close()
        for (d in found.values) synchronized(d) {
            d.metadataLookups.finish(stopped)
            for (entry in d.metadataLookups.snapshot()) when (entry.state) {
                MetadataLookups.State.FAILED -> MetadataHttp.recordFailure(health, entry.kind.source,
                    MetadataHttp.Failure(entry.reason ?: MetadataHttp.Reason.REQUEST_FAILED, entry.httpStatus), false)
                MetadataLookups.State.INCOMPLETE -> health.truncated(entry.kind.source, "Metadata lookup did not finish")
                MetadataLookups.State.STOPPED -> health.truncated(entry.kind.source, "Scan stopped")
                MetadataLookups.State.WORK_LIMIT -> health.truncated(entry.kind.source, "Metadata work limit reached")
                else -> {}
            }
            d.audioPlayerStatuses.replaceAll { _, value -> if (value.endsWith(": Waiting or checking"))
                value.removeSuffix("Waiting or checking") + (if (stopped) "Stopped before lookup finished" else "Lookup did not finish") else value }
            if (d.audioStatus.endsWith(": Waiting or checking")) d.audioStatus = d.audioStatus.substringBefore(":") +
                if (stopped) ": Stopped before lookup finished" else ": Lookup did not finish"
            for (source in arrayOf(AppleDeviceInfo.SOURCE, EapInfo.SOURCE, EnvisalinkInfo.SOURCE))
                if (d.details[source] == "Waiting or checking")
                    d.detail(source, if (stopped) "Stopped before lookup finished" else "Lookup did not finish")
        }
    }
    private fun <T> readOnlyMetadata(peer: InetAddress, deadline: Long, active: () -> Boolean, exchange: (Socket) -> T): T =
        MetadataHttp.retryReadOnly(deadline, active) { metadataSocket(peer, deadline, active, exchange) }
    private fun <T> metadataSocket(peer: InetAddress, deadline: Long, active: () -> Boolean, exchange: (Socket) -> T): T {
        if (!active()) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
        if (System.nanoTime() >= deadline) throw MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE)
        net.check(context)
        if (!inScope(peer)) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
        val socket = Socket(); sockets.add(socket)
        try { selectedNetwork().bindSocket(socket); return exchange(socket) }
        finally { sockets.remove(socket); try { socket.close() } catch (_: IOException) {} }
    }

    private fun requestMetadata(d: Device, service: MdnsPacket.Service) {
        LocalMetadata.request(service)?.let { requestMetadata(d, it) }
    }
    private fun requestSamsung(d: Device) {
        requestMetadata(d, LocalMetadata.Request(LocalMetadata.Kind.SAMSUNG, 8001, "/api/v2/"))
    }
    private fun requestMetadata(d: Device, request: LocalMetadata.Request) { synchronized(d) {
        if (metadataFinalized.get() || !enriching || cancelled.get() || d.self || !freshResponders.contains(d.ip) || found[d.ip] !== d) return
        val peer = peerAddress(d.ip)
        if (!inScope(peer)) return
        val source = request.kind.source
        val decision = metadataPlans.offer(d.ip, request, System.nanoTime())
        val admission = decision.admission
        if (admission == ServiceAdmission.Result.LIMIT) {
            synchronized(d) {
                if (enriching && !cancelled.get() && d.metadataLookups.queue(request)) d.metadataLookups.limit(request.kind)
            }
            health.truncated(source, "32-per-method / 96-total metadata limit reached"); return
        }
        if (admission != ServiceAdmission.Result.ADMITTED && admission != ServiceAdmission.Result.DUPLICATE) return
        val plan = decision.plan ?: return
        when (decision.offer) {
            MetadataCandidates.Offer.START -> {}
            MetadataCandidates.Offer.LIMIT -> { health.truncated(source, "Two metadata target limit reached"); return }
            else -> return
        }
        synchronized(d) { if (admission == ServiceAdmission.Result.ADMITTED) d.metadataLookups.queue(request) }
        health.workStarted(source)
        val lane = if (request.kind == LocalMetadata.Kind.CAST) DiscoveryWork.Lane.CAST else DiscoveryWork.Lane.METADATA
        if (!submitEnrichment(lane, source, d.ip, Runnable {
            try {
                while (enriching && !cancelled.get()) {
                    val candidate = plan.next(System.nanoTime(), scanDeadline) { d.metadataLookups.incomplete(request.kind) } ?: break
                    val deadline = plan.deadline()
                    if (!inScope(peer) || !freshResponders.contains(d.ip) || found[d.ip] !== d) break
                    if (!d.metadataLookups.beginCandidate(candidate)) break
                    try {
                        networkCheck.check(context, net)
                        // At most two targets, each with one transient retry, sharing the original deadline.
                        val report = metadataReader?.read(peer, candidate, deadline) { enriching && !cancelled.get() }
                            ?: readOnlyMetadata(peer, deadline, { enriching && !cancelled.get() }) { socket ->
                            health.count(source, "requests")
                            MetadataHttp.exchange(socket, peer, candidate, deadline, metadataTimeouts) { enriching && !cancelled.get() }
                        }
                        if (!enriching || cancelled.get()) break
                        networkCheck.check(context, net)
                        if (!inScope(peer) || !freshResponders.contains(d.ip) || found[d.ip] !== d) break
                        if (applyCurrentMetadata(d, candidate, report)) {
                            health.count(source, if (report.hints.isNotEmpty() || report.mac.isNotEmpty() || report.details.isNotEmpty()) "metadata replies" else "no extra details")
                            plan.close()
                        }
                        break
                    } catch (e: Exception) {
                        blocked(e)
                        if (enriching && !cancelled.get()) {
                            val failure = AndroidMetadataFailure.classify(e, System.nanoTime() >= deadline)
                            d.metadataLookups.failure(candidate.kind, failure)
                            health.count(source, "failed targets")
                        }
                    }
                }
            } finally { health.workFinished(source) }
        })) {
            plan.close(); d.metadataLookups.limit(request.kind); health.workFinished(source)
        }
    } }
    private fun applyCurrentMetadata(d: Device, request: LocalMetadata.Request, report: LocalMetadata.Report): Boolean = synchronized(d) {
        if (!enriching || cancelled.get() || d.self || found[d.ip] !== d || !freshResponders.contains(d.ip) || !inScope(peerAddress(d.ip))) return@synchronized false
        val entry = d.metadataLookups.snapshot().singleOrNull { it.kind == request.kind } ?: return@synchronized false
        if (entry.state != MetadataLookups.State.QUEUED || entry.port != request.port || entry.secure != request.secure) return@synchronized false
        applyMetadata(d, report, request.kind.source + " (reported)")
        d.metadataLookups.success(request.kind, report)
        true
    }
    private fun applyMetadata(d: Device, report: LocalMetadata.Report, source: String) {
        for ((field, value) in report.hints) {
            val priority = if (field != "Name") 80
                else if (source == LocalMetadata.Kind.CAST.source + " (reported)") {
                    if (CastInfo.needsName(d.name)) 70 else 1
                } else 90
            d.hint(field, value, source, priority)
        }
        for ((field, value) in report.details) d.detail(field, value)
        if (report.mac.isNotEmpty()) d.hardware(report.mac, source + " MAC address; may be another interface")
    }
    private fun currentAudioPeer(d: Device): Boolean = enriching && !cancelled.get() && !d.self &&
        found[d.ip] === d && freshResponders.contains(d.ip) && inScope(peerAddress(d.ip))

    private fun requestBroadAudio(d: Device, plan: BroadAudioInfo.Plan) {
        if (!currentAudioPeer(d)) return
        val key = d.ip + "|" + plan.key
        val source = plan.protocol.source + " · port " + plan.port
        synchronized(broadAudioRequests) {
            if (key in broadAudioRequests) return
            if (broadAudioRequests.size >= 64 || broadAudioRequests.count { it.startsWith(d.ip + "|") } >= 8) {
                health.truncated(plan.protocol.source, "Audio lookup limit reached"); return
            }
            broadAudioRequests.add(key)
        }
        synchronized(d) {
            if (!currentAudioPeer(d)) return
            d.audioPlayerStatuses[plan.key] = source + ": Waiting or checking"
        }
        health.begin(plan.protocol.source); health.workStarted(plan.protocol.source)
        if (!submitEnrichment(DiscoveryWork.Lane.METADATA, source, d.ip, Runnable {
            try {
                if (!currentAudioPeer(d)) return@Runnable
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                val result = BroadAudioInfo.read(plan, peerAddress(d.ip)) { query ->
                    if (!currentAudioPeer(d)) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
                    val socket = Socket(); sockets.add(socket)
                    try {
                        net.check(context); selectedNetwork().bindSocket(socket)
                        health.count(plan.protocol.source, "requests")
                        AudioWire.exchange(socket, peerAddress(d.ip), query, deadline, metadataTimeouts) { currentAudioPeer(d) }
                    } finally { sockets.remove(socket); try { socket.close() } catch (_: IOException) {} }
                }
                if (applyCurrentBroadAudio(d, plan, result)) {
                    health.count(plan.protocol.source, "received")
                    if (result.note.isEmpty()) health.ok(plan.protocol.source) else { health.count(plan.protocol.source, "partial metadata"); health.fail(plan.protocol.source, "Some audio information unavailable") }
                }
            } catch (e: Exception) {
                if (currentAudioPeer(d)) {
                    blocked(e); MetadataHttp.recordFailure(health, plan.protocol.source, e, false)
                    synchronized(d) { if (currentAudioPeer(d)) d.audioPlayerStatuses[plan.key] = source + ": " +
                        ((e as? MetadataHttp.Failure)?.reason?.detail ?: "Lookup unavailable") }
                }
            } finally {
                synchronized(d) { if (d.audioPlayerStatuses[plan.key] == source + ": Waiting or checking")
                    d.audioPlayerStatuses[plan.key] = source + ": Lookup did not finish" }
                health.workFinished(plan.protocol.source)
            }
        })) {
            synchronized(d) { d.audioPlayerStatuses[plan.key] = source + ": Lookup queue full" }
            health.workFinished(plan.protocol.source)
        }
    }
    private fun applyCurrentBroadAudio(d: Device, plan: BroadAudioInfo.Plan, result: AudioPlayers.Result): Boolean = synchronized(d) {
        val source = plan.protocol.source + " · port " + plan.port
        if (!currentAudioPeer(d) || result.player.protocol != plan.protocol || result.player.port != plan.port ||
            d.audioPlayerStatuses[plan.key] != source + ": Waiting or checking") return@synchronized false
        if (plan.key !in d.audioPlayers && d.audioPlayers.size >= 16) {
            d.audioPlayerStatuses[plan.key] = source + ": Player limit reached"
            health.truncated(plan.protocol.source, "16 audio player limit reached"); return@synchronized false
        }
        d.audioPlayers[plan.key] = result.player
        d.audioPlayerStatuses[plan.key] = source + ": " + if (result.note.isEmpty()) "Received" else "Partial; " + result.note
        for ((field, value) in result.hints) d.hint(field, value, source, 80)
        true
    }

    private fun requestAudio(d: Device, kind: AudioZones.Kind, identity: String) {
        if (!enriching || cancelled.get() || d.self || found[d.ip] !== d || !freshResponders.contains(d.ip)) return
        val peer = peerAddress(d.ip)
        if (!inScope(peer)) return
        synchronized(audioRequests) {
            if (d.ip in audioRequests) {
                synchronized(d) {
                    if (kind == AudioZones.Kind.SONOS && identity != d.audioIdentity) {
                        d.audioReport = null; d.audioStatus = kind.source + ": Conflicting player identity"
                    }
                }
                return
            }
            if (audioRequests.size >= 32) {
                synchronized(d) { d.audioStatus = kind.source + ": Lookup limit reached" }
                health.truncated(kind.source, "32 audio endpoint limit reached"); return
            }
            audioRequests.add(d.ip)
        }
        synchronized(d) {
            if (!enriching || cancelled.get() || found[d.ip] !== d) return
            d.audioIdentity = identity; d.audioStatus = kind.source + ": Waiting or checking"
        }
        health.begin(kind.source); health.workStarted(kind.source)
        if (!submitEnrichment(DiscoveryWork.Lane.METADATA, kind.source, d.ip, Runnable {
            try {
                if (!enriching || cancelled.get() || found[d.ip] !== d || !freshResponders.contains(d.ip) || !inScope(peer)) return@Runnable
                val deadline = minOf(scanDeadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(2))
                val report = readOnlyMetadata(peer, deadline, { enriching && !cancelled.get() }) { socket ->
                    health.count(kind.source, "requests")
                    AudioZoneInfo.exchange(socket, peer, kind, identity, deadline, metadataTimeouts) { enriching && !cancelled.get() }
                }
                if (applyCurrentAudio(d, report)) { health.count(kind.source, "received"); health.ok(kind.source) }
            } catch (e: Exception) {
                if (enriching && !cancelled.get()) {
                    blocked(e); MetadataHttp.recordFailure(health, kind.source, e, false)
                    synchronized(d) { d.audioStatus = kind.source + ": " + ((e as? MetadataHttp.Failure)?.reason?.detail ?: "Lookup unavailable") }
                }
            } finally {
                synchronized(d) { if (d.audioStatus.endsWith(": Waiting or checking")) d.audioStatus = kind.source + ": Lookup did not finish" }
                health.workFinished(kind.source)
            }
        })) {
            synchronized(d) { d.audioStatus = kind.source + ": Lookup queue full" }
            health.workFinished(kind.source)
        }
    }
    private fun applyCurrentAudio(d: Device, report: AudioZones.Report): Boolean = synchronized(d) {
        if (!enriching || cancelled.get() || d.self || found[d.ip] !== d || !freshResponders.contains(d.ip) || !inScope(peerAddress(d.ip)) ||
            d.audioStatus != report.kind.source + ": Waiting or checking") return@synchronized false
        d.audioReport = report
        d.audioStatus = report.kind.source + ": Received"
        if (report.kind == AudioZones.Kind.VSSL) {
            d.hint("Manufacturer", "VSSL", report.kind.source, 80)
            d.hint("Model", report.model, report.kind.source + " model code", 80)
        }
        true
    }
    private fun description(d: Device, responder: InetAddress, location: String?, headers: Map<String, String> = emptyMap()) {
        if (!enriching || !inScope(responder) || d.self) return
        val target = UpnpDescription.target(location, responder)
        if (target == null) {
            if (location != null) {
                health.count("UPnP descriptions", "unsupported URLs")
                synchronized(d) { if (!descriptions.hasSuccess(d.ip)) d.detail("UPnP description status", "Advertised description URL is unsupported; model details were not read") }
            }
            return
        }
        val offer = descriptions.offer(d.ip, target)
        if (offer == DescriptionQueue.Offer.LIMIT) {
            health.truncated("UPnP descriptions", "32-device / 3-location description limit reached")
            synchronized(d) { if (!descriptions.hasSuccess(d.ip)) d.detail("UPnP description status", "Description request limit reached; model details were not read") }
            return
        }
        synchronized(d) { applyFamilies(d, ssdpMatchers.headers(d.ip, target, headers), "SSDP response and UPnP description") }
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
            net.check(context); selectedNetwork().bindSocket(socket)
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
            val body = UpnpDescription.readResponse(bounded)
            val values = UpnpDescription.parse(body)
            if (values.isEmpty()) throw IOException("No supported description fields")
            synchronized(d) {
                if (!enriching || cancelled.get() || found[d.ip] !== d || !freshResponders.contains(d.ip)) return
                applyDescription(d, values)
                applyFamilies(d, ssdpMatchers.description(d.ip, target, values), "SSDP response and UPnP description")
            }
            BroadAudioInfo.descriptionPlan(values)?.let { requestBroadAudio(d, it) }
            descriptions.record(d.ip, true); health.count("UPnP descriptions", "received")
            if (target.port == 1400) AudioZoneInfo.sonosIdentity(body)?.let { requestAudio(d, AudioZones.Kind.SONOS, it) }
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
    private fun applyFamilies(d: Device, domains: Set<String>, source: String) {
        if (d.self || domains.isEmpty()) return
        d.discoveryFamilies(domains, source)
    }
    private fun applyDescription(d: Device, values: Map<String, String>) {
        applyFamilies(d, DiscoveryMatchers.match("ssdp", values), "UPnP device description")
        val source = "UPnP device description"
        d.hint("Name", values["friendlyName"], source + " friendly name", 80)
        d.hint("Manufacturer", values["manufacturer"], source, 70)
        d.hint("Model", values["modelName"], source, 70)
        d.hint("Device type", UpnpDescription.deviceKind(values), source + " device type", 80)
        d.detail("UPnP model number", values["modelNumber"])
        d.detail("UPnP model description", values["modelDescription"])
        d.detail("UPnP serial number", values["serialNumber"])
        d.detail("UPnP device type", values["deviceType"])
        d.detail("UPnP description status", if (IdentityHints.specific(values["modelName"])) "Description received" else "Description received without a specific model")
    }
    private fun finishDescriptions() {
        if (descriptions.peers() == 0) return
        val failed = descriptions.failedPeers()
        if (failed > 0) health.fail("UPnP descriptions", "" + failed + " responding device(s) supplied no usable description; see device details")
        else health.ok("UPnP descriptions")
    }
    @Synchronized private fun startNsd() {
        if (!accepting || cancelled.get() || !resources.active()) return
        val now = System.nanoTime(); nsdStarted = now; lastServiceChange = now
        try {
            enableMulticast()
        } catch (e: Exception) {
            if (cancelled.get() || !resources.active()) return
            health.fail("Multicast reception", "Android could not enable multicast reception"); blocked(e)
        }
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
    @Synchronized private fun enableMulticast(): WifiManager.MulticastLock? {
        if (!accepting || cancelled.get() || !resources.active()) return null
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wm.createMulticastLock("device-discovery")
        val lease = object : Closeable {
            @Synchronized override fun close() { if (lock.isHeld) lock.release() }
        }
        try {
            lock.setReferenceCounted(false)
            lock.acquire()
            // Cancellation between acquire and registration closes the lease immediately.
            multicast = resources.own(lease)
            return lock
        } catch (e: Exception) {
            lease.close()
            throw e
        }
    }
    @Synchronized private fun discoverType(type: String) {
        if (!accepting || nsdTypes.contains(type)) return
        if (nsdTypes.size >= MdnsTypePlan.MAX_TYPES) { health.truncated(MDNS_SOURCE, "512 observed-service-type safety limit reached"); return }
        nsdTypes.add(type); health.starting(type)
        if (nsd == null) { health.startFailed(type); return }
        val browse = LegacyBrowse(type)
        val listener = object : NsdManager.DiscoveryListener {
            // Legacy callbacks can hold Android's listener-map lock. Post before taking our monitor.
            private fun deliver(task: Runnable) { resolverTimeouts.post(task) }
            override fun onDiscoveryStarted(t: String) {
                deliver {
                    if (accepting) {
                        health.started(type)
                    }
                }
            }
            override fun onDiscoveryStopped(t: String) { deliver { finishLegacyBrowse(browse, true) } }
            override fun onStartDiscoveryFailed(t: String, error: Int) {
                deliver { if (accepting) { health.startFailed(type); health.fail("mDNS " + type, "Android discovery error " + error); finishLegacyBrowse(browse, false) } }
            }
            override fun onStopDiscoveryFailed(t: String, error: Int) { deliver { if (accepting) health.fail("mDNS " + type, "Android could not stop discovery; error " + error) } }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) { if (admitService(type, info)) deliver { serviceFound(type, info) } }
        }
        browse.listener = listener; legacyBrowses.add(browse); startLegacyBrowses()
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
    @Synchronized private fun nativeBrowsingPending() = legacyBrowses.isNotEmpty() || legacyActive.isNotEmpty()
    /** Bound outstanding Android browsing on every API level; resolutions have separate slots. */
    @Synchronized private fun startLegacyBrowses() {
        if (!accepting || nsd == null) return
        while (legacyActive.size < MdnsTypePlan.CONCURRENT_BROWSES && legacyBrowses.isNotEmpty()) {
            val browse = legacyBrowses.remove(); legacyActive.add(browse); nsdListeners.add(browse.listener)
            browse.timeout = Runnable { stopLegacyBrowse(browse) }
            resolverTimeouts.postDelayed(browse.timeout!!, MdnsTypePlan.BROWSE_MILLIS)
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
    @Synchronized private fun mdnsPending(): Boolean = mdnsQuestions.values.any { it.attempts < 2 } || mdnsTypesPending.any { it.get() } || mdnsAssembly.pending(System.nanoTime())
    private fun startMdns() {
        val ipv6 = net.addresses.any { Address.bytes(it)?.size == 16 }
        mdnsTypesPending[0].set(net.ip.isNotEmpty()); mdnsTypesPending[1].set(ipv6)
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
                selectedNetwork().bindSocket(socket); socket.networkInterface = iface; socket.timeToLive = 255; socket.soTimeout = 200
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
            finally {
                // A failed receive channel cannot advance its queue. Preserve the Partial status
                // without extending the scan just to wait for a worker that has already exited.
                if (mdnsTypesPending[if (ipv6) 1 else 0].getAndSet(false) && accepting && !cancelled.get())
                    health.truncated(MDNS_SOURCE, "Service type query window did not finish")
                socket?.let { datagrams.remove(it); it.close() }
            }
        }, if (ipv6) "mdns-ipv6-discovery" else "mdns-query-discovery")
        resources.start(thread); return thread
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
        val retries = mdnsRetries.due(ipv6, now, 2)
        for (type in retries) if (shouldAsk(ipv6, MdnsPacket.Question((type + "local").split('.'), 12), now)) questions.add(type + "local")
        // Recovery queries do not consume normal reverse/type attempts.
        for (q in mdnsQuestions.values) if ((Address.bytes(q.ip)!!.size == 16) == ipv6 && q.attempts < 2 && now >= q.next) {
            if (shouldAsk(ipv6, MdnsPacket.Question(q.question.split('.'), 12), now)) questions.add(q.question)
            q.attempts++; q.next = now + TimeUnit.MILLISECONDS.toNanos(1200)
            if (questions.size >= 4) break
        }
        for (type in MdnsTypePlan.ordered(nsdTypes, typeAttempts)) {
            if (questions.size == 8) break
            if (typeAttempts.getOrDefault(type, 0) < 2 && now >= typeNext.getOrDefault(type, 0L)) {
                if (shouldAsk(ipv6, MdnsPacket.Question((type + "local").split('.'), 12), now)) questions.add(type + "local")
                typeAttempts[type] = typeAttempts.getOrDefault(type, 0) + 1
                typeNext[type] = now + TimeUnit.MILLISECONDS.toNanos(1200)
            }
        }
        mdnsTypesPending[if (ipv6) 1 else 0].set(MdnsTypePlan.ordered(nsdTypes, typeAttempts).any { typeAttempts.getOrDefault(it, 0) < 2 })
        return MdnsBatch(questions, retries.isNotEmpty())
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
        applyAssociatedModels(responder, System.nanoTime())
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
        // A TXT-only model can become unambiguous after a conflicting record expires.
        for (sender in mdnsAssembly.modelPeers(now)) applyAssociatedModels(sender, now)
        applyRouted(now)
    }
    private fun applyAssociatedModels(sender: InetAddress, now: Long) {
        if (!accepting || cancelled.get() || !inScope(sender)) return
        val peer = net.scoped(sender)
        if (!freshResponders.contains(peer)) return
        val device = found[peer] ?: return
        if (device.self) return
        for (attributes in mdnsAssembly.associatedModels(sender, now)) synchronized(device) {
            device.recognizeModel(attributes)
            device.hint("Model", attribute(attributes, "model"), "mDNS current _device-info._tcp. TXT", 20)
            device.evidence("Current associated Bonjour model record from this address")
        }
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
        if (!accepting || cancelled.get()) return
        synchronized(d) {
            d.recognizeServiceModel(service.type, service.attributes)
            applyService(d, service, "mDNS current")
        }
        synchronized(d) {
            if (enriching && !d.self && found[d.ip] === d && freshResponders.contains(d.ip) && inScope(peerAddress(d.ip))) {
                val protocol = if (canonicalType(service.type) == "_airplay._tcp.") AudioPlayers.Protocol.AIRPLAY
                    else if (CastInfo.isGroup(service)) AudioPlayers.Protocol.CAST else null
                if (protocol != null) {
                    val key = "advertisement:" + AudioPlayers.key(protocol, service.port)
                    val player = BroadAudioInfo.advertisement(service, d.ip)
                    if (player == null) d.audioPlayers.remove(key)
                    else synchronized(audioAdvertisements) {
                        val admission = d.ip + "|" + key
                        if ((admission in audioAdvertisements || audioAdvertisements.size < 64) &&
                            (key in d.audioPlayers || d.audioPlayers.size < 16)) {
                            audioAdvertisements.add(admission); d.audioPlayers[key] = player
                        } else health.truncated(player.protocol.source, "Audio advertisement limit reached")
                    }
                }
            }
        }
        BroadAudioInfo.advertisementPlan(service)?.let { requestBroadAudio(d, it) }
        if (service.port == AppleDeviceInfo.PORT) requestAppleModel(d)
        if (service.port == 80) requestEapModel(d)
        if (service.port == 8001 && LocalServices.tcpConfirmation(service.type)) requestSamsung(d)
        if (AudioZoneInfo.vsslCandidate(service)) requestAudio(d, AudioZones.Kind.VSSL, "")
    }
    private fun applyService(d: Device, service: MdnsPacket.Service, prefix: String) {
        synchronized(d) {
            val type = service.type; val name = service.name; val source = prefix + " " + type
            if (canonicalType(type) in setOf("_musc._tcp.", "_musp._tcp.")) {
                // Per-port players and zone names are not hardware identity or grouping keys.
                d.service(type); d.port(service.port); return
            }
            if (CastInfo.isGroup(service)) {
                // A playback group's UUID, name and model describe a virtual target, not its host.
                d.service("Cast speaker group"); d.port(service.port)
                d.detail("Cast group (advertised) · port " + service.port, attribute(service.attributes, "fn").ifEmpty { name })
                return
            }
            if (canonicalType(type) == "_googlecast._tcp.") castReceivers.add(d.ip)
            d.service(type); if (LocalServices.tcpConfirmation(type)) d.port(service.port)
            applyFamilies(d, DiscoveryMatchers.mdns(service), source)
            val homekitFamilies = DiscoveryMatchers.homekit(service)
            if (homekitFamilies.isNotEmpty()) d.detail("HomeKit integration candidates", homekitFamilies.sorted().joinToString(", "))
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
            if (!d.self) FireTvInfo.parse(service)?.let { fire ->
                d.hint("Name", fire.name, source + " TXT n (friendly name)", 90)
                d.recognizeFireTvType(fire.typeCode, source + " TXT ad")
                d.discoveryFamilies(setOf(FireTvInfo.FAMILY), source)
                d.detail("Amazon discovery", "Fire TV family from a WhisperPlay advertisement; model and generation require additional evidence")
                d.detail("Amazon device type code (advertised)", fire.typeCode)
            }
            if (!d.self && SmartThingsInfo.matches(service)) d.recognizeSmartThings(source)
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
                d.hint("Device type", "Audio service", source, 60)
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
            else if (!d.self && type == "_matterc._udp.") {
                d.hint("Device type", "Matter setup service", source + " service", 35)
                MatterInfo.parse(service.attributes)?.let { d.recognizeMatter(it, source) }
            }
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
        queue.clear(); multicast?.close()
    }
    companion object {
        private val NSD_HANDLE = ServiceHandle<NsdManager>()
        private val ACTIVE = AtomicBoolean()
        private val PORTS = intArrayOf(80, 443, 22, 445, 62078, 631, 8008, 8001)
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
