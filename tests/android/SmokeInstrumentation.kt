@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

package io.github.kazands.networklens.test

import android.app.*
import android.content.*
import android.content.pm.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import io.github.kazands.networklens.*
import java.io.*
import java.lang.reflect.*
import java.net.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import org.json.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull

/** Runs only against isolated emulators. Fixture metadata is never called live discovery. */
/** Keep Java platform nulls observable by the original null-guard assertions. */
private fun <T> nullablePlatformResult(value: T?): T? = value

class SmokeInstrumentation : Instrumentation() {
    // Full scans have a 55-second work deadline plus bounded cleanup.

    private var checks: Int = 0

    private var activity: Activity? = null

    private var arguments: Bundle? = null

    override fun onCreate(b: Bundle?): Unit {
        super.onCreate(b)
        arguments = b
        start()
    }

    private fun check(value: Boolean, message: String): Unit {
        checks++
        if (!(value)) {
            throw AssertionError(message)
        }
        if (((checks % 10) == 0)) {
            var progress: Bundle = Bundle()
            progress.putString(
                "stream",
                (((("Progress · " + checks) + " checks · ") + message) + "\n"),
            )
            sendStatus(0, progress)
        }
    }

    private var compose: ComposeDriver? = null
    override fun onStart() {
        val result=Bundle()
        try {
            val mode=arguments?.getString("mode","full") ?: "full"
            InstrumentationStart.prepare(mode,{
                val qemu=Runtime.getRuntime().exec(arrayOf("/system/bin/getprop","ro.kernel.qemu"))
                check(qemu.inputStream.bufferedReader().use {it.readText().trim()}=="1","Instrumentation rejects physical devices before app state changes")
            },{Store(targetContext).dark(false)})
            compose=ComposeDriver(this,::check)
            when(mode) {
                "ipv4-scope" -> Ipv4ScopeChecks.run(this,::check)
                "compose-privacy" -> ComposePrivacyChecks.run(this,::check)
                "compose-update-network" -> UpdateNetworkChecks.run(this,::check)
                "compose-list-anchor" -> ListAnchorChecks.run(this,::check)
                "compose-icmp-errors" -> IcmpUiChecks.fixture(this,::check)
                "compose-design" -> ComposeDesignChecks.run(this,::check)
                "compose-ui","compose-typography","compose-updates","compose-reports","compose-rtl","navigation","options","scan-details","settings-scroll","lifecycle","full","branding","wifi-name","grouping","ui-refinement","home-user","detail-stability","typography","recognition-report" -> ComposeUiChecks.run(this,::check,mode)
                "failure-reporting" -> failureReporting()
                "freshness-fixes" -> freshnessFixes()
                "split-mdns" -> splitMdns()
                "homekit" -> homeKit()
                "legacy-seed" -> legacySeed()
                "session-only" -> sessionOnly()
                "session-cold" -> sessionCold()
                "fresh-presence" -> freshPresence()
                "mac-vendors" -> macVendors()
                "service-retries" -> serviceRetries()
                "details-upgrade" -> ComposeUiChecks.run(this,::check,"compose-ui")
                "resolver-upgrade" -> resolverUpgrade()
                "engine-upgrade" -> engineUpgrade()
                "icmp-denied" -> icmpDeniedFlow()
                "icmp" -> icmpFlow()
                "confirmed-fixes" -> confirmedFixes()
                "ws-discovery" -> wsDiscovery()
                "discovery-upgrade" -> discoveryUpgrade()
                "permission" -> permissionFlow()
                "scan-efficiency" -> ScanEfficiency.run(this,::check)
                "scan-ownership" -> ScanOwnershipChecks.run(this,::check)
                "performance" -> PerformanceChecks.run(this,::check)
                "local-metadata" -> MetadataChecks.run(this,::check)
                "public-identity" -> PublicIdentityChecks.run(this,::check)
                "recog" -> RecogChecks.run(this,::check)
                "fibe-tv" -> FibeTvChecks.run(this,::check)
                "network-names" -> NetworkNameChecks.run(this,::check)
                "kidde" -> KiddeChecks.run(this,::check)
                "smartthings" -> SmartThingsChecks.run(this,::check)
                "envisalink" -> EnvisalinkChecks.run(this,::check)
                "fire-tv" -> FireTvIdentityChecks.run(this,::check)
                "google-cast" -> GoogleCastIdentityChecks.run(this,::check)
                "app-updates" -> { AppUpdateChecks.run(this,::check);ComposeUiChecks.run(this,::check,"compose-updates") }
                "audio-zones" -> { AudioZoneChecks.run(this,::check);BroadAudioChecks.run(this,::check) }
                "identification" -> DeviceIdentityChecks.run(this,::check)
                "device-identities" -> { BroadRecognitionChecks.run(this,::check);KiddeChecks.run(this,::check);SmartThingsChecks.run(this,::check)
                    EnvisalinkChecks.run(this,::check);AppleRecognitionChecks.run(this,::check);AndroidRecognitionChecks.run(this,::check)
                    GoogleCastIdentityChecks.run(this,::check);FireTvIdentityChecks.run(this,::check);FibeTvChecks.run(this,::check);DeviceIdentityChecks.run(this,::check) }
                else -> throw IllegalArgumentException("Unknown instrumentation mode: $mode")
            }
            result.putString("stream","PASS · $checks Android checks · $mode · API ${Build.VERSION.SDK_INT}\n")
            finish(Activity.RESULT_OK,result)
        } catch(e: Throwable) {
            result.putString("stream","FAIL: $e\n"+android.util.Log.getStackTraceString(e))
            finish(Activity.RESULT_CANCELED,result)
        } finally {compose?.close();compose=null}
    }

    private fun copyProperties(original: android.net.LinkProperties): android.net.LinkProperties {
        return copyProperties(original, true)
    }

    private fun copyProperties(
        original: android.net.LinkProperties,
        includeIpv6Routes: Boolean,
    ): android.net.LinkProperties {
        var copy: android.net.LinkProperties = android.net.LinkProperties()
        copy.setInterfaceName(original.getInterfaceName())
        copy.setLinkAddresses(original.getLinkAddresses())
        copy.setDnsServers(original.getDnsServers())
        for (route in original.routes) {
            if ((includeIpv6Routes || ((route.destination)!!.address is Inet4Address))) {
                copy.addRoute(route)
            }
        }
        return copy
    }

    private fun fixtureAddress(
        original: android.net.LinkProperties,
        address: String,
        prefix: Int,
    ): android.net.LinkAddress {
        var bytes: ByteArray = (InetAddress.getByName(address))!!.address
        var template: android.net.LinkAddress? = null
        for (candidate in original.getLinkAddresses()) {
            if ((((candidate.address)!!.address)!!.size == bytes.size)) {
                template = candidate
                break
            }
        }
        if ((template === null)) {
            throw AssertionError("Emulator lacks an address in the fixture family")
        }
        var parcel: Parcel = Parcel.obtain()
        try {
            template!!.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            parcel.writeByteArray(bytes)
            parcel.writeInt(prefix)
            parcel.writeInt(0)
            parcel.writeInt(0)
            parcel.setDataPosition(0)
            var result: android.net.LinkAddress =
                android.net.LinkAddress.CREATOR.createFromParcel(parcel)
            check(
                (Arrays.equals((result.address)!!.address, bytes) &&
                    (result.getPrefixLength() == prefix)),
                "Parcel fixture has the requested numeric prefix",
            )
            return result
        } finally {
            parcel.recycle()
        }
    }

    private fun networkFixture(
        context: Context,
        network: android.net.Network,
        properties: android.net.LinkProperties,
    ): NetworkState {
        var read: Method =
            NetworkState::class
                .java
                .getDeclaredMethod(
                    "fromProperties",
                    Context::class.java,
                    android.net.Network::class.java,
                    android.net.LinkProperties::class.java,
                )
        read.setAccessible(true)
        return (read.invoke(null, context, network, properties) as NetworkState)
    }

    private fun reconcileFixture(state: NetworkState?): Unit {
        onMain(
            callback1@{
                try {
                    var reconcile: Method =
                        (activity!!.javaClass)!!.getDeclaredMethod(
                            "reconcileNetwork",
                            NetworkState::class.java,
                        )
                    reconcile.setAccessible(true)
                    reconcile.invoke(activity, state)
                    invoke("render")
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            }
        )
        settle()
    }

    private fun recoveryReachable(label: String) {
        compose!!.reveal(label,"cleanup")
        onMain {
            var node = compose!!.text(label)!!
            while (node.config.getOrNull(SemanticsActions.OnClick)?.action == null && node.parent != null) node = node.parent!!
            check(node.config.getOrNull(SemanticsActions.OnClick)?.action != null && node.touchBoundsInRoot.height >= 48 * compose!!.root().density.density,
                "Recovery action retains an accessible clickable touch target: $label")
        }
    }

    class CleanupContext : ContextWrapper {
        var credential: Boolean = false
        var device: Boolean = false
        var throwCredential: Boolean = false
        var throwDevice: Boolean = false

        var credentialCalls: Int = 0
        var deviceCalls: Int = 0

        constructor(base: Context) : super(base) {}

        override fun getApplicationContext(): Context {
            return this
        }

        override fun deleteSharedPreferences(name: String): Boolean {
            credentialCalls++
            if (throwCredential) {
                throw SecurityException("fixture")
            }
            return credential
        }

        override fun createDeviceProtectedStorageContext(): Context {
            return object :
                ContextWrapper((getBaseContext())!!.createDeviceProtectedStorageContext()) {
                override fun deleteSharedPreferences(name: String): Boolean {
                    deviceCalls++
                    if (throwDevice) {
                        throw SecurityException("fixture")
                    }
                    return device
                }
            }
        }
    }

    private fun freshnessFixes(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        live.check(target)
        Ipv4ScopeChecks.run(this, ::check)
        store.clear()
        var receive: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsResolved",
                    InetAddress::class.java,
                    MdnsPacket.Result::class.java,
                )
        var resolved: Method =
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
        var drain: Method =
            Discovery::class.java.getDeclaredMethod("drainMdns", Long::class.javaPrimitiveType!!)
        receive.setAccessible(true)
        resolved.setAccessible(true)
        drain.setAccessible(true)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
        var assembly: Field = Discovery::class.java.getDeclaredField("mdnsAssembly")
        accepting.setAccessible(true)
        types.setAccessible(true)
        assembly.setAccessible(true)
        run {
            var scenario: Int = 0
            while ((scenario < 3)) {
                var scan: Discovery = Discovery(target, live)
                accepting.set(scan, true)
                (((types.get(scan) as MutableSet<String>?)))!!.add("_hap._tcp.")
                try {
                    var positive: ByteArray =
                        txtWire(
                            "Expiry.fixture",
                            "_hap._tcp.",
                            (if ((scenario == 1)) 1 else 120),
                            "md=Withdrawn fixture model",
                            "sf=1",
                        )
                    receive.invoke(
                        scan,
                        InetAddress.getByName(live.gateway),
                        MdnsPacket.parse(positive, positive.size),
                    )
                    check(
                        (((scan.snapshot())!!.size == 1) &&
                            ((scan.snapshot())!!.get(0))!!.model.isEmpty()),
                        "Partial TXT cannot establish identity before complete peer records",
                    )
                    if ((scenario == 0)) {
                        var goodbye: ByteArray =
                            txtWire(
                                "Expiry.fixture",
                                "_hap._tcp.",
                                0,
                                "md=Withdrawn fixture model",
                                "sf=1",
                            )
                        receive.invoke(
                            scan,
                            InetAddress.getByName(live.gateway),
                            MdnsPacket.parse(goodbye, goodbye.size),
                        )
                    } else {
                        if ((scenario == 2)) {
                            var replacement: ByteArray =
                                txtWire(
                                    "Expiry.fixture",
                                    "_hap._tcp.",
                                    120,
                                    "fn=Replacement fixture",
                                )
                            receive.invoke(
                                scan,
                                InetAddress.getByName(live.gateway),
                                MdnsPacket.parse(replacement, replacement.size),
                            )
                        }
                    }
                    SystemClock.sleep(1100)
                    drain.invoke(scan, System.nanoTime())
                    var late: NsdServiceInfo = NsdServiceInfo()
                    late.setServiceName("Expiry.fixture")
                    late.setServiceType("_hap._tcp.")
                    late.setHost(InetAddress.getByName(live.gateway))
                    if ((Build.VERSION.SDK_INT >= 34)) {
                        late.setHostAddresses(Collections.singletonList(late.host))
                    }
                    late.setAttribute("md", "Cached native model")
                    resolved.invoke(scan, late)
                    var result: Device = (scan.snapshot())!!.get(0)
                    check(
                        (result.model.isEmpty() &&
                            !(result.details.containsKey("HomeKit setup (advertised)"))),
                        ("Late NSD cannot revive withdrawn, expired or replaced TXT: " + scenario),
                    )
                    late.setServiceName("Fresh.current.fixture")
                    late.setAttribute("md", "Current fixture model")
                    late.setAttribute("fn", "Current fixture name")
                    freshNsd(scan, late)
                    result = (scan.snapshot())!!.get(0)
                    check(
                        (result.model.equals("Current fixture model") &&
                            result.name.equals("Current fixture name")),
                        ("Complete current peer records still identify a dotted native instance: " +
                            scenario),
                    )
                } finally {
                    scan.cancel()
                }
                scenario++
            }
        }
        activity = launch(false)
        settle()
        var broken: CleanupContext = CleanupContext(target)
        var legacy: Field = Store::class.java.getDeclaredField("legacyRemoved")
        legacy.setAccessible(true)
        legacy.setBoolean(null, false)
        var recovery: Store = Store(broken)
        check(
            ((!(recovery.ready()) && (broken.credentialCalls == 1)) && (broken.deviceCalls == 1)),
            "Failed cleanup remains recoverable and attempts both storage domains",
        )
        check(
            !(recovery.startScanIfCurrent(recovery.token())),
            "Scanning cannot start while old data remains",
        )
        broken.throwCredential = true
        broken.throwDevice = true
        check(
            ((!(recovery.migrate()) && (broken.credentialCalls == 2)) && (broken.deviceCalls == 2)),
            "Storage exceptions remain recoverable and do not skip the other domain",
        )
        broken.throwCredential = false
        broken.throwDevice = false
        onMain(
            callback21@{
                setField("store", recovery)
                invoke("stopWatchingConnection")
                invoke("render")
            }
        )
        settle()
        assertText("Finish removing older app data")
        assertText("Retry cleanup")
        check(
            !((texts())!!.contains("Scan Wi-Fi")),
            "Recovery screen does not claim a clean normal session",
        )
        capture("cleanup-recovery-light")
        click("Retry cleanup")
        assertText("Finish removing older app data")
        description("Switch to dark mode")
        assertText("Retry cleanup")
        capture("cleanup-recovery-dark")
        onMain(
            callback22@{
                var c: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                c.fontScale = 2f
                (activity!!.getResources())!!.updateConfiguration(
                    c,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                invoke("render")
            }
        )
        settle()
        recoveryReachable("Open Android settings")
        capture("cleanup-recovery-large-text")
        onMain(
            callback23@{
                activity!!.setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                )
            }
        )
        SystemClock.sleep(900)
        settle()
        recoveryReachable("Retry cleanup")
        recoveryReachable("Open Android settings")
        capture("cleanup-recovery-landscape")
        onMain(
            callback24@{
                activity!!.setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                )
            }
        )
        SystemClock.sleep(900)
        settle()
        onMain(
            callback25@{
                var c: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                c.fontScale = 1f
                (activity!!.getResources())!!.updateConfiguration(
                    c,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                invoke("render")
            }
        )
        settle()
        recoveryReachable("Retry cleanup")
        var monitor: ActivityMonitor = addMonitor("io.github.kazands.networklens.MainActivity", null, false)
        broken.credential = true
        broken.device = true
        click("Retry cleanup")
        var replacement: Activity? =
            nullablePlatformResult(waitForMonitorWithTimeout(monitor, 8000))
        removeMonitor(monitor)
        check((replacement !== null), "Successful cleanup recreates the ordinary app")
        activity = replacement
        settle()
        assertText("Your devices")
        check(
            (Store(target).ready() && !((field("scanning") as Boolean))),
            "Recovery returns to an empty app without automatic discovery",
        )
        check(Store(target).migrate(), "Real Android storage cleanup remains successful")
        onMain(callback26@{ activity!!.finish() })
        store.clear()
    }

    private fun splitMdns(): Unit {
        var target: Context = getTargetContext()
        var live: NetworkState = NetworkState.current(target)
        check(
            ((live.network !== null) && !(live.ip.isEmpty())),
            "Split service test has selected Wi-Fi",
        )
        var receive: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsResolved",
                    InetAddress::class.java,
                    MdnsPacket.Result::class.java,
                )
        var raw: Method =
            Discovery::class
                .java
                .getDeclaredMethod("startMdnsReceiver")
        receive.setAccessible(true)
        raw.setAccessible(true)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
        accepting.setAccessible(true)
        types.setAccessible(true)
        var scan: Discovery = Discovery(target, live)
        accepting.set(scan, true)
        (((types.get(scan) as MutableSet<String>?)))!!.add("_networklens-fixture._tcp.")
        var peer: InetAddress = InetAddress.getByName(live.gateway)
        try {
            for (part in intArrayOf(2, 0, 3)) {
                var p: ByteArray = splitPacket(live.gateway, part)
                receive.invoke(scan, peer, MdnsPacket.parse(p, p.size))
                check(
                    (((scan.snapshot())!!.size == 1) &&
                        ((scan.snapshot())!!.get(0))!!.model.isEmpty()),
                    "Partial service replies establish sender presence without inventing identity",
                )
            }
            var p: ByteArray = splitPacket(live.gateway, 1)
            receive.invoke(scan, peer, MdnsPacket.parse(p, p.size))
            var d: Device = (scan.snapshot())!!.get(0)
            check(
                (d.name.equals("Fresh split name") && d.model.equals("Fresh split model")),
                "General split records recover identity without an NSD callback",
            )
            check(
                d.evidence.contains("Current service records from this address"),
                "Recovery has explicit current-peer provenance",
            )
            check(d.mac.isEmpty(), "Service recovery does not invent a MAC")
            scan.cancel()
            p = splitPacket(live.gateway, 2)
            receive.invoke(scan, peer, MdnsPacket.parse(p, p.size))
            check(
                ((scan.snapshot())!!.get(0))!!.name.equals("Fresh split name"),
                "Late packets cannot modify a stopped scan",
            )
        } finally {
            scan.cancel()
        }
        var fresh: Discovery = Discovery(target, live)
        accepting.set(fresh, true)
        (((types.get(fresh) as MutableSet<String>?)))!!.add("_networklens-fixture._tcp.")
        try {
            var p: ByteArray = splitPacket(live.gateway, 3)
            receive.invoke(fresh, peer, MdnsPacket.parse(p, p.size))
            check(
                ((fresh.snapshot())!!.get(0))!!.model.isEmpty(),
                "New scan starts with an empty service assembly",
            )
        } finally {
            fresh.cancel()
        }
        var proxy: Discovery = Discovery(target, live)
        accepting.set(proxy, true)
        (((types.get(proxy) as MutableSet<String>?)))!!.add("_networklens-fixture._tcp.")
        try {
            run {
                var part: Int = 0
                while ((part < 4)) {
                    var p: ByteArray = splitPacket(live.gateway, part)
                    receive.invoke(
                        proxy,
                        InetAddress.getByName(live.ip),
                        MdnsPacket.parse(p, p.size),
                    )
                    part++
                }
            }
            check(
                ((proxy.snapshot())!!.get(0))!!.model.isEmpty(),
                "A proxy's service graph cannot label its sender as the other device",
            )
        } finally {
            proxy.cancel()
        }
        var burst: MdnsAssembly = MdnsAssembly()
        var ipv4Peer: InetAddress = InetAddress.getByName("192.0.2.20")
        var now: Long = System.nanoTime()
        for (part in intArrayOf(0, 1, 3)) {
            var p: ByteArray = splitPacket("192.0.2.20", part)
            burst.accept(ipv4Peer, MdnsPacket.parse(p, p.size), now)
        }
        var alternate: ByteArray = splitPacket("192.0.2.21", 3)
        var txt: ByteArray = splitPacket("192.0.2.20", 2)
        burst.accept(ipv4Peer, MdnsPacket.parse(alternate, alternate.size), (now + 200000000L))
        check(
            ((burst.accept(ipv4Peer, MdnsPacket.parse(txt, txt.size), (now + 400000000L)))!!.size ==
                1),
            "Split A cache-flush burst retains its recent matching address",
        )
        burst.accept(ipv4Peer, MdnsPacket.parse(alternate, alternate.size), (now + 2000000000L))
        check(
            (burst.accept(ipv4Peer, MdnsPacket.parse(txt, txt.size), (now + 3000000000L)))!!
                .isEmpty(),
            "Older unrefreshed address expires after cache-flush grace",
        )
        check(
            ((burst.due((now + 3000000000L), 4))!!.stream())!!.anyMatch(
                callback45@{ q -> (q.kind == 1) }
            ),
            "Missing matching A triggers repair even when an alternate exists",
        )
        burst.close()
        var timed: MdnsAssembly = MdnsAssembly()
        var timedPeer: InetAddress = InetAddress.getByName(live.gateway)
        var clock: Long = System.nanoTime()
        run {
            var part: Int = 0
            while ((part < 4)) {
                var p: ByteArray = splitPacket(live.gateway, part)
                timed.accept(timedPeer, MdnsPacket.parse(p, p.size), clock)
                part++
            }
        }
        var newTxt: ByteArray = splitPacket(live.gateway, 2)
        run {
            var pos: Int = 0
            while ((pos < (newTxt.size - 5))) {
                if (
                    ((((((newTxt[pos]).toInt() == ('F').code.toInt()) &&
                        ((newTxt[(pos + 1)]).toInt() == ('r').code.toInt())) &&
                        ((newTxt[(pos + 2)]).toInt() == ('e').code.toInt())) &&
                        ((newTxt[(pos + 3)]).toInt() == ('s').code.toInt())) &&
                        ((newTxt[(pos + 4)]).toInt() == ('h').code.toInt()))
                ) {
                    newTxt[pos] = 'N'.code.toByte()
                }
                pos++
            }
        }
        timed.accept(timedPeer, MdnsPacket.parse(newTxt, newTxt.size), (clock + 2000000000L))
        check(
            timed.pending((clock + 2500000000L)),
            "Expiry waiting is visible to the scan quiet-window decision",
        )
        check(
            ((timed.ready((clock + 3000000000L)))!!.size == 1),
            "Timer completes a resolved TXT conflict without another packet",
        )
        check(
            (timed.ready((clock + 3100000000L)))!!.isEmpty(),
            "Completed timer results are delivered only once",
        )
        timed.close()
        var delayed: Discovery = Discovery(target, live)
        accepting.set(delayed, true)
        (((types.get(delayed) as MutableSet<String>?)))!!.add("_networklens-fixture._tcp.")
        var assemblyField: Field = Discovery::class.java.getDeclaredField("mdnsAssembly")
        assemblyField.setAccessible(true)
        var drain: Method =
            Discovery::class.java.getDeclaredMethod("drainMdns", Long::class.javaPrimitiveType!!)
        drain.setAccessible(true)
        try {
            var address: ByteArray? = nullablePlatformResult(splitPacket(live.gateway, 3))
            receive.invoke(delayed, peer, MdnsPacket.parse(address, (address)!!.size))
            var assembly: MdnsAssembly? = (assemblyField.get(delayed) as MdnsAssembly?)
            clock = System.nanoTime()
            run {
                var part: Int = 0
                while ((part < 3)) {
                    var p: ByteArray = splitPacket(live.gateway, part)
                    (assembly)!!.accept(peer, MdnsPacket.parse(p, p.size), clock)
                    part++
                }
            }
            (assembly)!!.accept(peer, MdnsPacket.parse(newTxt, newTxt.size), (clock + 2000000000L))
            drain.invoke(delayed, (clock + 2500000000L))
            check(
                ((delayed.snapshot())!!.get(0))!!.model.isEmpty(),
                "Production timer withholds conflicting identity during grace",
            )
            drain.invoke(delayed, (clock + 3000000000L))
            check(
                ((delayed.snapshot())!!.get(0))!!.model.equals("Nresh split model"),
                "Production timer applies the current model after expiry without another packet",
            )
            var changed: Field = Discovery::class.java.getDeclaredField("lastServiceChange")
            changed.setAccessible(true)
            var settled: Long = changed.getLong(delayed)
            drain.invoke(delayed, (clock + 3100000000L))
            check(
                (changed.getLong(delayed) == settled),
                "Repeated production timer ticks do not prolong the quiet window",
            )
        } finally {
            delayed.cancel()
        }
        // Observe the production follow-up datagram on Android's real selected interface.
        var wired: Discovery = Discovery(target, live)
        accepting.set(wired, true)
        (((types.get(wired) as MutableSet<String>?)))!!.add("_networklens-fixture._tcp.")
        var observer: java.net.MulticastSocket = java.net.MulticastSocket(null)
        var worker: Thread? = null
        try {
            observer.setReuseAddress(true)
            observer.bind(InetSocketAddress(5353))
            (live.network)!!.bindSocket(observer)
            var iface: NetworkInterface = NetworkInterface.getByName(live.iface)
            observer.setNetworkInterface(iface)
            observer.setSoTimeout(300)
            var group: InetAddress = InetAddress.getByName("224.0.0.251")
            observer.joinGroup(InetSocketAddress(group, 5353), iface)
            var p: ByteArray = splitPacket(live.ip, 0)
            receive.invoke(wired, InetAddress.getByName(live.ip), MdnsPacket.parse(p, p.size))
            // A valid whitespace-only DNS label must survive auxiliary model querying.
            // The later ordinary reply also proves this input did not terminate the receiver.
            for (part in 0..3) {
                val anchor = splitPacket(live.ip, part, "_companion-link._tcp.local", " ")
                receive.invoke(wired, InetAddress.getByName(live.ip), MdnsPacket.parse(anchor, anchor.size))
            }
            check(wired.snapshot().single { it.ip == live.ip }.model.isEmpty(),
                "Whitespace anchor has no model before the later ordinary multicast reply")
            worker = (raw.invoke(wired) as Thread?)
            var sawSrv: Boolean = false
            var sawTxt: Boolean = false
            var sawRawModelName = false
            var deadline: Long = (SystemClock.uptimeMillis() + 5000)
            while (SystemClock.uptimeMillis() < deadline && !(sawSrv && sawTxt && sawRawModelName)) {
                var b: ByteArray = ByteArray(2048)
                var packet: DatagramPacket = DatagramPacket(b, b.size)
                try {
                    observer.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                if (((packet.length < 12) || ((((b[2]).toInt() and (128).toInt())) != 0))) {
                    continue
                }
                if (MdnsPacket.multicastQuestions(b, packet.length).any {
                    it.kind == 16 && it.labels == listOf(" ", "_device-info", "_tcp", "local")
                }) sawRawModelName = true
                var n: Int =
                    ((((((((b[4]).toInt() and (255).toInt()))).toInt() shl (8).toInt()))).toInt() or
                        ((((b[5]).toInt() and (255).toInt()))).toInt())
                var pos: Int = 12
                run {
                    var q: Int = 0
                    while (((q < n) && (pos < packet.length))) {
                        var name: StringBuilder = StringBuilder()
                        while (
                            ((pos < packet.length) && ((((b[pos]).toInt() and (255).toInt())) != 0))
                        ) {
                            var size: Int = ((b[pos++]).toInt() and (255).toInt())
                            if (((size > 63) || ((pos + size) > packet.length))) {
                                throw AssertionError("Invalid local query")
                            }
                            if ((name.length > 0)) {
                                name.append('.')
                            }
                            name.append(
                                String(b, pos, size, java.nio.charset.Charset.forName("UTF-8"))
                            )
                            pos += size
                        }
                        pos++
                        if (((pos + 4) > packet.length)) {
                            break
                        }
                        var kind: Int =
                            ((((((((b[pos]).toInt() and (255).toInt()))).toInt() shl (8).toInt())))
                                .toInt() or ((((b[(pos + 1)]).toInt() and (255).toInt()))).toInt())
                        pos += 4
                        if (
                            (name.toString())!!.equals(
                                "Split fixture._networklens-fixture._tcp.local"
                            )
                        ) {
                            sawSrv = sawSrv or (kind == 33)
                            sawTxt = sawTxt or (kind == 16)
                        }
                        q++
                    }
                }
            }
            check(
                (sawSrv && sawTxt),
                "Actual selected-interface multicast carries explicit SRV and TXT follow-up questions",
            )
            check(sawRawModelName && worker!!.isAlive,
                "Auxiliary multicast preserves the raw whitespace label and keeps the discovery receiver running")
            run {
                var part: Int = 1
                while ((part < 4)) {
                    var reply: ByteArray? = splitPacket(live.ip, part)
                    observer.send(DatagramPacket(reply, reply!!.size, group, 5353))
                    SystemClock.sleep(40)
                    part++
                }
            }
            deadline = (SystemClock.uptimeMillis() + 3000)
            var identified: Boolean = false
            while ((SystemClock.uptimeMillis() < deadline)) {
                for (d in wired.snapshot()) {
                    if ((d.ip.equals(live.ip) && d.model.equals("Fresh split model"))) {
                        identified = true
                    }
                }
                if (identified) {
                    break
                }
                SystemClock.sleep(50)
            }
            check(
                identified,
                "Actual split multicast responses reach the production device identity",
            )
        } finally {
            wired.cancel()
            observer.close()
            if ((worker !== null)) {
                worker!!.join(1500)
            }
        }
        check(
            ((worker !== null) && !(worker!!.isAlive())),
            "Stop closes the new resolver's actual receive loop",
        )
    }

    private fun homeKit(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        var peer: InetAddress = InetAddress.getByName(live.gateway)
        check(
            (!(live.gateway.isEmpty()) && !(live.ip.equals(live.gateway))),
            "Fixture uses a separate on-link endpoint",
        )
        var receive: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsResolved",
                    InetAddress::class.java,
                    MdnsPacket.Result::class.java,
                )
        var resolve: Method =
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
        var admit: Method =
            Discovery::class
                .java
                .getDeclaredMethod("admitService", String::class.java, NsdServiceInfo::class.java)
        for (m in arrayOf(receive, resolve, admit)) {
            m.setAccessible(true)
        }
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
        var healthField: Field = Discovery::class.java.getDeclaredField("health")
        var enriching: Field = Discovery::class.java.getDeclaredField("enriching")
        for (f in arrayOf(accepting, types, healthField, enriching)) {
            f.setAccessible(true)
        }
        var raw: ByteArray = hkPacket(live.gateway, "_hap._tcp.", true)
        var wire: MdnsPacket.Result = MdnsPacket.parse(raw, raw.size)
        var direct: Discovery = Discovery(target, live)
        accepting.set(direct, true)
        (((types.get(direct) as MutableSet<String>?)))!!.add("_hap._tcp.")
        var health: DiscoveryHealth? = (healthField.get(direct) as DiscoveryHealth?)
        var info: NsdServiceInfo = NsdServiceInfo()
        info.setServiceName("HOMEKIT FIXTURE")
        info.setServiceType("_hap._tcp.")
        (health)!!.begin("mDNS _hap._tcp.")
        try {
            check(
                (admit.invoke(direct, "_hap._tcp.", info) as Boolean),
                "Native candidate can exist without a resolved address",
            )
            receive.invoke(direct, peer, wire)
            check(
                ((direct.snapshot())!!.size == 1),
                "A current direct packet is sufficient when native NSD has no address",
            )
            var d: Device = (direct.snapshot())!!.get(0)
            check(
                ((d.name.equals("HomeKit fixture") && d.model.equals("Current HomeKit model")) &&
                    d.kind!!.equals("HomeKit light")),
                "Direct current name, model and category reach the displayed device",
            )
            check(
                (d.mac.isEmpty() && d.macSource.isEmpty()),
                "HomeKit pairing ID never becomes a hardware MAC",
            )
            check(
                (d.details.get("HomeKit setup (advertised)"))!!.equals("Not paired"),
                "sf bit zero supplies advertised setup state",
            )
            check(
                d.evidence.contains("Current HomeKit service records from this address"),
                "The fallback has distinct discovery evidence",
            )
            check(
                ((direct.sources())!!.get("mDNS _hap._tcp."))!!.contains("live service records 1"),
                "Direct services are counted separately from native resolver success",
            )
            receive.invoke(direct, peer, wire)
            check(
                ((direct.sources())!!.get("mDNS _hap._tcp."))!!.contains("live service records 1"),
                "Repeated packets do not duplicate the service count",
            )
            check(
                !((admit.invoke(direct, "_hap._tcp.", info) as Boolean)),
                "A recovered instance does not create another native resolver request",
            )
            if ((Build.VERSION.SDK_INT >= 34)) {
                var requestClass: Class<*> = Class.forName("io.github.kazands.networklens.Discovery\$ModernRequest")
                var ctor: Constructor<*> =
                    requestClass.getDeclaredConstructor(
                        NsdServiceInfo::class.java,
                        Int::class.javaPrimitiveType!!,
                    )
                ctor.setAccessible(true)
                var finish: Method =
                    Discovery::class
                        .java
                        .getDeclaredMethod(
                            "finishModern",
                            requestClass,
                            Boolean::class.javaPrimitiveType!!,
                            String::class.java,
                        )
                finish.setAccessible(true)
                var request: Any = ctor.newInstance(info, 1)
                finish.invoke(direct, request, false, "Controlled no-address timeout")
                check(
                    (!((health)!!.pending()) &&
                        ((direct.sources())!!.get("mDNS _hap._tcp."))!!.contains(
                            "live reply recovery 1"
                        )),
                    "Current direct records recover the original case-variant pending request without pretending native success",
                )
            } else {
                (health)!!.resolved((info.getServiceName() + "|_hap._tcp."))
            }
            (health)!!.resolving("Other|_hap._tcp.", "_hap._tcp.")
            (health)!!.resolveFailed("Other|_hap._tcp.", "Another instance failed")
            (health)!!.ok("mDNS _hap._tcp.")
            check(
                ((direct.sources())!!.get("mDNS _hap._tcp."))!!.startsWith("Failed"),
                "Recovering one instance cannot hide another failure",
            )
            direct.cancel()
            var changed: ByteArray = raw.clone()
            receive.invoke(direct, peer, MdnsPacket.parse(changed, changed.size))
            check(((direct.snapshot())!!.size == 1), "Stop prevents late additions")
        } finally {
            direct.cancel()
        }
        run {
            var scenario: Int = 0
            while ((scenario < 3)) {
                var rejected: Discovery = Discovery(target, live)
                accepting.set(rejected, true)
                (((types.get(rejected) as MutableSet<String>?)))!!.add("_hap._tcp.")
                try {
                    var packet: ByteArray = hkPacket(live.gateway, "_hap._tcp.", (scenario != 1))
                    var sender: InetAddress =
                        (if ((scenario == 0)) InetAddress.getByName(live.ip)
                        else (if ((scenario == 2)) InetAddress.getByName("203.0.113.20") else peer))
                    receive.invoke(rejected, sender, MdnsPacket.parse(packet, packet.size))
                    check(
                        ((rejected.snapshot())!!.stream())!!.noneMatch(
                            callback46@{ d -> (!(d.model.isEmpty()) || !(d.services.isEmpty())) }
                        ),
                        "Proxy, incomplete and off-scope records cannot assign accessory identity",
                    )
                    if ((scenario == 2)) {
                        check(
                            (rejected.snapshot())!!.isEmpty(),
                            "An off-scope sender cannot create an active endpoint",
                        )
                    }
                } finally {
                    rejected.cancel()
                }
                scenario++
            }
        }
        for (type in arrayOf("_hap._udp.", "_matter._tcp.", "_matterc._udp.", "_meshcop._udp.")) {
            for (callbackType in arrayOf(type, ("." + type.substring(0, (type.length - 1))))) {
                var udp: Discovery = Discovery(target, live)
                accepting.set(udp, true)
                enriching.set(udp, true)
                try {
                    ServerSocket(0, 4, InetAddress.getByName(live.ip)).use { server ->
                        server.setSoTimeout(350)
                        var candidate: NsdServiceInfo = NsdServiceInfo()
                        candidate.setServiceName("Provisional UDP fixture")
                        candidate.setServiceType(callbackType)
                        candidate.setHost(InetAddress.getByName(live.ip))
                        if ((Build.VERSION.SDK_INT >= 34)) {
                            candidate.setHostAddresses(
                                Collections.singletonList(InetAddress.getByName(live.ip))
                            )
                        }
                        candidate.setPort(server.getLocalPort())
                        resolve.invoke(udp, candidate)
                        try {
                            server.accept().use { unexpected ->
                                throw AssertionError(
                                    "UDP/Matter advertisement caused a TCP connection"
                                )
                            }
                        } catch (expected: SocketTimeoutException) {
                            check(true, ("No TCP connection for " + type))
                        }

                        check(
                            (udp.snapshot())!!.isEmpty(),
                            "A cached UDP/Matter candidate does not become active",
                        )
                    }
                } finally {
                    udp.cancel()
                }
            }
        }
        var categories: IntArray = intArrayOf(3, 5, 6, 7, 8, 9, 10, 17, 18, 20, 21)
        var drawables: kotlin.Array<String> =
            arrayOf(
                "fan",
                "lightbulb",
                "lock",
                "plug",
                "switch",
                "thermostat",
                "sensor",
                "cctv",
                "doorbell",
                "thermostat",
                "thermostat",
            )
        var devices: MutableList<Device> = ArrayList()
        run {
            var i: Int = 0
            while ((i < categories.size)) {
                var d: Device = Device()
                d.ip = ("192.0.2." + ((i + 1)))
                d.name = ("Fixture " + HomeKitInfo.category(categories[i]))
                d.kind = HomeKitInfo.category(categories[i])
                d.model = "Fixture model"
                d.seen = true
                d.detail("HomeKit setup (advertised)", "Not paired")
                var icon: DeviceIcon.Spec = DeviceIcon.forGroup(d)
                check(
                    (icon.drawable ==
                        (target.getResources())!!.getIdentifier(
                            ("ic_device_" + drawables[i]),
                            "drawable",
                            "io.github.kazands.networklens",
                        )),
                    ("Correct Lucide role for " + d.kind),
                )
                check(
                    icon.label == HardwareClassification.reported(d.kind)?.label,
                    "Accessibility label describes the interpreted physical category",
                )
                devices.add(d)
                i++
            }
        }
        store.retainIfCurrent(store.token(), live.key, devices, true)
        activity = launch(false)
        settle()
        capture("homekit-icons-light")
        description("Switch to dark mode")
        capture("homekit-icons-dark")
        click("Fixture HomeKit light")
        assertText("HomeKit light")
        check(
            !((texts())!!.contains("HomeKit setup (advertised)")),
            "Setup evidence stays collapsed on the simple details page",
        )
        click("Copy device details")
        onMain(
            callback47@{
                var c: ClipboardManager? =
                    (target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                var copied: String = ((((c)!!.getPrimaryClip())!!.getItemAt(0))!!.text)!!.toString()
                check(
                    (copied.contains("Device type: HomeKit light") &&
                        copied.contains("HomeKit setup (advertised): Not paired")),
                    "Copied details include the role and distinguish advertised setup state",
                )
            }
        )
        click("Show technical details")
        assertText("HomeKit setup (advertised)")
        assertText("Not paired")
        capture("homekit-details-dark")
        description("Back to devices")
        check(
            (texts())!!.contains("Fixture HomeKit light"),
            "Back returns to the populated device list",
        )
        var self: Device = Device()
        self.ip = live.ip
        self.self = true
        self.name = "Local role fixture"
        self.kind = "This phone"
        store.retainIfCurrent(store.token(), live.key, Collections.singletonList(self), true)
        onMain(callback48@{ activity!!.finish() })
        settle()
        activity = launch(false)
        settle()
        click("Local role fixture")
        click("Copy device details")
        onMain(
            callback49@{
                var c: ClipboardManager? =
                    (target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                var copied: String = ((((c)!!.getPrimaryClip())!!.getItemAt(0))!!.text)!!.toString()
                check(
                    (copied.contains("Device type: This phone") &&
                        !(copied.contains("Device type (advertised):"))),
                    "A locally derived self role is never described as an advertisement",
                )
            }
        )
        store.clear()
    }

    private fun macVendors(): Unit {
        var target: Context = getTargetContext()
        MacVendors.load(target)
        check(
            (MacVendors.ready() && ((MacVendors.snapshot()).toLong() > (0).toLong())),
            "Bundled IEEE index loads without a network service",
        )
        var vendor: String = MacVendors.lookup("30:05:5C:00:00:01")
        check(
            (vendor.lowercase(Locale.ROOT))!!.contains("brother"),
            "Synthetic printer prefix fixture matches the official registry",
        )
        check(
            (MacVendors.lookup("02:11:22:33:44:55"))!!.isEmpty(),
            "Locally assigned hardware addresses never imply a vendor",
        )
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        var d: Device = Device()
        d.ip = live.gateway
        d.name = "Vendor fixture"
        d.mac = "30:05:5C:00:00:01"
        d.macSource = "Controlled device report"
        d.manufacturer = "Reported maker"
        d.model = "Reported model"
        store.retainIfCurrent(store.token(), live.key, Collections.singletonList(d), true)
        activity = launch(false)
        settle()
        setSearch("Brother")
        assertText("1 matching device group")
        click("Vendor fixture")
        click("Show technical details")
        assertText("MAC vendor")
        assertText(vendor)
        assertText("Reported maker")
        assertText("Reported model")
        check(
            !((texts())!!.contains("Network identifier (DNS)")),
            "Lookup uses the endpoint hardware MAC rather than arbitrary metadata",
        )
        capture("mac-vendor-light")
        description("Switch to dark mode")
        assertText(vendor)
        capture("mac-vendor-dark")
        click("Copy device details")
        onMain(
            callback50@{
                var c: ClipboardManager? =
                    (target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                var value: String = ((((c)!!.getPrimaryClip())!!.getItemAt(0))!!.text)!!.toString()
                check(
                    (value.contains(("MAC vendor (IEEE registry): " + vendor)) &&
                        value.contains("Manufacturer: Reported maker")),
                    "Clipboard keeps MAC vendor separate from the reported manufacturer",
                )
            }
        )
        description("Back to devices")
        setSearch("")
        d.mac = ""
        d.manufacturer = ""
        d.model = ""
        d.detail("Network identifier (DNS)", "30:05:5C:00:00:01")
        store.retainIfCurrent(store.token(), live.key, Collections.singletonList(d), true)
        onMain(callback51@{ activity!!.finish() })
        waitForIdleSync()
        activity = launch(false)
        settle()
        setSearch("Brother")
        assertText("No matching devices")
        setSearch("")
        click("Vendor fixture")
        check(
            !((texts())!!.contains("MAC vendor")),
            "A MAC-shaped DNS identifier does not become a registry lookup",
        )
        check(
            d.manufacturer.isEmpty(),
            "Registry enrichment never mutates reported manufacturer identity",
        )
        store.clear()
    }

    private fun serviceRetries(): Unit {
        var target: Context = getTargetContext()
        var live: NetworkState = NetworkState.current(target)
        var scan: Discovery = Discovery(target, live)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var plan: Field = Discovery::class.java.getDeclaredField("mdnsRetries")
        var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
        accepting.setAccessible(true)
        plan.setAccessible(true)
        types.setAccessible(true)
        accepting.set(scan, true)
        var batching: Discovery = Discovery(target, live)
        accepting.set(batching, true)
        (((types.get(batching) as MutableSet<String>?)))!!.add("_http._tcp.")
        (((plan.get(batching) as MdnsRetries?)))!!.request("_devicelens-fixture._tcp.")
        var reverse: Device = Device()
        reverse.ip = live.gateway
        var queueName: Method =
            Discovery::class.java.getDeclaredMethod("queueMdnsName", Device::class.java)
        queueName.setAccessible(true)
        queueName.invoke(batching, reverse)
        var batch: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsBatch",
                    Long::class.javaPrimitiveType!!,
                    Map::class.java,
                    Map::class.java,
                )
        batch.setAccessible(true)
        var normalAttempts: Map<String, Int> = HashMap()
        var normalNext: Map<String, Long> = HashMap()
        try {
            var now: Long = System.nanoTime()
            var packet: Any = batch.invoke(batching, now, normalAttempts, normalNext)
            var questions: Field = (packet.javaClass)!!.getDeclaredField("questions")
            var isRetry: Field = (packet.javaClass)!!.getDeclaredField("retry")
            questions.setAccessible(true)
            isRetry.setAccessible(true)
            val firstQuestions = questions.get(packet) as List<String>
            check(isRetry.getBoolean(packet) && firstQuestions.size <= 8 &&
                firstQuestions.contains("_devicelens-fixture._tcp.local") &&
                firstQuestions.contains(ReverseDns.question(live.gateway)) &&
                normalAttempts["_http._tcp."] == 1 && "_devicelens-fixture._tcp." !in normalAttempts,
                "Recovery, reverse and ordinary type queries share a bounded batch without conflating retry attempts")
            var normal: Any =
                batch.invoke(
                    batching,
                    (now + TimeUnit.MILLISECONDS.toNanos(100)),
                    normalAttempts,
                    normalNext,
                )
            val nextQuestions = questions.get(normal) as List<String>
            check(!isRetry.getBoolean(normal) && nextQuestions.size <= 8 &&
                !nextQuestions.contains(ReverseDns.question(live.gateway)) && normalAttempts["_http._tcp."] == 1,
                "Untouched types advance while reverse/type retries honor their gap")
            val expanded = (0 until 100).map { "_coverage$it._tcp." }.toSet()
            (types.get(batching) as MutableSet<String>).addAll(expanded)
            repeat(200) { n ->
                val scheduled = batch.invoke(batching, now + TimeUnit.MILLISECONDS.toNanos((n+1)*250L), normalAttempts, normalNext)
                check((questions.get(scheduled) as List<String>).size <= 8, "Expanded catalogue keeps the Android query batch bounded")
            }
            check(expanded.all { normalAttempts[it] == 2 }, "Android scheduler reaches observed types beyond the old 48-type ceiling")
            check(HaMatchers.types.all { normalAttempts[it] == 2 }, "Android scheduler reaches every bundled matcher type")
        } finally {
            batching.cancel()
        }
        var retries: MdnsRetries? = (plan.get(scan) as MdnsRetries?)
        check(
            (retries)!!.request("_devicelens-fixture._tcp."),
            "Controlled local follow-up type admitted",
        )
        var start: Method =
            Discovery::class
                .java
                .getDeclaredMethod("startMdnsReceiver")
        start.setAccessible(true)
        var thread: Thread? = null
        try {
            thread = (start.invoke(scan) as Thread?)
            var end: Long = (SystemClock.uptimeMillis() + 5000)
            var status: String = ""
            while ((SystemClock.uptimeMillis() < end)) {
                status = (scan.sources())!!.getOrDefault("mDNS type and hostname queries", "")
                if (status.contains("service retry batches 2")) {
                    break
                }
                SystemClock.sleep(50)
            }
            check(
                status.contains("service retry batches 2"),
                ("Bound Android multicast socket sends both paced retry batches: " + status),
            )
            SystemClock.sleep(1500)
            check(
                ((scan.sources())!!.get("mDNS type and hostname queries"))!!.contains(
                    "service retry batches 2"
                ),
                "Follow-up type is not queried indefinitely",
            )
        } finally {
            scan.cancel()
            if ((thread !== null)) {
                thread!!.join(1500)
            }
        }
        check(
            ((thread !== null) && !(thread!!.isAlive())),
            "Stop closes the follow-up socket promptly",
        )
        if ((Build.VERSION.SDK_INT >= 34)) {
            var requestClass: Class<*> = Class.forName("io.github.kazands.networklens.Discovery\$ModernRequest")
            var ctor: Constructor<*> =
                requestClass.getDeclaredConstructor(
                    NsdServiceInfo::class.java,
                    Int::class.javaPrimitiveType!!,
                )
            ctor.setAccessible(true)
            var registered: Field = requestClass.getDeclaredField("registered")
            registered.setAccessible(true)
            var finish: Method =
                Discovery::class
                    .java
                    .getDeclaredMethod(
                        "finishModern",
                        requestClass,
                        Boolean::class.javaPrimitiveType!!,
                        String::class.java,
                    )
            finish.setAccessible(true)
            var gate: Discovery = Discovery(target, live)
            accepting.set(gate, true)
            (((types.get(gate) as MutableSet<String>?)))!!.add("_devicelens-fixture._tcp.")
            var info: NsdServiceInfo = NsdServiceInfo()
            info.setServiceName("Controlled retry fixture")
            info.setServiceType("_devicelens-fixture._tcp.")
            var request: Any = ctor.newInstance(info, 0)
            var gatePlan: MdnsRetries? = (plan.get(gate) as MdnsRetries?)
            try {
                onMain(
                    callback52@{
                        try {
                            finish.invoke(gate, request, false, "Controlled registration failure")
                        } catch (e: Exception) {
                            throw AssertionError(e)
                        }
                    }
                )
                check(
                    ((gatePlan)!!.due(System.nanoTime(), 8))!!.isEmpty(),
                    "A registration failure does not trigger multicast follow-ups",
                )
                var registeredRequest: Any = ctor.newInstance(info, 0)
                registered.setBoolean(registeredRequest, true)
                onMain(
                    callback53@{
                        try {
                            finish.invoke(
                                gate,
                                registeredRequest,
                                false,
                                "Controlled no-address timeout",
                            )
                        } catch (e: Exception) {
                            throw AssertionError(e)
                        }
                    }
                )
                check(
                    ((gatePlan)!!.due(System.nanoTime(), 8))!!.equals(
                        Collections.singletonList("_devicelens-fixture._tcp.")
                    ),
                    "An admitted registered request schedules the local type after missing data",
                )
            } finally {
                gate.cancel()
            }
            var cancelled: Discovery = Discovery(target, live)
            accepting.set(cancelled, true)
            (((types.get(cancelled) as MutableSet<String>?)))!!.add("_devicelens-fixture._tcp.")
            var afterStop: Any = ctor.newInstance(info, 0)
            registered.setBoolean(afterStop, true)
            cancelled.cancel()
            finish.invoke(cancelled, afterStop, false, "Late timeout")
            check(
                ((((plan.get(cancelled) as MdnsRetries?)))!!.due(System.nanoTime(), 8))!!
                    .isEmpty(),
                "A timeout after Stop cannot schedule discovery traffic",
            )
        }
    }





    private fun resolverUpgrade(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        var fixture: Discovery = Discovery(target, live)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var enriching: Field = Discovery::class.java.getDeclaredField("enriching")
        accepting.setAccessible(true)
        enriching.setAccessible(true)
        accepting.set(fixture, true)
        enriching.set(fixture, true)
        var provisional: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "observe",
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!,
                )
        provisional.setAccessible(true)
        try {
            var old: Device = Device()
            old.ip = live.ip
            old.name = "Old router name"
            old.hostname = "Old router hostname"
            old.model = "Previous occupant"
            old.hardware("00:11:22:33:44:55", "Old scan fixture")
            store.retainIfCurrent(store.token(), live.key, Collections.singletonList(old), true)
            var device: Device? =
                (provisional.invoke(fixture, live.ip, "Provisional address", false) as Device?)
            check(
                (fixture.snapshot())!!.isEmpty(),
                "An old saved device never supplies current presence",
            )
            check(
                (((device!!.name.isEmpty() && device!!.hostname.isEmpty()) &&
                    device!!.model.isEmpty()) && device!!.mac.isEmpty()),
                "A fresh observation never inherits the old occupant's router name or hardware details",
            )
            var query: Method =
                Discovery::class.java.getDeclaredMethod("queryDeviceName", Device::class.java)
            query.setAccessible(true)
            query.invoke(fixture, device)
            check(
                ((device!!.name.isEmpty() && device!!.hostname.isEmpty()) &&
                    device!!.details.isEmpty()),
                "Scheduling direct endpoint name queries does not import remembered router records",
            )
            provisional.invoke(fixture, live.ip, "Explicit current response fixture", true)
            check(
                (((fixture.snapshot())!!.size == 1) &&
                    ((fixture.snapshot())!!.get(0))!!.name.isEmpty()),
                "A responsive address stays unnamed until a current source reports a name",
            )
            fixture.cancel()
            check(
                ((fixture.snapshot())!!.get(0))!!.hostname.isEmpty(),
                "Stop does not restore old router data",
            )
        } finally {
            fixture.cancel()
        }
        if ((Build.VERSION.SDK_INT >= 34)) {
            var updates: Discovery = Discovery(target, live)
            accepting.set(updates, true)
            enriching.set(updates, true)
            var requestType: Class<*> = Class.forName("io.github.kazands.networklens.Discovery\$ModernRequest")
            var constructor: Constructor<*> =
                requestType.getDeclaredConstructor(
                    NsdServiceInfo::class.java,
                    Int::class.javaPrimitiveType!!,
                )
            constructor.setAccessible(true)
            var update: Method =
                Discovery::class
                    .java
                    .getDeclaredMethod("modernUpdated", requestType, NsdServiceInfo::class.java)
            update.setAccessible(true)
            var done: Field = requestType.getDeclaredField("done")
            var usable: Field = requestType.getDeclaredField("usable")
            var active: Field = Discovery::class.java.getDeclaredField("modernActive")
            var healthField: Field = Discovery::class.java.getDeclaredField("health")
            done.setAccessible(true)
            usable.setAccessible(true)
            active.setAccessible(true)
            healthField.setAccessible(true)
            try {
                var first: NsdServiceInfo = NsdServiceInfo()
                first.setServiceName("0123456789ABCDEF-FEDCBA9876543210")
                first.setServiceType("_http._tcp.")
                first.setPort(0)
                first.setNetwork(live.network)
                var request: Any = constructor.newInstance(first, 0)
                (((active.get(updates) as MutableSet<Any>?)))!!.add(request)
                (((healthField.get(updates) as DiscoveryHealth?)))!!.resolving(
                    (first.getServiceName() + "|_http._tcp."),
                    "_http._tcp.",
                )
                var seeded: Device? =
                    (provisional.invoke(
                        updates,
                        live.ip,
                        "Explicit update-policy fixture responder",
                        true,
                    ) as Device?)
                update.invoke(updates, request, first)
                check(
                    !(usable.getBoolean(request)),
                    "An addressless initial update does not finish resolution",
                )
                first.setHostAddresses(
                    Collections.singletonList(InetAddress.getByName("203.0.113.21"))
                )
                update.invoke(updates, request, first)
                check(
                    !(usable.getBoolean(request)),
                    "An off-network address does not start the successful settling timer",
                )
                first.setHostAddresses(Collections.singletonList(InetAddress.getByName(live.ip)))
                update.invoke(updates, request, first)
                check(
                    (!(done.getBoolean(request)) && (seeded)!!.model.isEmpty()),
                    "First usable update publishes an endpoint while keeping the continuous callback open",
                )
                SystemClock.sleep(300)
                var later: NsdServiceInfo = NsdServiceInfo()
                later.setServiceName(first.getServiceName())
                later.setServiceType(first.getServiceType())
                later.setNetwork(live.network)
                later.setPort(0)
                var addresses: MutableList<InetAddress> = ArrayList()
                addresses.add(InetAddress.getByName(live.ip))
                for (value in live.addresses) {
                    if (value.contains(":")) {
                        addresses.add(InetAddress.getByName(value))
                        break
                    }
                }
                later.setHostAddresses(addresses)
                later.setAttribute("fn", "Late friendly fixture")
                later.setAttribute("model", "Late model 216")
                update.invoke(updates, request, later)
                check(
                    ((seeded)!!.name.isEmpty() && (seeded)!!.model.isEmpty()),
                    "A later platform callback still cannot promote cached identity",
                )
                freshNsd(updates, later)
                check(
                    ((seeded)!!.name.equals("Late friendly fixture") &&
                        (seeded)!!.model.equals("Late model 216")),
                    "A current peer packet supplies missing friendly name and model",
                )
                var found: Field? = Discovery::class.java.getDeclaredField("found")
                found!!.setAccessible(true)
                check(
                    ((((found!!.get(updates) as MutableMap<*, *>?)))!!.size == addresses.size),
                    "The later host-address list is retained as provisional endpoints",
                )
                var end: Long = (SystemClock.uptimeMillis() + 2000)
                var finished: Boolean = false
                while ((SystemClock.uptimeMillis() < end)) {
                    synchronized((updates)) {
                        finished = done.getBoolean(request)
                    }
                    if (finished) {
                        break
                    }
                    SystemClock.sleep(25)
                }
                check(
                    (finished && (((active.get(updates) as Set<*>?)))!!.isEmpty()),
                    "One bounded settling timer releases request capacity",
                )
                later.setAttribute("fn", "A stale callback")
                update.invoke(updates, request, later)
                check(
                    (seeded)!!.name.equals("Late friendly fixture"),
                    "A callback after settling cannot overwrite the completed request",
                )
                var cancelled: Any = constructor.newInstance(first, 0)
                updates.cancel()
                update.invoke(updates, cancelled, later)
                check(!(usable.getBoolean(cancelled)), "Stop rejects late service updates")
            } finally {
                updates.cancel()
            }
            var unresolved: Discovery = Discovery(target, live)
            accepting.set(unresolved, true)
            enriching.set(unresolved, true)
            var manager: Field = Discovery::class.java.getDeclaredField("nsd")
            var queue: Field = Discovery::class.java.getDeclaredField("modernQueue")
            manager.setAccessible(true)
            queue.setAccessible(true)
            manager.set(unresolved, target.getSystemService(Context.NSD_SERVICE))
            var missing: NsdServiceInfo = NsdServiceInfo()
            missing.setServiceName(("DeviceLens-absent-" + UUID.randomUUID()))
            missing.setServiceType("_devicelens-fixture._tcp.")
            missing.setNetwork(live.network)
            var request: Any = constructor.newInstance(missing, 0)
            (((queue.get(unresolved) as Queue<Any>?)))!!.add(request)
            (((healthField.get(unresolved) as DiscoveryHealth?)))!!.resolving(
                (missing.getServiceName() + "|_devicelens-fixture._tcp."),
                "_devicelens-fixture._tcp.",
            )
            var begin: Method = Discovery::class.java.getDeclaredMethod("resolveModern")
            begin.setAccessible(true)
            try {
                var began: Long = SystemClock.uptimeMillis()
                begin.invoke(unresolved)
                var status: String = ""
                while ((((SystemClock.uptimeMillis() - began)).toLong() < (12000).toLong())) {
                    status =
                        (unresolved.sources())!!.getOrDefault("mDNS _devicelens-fixture._tcp.", "")
                    if (status.startsWith("Failed")) {
                        break
                    }
                    SystemClock.sleep(50)
                }
                check(
                    ((status.startsWith("Failed") && status.contains("First attempt:")) &&
                        !(status.contains("Service resolution failed"))),
                    ("A real unresolved Android request retains both attempt outcomes: " + status),
                )
                check(
                    status.contains("failed 1"),
                    "The first observable terminal failure includes its final counter as well as both attempt reasons",
                )
                check(status.contains("native failures: wire not observed 1"),
                    "An unresolved native candidate is labelled by captured evidence without claiming stale cache")
                val wire = Discovery::class.java.getDeclaredField("mdnsAssembly").apply { isAccessible = true }
                    .get(unresolved) as MdnsAssembly
                val labels = listOf(missing.serviceName) + listOf("_devicelens-fixture", "_tcp", "local")
                val host = listOf("correlation-fixture", "local")
                val synthetic = MdnsPacket.Result().apply {
                    parsedResponse = true
                    records.add(MdnsPacket.Record(labels.drop(1), 12, 120, false, target = labels))
                    records.add(MdnsPacket.Record(labels, 33, 120, false, target = host, port = 8080))
                    records.add(MdnsPacket.Record(labels, 16, 120, false))
                    records.add(MdnsPacket.Record(host, 1, 120, false, address = live.ip))
                }
                wire.accept(InetAddress.getByName(live.ip), synthetic, System.nanoTime())
                val cleanup = Discovery::class.java.getDeclaredMethod("cleanupNsd").apply { isAccessible = true }
                cleanup.invoke(unresolved)
                val correlated = unresolved.sources().getValue("mDNS _devicelens-fixture._tcp.")
                check(correlated.startsWith("Failed") && correlated.contains("wire observed after native failure 1") &&
                    correlated.contains("wire complete after native failure 1"),
                    "Cleanup correlates synthetic late replies while retaining the native failure")
                check(unresolved.snapshot().isEmpty(), "Diagnostic graph correlation alone cannot add presence or identity")
                cleanup.invoke(unresolved)
                check(unresolved.sources().getValue("mDNS _devicelens-fixture._tcp.") == correlated,
                    "Repeated cleanup cannot publish duplicate correlation counters")
                check(
                    (((SystemClock.uptimeMillis() - began)).toLong() < (11000).toLong()),
                    "The unchanged hard deadline and single retry remain bounded",
                )
            } finally {
                unresolved.cancel()
            }
        }
        store.clear()
    }

    /** An actual socket fixture, not an assumption that the virtual router runs a service. */
    private fun legacyTcpFallback(target: Context, live: NetworkState): Unit {
        var scan: Discovery = Discovery(target, live)
        var serverThread: ExecutorService = Executors.newSingleThreadExecutor()
        var accepted: java.util.concurrent.atomic.AtomicInteger =
            java.util.concurrent.atomic.AtomicInteger()
        try {
            ServerSocket(8008, 4, InetAddress.getByName(live.ip)).use { server ->
                server.setSoTimeout(8000)
                var serving: Future<*>? =
                    serverThread.submit(
                        callback63@{
                            try {
                                run {
                                    var i: Int = 0
                                    while ((i < 2)) {
                                        server.accept().use { socket ->
                                            accepted.incrementAndGet()
                                        }
                                        i++
                                    }
                                }
                            } catch (e: IOException) {
                                throw RuntimeException(e)
                            }
                        }
                    )
                for (name in arrayOf("accepting", "enriching")) {
                    var f: Field = Discovery::class.java.getDeclaredField(name)
                    f.setAccessible(true)
                    f.set(scan, true)
                }
                var hf: Field = Discovery::class.java.getDeclaredField("health")
                hf.setAccessible(true)
                var health: DiscoveryHealth? = (hf.get(scan) as DiscoveryHealth?)
                (health)!!.begin("ICMP echo")
                (health)!!.begin("IPv4 checks")
                var probe: Method =
                    Discovery::class
                        .java
                        .getDeclaredMethod(
                            "probe",
                            String::class.java,
                            Int::class.javaPrimitiveType!!,
                            Int::class.javaPrimitiveType!!,
                        )
                probe.setAccessible(true)
                var began: Long = SystemClock.elapsedRealtime()
                probe.invoke(scan, live.ip, 250, 220)
                serving!!.get(2, TimeUnit.SECONDS)
                check(
                    (accepted.get() == 2),
                    "Real TCP fixture accepted both fallback connections after actual OS echo denial",
                )
                check(
                    (((SystemClock.elapsedRealtime() - began)).toLong() >= (200).toLong()),
                    "TCP fallback retains its paced confirmation window",
                )
                var d: Device? =
                    nullablePlatformResult(
                        ((((scan.snapshot())!!.stream())!!.filter(
                                    callback64@{ x -> x.ip.equals(live.ip) }
                                ))!!
                                .findFirst())!!
                            .orElse(null)
                    )
                check(
                    ((((d !== null) && d.seen) && !(d.self)) &&
                        d.evidence.contains("TCP connection on port 8008 (confirmed twice)")),
                    "Fallback admission comes from real connections, not self seeding",
                )
                check(
                    (d)!!.ports.contains(8008),
                    "The responding fallback port reaches the endpoint details",
                )
                (health)!!.ok("IPv4 checks")
                (health)!!.ok("ICMP echo")
                check(
                    (((health)!!.sources())!!.get("ICMP echo"))!!.startsWith("Failed"),
                    "Actual OS echo denial remains visible after successful TCP fallback",
                )
                check(
                    !(((health)!!.summary())!!.isEmpty()),
                    "Fallback success cannot erase Partial source health",
                )
            }
        } finally {
            scan.cancel()
            serverThread.shutdownNow()
            check(
                serverThread.awaitTermination(2, TimeUnit.SECONDS),
                "Controlled fallback server releases its thread",
            )
        }
    }

    private fun icmpFlow(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        var self: InetAddress = InetAddress.getByName(live.ip)
        var gateway: InetAddress = InetAddress.getByName(live.gateway)
        check(
            (!(live.ip.isEmpty()) && (self is Inet4Address)),
            "ICMP fixture uses actual Wi-Fi IPv4",
        )
        for (peer in arrayOf(self, gateway)) {
            var reply: IcmpEcho.Result? =
                IcmpEcho.probe(
                    callback65@{ AndroidIcmp.open(live, peer) },
                    callback66@{ false },
                    300,
                    2,
                    120,
                )
            if (
                (((Build.VERSION.SDK_INT <= 28) && (reply!!.status === IcmpEcho.Status.ERROR)) &&
                    (((reply!!.problem === IcmpEcho.Problem.PERMISSION) ||
                        (reply!!.problem === IcmpEcho.Problem.UNSUPPORTED))))
            ) {
                var platform: Bundle = Bundle()
                platform.putString(
                    "stream",
                    (("PLATFORM ICMP unavailable: " + reply!!.problem) +
                        "; checking real TCP fallback and Partial status\n"),
                )
                sendStatus(0, platform)
                legacyTcpFallback(target, live)
                // The virtual router need not have a listening TCP service. Its absence
                // is valid; still exercise the full scan and persisted-in-RAM status path.
                var scan: Discovery = Discovery(target, live)
                var done: CountDownLatch = CountDownLatch(1)
                try {
                    scan.scan(
                        (live.gateway + "/32"),
                        object : Discovery.Listener {
                            override fun progress(n: Int, total: Int, ds: List<Device>): Unit {}

                            override fun finished(
                                ds: List<Device>,
                                complete: Boolean,
                                message: String,
                            ): Unit {
                                done.countDown()
                            }
                        },
                    )
                    check(
                        done.await((FULL_SCAN_SECONDS).toLong(), TimeUnit.SECONDS),
                        "Legacy platform ICMP denial scan completes",
                    )
                    var saved: ScanReport? = store.report(live.key)
                    check(
                        (((saved !== null) && !(saved.complete)) &&
                            (saved.sources.get("ICMP echo"))!!.startsWith("Failed")),
                        "Legacy OS denial remains visible as Partial",
                    )
                    IcmpUiChecks.run(this,::check)
                } finally {
                    scan.cancel()
                    store.clear()
                }
                return
            }
            check(
                ((reply!!.status === IcmpEcho.Status.REPLIED) && (reply!!.problem === null)),
                ((((("Native bound ICMP reply from " + peer) + ": ") + reply!!.status) + " ") +
                    reply!!.reason()),
            )
            check(
                (((reply!!.requests >= 1) && (reply!!.requests <= 2)) &&
                    ((reply!!.latencyMillis).toLong() >= (0).toLong())),
                "Real echo request and latency recorded",
            )
        }
        var descriptors: Int = (File("/proc/self/fd").list())!!.size
        run {
            var n: Int = 0
            while ((n < 64)) {
                (AndroidIcmp.open(live, gateway))!!.close()
                n++
            }
        }
        check(
            ((File("/proc/self/fd").list())!!.size <= (descriptors + 4)),
            "Repeated native echo socket close leaks no descriptors",
        )
        for (outside in arrayOf("127.0.0.1", "224.0.0.251", "203.0.113.29")) {
            var invalid: IcmpEcho.Result =
                IcmpEcho.probe(
                    callback67@{ AndroidIcmp.open(live, InetAddress.getByName(outside)) },
                    callback68@{ false },
                    200,
                    2,
                    120,
                )
            check(
                (((invalid.status === IcmpEcho.Status.ERROR) &&
                    (invalid.problem === IcmpEcho.Problem.SETUP)) && (invalid.requests == 0)),
                "Off-scope ICMP target rejected before send",
            )
        }
        var unavailable: NetworkState = NetworkState.current(target)
        unavailable.network =
            android.net.Network.fromNetworkHandle(
                (((((65534L).toLong() shl (32).toInt()))).toLong() or (3405697037L).toLong())
            )
        descriptors = (File("/proc/self/fd").list())!!.size
        run {
            var n: Int = 0
            while ((n < 24)) {
                var failed: IcmpEcho.Result =
                    IcmpEcho.probe(
                        callback69@{ AndroidIcmp.open(unavailable, gateway) },
                        callback70@{ false },
                        200,
                        2,
                        120,
                    )
                check(
                    ((failed.status === IcmpEcho.Status.ERROR) && (failed.requests == 0)),
                    "Unavailable exact Network fails without route fallback",
                )
                n++
            }
        }
        check(
            ((File("/proc/self/fd").list())!!.size <= (descriptors + 4)),
            "Failed native bind closes every opened descriptor",
        )
        var cancel: java.util.concurrent.atomic.AtomicBoolean =
            java.util.concurrent.atomic.AtomicBoolean()
        var candidate: String = (live.ip.substring(0, (live.ip.lastIndexOf('.') + 1)) + "249")
        var quiet: InetAddress = InetAddress.getByName(candidate)
        if (((live.local(quiet) && !(quiet.equals(self))) && !(quiet.equals(gateway)))) {
            var executor: ExecutorService = Executors.newSingleThreadExecutor()
            var start: Long = SystemClock.uptimeMillis()
            try {
                var task: Future<IcmpEcho.Result> =
                    executor.submit(
                        Callable<IcmpEcho.Result> {
                            IcmpEcho.probe(
                                callback72@{ AndroidIcmp.open(live, quiet) },
                                cancel::get,
                                1000,
                                2,
                                120,
                            )
                        }
                    )
                SystemClock.sleep(80)
                cancel.set(true)
                var stopped: IcmpEcho.Result = task.get(1, TimeUnit.SECONDS)
                check(
                    ((stopped.status === IcmpEcho.Status.CANCELLED) ||
                        (stopped.status === IcmpEcho.Status.REPLIED)),
                    "Native echo cancellation completes promptly",
                )
                check(
                    (((SystemClock.uptimeMillis() - start)).toLong() < (700).toLong()),
                    "Native polling cancellation remains bounded",
                )
            } finally {
                cancel.set(true)
                executor.shutdownNow()
                executor.awaitTermination(2, TimeUnit.SECONDS)
            }
        }
        var constructor: Constructor<Discovery> =
            Discovery::class
                .java
                .getDeclaredConstructor(
                    Context::class.java,
                    NetworkState::class.java,
                    Discovery.EchoFactory::class.java,
                )
        constructor.setAccessible(true)
        for (executionError in booleanArrayOf(false, true)) {
            var opened: java.util.concurrent.atomic.AtomicInteger =
                java.util.concurrent.atomic.AtomicInteger()
            var closed: java.util.concurrent.atomic.AtomicInteger? =
                java.util.concurrent.atomic.AtomicInteger()
            var factory: Discovery.EchoFactory =
                Discovery.EchoFactory(
                    callback73@{ network, peer ->
                        opened.incrementAndGet()
                        if (executionError) {
                            throw IcmpEcho.Failure(
                                IcmpEcho.Problem.PERMISSION,
                                "Injected ICMP denial",
                            )
                        }
                        return@callback73 object : IcmpEcho.Channel {
                            override fun identifier(): Int {
                                return 12345
                            }

                            override fun send(packet: ByteArray): Unit {}

                            override fun receive(wait: Int): ByteArray? {
                                SystemClock.sleep((wait).toLong())
                                return null
                            }

                            override fun close(): Unit {
                                closed!!.incrementAndGet()
                            }
                        }
                    }
                )
            var scan: Discovery = constructor.newInstance(target, live, factory)
            var done: CountDownLatch = CountDownLatch(1)
            var found: kotlin.Array<List<Device>?> = arrayOf(null)
            scan.scan(
                (live.gateway + "/32"),
                object : Discovery.Listener {
                    override fun progress(n: Int, total: Int, ds: List<Device>): Unit {}

                    override fun finished(
                        ds: List<Device>,
                        complete: Boolean,
                        message: String,
                    ): Unit {
                        found!![0] = ds
                        done.countDown()
                    }
                },
            )
            check(
                done.await((FULL_SCAN_SECONDS).toLong(), TimeUnit.SECONDS),
                "TCP fallback scan completes",
            )
            check((opened.get() > 0), "ICMP path was actually attempted before TCP fallback")
            var endpoint: Device? = null
            for (d in found!![0]!!) {
                if (d.ip.equals(live.gateway)) {
                    endpoint = d
                }
            }
            check(
                ((endpoint !== null) &&
                    (endpoint!!.evidence.stream())!!.anyMatch(
                        callback74@{ e -> e.startsWith("TCP ") }
                    )),
                ("TCP still discovers a peer after ICMP " +
                    ((if (executionError) "execution failure" else "silence"))),
            )
            var saved: ScanReport? = store.report(live.key)
            var state: String? = (saved)!!.sources.get("ICMP echo")
            check(
                ((state !== null) && state.startsWith((if (executionError) "Failed" else "OK"))),
                ("Saved ICMP health distinguishes execution error from no reply: " + state),
            )
            if (executionError) {
                check(
                    (!((saved)!!.complete) && (state)!!.contains("blocked ICMP")),
                    "TCP success cannot hide a failed ICMP source",
                )
                IcmpUiChecks.run(this,::check)
            } else {
                check(
                    (opened.get() == closed!!.get()),
                    "Silent ICMP transport always closes before scan completion",
                )
            }
        }
        store.clear()
    }

    private fun freshNsd(scan: Discovery, info: NsdServiceInfo): Unit {
        var type: String = LocalServices.canonicalType(info.getServiceType())
        var receive: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsResolved",
                    InetAddress::class.java,
                    MdnsPacket.Result::class.java,
                )
        receive.setAccessible(true)
        var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
        types.setAccessible(true)
        (((types.get(scan) as MutableSet<String>?)))!!.add(type)
        var addresses: List<InetAddress?> =
            (if ((Build.VERSION.SDK_INT >= 34)) info.getHostAddresses()
            else Collections.singletonList(info.host))
        for (address in addresses) {
            if ((address !== null)) {
                var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
                var out: DataOutputStream = DataOutputStream(bytes)
                out.writeShort(0)
                out.writeShort(33792)
                out.writeShort(0)
                out.writeShort(4)
                out.writeInt(0)
                var serviceType: List<String> =
                    Arrays.asList(
                        *java.util.regex.Pattern.compile("\\.").split(((type + "local")), 0)
                    )
                var instance: MutableList<String> = ArrayList()
                instance.add(info.getServiceName())
                instance.addAll(serviceType)
                var host: List<String> = Arrays.asList("fresh-fixture", "local")
                var b: ByteArrayOutputStream = ByteArrayOutputStream()
                var data: DataOutputStream = DataOutputStream(b)
                wireName(data, instance)
                wireRecord(out, serviceType, 12, 120, b.toByteArray())
                b.reset()
                data = DataOutputStream(b)
                data.writeShort(0)
                data.writeShort(0)
                data.writeShort((if ((info.port > 0)) info.port else 51827))
                wireName(data, host)
                wireRecord(out, instance, 33, 120, b.toByteArray())
                b.reset()
                for (entry in (info.attributes)!!.entries) {
                    var key: ByteArray =
                        (((entry.key + "=")))!!.toByteArray(
                            java.nio.charset.Charset.forName("UTF-8")
                        )
                    var value: ByteArray = entry.value
                    b.write((key.size + value.size))
                    b.write(key)
                    b.write(value)
                }
                wireRecord(out, instance, 16, 120, b.toByteArray())
                wireRecord(
                    out,
                    host,
                    (if ((address is Inet6Address)) 28 else 1),
                    120,
                    address.address,
                )
                var packet: ByteArray = bytes.toByteArray()
                receive.invoke(scan, address, MdnsPacket.parse(packet, packet.size))
            }
        }
    }

    private fun freshPresence(): Unit {
        var target: Context = getTargetContext()
        var live: NetworkState = NetworkState.current(target)
        var address: InetAddress = InetAddress.getByName(live.ip)
        var resolved: Method =
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
        resolved.setAccessible(true)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var enriching: Field = Discovery::class.java.getDeclaredField("enriching")
        accepting.setAccessible(true)
        enriching.setAccessible(true)
        var servers: ExecutorService = Executors.newSingleThreadExecutor()
        for (callbackType in arrayOf("_http._tcp.", "._http._tcp")) {
            run {
                var mode: Int = 0
                while ((mode < 3)) {
                    var candidate: Discovery = Discovery(target, live)
                    accepting.set(candidate, true)
                    enriching.set(candidate, true)
                    var server: ServerSocket = ServerSocket(0, 4, address)
                    server.setSoTimeout(4000)
                    var port: Int = server.getLocalPort()
                    var scenario: Int = mode
                    var accepted: java.util.concurrent.atomic.AtomicInteger =
                        java.util.concurrent.atomic.AtomicInteger()
                    var serving: Future<*>? = null
                    try {
                        if ((mode == 0)) {
                            server.close()
                        } else {
                            serving =
                                servers.submit(
                                    callback78@{
                                        try {
                                            server.accept().use { first ->
                                                accepted.incrementAndGet()
                                                if ((scenario == 1)) {
                                                    server.close()
                                                }
                                            }
                                            if ((scenario == 2)) {
                                                server.accept().use { second ->
                                                    accepted.incrementAndGet()
                                                }
                                            }
                                        } catch (e: IOException) {
                                            throw RuntimeException(e)
                                        }
                                    }
                                )
                        }
                        var cached: NsdServiceInfo = NsdServiceInfo()
                        cached.setServiceName("Remembered absent phone")
                        cached.setServiceType(callbackType)
                        cached.setPort(port)
                        cached.setHost(address)
                        if ((Build.VERSION.SDK_INT >= 34)) {
                            cached.setHostAddresses(Collections.singletonList(address))
                        }
                        var began: Long = SystemClock.uptimeMillis()
                        resolved.invoke(candidate, cached)
                        var end: Long = (began + 4000)
                        var work: DiscoveryWork? = null
                        var wf: Field = Discovery::class.java.getDeclaredField("enrichment")
                        wf.setAccessible(true)
                        work = (wf.get(candidate) as DiscoveryWork?)
                        work!!.shutdown()
                        check(
                            work!!.awaitTermination(4, TimeUnit.SECONDS),
                            "Presence confirmation finishes within its bounded window",
                        )
                        if ((serving !== null)) {
                            serving!!.get(1, TimeUnit.SECONDS)
                        }
                        if ((mode < 2)) {
                            check(
                                (candidate.snapshot())!!.isEmpty(),
                                (if ((mode == 0))
                                    "A refused port cannot revive a cached offline service"
                                else
                                    "One accepted connection followed by refusal cannot revive a cached offline service"),
                            )
                            if ((mode == 1)) {
                                check(
                                    (accepted.get() == 1),
                                    "The one-off responder really accepted the first connection",
                                )
                            }
                        } else {
                            check(
                                (accepted.get() == 2),
                                "TCP-only confirmation creates two real, separately accepted connections",
                            )
                            check(
                                (((SystemClock.uptimeMillis() - began)).toLong() >= (200).toLong()),
                                "Connections are spaced by the presence-check interval",
                            )
                            check(
                                ((candidate.snapshot())!!.size == 1),
                                "A stable service is found without requiring ICMP replies",
                            )
                            var d: Device = (candidate.snapshot())!!.get(0)
                            check(
                                (d.name.isEmpty() && d.model.isEmpty()),
                                "Successful TCP cannot promote cached NSD identity",
                            )
                            cached.setAttribute("fn", "Current device name")
                            cached.setAttribute("model", "Current model")
                            cached.setServiceType("_http._tcp.")
                            freshNsd(candidate, cached)
                            d = (candidate.snapshot())!!.get(0)
                            check(
                                (d.name.equals("Current device name") &&
                                    d.model.equals("Current model")),
                                "A matching current packet from the same address supplies current identity",
                            )
                            check(
                                (d.evidence.contains(
                                    "Connection to advertised service (confirmed twice)"
                                ) && (d.latency >= 0)),
                                "Presence evidence identifies reconfirmed TCP and retains measured timing",
                            )
                        }
                        candidate.cancel()
                        resolved.invoke(candidate, cached)
                        check(
                            ((candidate.snapshot())!!.size == ((if ((mode == 2)) 1 else 0))),
                            "Stopped callbacks cannot change the confirmed device list",
                        )
                    } finally {
                        candidate.cancel()
                        server.close()
                    }
                    mode++
                }
            }
        }
        servers.shutdownNow()
        check(
            servers.awaitTermination(2, TimeUnit.SECONDS),
            "Presence test servers release their threads",
        )
    }

    private fun engineUpgrade(): Unit {
        var target: Context = getTargetContext()
        var live: NetworkState = NetworkState.current(target)
        var store: Store = Store(target)
        store.clear()
        var resolved: Method =
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
        var mdns: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsResolved",
                    InetAddress::class.java,
                    MdnsPacket.Result::class.java,
                )
        var observe: Method =
            Discovery::class
                .java
                .getDeclaredMethod("observe", String::class.java, String::class.java)
        var cast: Method = Discovery::class.java.getDeclaredMethod("maybeCast", Device::class.java)
        for (method in arrayOf(resolved, mdns, observe, cast)) {
            method.setAccessible(true)
        }
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var enriching: Field = Discovery::class.java.getDeclaredField("enriching")
        var workField: Field = Discovery::class.java.getDeclaredField("enrichment")
        for (f in arrayOf(accepting, enriching, workField)) {
            f.setAccessible(true)
        }
        var candidate: Discovery = Discovery(target, live)
        accepting.set(candidate, true)
        enriching.set(candidate, true)
        try {
            var cached: NsdServiceInfo = NsdServiceInfo()
            cached.setServiceName("Cached-only fixture")
            cached.setServiceType("_http._tcp.")
            cached.setHost(InetAddress.getByName(live.ip))
            if ((Build.VERSION.SDK_INT >= 34)) {
                cached.setHostAddresses(Collections.singletonList(InetAddress.getByName(live.ip)))
            }
            resolved.invoke(candidate, cached)
            check(
                (candidate.snapshot())!!.isEmpty(),
                "A cached NSD address without a response is provisional and not shown as active",
            )
            var fresh: MdnsPacket.Result = MdnsPacket.Result()
            fresh.freshResponse = true
            mdns.invoke(candidate, InetAddress.getByName("203.0.113.20"), fresh)
            check(
                (candidate.snapshot())!!.isEmpty(),
                "Off-network mDNS cannot confirm a provisional address",
            )
            mdns.invoke(candidate, InetAddress.getByName(live.ip), fresh)
            check(
                (((candidate.snapshot())!!.size == 1) &&
                    ((candidate.snapshot())!!.get(0))!!.name.isEmpty()),
                "A fresh responder is shown without reusing cached identity",
            )
            freshNsd(candidate, cached)
            check(
                ((candidate.snapshot())!!.get(0))!!.name.equals("Cached-only fixture"),
                "The matching current wire advertisement supplies its reported name",
            )
            candidate.cancel()
            mdns.invoke(candidate, InetAddress.getByName(live.ip), fresh)
            check(
                ((candidate.snapshot())!!.size == 1),
                "Cancellation rejects subsequent fresh service mutations",
            )
        } finally {
            candidate.cancel()
        }
        var directed: Discovery = Discovery(target, live)
        accepting.set(directed, true)
        enriching.set(directed, true)
        try {
            ServerSocket(0, 4, InetAddress.getByName(live.ip)).use { server ->
                server.setSoTimeout(3000)
                var service: NsdServiceInfo = NsdServiceInfo()
                service.setServiceName("Confirmed service fixture")
                service.setServiceType("_http._tcp.")
                service.setPort(server.getLocalPort())
                service.setHost(InetAddress.getByName(live.ip))
                if ((Build.VERSION.SDK_INT >= 34)) {
                    service.setHostAddresses(
                        Collections.singletonList(InetAddress.getByName(live.ip))
                    )
                }
                resolved.invoke(directed, service)
                server.accept().use { accepted ->
                    check(
                        accepted.isConnected(),
                        "Confirmation reaches the actual advertised port on the bound Wi-Fi",
                    )
                }
                server.accept().use { accepted ->
                    check(
                        accepted.isConnected(),
                        "A second socket reconfirms the advertised service",
                    )
                }
                var end: Long = (SystemClock.uptimeMillis() + 2000)
                while (((directed.snapshot())!!.isEmpty() && (SystemClock.uptimeMillis() < end))) {
                    SystemClock.sleep(25)
                }
                check(
                    (((directed.snapshot())!!.size == 1) &&
                        ((directed.snapshot())!!.get(0))!!
                            .evidence
                            .contains("Connection to advertised service (confirmed twice)")),
                    "Successful directed service response confirms an NSD candidate",
                )
            }
        } finally {
            directed.cancel()
        }

        var constructor: Constructor<Discovery> =
            Discovery::class
                .java
                .getDeclaredConstructor(
                    Context::class.java,
                    NetworkState::class.java,
                    Discovery.EchoFactory::class.java,
                )
        constructor.setAccessible(true)
        var echo: Discovery.EchoFactory =
            Discovery.EchoFactory(
                callback79@{ network, peer ->
                    object : IcmpEcho.Channel {
                        var reply: ByteArray? = null

                        override fun identifier(): Int {
                            return 215
                        }

                        override fun send(packet: ByteArray): Unit {
                            reply = packet.clone()
                            reply!![0] = 0
                            reply!![3] = 0
                            reply!![2] = 0
                            var sum: Int = IcmpEcho.checksum(reply!!)
                            reply!![2] = ((((sum).toInt() ushr (8).toInt()))).toByte()
                            reply!![3] = (sum).toByte()
                        }

                        override fun receive(wait: Int): ByteArray? {
                            var result: ByteArray? = reply
                            reply = null
                            return result
                        }

                        override fun close(): Unit {}
                    }
                }
            )
        var quick: Discovery = constructor.newInstance(target, live, echo)
        accepting.set(quick, true)
        enriching.set(quick, true)
        var work: DiscoveryWork? = (workField.get(quick) as DiscoveryWork?)
        var busy: CountDownLatch = CountDownLatch(4)
        var hold: CountDownLatch = CountDownLatch(1)
        try {
            run {
                var i: Int = 0
                while ((i < 4)) {
                    work!!.submit(
                        DiscoveryWork.Lane.DETAILS,
                        callback80@{
                            busy.countDown()
                            try {
                                hold.await()
                            } catch (stopped: InterruptedException) {
                                (Thread.currentThread())!!.interrupt()
                            }
                        },
                    )
                    i++
                }
            }
            check(busy.await(2, TimeUnit.SECONDS), "Optional-detail lane deliberately blocked")
            var probe: Method =
                Discovery::class
                    .java
                    .getDeclaredMethod(
                        "probe",
                        String::class.java,
                        Int::class.javaPrimitiveType!!,
                        Int::class.javaPrimitiveType!!,
                    )
            probe.setAccessible(true)
            var began: Long = SystemClock.uptimeMillis()
            probe.invoke(quick, live.ip, 160, 220)
            check(
                ((((SystemClock.uptimeMillis() - began)).toLong() < (500).toLong()) &&
                    ((quick.snapshot())!!.size == 1)),
                "An ICMP-confirmed address returns to the sweep without waiting for blocked optional port checks",
            )
        } finally {
            quick.cancel()
            hold.countDown()
            work!!.awaitTermination(2, TimeUnit.SECONDS)
        }
        var serverWorker: ExecutorService = Executors.newSingleThreadExecutor()
        var names: Discovery = Discovery(target, live)
        accepting.set(names, true)
        enriching.set(names, true)
        try {
            ServerSocket(8008, 4, InetAddress.getByName(live.ip)).use { server ->
                server.setSoTimeout(4000)
                var line: java.util.concurrent.atomic.AtomicReference<String>? =
                    java.util.concurrent.atomic.AtomicReference()
                val groupServed = java.util.concurrent.atomic.AtomicBoolean()
                val serving = serverWorker.submit {
                    // Identity and group discovery are independent and may arrive in either order.
                    // Serve both real request paths instead of racing for one fixture response.
                    repeat(2) {
                        server.accept().use { socket ->
                            val input = socket.getInputStream().bufferedReader(java.nio.charset.StandardCharsets.US_ASCII)
                            val requestLine = input.readLine()
                            while (!input.readLine().isNullOrEmpty()) {}
                            val body = when (requestLine) {
                                "GET /setup/eureka_info?params=device_info,name HTTP/1.1" -> {
                                    line!!.set(requestLine)
                                    "{\"name\":\"Fixture Cast speaker\",\"ssid\":\"PRIVATE_SSID\",\"location\":{\"latitude\":123},\"mac_address\":\"00:00:00:00:00:00\",\"ip_address\":\"203.0.113.9\"}"
                                }
                                "GET /setup/eureka_info?params=multizone HTTP/1.1" -> {
                                    groupServed.set(true)
                                    "{\"multizone\":{\"groups\":[],\"dynamic_groups\":[]}}"
                                }
                                else -> throw AssertionError("Unexpected synthetic Cast request")
                            }.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.size +
                                "\r\nConnection: close\r\n\r\n").toByteArray(java.nio.charset.StandardCharsets.US_ASCII))
                            socket.getOutputStream().write(body)
                        }
                    }
                }
                var device: Device? =
                    (observe.invoke(names, live.ip, "Explicit test responder") as Device?)
                device!!.hint("Name", "Chromecast", "Fixture TXT friendly name", 90)
                Discovery::class.java.getDeclaredMethod("applyCurrentService", Device::class.java, MdnsPacket.Service::class.java)
                    .apply { isAccessible = true }.invoke(names, device, MdnsPacket.Service("Chromecast", "_googlecast._tcp.").apply { port = 8009 })
                cast.invoke(names, device)
                serving.get(5, TimeUnit.SECONDS)
                check(groupServed.get(), "Cast identity and group fixture handles both request orders")
                var end: Long = (SystemClock.uptimeMillis() + 3000)
                while (
                    (!(device!!.name.equals("Fixture Cast speaker")) &&
                        (SystemClock.uptimeMillis() < end))
                ) {
                    SystemClock.sleep(25)
                }
                check(
                    (device!!.name.equals("Fixture Cast speaker") &&
                        "GET /setup/eureka_info?params=device_info,name HTTP/1.1".equals(line!!.get())),
                    "Bound local Cast fallback supplies a missing friendly name; name=" + device!!.name +
                        ", request=" + line!!.get() + ", lookups=" + device!!.metadataLookupRows() + ", sources=" + names.sources(),
                )
                check(
                    (((device!!.mac.isEmpty() && device!!.ip.equals(live.ip)) &&
                        !(((device!!.json())!!.toString())!!.contains("PRIVATE_SSID"))) &&
                        !(((device!!.json())!!.toString())!!.contains("latitude"))),
                    "Cast name enrichment retains neither unrelated identifiers nor a placeholder MAC/claimed address",
                )
                device!!.hint("Name", "Better advertised name", "mDNS friendly", 90)
                cast.invoke(names, device)
                check(
                    device!!.name.equals("Better advertised name"),
                    "Cast fallback preserves stronger advertised identity and never repeats for the same peer",
                )
            }
        } finally {
            names.cancel()
            serverWorker.shutdownNow()
        }

    }

    private fun icmpDeniedFlow(): Unit {
        var target: Context = getTargetContext()
        check((Build.VERSION.SDK_INT >= 37), "Direct local-network denial applies on API37+")
        check(
            (target.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") !=
                PackageManager.PERMISSION_GRANTED),
            "Local network grant starts denied on emulator",
        )
        var live: NetworkState = NetworkState.current(target)
        var denied: IcmpEcho.Result =
            IcmpEcho.probe(
                callback82@{ AndroidIcmp.open(live, InetAddress.getByName(live.gateway)) },
                callback83@{ false },
                200,
                2,
                120,
            )
        check(
            ((denied.status === IcmpEcho.Status.ERROR) &&
                (denied.problem === IcmpEcho.Problem.PERMISSION)),
            ((("Actual OS local-network denial is reported by ICMP transport: " + denied.status) +
                " ") + denied.reason()),
        )
    }



    private fun confirmedFixes(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        live.check(target)
        check(
            live.sameScope(NetworkState.current(target)),
            "Repeated observation retains the same live connection",
        )
        var manager: android.net.ConnectivityManager? =
            (target.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager?)
        var original: android.net.LinkProperties? = (manager)!!.getLinkProperties(live.network)
        var changed: android.net.LinkProperties = copyProperties((original)!!)
        var addresses: MutableList<android.net.LinkAddress> =
            ArrayList((original)!!.getLinkAddresses())
        Collections.reverse(addresses)
        changed.setLinkAddresses(addresses)
        var dns: List<InetAddress> = ArrayList((original)!!.getDnsServers())
        Collections.reverse(dns)
        changed.setDnsServers(dns)
        var reordered: NetworkState = networkFixture(target, (live.network)!!, changed)
        check(live.sameScope(reordered), "Address and DNS ordering preserve connection and scope")
        var ipv4Only: MutableList<android.net.LinkAddress> = ArrayList()
        for (a in addresses) {
            if ((a.address is Inet4Address)) {
                ipv4Only.add(a)
            }
        }
        check((ipv4Only.size < addresses.size), "Fixture has an IPv6 prefix to withdraw")
        changed.setLinkAddresses(ipv4Only)
        var withdrawn: NetworkState = networkFixture(target, (live.network)!!, changed)
        check(
            (live.sameConnection(withdrawn) && live.retainsScope(withdrawn)),
            "IPv6 withdrawal leaves IPv4 connection and discovery scope unchanged",
        )
        check(
            live.refresh(withdrawn),
            "IPv4 work survives unrelated IPv6 prefix withdrawal",
        )
        live.check(target)
        var old: Device = Device()
        old.ip = live.ip
        old.name = "Previous network fixture"
        store.retainIfCurrent(store.token(), live.key, Collections.singletonList(old), true)
        var observerField: Field = NetworkState::class.java.getDeclaredField("sessionObserver")
        observerField.setAccessible(true)
        (((observerField.get(null) as android.net.ConnectivityManager.NetworkCallback?)))!!.onLost(
            (live.network)!!
        )
        var next: NetworkState = NetworkState.current(target)
        check(
            ((!(next.key.equals(live.key)) && (store.devices(next.key))!!.isEmpty()) &&
                (store.report(next.key) === null)),
            "Reused Android handle cannot restore a lost session's results",
        )
        live = next
        var rich: Device = Device()
        rich.ip = "192.0.2.5"
        for (field in
            Arrays.asList(
                "Name",
                "Hostname",
                "Manufacturer",
                "Model",
                "Device type",
                "OS",
                "Firmware",
            )) {
            rich.hint(field, ("Useful " + field), "Fixture advertised source", 80)
        }
        run {
            var n: Int = 0
            while ((n < 14)) {
                rich.detail(("Extra fixture " + n), ("value " + n))
                n++
            }
        }
        rich.detail("ONVIF hardware (advertised)", "Camera hardware")
        rich.detail("UPnP model number", "Model number")
        check(
            (((rich.details.size <= 16) && !(rich.details.containsKey("Name source"))) &&
                ((rich.observations())!!.size == 7)),
            "Provenance has independent bounded storage",
        )
        check(
            ((rich.details.get("ONVIF hardware (advertised)"))!!.equals("Camera hardware") &&
                (rich.details.get("UPnP model number"))!!.equals("Model number")),
            "Late useful hardware and model details survive a full detail budget",
        )
        var copied: Device = rich.copy()
        var restored: Device = Device.from(rich.json())
        check(
            ((copied.details.equals(rich.details) && restored.details.equals(rich.details)) &&
                ((copied.observations())!!.size == 7)),
            "Copy and persistence preserve substantive details and provenance",
        )
        copied.detail("UPnP model number", "Changed copy")
        check(
            (rich.details.get("UPnP model number"))!!.equals("Model number"),
            "Snapshot maps are isolated from the source device",
        )
        var legacy: JSONObject = rich.json()
        var meta: JSONObject = legacy.getJSONObject("details")
        meta.put("Name source", "Legacy source")
        meta.put("Model source", "Legacy source")
        check(
            (!((Device.from(legacy))!!.details.containsKey("Name source")) &&
                (Device.from(legacy))!!.details.containsKey("ONVIF hardware (advertised)")),
            "Old source labels do not consume migrated detail capacity",
        )
        var identity: String = ""
        run {
            var seed: Int = 0
            while ((seed < 24)) {
                var d: Device = Device()
                var ids: List<String> = ArrayList(Arrays.asList("_cast|b", "_cast|a", "_airplay|c"))
                Collections.shuffle(ids, Random((seed).toLong()))
                for (id in ids) {
                    d.identify(id, 80)
                }
                d.identify("fallback", 20)
                if ((seed == 0)) {
                    identity = d.identity
                }
                check(
                    ((d.identity.equals(identity) && (d.copy())!!.identity.equals(identity)) &&
                        (Device.from(d.json()))!!.identity.equals(identity)),
                    "Internal identity is deterministic across callback orders",
                )
                seed++
            }
        }
        var v4: Device = Device()
        v4.ip = "192.0.2.5"
        v4.name = "Address layout fixture"
        v4.link("fixture:dual")
        var v6: Device = Device()
        v6.ip = "fd00:1234:5678:abcd:1234:5678:abcd:1234"
        v6.link("fixture:dual")
        var linkLocal: Device = Device()
        linkLocal.ip = "fe80::abcd%wlan0"
        linkLocal.link("fixture:dual")
        store.clear()
        store.retainIfCurrent(store.token(), live.key, Arrays.asList(v4, v6, linkLocal), true)
        ComposeUiChecks.run(this,::check,"compose-ui")
    }

    private fun launch(demo: Boolean): Activity {
        return startActivitySync(
            ((Intent(Intent.ACTION_MAIN).setClassName("io.github.kazands.networklens", "io.github.kazands.networklens.MainActivity"))!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))!!
                .putExtra("demo", demo)
        )
    }











    private fun wsDiscovery(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        check(!(live.ip.isEmpty()), "Wi-Fi available for WSD fixtures")
        var discovery: String = "http://schemas.xmlsoap.org/ws/2005/04/discovery"
        var addressing: String = "http://schemas.xmlsoap.org/ws/2004/08/addressing"
        var id: String = "11111111-1111-1111-1111-111111111111"
        var reply: ByteArray? =
            (wsReply(discovery, addressing, ("urn:uuid:" + id), live.ip, "Office%20printer"))!!
                .toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        var fixture: Discovery = Discovery(target, live)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        accepting.setAccessible(true)
        accepting.set(fixture, true)
        var packet: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "wsPacket",
                    InetAddress::class.java,
                    ByteArray::class.java,
                    Int::class.javaPrimitiveType!!,
                    WsDiscovery.Version::class.java,
                    String::class.java,
                )
        packet.setAccessible(true)
        try {
            check(
                (((packet.invoke(
                    fixture,
                    InetAddress.getByName("203.0.113.20"),
                    reply,
                    reply!!.size,
                    WsDiscovery.Version.LEGACY,
                    id,
                ) as Int) == 0) && (fixture.snapshot())!!.isEmpty()),
                "Off-network WSD sender is rejected",
            )
            check(
                (((packet.invoke(
                    fixture,
                    InetAddress.getByName(live.ip),
                    reply,
                    reply!!.size,
                    WsDiscovery.Version.LEGACY,
                    "22222222-2222-2222-2222-222222222222",
                ) as Int) == 0) && (fixture.snapshot())!!.isEmpty()),
                "An uncorrelated response cannot create a device",
            )
            check(
                ((packet.invoke(
                    fixture,
                    InetAddress.getByName(live.ip),
                    reply,
                    reply!!.size,
                    WsDiscovery.Version.LEGACY,
                    id,
                ) as Int) == 1),
                "Platform SAX parser accepts a correlated response",
            )
            var d: Device = (fixture.snapshot())!!.get(0)
            check(
                ((d.name.equals("Office printer") && d.mac.equals("00:11:22:33:44:55")) &&
                    d.ports.contains(8899)),
                "Name, reported MAC and port enrich the matching endpoint",
            )
            check(
                ((d.kind!!.equals("Printer service") && d.model.isEmpty()) &&
                    d.manufacturer.isEmpty()),
                "Hardware and role do not invent model or manufacturer",
            )
            check(
                "Fixture hardware".equals(d.details.get("ONVIF hardware (advertised)")),
                "Hardware description retains explicit provenance",
            )
            check(
                !(((d.json())!!.toString())!!.contains("private")),
                "Private location scope is not retained",
            )
            packet.invoke(
                fixture,
                InetAddress.getByName(live.ip),
                reply,
                reply!!.size,
                WsDiscovery.Version.LEGACY,
                id,
            )
            check(
                (((fixture.snapshot())!!.size == 1) &&
                    ((((fixture.snapshot())!!.get(0))!!.observations())!!.size ==
                        (d.observations())!!.size)),
                "Repeated replies do not duplicate devices or observations",
            )
            var sources: MutableMap<String, String> = TreeMap(fixture.sources())
            sources.put("WS-Discovery", "OK · fixture")
            var report: ScanReport =
                ScanReport(
                    "Fixture subnet",
                    true,
                    "Fixture complete",
                    1,
                    1,
                    System.currentTimeMillis(),
                    sources,
                )
            check(
                store.retainIfCurrent(store.token(), live.key, fixture.snapshot(), report),
                "New observations and source status persist",
            )
            var saved: Device = (store.devices(live.key))!!.get(0)
            check(
                (saved.name.equals(d.name) &&
                    (saved.details.get("ONVIF hardware (advertised)"))!!.equals(
                        "Fixture hardware"
                    )),
                "New metadata survives reopening",
            )
            activity = launch(false)
            waitForIdleSync()
            click("Office printer")
            click("Show technical details")
            assertText("Reported hardware")
            assertText("Fixture hardware")
            capture("ws-hardware-light")
            onMain(
                callback107@{
                    try {
                        var copy: Method =
                            (activity!!.javaClass)!!.getDeclaredMethod(
                                "deviceText",
                                Device::class.java,
                            )
                        copy.setAccessible(true)
                        var value: String? = (copy.invoke(activity, saved) as String?)
                        check(
                            ((value)!!.contains("Reported hardware (ONVIF): Fixture hardware") &&
                                (value)!!.contains("MAC: 00:11:22:33:44:55")),
                            "Copy includes hardware description and reported addresses",
                        )
                    } catch (e: Exception) {
                        throw AssertionError(e)
                    }
                }
            )
            description("Switch to dark mode")
            assertText("Fixture hardware")
            capture("ws-hardware-dark")
            description("Back to devices")
            fixture.cancel()
            var after: ByteArray =
                (wsReply(discovery, addressing, ("urn:uuid:" + id), live.ip, "Changed"))!!
                    .toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            check(
                (((packet.invoke(
                    fixture,
                    InetAddress.getByName(live.ip),
                    after,
                    after.size,
                    WsDiscovery.Version.LEGACY,
                    id,
                ) as Int) == 0) && ((fixture.snapshot())!!.get(0))!!.name.equals("Office printer")),
                "Cancelled discovery rejects later metadata",
            )
        } finally {
            fixture.cancel()
        }
        var transport: Discovery = Discovery(target, live)
        accepting.set(transport, true)
        var stop: java.util.concurrent.atomic.AtomicBoolean =
            java.util.concurrent.atomic.AtomicBoolean()
        var requests: java.util.concurrent.atomic.AtomicInteger? =
            java.util.concurrent.atomic.AtomicInteger()
        var receiver: ExecutorService = Executors.newSingleThreadExecutor()
        var wm: android.net.wifi.WifiManager? =
            (target.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager?)
        var lock: android.net.wifi.WifiManager.MulticastLock =
            (wm)!!.createMulticastLock("wsd-test")
        lock.acquire()
        try {
            MulticastSocket(null).use { server ->
                server.setReuseAddress(true)
                server.bind(InetSocketAddress(3702))
                (live.network)!!.bindSocket(server)
                var iface: NetworkInterface = NetworkInterface.getByName(live.iface)
                server.setNetworkInterface(iface)
                server.joinGroup(
                    InetSocketAddress(InetAddress.getByName("239.255.255.250"), 3702),
                    iface,
                )
                server.setSoTimeout(200)
                var serving: Future<*>? =
                    receiver.submit(
                        callback108@{
                            try {
                                while (!(stop.get())) {
                                    var bytes: ByteArray = ByteArray(2048)
                                    var q: DatagramPacket = DatagramPacket(bytes, bytes.size)
                                    try {
                                        server.receive(q)
                                    } catch (ignored: SocketTimeoutException) {
                                        continue
                                    }
                                    var xml: String =
                                        String(
                                            bytes,
                                            0,
                                            q.length,
                                            java.nio.charset.StandardCharsets.UTF_8,
                                        )
                                    var first: Int = xml.indexOf("<a:MessageID>")
                                    var last: Int = xml.indexOf("</a:MessageID>")
                                    if (
                                        (((first < 0) || (last < first)) ||
                                            !(xml.contains("<d:Probe/>")))
                                    ) {
                                        continue
                                    }
                                    var message: String = xml.substring((first + 13), last)
                                    if (!(message.matches(Regex("urn:uuid:[0-9a-f-]{36}")))) {
                                        continue
                                    }
                                    var modern: Boolean =
                                        xml.contains(
                                            "http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01"
                                        )
                                    var dd: String =
                                        (if (modern)
                                            "http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01"
                                        else discovery)
                                    var aa: String =
                                        (if (modern) "http://www.w3.org/2005/08/addressing"
                                        else addressing)
                                    var response: ByteArray =
                                        (wsReply(dd, aa, message, live.ip, "Socket%20printer"))!!
                                            .toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                                    server.send(
                                        DatagramPacket(response, response.size, q.address, q.port)
                                    )
                                    requests!!.incrementAndGet()
                                }
                            } catch (e: IOException) {
                                if (!(stop.get())) {
                                    throw RuntimeException(e)
                                }
                            }
                        }
                    )
                var start: Method = Discovery::class.java.getDeclaredMethod("startWsDiscovery")
                start.setAccessible(true)
                start.invoke(transport)
                var thread: Field? = Discovery::class.java.getDeclaredField("wsThread")
                thread!!.setAccessible(true)
                (((thread!!.get(transport) as Thread?)))!!.join(8000)
                stop.set(true)
                serving!!.get(2, TimeUnit.SECONDS)
                check(
                    (requests!!.get() == 4),
                    "Multicast transport sends two copies of both protocol versions",
                )
                check(
                    (((transport.snapshot())!!.size == 1) &&
                        ((transport.snapshot())!!.get(0))!!.name.equals("Socket printer")),
                    "Bound Android socket receives and applies correlated UDP replies",
                )
                var status: String? = (transport.sources())!!.get("WS-Discovery")
                check(
                    (((status)!!.startsWith("OK") && (status)!!.contains("devices 1")) &&
                        (status)!!.contains("requests 4")),
                    "Source health reports the bounded window and one deduplicated device",
                )
            }
        } finally {
            stop.set(true)
            transport.cancel()
            receiver.shutdownNow()
            lock.release()
        }

        store.clear()
    }

    private fun discoveryUpgrade(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        var v4: Device = Device()
        v4.ip = "192.0.2.20"
        v4.link("fixture:one")
        v4.hint("Name", "Android", "mDNS instance", 65)
        v4.hint("Model", "Xserve", "compatibility", 20)
        var v6: Device = Device()
        v6.ip = "fd00::20"
        v6.link("fixture:one")
        v6.hint("Name", "Living room TV", "mDNS friendly", 90)
        v6.hint("Model", "Actual model", "TXT", 80)
        v6.hint("Manufacturer", "Fixture Inc", "TXT", 80)
        var group: Device = (Device.grouped(Arrays.asList(v4, v6)))!!.get(0)
        check(
            (((group.title())!!.equals("Living room TV") && group.model.equals("Actual model")) &&
                group.manufacturer.equals("Fixture Inc")),
            "Group display uses strongest available identity across its addresses",
        )
        check(
            ((((group.endpoints())!!.size == 2) &&
                ((group.endpoints())!!.get(0))!!.name.equals("Android")) &&
                ((group.endpoints())!!.get(0))!!.model.equals("Xserve")),
            "Group selection preserves original per-address values",
        )
        check(
            (((Device.grouped(Arrays.asList(v6, v4)))!!.get(0))!!.title())!!.equals(group.title()),
            "Grouping identity is independent of input order",
        )
        check(
            ((((group.copy())!!.endpoints())!!.size == 2) &&
                ((group.copy())!!.title())!!.equals(group.title())),
            "Group selection survives copy and endpoint JSON round trips",
        )
        var sources: MutableMap<String, String> = TreeMap()
        run {
            var i: Int = 0
            while ((i < 48)) {
                sources.put(("mDNS fixture" + i), "OK")
                i++
            }
        }
        sources.put("z failure", "Failed · preserved")
        var report: ScanReport =
            ScanReport(
                "fixture",
                false,
                "Sources incomplete",
                1,
                1,
                System.currentTimeMillis(),
                sources,
            )
        check(
            (((ScanReport.from(report.json()))!!.sources.size == 49) &&
                (ScanReport.from(report.json()))!!.sources.containsKey("z failure")),
            "Expanded discovery source report retains late failures",
        )
        var fixture: Discovery = Discovery(target, live)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var enriching: Field = Discovery::class.java.getDeclaredField("enriching")
        accepting.setAccessible(true)
        enriching.setAccessible(true)
        accepting.set(fixture, true)
        var observe: Method =
            Discovery::class
                .java
                .getDeclaredMethod("observe", String::class.java, String::class.java)
        observe.setAccessible(true)
        var local: Device? = (observe.invoke(fixture, live.ip, "Fixture response") as Device?)
        var queue: Method =
            Discovery::class.java.getDeclaredMethod("queueMdnsName", Device::class.java)
        queue.setAccessible(true)
        queue.invoke(fixture, local)
        var questions: Field = Discovery::class.java.getDeclaredField("mdnsQuestions")
        questions.setAccessible(true)
        var question: Any? = (((questions.get(fixture) as MutableMap<*, *>?)))!!.get(live.ip)
        var attempts: Field = ((question)!!.javaClass)!!.getDeclaredField("attempts")
        attempts.setAccessible(true)
        attempts.setInt(question, 1)
        var reverse: MdnsPacket.Result = MdnsPacket.Result()
        reverse.hostnames.put(ReverseDns.question(live.ip), "fixture.local")
        var mdns: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsResolved",
                    InetAddress::class.java,
                    MdnsPacket.Result::class.java,
                )
        mdns.setAccessible(true)
        mdns.invoke(fixture, InetAddress.getByName("203.0.113.20"), reverse)
        check(
            local!!.hostname.isEmpty(),
            "Off-network PTR sender cannot label an observed endpoint",
        )
        var address: ByteArray? = Address.bytes(live.ip)
        address!![3] =
            (((if (((((address!![3]).toInt() and (255).toInt())) == 200)) 201 else 200))).toByte()
        mdns.invoke(fixture, InetAddress.getByAddress(address), reverse)
        check(
            local!!.hostname.isEmpty(),
            "A different local sender cannot name the requested address",
        )
        mdns.invoke(fixture, InetAddress.getByName(live.ip), reverse)
        check(
            (local!!.hostname.equals("fixture.local") &&
                local!!.evidence.contains("Matching mDNS reverse-PTR reply")),
            "Only a matching requested reverse-PTR reply supplies a hostname",
        )
        var manager: android.net.nsd.NsdManager? =
            (target.getSystemService(Context.NSD_SERVICE) as android.net.nsd.NsdManager?)
        var registered: CountDownLatch = CountDownLatch(1)
        var registrationOk: BooleanArray = booleanArrayOf(false)
        var registrationError: IntArray = intArrayOf(-(1))
        var registration: android.net.nsd.NsdManager.RegistrationListener =
            object : android.net.nsd.NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo): Unit {
                    registrationOk[0] = true
                    registered.countDown()
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, error: Int): Unit {
                    registrationError[0] = error
                    registered.countDown()
                }

                override fun onServiceUnregistered(info: NsdServiceInfo): Unit {}

                override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int): Unit {}
            }
        try {
            ServerSocket(0).use { serviceSocket ->
                var service: NsdServiceInfo = NsdServiceInfo()
                service.setServiceName("DeviceLens discovery fixture")
                service.setServiceType("_lanwhisp-test._tcp.")
                service.setPort(serviceSocket.getLocalPort())
                service.setAttribute("fn", "Discovered appliance")
                service.setAttribute("model", "Fixture 800")
                (manager)!!.registerService(
                    service,
                    android.net.nsd.NsdManager.PROTOCOL_DNS_SD,
                    registration,
                )
                check(
                    (registered.await(15, TimeUnit.SECONDS) && registrationOk[0]),
                    ("Register an omitted-type fixture with Android's real NSD service; callback error=" +
                        registrationError[0]),
                )
                var start: Method = Discovery::class.java.getDeclaredMethod("startNsd")
                var raw: Method = Discovery::class.java.getDeclaredMethod("startMdns")
                start.setAccessible(true)
                raw.setAccessible(true)
                start.invoke(fixture)
                val activeBrowses = Discovery::class.java.getDeclaredField("legacyActive").apply { isAccessible = true }
                val queuedBrowses = Discovery::class.java.getDeclaredField("legacyBrowses").apply { isAccessible = true }
                synchronized(fixture) {
                    check((activeBrowses.get(fixture) as Collection<*>).size in 1..6 &&
                        (queuedBrowses.get(fixture) as Collection<*>).isNotEmpty(),
                        "Android browsing uses six slots and queues remaining types on this API level")
                }
                raw.invoke(fixture)
                var enumeration: MdnsPacket.Result = MdnsPacket.Result()
                enumeration.serviceTypes.add("_lanwhisp-test._tcp.")
                mdns.invoke(fixture, InetAddress.getByName(live.ip), enumeration)
                var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
                types.setAccessible(true)
                check(
                    (((types.get(fixture) as Set<String>?)))!!.contains("_lanwhisp-test._tcp."),
                    "Enumeration starts discovery outside the original fixed types",
                )
                var end: Long = (SystemClock.uptimeMillis() + 18000)
                while (
                    ((SystemClock.uptimeMillis() < end) &&
                        !(((fixture.snapshot())!!.stream())!!.anyMatch(
                            callback109@{ d ->
                                (d.name.equals("Discovered appliance") &&
                                    d.model.equals("Fixture 800"))
                            }
                        )))
                ) {
                    SystemClock.sleep(100)
                }
                check(
                    ((fixture.snapshot())!!.stream())!!.anyMatch(
                        callback110@{ d ->
                            (d.name.equals("Discovered appliance") && d.model.equals("Fixture 800"))
                        }
                    ),
                    ((("Real Android discovery and queued callback resolve dynamic name/model on a service address; sources=" +
                        fixture.sources()) + "; observed=") +
                        (((fixture.snapshot())!!.stream())!!.map(
                                callback111@{ d -> ((((d.ip + "/") + d.name) + "/") + d.model) }
                            ))!!
                            .collect(java.util.stream.Collectors.joining(", "))),
                )
                if ((Build.VERSION.SDK_INT < 34)) {
                    check(
                        (((fixture.sources())!!.entries)!!.stream())!!.noneMatch(
                            callback112@{ e ->
                                ((e.key)!!.startsWith("mDNS ") &&
                                    (e.value)!!.contains("Android discovery error 4"))
                            }
                        ),
                        "Legacy DNS-SD batching leaves request capacity for registration and serialized resolution",
                    )
                }
            }
        } finally {
            try {
                (manager)!!.unregisterService(registration)
            } catch (ignored: Exception) {}

            fixture.cancel()
        }

        fixture = Discovery(target, live)
        accepting.set(fixture, true)
        enriching.set(fixture, true)
        var described: Device? = (observe.invoke(fixture, live.ip, "Fixture response") as Device?)
        var description: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "description",
                    Device::class.java,
                    InetAddress::class.java,
                    String::class.java,
                    Map::class.java,
                )
        description.setAccessible(true)
        var responder: ExecutorService = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 2, InetAddress.getByName(live.ip)).use { server ->
                server.setSoTimeout(8000)
                var serving: Future<*>? =
                    responder.submit(
                        callback113@{
                            try {
                                run {
                                    var i: Int = 0
                                    while ((i < 2)) {
                                        server.accept().use { socket ->
                                            socket.setSoTimeout(1500)
                                            var `in`: BufferedReader =
                                                BufferedReader(
                                                    InputStreamReader(
                                                        socket.getInputStream(),
                                                        java.nio.charset.StandardCharsets.US_ASCII,
                                                    )
                                                )
                                            var first: String = `in`.readLine()
                                            var line: String? = null
                                            while (
                                                ((run {
                                                    line = `in`.readLine()
                                                    line
                                                } !== null) && !(line!!.isEmpty()))
                                            ) {}
                                            var bytes: ByteArray =
                                                (("<root><device><friendlyName>Fallback appliance</friendlyName><modelName>Fallback model</modelName></device></root>"))!!
                                                    .toByteArray(
                                                        java.nio.charset.StandardCharsets.UTF_8
                                                    )
                                            var good: Boolean = first.contains("/good.xml")
                                            (socket.getOutputStream())!!.write(
                                                (((((((if (good) "HTTP/1.1 200 OK"
                                                    else "HTTP/1.1 500 Error")) +
                                                        "\r\nContent-Length: ") +
                                                        ((if (good) bytes.size else 0))) +
                                                        "\r\nConnection: close\r\n\r\n")))!!
                                                    .toByteArray(
                                                        java.nio.charset.StandardCharsets.US_ASCII
                                                    )
                                            )
                                            if (good) {
                                                (socket.getOutputStream())!!.write(bytes)
                                            }
                                        }
                                        i++
                                    }
                                }
                            } catch (e: IOException) {
                                throw RuntimeException(e)
                            }
                        }
                    )
                var base: String = ((("http://" + live.ip) + ":") + server.getLocalPort())
                description.invoke(
                    fixture,
                    described,
                    InetAddress.getByName(live.ip),
                    (base + "/bad.xml"),
                    emptyMap<String, String>(),
                )
                description.invoke(
                    fixture,
                    described,
                    InetAddress.getByName(live.ip),
                    (base + "/good.xml"),
                    emptyMap<String, String>(),
                )
                var ef: Field = Discovery::class.java.getDeclaredField("enrichment")
                ef.setAccessible(true)
                var executor: DiscoveryWork? = (ef.get(fixture) as DiscoveryWork?)
                (executor)!!.shutdown()
                check(
                    (executor)!!.awaitTermination(8, TimeUnit.SECONDS),
                    "Description alternatives finish within budget",
                )
                serving!!.get(2, TimeUnit.SECONDS)
                check(
                    ((described)!!.name.equals("Fallback appliance") &&
                        (described)!!.model.equals("Fallback model")),
                    "A failed first HTTP description does not suppress a later useful one",
                )
                var finish: Method = Discovery::class.java.getDeclaredMethod("finishDescriptions")
                finish.setAccessible(true)
                finish.invoke(fixture)
                var hf: Field = Discovery::class.java.getDeclaredField("health")
                hf.setAccessible(true)
                var status: String? =
                    ((((hf.get(fixture) as DiscoveryHealth?)))!!.sources())!!.get(
                        "UPnP descriptions"
                    )
                check(
                    (((status)!!.startsWith("OK") && (status)!!.contains("failed attempts 1")) &&
                        (status)!!.contains("received 1")),
                    "Recovered description reports success while retaining failed-attempt count",
                )
            }
        } finally {
            fixture.cancel()
            responder.shutdownNow()
        }

        store.clear()
    }



    private fun failureReporting(): Unit {
        ScanReportChecks.run(::check)
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        store.dark(false)
        var live: NetworkState = NetworkState.current(target)
        var health: DiscoveryHealth = DiscoveryHealth()
        var source: String = "mDNS _hap._tcp."
        health.resolving("fixture", "_hap._tcp.")
        health.resolveFailed(
            "fixture",
            "No usable service data after 4002 ms (registered; no addresses)",
        )
        health.fail(
            source,
            "First attempt: No usable service data after 4001 ms (registered; no addresses)",
        )
        for (event in
            arrayOf(
                "failed",
                "found",
                "live service records",
                "resolved",
                "retried",
                "timeout attempts",
            )) {
            health.count(source, event)
            health.count(source, event)
        }
        var full: String? = (health.sources())!!.get(source)
        check(
            (((full)!!.length > 200) && ((full)!!.length < 512)),
            "Real two-attempt error plus six counters reproduces the old report overflow",
        )
        var sources: MutableMap<String, String> = LinkedHashMap(health.sources())
        sources.put("mDNS _http._tcp.", (full)!!)
        sources.put("mDNS _fixture-unknown._tcp.", "Truncated · Controlled fixture window ended")
        var report: ScanReport =
            ScanReport(
                "Fixture coverage",
                false,
                "Some checks could not finish: 2 discovery sources failed, 1 truncated. Tap scan status for details.",
                254,
                254,
                System.currentTimeMillis(),
                sources,
            )
        check(
            ((report.sources.get(source))!!.equals(full) &&
                (report.sources.get(source))!!.endsWith("timeout attempts 2")),
            "Terminal report preserves both native timeout reasons and all trailing counters",
        )
        check(
            ((ScanReport.from(report.json()))!!.sources.get(source))!!.equals(full),
            "JSON round trip preserves the same bounded diagnostic value",
        )
        var oversized: MutableMap<String, String> = LinkedHashMap()
        var longValue: StringBuilder = StringBuilder("Failed · ")
        run {
            var i: Int = 0
            while ((i < 2000)) {
                longValue.append('x')
                i++
            }
        }
        oversized.put(source, longValue.toString())
        check(
            ((ScanReport("", false, "", 0, 0, 1, oversized).sources.get(source))!!.length == 1024),
            "Untrusted overlong source values remain capped",
        )
        val trafficHealth = DiscoveryHealth().apply {
            fail(source, "x".repeat(80)); truncated(source, "y".repeat(80))
            for (label in listOf("hostname batches", "service retry batches", "type queries", "new types", "hostnames", "query batches")) count(source, label)
            for (event in DiscoveryHealth.Diagnostic.values()) if (event.name.startsWith("MDNS_")) diagnostic(source, event, 4096)
        }
        val trafficDetail = trafficHealth.sources().getValue(source)
        check(trafficDetail.length > 512 && trafficDetail.length <= 1024, "Finite traffic diagnostics require the expanded bounded report entry")
        check(ScanReport("", false, "", 0, 0, 1, trafficHealth.sources()).sources[source] == trafficDetail,
            "Terminal reports retain all traffic counters without silently truncating them")
        val nativeHealth = DiscoveryHealth().apply {
            fail(source, "x".repeat(80)); fail(source, "y".repeat(80))
            for (event in listOf("failed", "found", "live service records", "resolved", "retried", "limited")) count(source, event)
            for (event in DiscoveryHealth.Diagnostic.values()) if (event.name.startsWith("NSD_")) diagnostic(source, event, 256)
        }
        val nativeDetail = nativeHealth.sources().getValue(source)
        check(nativeDetail.length <= 1024 && nativeDetail.contains("wire complete after native failure 256"),
            "The full finite native-correlation vocabulary fits the existing source-detail bound")
        check(ScanReport.from(ScanReport("", false, "", 0, 0, 1, nativeHealth.sources()).json())!!.sources[source] == nativeDetail,
            "Terminal serialization preserves correlation counters without adding identifiers")
        check(
            store.retainIfCurrent(store.token(), live.key, Collections.emptyList(), report),
            "Failure fixture retains a report without inventing devices",
        )
        ComposeUiChecks.run(this,::check,"compose-ui")
    }





    private fun optionTexts(): String = texts()

    private fun optionClick(label: String) {compose!!.click(label)}

    private fun menuChoose(label: String) {compose!!.click(label)}



    private fun legacyFile(c: Context): java.io.File {
        return java.io.File(c.getDataDir(), "shared_prefs/network_lens.xml")
    }

    private fun noLegacyFiles(c: Context): Boolean {
        return (((!((legacyFile(c))!!.exists()) &&
            !(java.io.File(((legacyFile(c))!!.path + ".bak")).exists())) &&
            !((legacyFile(c.createDeviceProtectedStorageContext()))!!.exists())) &&
            !(java.io
                .File(((legacyFile(c.createDeviceProtectedStorageContext()))!!.path + ".bak"))
                .exists()))
    }







    private fun legacySeed(): Unit {
        var c: Context = getTargetContext()
        for (storage in arrayOf(c, c.createDeviceProtectedStorageContext())) {
            check(
                ((((((storage.getSharedPreferences("network_lens", 0))!!.edit())!!.putBoolean(
                                    "dark",
                                    true,
                                ))!!
                                .putString("inventory-network", "old network"))!!
                            .putString("inventory", "private legacy fixture"))!!
                        .putString("events:legacy", "old history"))!!
                    .commit(),
                "Seed legacy preferences synchronously for upgrade verification",
            )
            check(
                (legacyFile(storage))!!.exists(),
                "Legacy preference file really exists before upgrade",
            )
            java.nio.file.Files.copy(
                (legacyFile(storage))!!.toPath(),
                java.io.File(((legacyFile(storage))!!.path + ".bak")).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            check(
                java.io.File(((legacyFile(storage))!!.path + ".bak")).exists(),
                "Legacy backup exists before upgrade",
            )
        }
    }

    private fun sessionOnly(): Unit {
        var c: Context = getTargetContext()
        var store: Store = Store(c)
        var live: NetworkState = NetworkState.current(c)
        check(
            noLegacyFiles(c),
            "Starting the session removes both legacy preference files and backups",
        )
        check(
            ((store.devices(live.key))!!.isEmpty() && (store.report(live.key) === null)),
            "The session starts without an old inventory or report",
        )
        var system: Boolean =
            (((((((c.getApplicationContext())!!.getResources())!!.getConfiguration())!!.uiMode)
                .toInt() and (Configuration.UI_MODE_NIGHT_MASK).toInt())) ==
                Configuration.UI_MODE_NIGHT_YES)
        check(
            (store.dark() == system),
            "Initial appearance follows Android rather than an old preference",
        )
        store.dark(!(system))
        var d: Device = Device()
        d.ip = live.ip
        d.name = "RAM-only fixture"
        d.seen = true
        var summary: ScanReport =
            ScanReport("RAM fixture", true, "Current session", 1, 1, System.currentTimeMillis())
        var token: Long = store.token()
        check(
            store.retainIfCurrent(token, live.key, Collections.singletonList(d), summary),
            "Session results can be shared in memory",
        )
        d.name = "Changed source"
        var second: Store = Store(c)
        check(
            (((second.devices(live.key))!!.get(0))!!.name.equals("RAM-only fixture") &&
                (second.report(live.key))!!.scope.equals(summary.scope)),
            "Another Store reads a defensive current-session snapshot",
        )
        var snapshot: Device = (second.devices(live.key))!!.get(0)
        snapshot.name = "Changed reader"
        check(
            ((store.devices(live.key))!!.get(0))!!.name.equals("RAM-only fixture"),
            "A reader cannot mutate the session inventory",
        )
        check(
            ((second.devices("other connection"))!!.isEmpty() &&
                (second.report("other connection") === null)),
            "Memory results remain isolated by connection",
        )
        check(noLegacyFiles(c), "Results and appearance create no preference file")
        activity = launch(false)
        settle()
        onMain(
            callback164@{
                setField("query", "RAM-only")
                setField("range", (live.ip + "/32"))
                invoke("render")
                var state: Bundle = Bundle()
                state.putString("old fixture", "private")
                try {
                    var save: Method =
                        (activity!!.javaClass)!!.getDeclaredMethod(
                            "onSaveInstanceState",
                            Bundle::class.java,
                        )
                    save.setAccessible(true)
                    save.invoke(activity, state)
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
                check(
                    state.isEmpty(),
                    "Activity state never serializes network filters or device information",
                )
            }
        )
        var monitor: ActivityMonitor = addMonitor("io.github.kazands.networklens.MainActivity", null, false)
        onMain(callback165@{ activity!!.recreate() })
        var replacement: Activity? =
            nullablePlatformResult(waitForMonitorWithTimeout(monitor, 8000))
        removeMonitor(monitor)
        check((replacement !== null), "The Activity can be recreated with no persisted Bundle")
        activity = replacement
        settle()
        check(
            ((field("query"))!!.equals("RAM-only") && (field("range"))!!.equals((live.ip + "/32"))),
            "Non-configuration memory preserves filters within the same process",
        )
        check(
            ((Store(c).dark() == !(system)) && ((store.devices(live.key))!!.size == 1)),
            "Session theme and results survive Activity recreation without disk storage",
        )
        description("Options")
        onMain {check(!compose!!.words().contains("Delete saved data"),"The Compose menu has no delete-saved-data action")}
        menuChoose("Settings")
        settingsClick("Privacy & device details")
        check(
            ((optionTexts())!!.contains("Session-only results") &&
                (optionTexts())!!.contains("no scan files")),
            "Privacy explains the session-only policy",
        )
        optionClick("Done")
        settingsClick("Clear current results")
        check((optionTexts())!!.contains("no saved history"), "Memory reset explains its scope")
        optionClick("Cancel")
        check(
            (((store.devices(live.key))!!.size == 1) && (store.dark() == !(system))),
            "Cancelling keeps the list and appearance",
        )
        settingsClick("Clear current results")
        optionClick("Clear results")
        check(
            (((store.devices(live.key))!!.isEmpty() && (store.report(live.key) === null)) &&
                (((field("devices") as List<*>?)))!!.isEmpty()),
            "Privacy reset empties both displayed and shared session results",
        )
        check(
            (((store.dark() == !(system)) && (field("range"))!!.equals((live.ip + "/32"))) &&
                (field("query"))!!.equals("")),
            "Clearing results preserves appearance and range, and clears search",
        )
        check(
            !(store.retainIfCurrent(token, live.key, Collections.singletonList(d), summary)),
            "Privacy reset fences late callbacks",
        )
        capture("session-only")
        token = store.token()
        check(
            store.retainIfCurrent(token, live.key, Collections.singletonList(d), summary),
            "New work can populate a reset session",
        )
        check(
            ((store.startScanIfCurrent(token) && (store.devices(live.key))!!.isEmpty()) &&
                (store.report(live.key) === null)),
            "Every new scan forgets the previous in-memory results",
        )
        check((store.dark() == !(system)), "Starting a new scan keeps the session appearance")
        store.clear()
        check(
            !(store.retainIfCurrent(token, live.key, Collections.singletonList(d), summary)),
            "A reset session rejects late results",
        )
        check(noLegacyFiles(c), "Reset creates no preference file")
        onMain(callback167@{ activity!!.finish() })
        waitForIdleSync()
    }

    private fun sessionCold(): Unit {
        var c: Context = getTargetContext()
        var store: Store = Store(c)
        var live: NetworkState = NetworkState.current(c)
        check(
            ((store.devices(live.key))!!.isEmpty() && (store.report(live.key) === null)),
            "A new process restores no scan data",
        )
        var system: Boolean =
            (((((((c.getApplicationContext())!!.getResources())!!.getConfiguration())!!.uiMode)
                .toInt() and (Configuration.UI_MODE_NIGHT_MASK).toInt())) ==
                Configuration.UI_MODE_NIGHT_YES)
        check((store.dark() == system), "A new process restores no appearance override")
        check(noLegacyFiles(c), "A new process creates no preference file")
    }



    private fun settingsClick(label: String) {compose!!.reveal(label,"settings");compose!!.click(label)}

    private fun settingsBack(): Unit {
        description(
            (if (((field("selected") as Device?) === null)) "Back to devices"
            else "Back to device details")
        )
        settle()
    }



    private fun waitForDialog(text: String): AccessibilityNodeInfo? {
        var end: Long = (SystemClock.uptimeMillis() + 5000)
        var root: AccessibilityNodeInfo? = null
        while ((SystemClock.uptimeMillis() < end)) {
            root = (getUiAutomation())!!.getRootInActiveWindow()
            if (
                ((root !== null) && !((root!!.findAccessibilityNodeInfosByText(text))!!.isEmpty()))
            ) {
                return root
            }
            SystemClock.sleep(100)
        }
        return root
    }





    private fun settle(): Unit {
        waitForIdleSync()
        SystemClock.sleep(350)
        waitForIdleSync()
    }















    private fun permissionFlow(): Unit {
        check((Build.VERSION.SDK_INT >= 37), "Android 17 permission test runs on API 37")
        var automation: android.accessibilityservice.AccessibilityServiceInfo =
            (getUiAutomation())!!.getServiceInfo()
        automation.flags =
            ((automation.flags).toInt() or
                (android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS)
                    .toInt())
        (getUiAutomation())!!.setServiceInfo(automation)
        var target: Context = getTargetContext()
        var permission: String = "android.permission.ACCESS_LOCAL_NETWORK"
        check(
            (target.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED),
            "Local-network permission starts denied",
        )
        activity = launch(false)
        waitForIdleSync()
        var live: NetworkState = NetworkState.current(target)
        onMain(callback210@{ setField("range", (live.ip + "/32")) })
        check(!((field("scanning") as Boolean)), "No scan on launch before consent")
        description("Options")
        menuChoose("Settings")
        check(
            ((texts())!!.contains("Not allowed") && !((field("scanning") as Boolean))),
            "Settings reports denied network permission without requesting a scan",
        )
        settingsBack()
        tapPermissionScan()
        osButton("permission_deny_button")
        waitForIdleSync()
        assertText("Local-network access is needed to find devices.")
        check(
            (target.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED),
            "Deny leaves permission denied",
        )
        check(!((field("scanning") as Boolean)), "Deny does not start discovery")
        capture("permission-denied")
        tapPermissionScan()
        osButton("permission_allow_button")
        var grantedAt: Long = SystemClock.uptimeMillis()
        var end: Long =
            (SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis((FULL_SCAN_SECONDS).toLong()))
        while ((SystemClock.uptimeMillis() < end)) {
            SystemClock.sleep(250)
            if (
                (((target.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) &&
                    !((field("scanning") as Boolean))) &&
                    ((Store(target).last((NetworkState.current(target))!!.key)).toLong() >
                        (0).toLong()))
            ) {
                break
            }
        }
        var diagnostic: Bundle = Bundle()
        diagnostic.putString(
            "stream",
            (((((("Permission scan diagnostic: elapsedMs=" +
                ((SystemClock.uptimeMillis() - grantedAt))) + ", initialConnectionMatches=") +
                live.key.equals((NetworkState.current(target))!!.key)) + ", scanning=") +
                field("scanning")) + "\n"),
        )
        sendStatus(0, diagnostic)
        check(
            (target.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED),
            "OS dialog grants local-network permission",
        )
        check(
            ((Store(target).last((NetworkState.current(target))!!.key)).toLong() > (0).toLong()),
            ((("Grant starts and saves user-requested scan; scanning=" + field("scanning")) +
                ", UI=") + texts()),
        )
        capture("permission-granted")
    }

    /** System permission UI requires a focused, visible window and a genuine input event. */
    private fun tapPermissionScan() {compose!!.tapTag("scan")}

    private fun osButton(id: String): Unit {
        var end: Long = (SystemClock.uptimeMillis() + 20000)
        while ((SystemClock.uptimeMillis() < end)) {
            var root: AccessibilityNodeInfo? = (getUiAutomation())!!.getRootInActiveWindow()
            if ((root !== null)) {
                var buttons: MutableList<AccessibilityNodeInfo> = ArrayList()
                for (pkg in
                    arrayOf(
                        "com.android.permissioncontroller",
                        "com.google.android.permissioncontroller",
                        "com.android.packageinstaller",
                        "com.google.android.packageinstaller",
                    )) {
                    buttons.addAll(root!!.findAccessibilityNodeInfosByViewId(((pkg + ":id/") + id)))
                    if (id.equals("permission_allow_button")) {
                        buttons.addAll(
                            root!!.findAccessibilityNodeInfosByViewId(
                                (pkg + ":id/permission_allow_foreground_only_button")
                            )
                        )
                    }
                    if (
                        ((Build.VERSION.SDK_INT < 29) &&
                            id.equals("permission_allow_foreground_only_button"))
                    ) {
                        buttons.addAll(
                            root!!.findAccessibilityNodeInfosByViewId(
                                (pkg + ":id/permission_allow_button")
                            )
                        )
                    }
                }
                if (buttons.isEmpty()) {
                    var labels: kotlin.Array<String> =
                        (if (id.equals("permission_deny_button"))
                            arrayOf("Don’t allow", "Don't allow", "Deny")
                        else
                            (if (id.equals("permission_allow_foreground_only_button"))
                                arrayOf("While using the app", "Allow only while using the app")
                            else arrayOf("Allow", "While using the app")))
                    for (label in labels) {
                        buttons.addAll(root!!.findAccessibilityNodeInfosByText(label))
                    }
                }
                for (button in buttons) {
                    var pkg: String = java.lang.String.valueOf(button.getPackageName())
                    if (
                        (((!(pkg.equals("com.android.permissioncontroller")) &&
                            !(pkg.equals("com.google.android.permissioncontroller"))) &&
                            !(pkg.equals("com.android.packageinstaller"))) &&
                            !(pkg.equals("com.google.android.packageinstaller")))
                    ) {
                        continue
                    }
                    var image: Bitmap? =
                        nullablePlatformResult((getUiAutomation())!!.takeScreenshot())
                    if ((image !== null)) {
                        save(image, ("os-" + id))
                    }
                    check(
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                        ("OS permission button: " + id),
                    )
                    SystemClock.sleep(500)
                    return
                }
            }
            SystemClock.sleep(250)
        }
        var failed: Bitmap? = nullablePlatformResult((getUiAutomation())!!.takeScreenshot())
        if ((failed !== null)) {
            save(failed, "os-permission-test-failure")
        }
        throw AssertionError(("OS permission button not found: " + id))
    }

    private fun onMain(task: Runnable): Unit {
        var error: kotlin.Array<Throwable?> = arrayOf(null)
        runOnMainSync(
            callback211@{
                try {
                    task.run()
                } catch (e: Throwable) {
                    error[0] = e
                }
            }
        )
        if ((error[0] !== null)) {
            throw AssertionError(error[0])
        }
    }

    private fun field(name: String): Any? {
        try {
            var f: Field = MainActivity::class.java.getDeclaredField(name)
            f.setAccessible(true)
            return f.get(activity)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    private fun setField(name: String, value: Any?): Unit {
        try {
            var f: Field = MainActivity::class.java.getDeclaredField(name)
            f.setAccessible(true)
            f.set(activity, value)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    private fun invoke(name: String): Unit {
        try {
            var m: Method = MainActivity::class.java.getDeclaredMethod(name)
            m.setAccessible(true)
            m.invoke(activity)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    private fun setSearch(value: String) { compose!!.search(value) }

    private fun capture(name: String): Unit {
        waitForIdleSync()
        SystemClock.sleep(300)
        var bitmap: kotlin.Array<Bitmap?> = arrayOfNulls<Bitmap>(1)
        onMain(
            callback213@{
                var v: View = (activity!!.window)!!.getDecorView()
                bitmap[0] = Bitmap.createBitmap(v.width, v.getHeight(), Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas((bitmap[0])!!))
            }
        )
        save((bitmap[0])!!, name)
    }

    private fun save(image: Bitmap, name: String): Unit {
        var directory: File =
            File(
                (getTargetContext())!!.getExternalFilesDir(null),
                ("previews-0.2.2-api" + Build.VERSION.SDK_INT),
            )
        directory.mkdirs()
        FileOutputStream(File(directory, (name + ".png"))).use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        image.recycle()
    }

    private fun assertText(text: String): Unit {
        check((texts())!!.contains(text), ("Missing UI text: " + text))
    }

    private fun texts(): String {
        var value=""
        onMain {value=compose!!.words().joinToString("\n")}
        return value
    }

    private fun click(text: String): Unit {
        activate(text, false)
    }

    private fun description(text: String): Unit {
        activate(text, true)
    }

    private fun activate(text: String,description: Boolean) {compose!!.click(text)}

    private fun find(view: View, text: String, desc: Boolean): View? {
        if (
            ((desc &&
                text.contentEquals(
                    (if ((view.getContentDescription() === null)) ""
                    else view.getContentDescription())
                )) ||
                ((!(desc) && (view is TextView)) &&
                    text.contentEquals((((view as TextView?)))!!.text)))
        ) {
            return view
        }
        if ((view is ViewGroup)) {
            run {
                var i: Int = 0
                while ((i < (((view as ViewGroup?)))!!.getChildCount())) {
                    var match: View? = find((((view as ViewGroup?)))!!.getChildAt(i), text, desc)
                    if ((match !== null)) {
                        return match
                    }
                    i++
                }
            }
        }
        return null
    }



    companion object {
        private var FULL_SCAN_SECONDS: Int = 65

        private fun wireName(out: DataOutputStream, labels: List<String>): Unit {
            for (label in labels) {
                var bytes: ByteArray = label.toByteArray(java.nio.charset.Charset.forName("UTF-8"))
                out.writeByte(bytes.size)
                out.write(bytes)
            }
            out.writeByte(0)
        }

        private fun wireRecord(
            out: DataOutputStream,
            owner: List<String>?,
            kind: Int,
            ttl: Long,
            value: ByteArray,
        ): Unit {
            wireName(out, (owner)!!)
            out.writeShort(kind)
            out.writeShort(32769)
            out.writeInt((ttl).toInt())
            out.writeShort(value.size)
            out.write(value)
        }

        private fun txtWire(
            instance: String,
            type: String,
            ttl: Long,
            vararg attributes: String,
        ): ByteArray {
            var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
            var out: DataOutputStream = DataOutputStream(bytes)
            out.writeShort(0)
            out.writeShort(33792)
            out.writeShort(0)
            out.writeShort(1)
            out.writeInt(0)
            var txt: ByteArrayOutputStream = ByteArrayOutputStream()
            for (attribute in attributes) {
                var value: ByteArray =
                    attribute.toByteArray(java.nio.charset.Charset.forName("UTF-8"))
                txt.write(value.size)
                txt.write(value)
            }
            var owner: MutableList<String>? = ArrayList()
            owner!!.add(instance)
            owner!!.addAll(
                Arrays.asList(*java.util.regex.Pattern.compile("\\.").split(((type + "local")), 0))
            )
            wireRecord(out, owner, 16, ttl, txt.toByteArray())
            return bytes.toByteArray()
        }

        private fun splitPacket(address: String, part: Int, type: String = "_networklens-fixture._tcp.local", label: String = "Split fixture"): ByteArray {
            var instance: String = (label + "." + type)
            var body: ByteArrayOutputStream = ByteArrayOutputStream()
            var payload: DataOutputStream = DataOutputStream(body)
            var owner: String? = null
            var kind: Int = 0
            if ((part == 0)) {
                owner = type
                kind = 12
                hkName(payload, instance)
            } else {
                if ((part == 1)) {
                    owner = instance
                    kind = 33
                    payload.writeInt(0)
                    payload.writeShort(43210)
                    hkName(payload, "split-fixture.local")
                } else {
                    if ((part == 2)) {
                        owner = instance
                        kind = 16
                        val fields = if (type == "_companion-link._tcp.local") arrayOf("rpVr=1")
                            else arrayOf("fn=Fresh split name", "md=Fresh split model")
                        for (field in fields) {
                            var v: ByteArray =
                                field.toByteArray(java.nio.charset.Charset.forName("UTF-8"))
                            payload.writeByte(v.size)
                            payload.write(v)
                        }
                    } else {
                        owner = "split-fixture.local"
                        var ip: InetAddress = InetAddress.getByName(address)
                        kind = (if ((ip is Inet4Address)) 1 else 28)
                        payload.write(ip.address)
                    }
                }
            }
            var packet: ByteArrayOutputStream = ByteArrayOutputStream()
            var out: DataOutputStream = DataOutputStream(packet)
            out.writeShort(0)
            out.writeShort(33792)
            out.writeShort(0)
            out.writeShort(1)
            out.writeInt(0)
            hkRr(out, owner, kind, body.toByteArray())
            return packet.toByteArray()
        }

        private fun hkName(out: DataOutputStream, value: String): Unit {
            for (label in java.util.regex.Pattern.compile("\\.").split(value, 0)) {
                var b: ByteArray = label.toByteArray(java.nio.charset.Charset.forName("UTF-8"))
                out.writeByte(b.size)
                out.write(b)
            }
            out.writeByte(0)
        }

        private fun hkPacket(address: String, type: String, complete: Boolean): ByteArray {
            var fq: String = (type + "local")
            var instance: String = ("HomeKit fixture." + fq)
            var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
            var out: DataOutputStream = DataOutputStream(bytes)
            out.writeShort(0)
            out.writeShort(33792)
            out.writeShort(0)
            out.writeShort((if (complete) 4 else 3))
            out.writeInt(0)
            var n: ByteArrayOutputStream = ByteArrayOutputStream()
            hkName(DataOutputStream(n), instance)
            hkRr(out, fq, 12, n.toByteArray())
            n.reset()
            var s: DataOutputStream = DataOutputStream(n)
            s.writeShort(0)
            s.writeShort(0)
            s.writeShort(51827)
            hkName(s, "homekit-fixture.local")
            hkRr(out, instance, 33, n.toByteArray())
            n.reset()
            for (field in
                arrayOf("md=Current HomeKit model", "ci=5", "sf=5", "id=AA:BB:CC:DD:EE:FF")) {
                var b: ByteArray = field.toByteArray(java.nio.charset.Charset.forName("US-ASCII"))
                n.write(b.size)
                n.write(b)
            }
            hkRr(out, instance, 16, n.toByteArray())
            if (complete) {
                var ip: InetAddress = InetAddress.getByName(address)
                hkRr(
                    out,
                    "homekit-fixture.local",
                    (if ((ip is Inet4Address)) 1 else 28),
                    ip.address,
                )
            }
            return bytes.toByteArray()
        }

        private fun hkRr(out: DataOutputStream, owner: String?, type: Int, bytes: ByteArray): Unit {
            hkName(out, (owner)!!)
            out.writeShort(type)
            out.writeShort(32769)
            out.writeInt(120)
            out.writeShort(bytes.size)
            out.write(bytes)
        }

        private fun wsReply(
            discovery: String,
            addressing: String,
            id: String,
            peer: String,
            name: String,
        ): String {
            return (((((((((((("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:a='" +
                addressing) + "' xmlns:d='") + discovery) + "'><s:Header><a:Action>") + discovery) +
                "/ProbeMatches</a:Action><a:RelatesTo>") + id) +
                "</a:RelatesTo></s:Header><s:Body><d:ProbeMatches><d:ProbeMatch><a:EndpointReference><a:Address>urn:uuid:eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee</a:Address></a:EndpointReference><d:Types xmlns:p='http://schemas.microsoft.com/windows/2006/08/wdp/print'>p:PrintDeviceType</d:Types><d:Scopes>onvif://www.onvif.org/name/") +
                name) +
                " onvif://www.onvif.org/hardware/Fixture%20hardware onvif://www.onvif.org/MacAddress/00-11-22-33-44-55 onvif://www.onvif.org/location/private</d:Scopes><d:XAddrs>http://") +
                peer) +
                ":8899/device</d:XAddrs><d:MetadataVersion>1</d:MetadataVersion></d:ProbeMatch></d:ProbeMatches></s:Body></s:Envelope>")
        }
    }
}
