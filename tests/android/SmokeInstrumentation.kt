@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

package dev.netlens.test

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
import dev.netlens.*
import java.io.*
import java.lang.reflect.*
import java.net.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import org.json.*

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

    override fun onStart(): Unit {
        var result: Bundle = Bundle()
        try {
            // Deterministic emulator fixtures; production deletion preserves appearance.
            var mode: String =
                (if ((arguments === null)) "full" else arguments!!.getString("mode", "full"))
            if ((!(mode.startsWith("session-")) && !(mode.equals("legacy-seed")))) {
                Store(getTargetContext()).dark(false)
            }
            if (mode.equals("failure-reporting")) {
                failureReporting()
            } else {
                if (mode.equals("routed-ipv6")) {
                    routedIpv6()
                } else {
                    if (mode.equals("freshness-fixes")) {
                        freshnessFixes()
                    } else {
                        if (mode.equals("settings-scroll")) {
                            settingsScrollRecreation()
                        } else {
                            if (mode.equals("ipv6-feasibility")) {
                                ipv6Feasibility()
                            } else {
                                if (mode.equals("split-mdns")) {
                                    splitMdns()
                                } else {
                                    if (mode.equals("homekit")) {
                                        homeKit()
                                    } else {
                                        if (mode.equals("branding")) {
                                            branding()
                                        } else {
                                            if (mode.equals("legacy-seed")) {
                                                legacySeed()
                                            } else {
                                                if (mode.equals("session-only")) {
                                                    sessionOnly()
                                                } else {
                                                    if (mode.equals("session-cold")) {
                                                        sessionCold()
                                                    } else {
                                                        if (
                                                            ((arguments !== null) &&
                                                                "fresh-presence"
                                                                    .equals(
                                                                        arguments!!.getString(
                                                                            "mode"
                                                                        )
                                                                    ))
                                                        ) {
                                                            freshPresence()
                                                        } else {
                                                            if (
                                                                ((arguments !== null) &&
                                                                    "mac-vendors"
                                                                        .equals(
                                                                            arguments!!.getString(
                                                                                "mode"
                                                                            )
                                                                        ))
                                                            ) {
                                                                macVendors()
                                                            } else {
                                                                if (
                                                                    ((arguments !== null) &&
                                                                        "service-retries"
                                                                            .equals(
                                                                                arguments!!
                                                                                    .getString(
                                                                                        "mode"
                                                                                    )
                                                                            ))
                                                                ) {
                                                                    serviceRetries()
                                                                } else {
                                                                    if (
                                                                        ((arguments !== null) &&
                                                                            "details-upgrade"
                                                                                .equals(
                                                                                    arguments!!
                                                                                        .getString(
                                                                                            "mode"
                                                                                        )
                                                                                ))
                                                                    ) {
                                                                        detailsUpgrade()
                                                                    } else {
                                                                        if (
                                                                            ((arguments !== null) &&
                                                                                "resolver-upgrade"
                                                                                    .equals(
                                                                                        arguments!!
                                                                                            .getString(
                                                                                                "mode"
                                                                                            )
                                                                                    ))
                                                                        ) {
                                                                            resolverUpgrade()
                                                                        } else {
                                                                            if (
                                                                                ((arguments !==
                                                                                    null) &&
                                                                                    "engine-upgrade"
                                                                                        .equals(
                                                                                            arguments!!
                                                                                                .getString(
                                                                                                    "mode"
                                                                                                )
                                                                                        ))
                                                                            ) {
                                                                                engineUpgrade()
                                                                            } else {
                                                                                if (
                                                                                    ((arguments !==
                                                                                        null) &&
                                                                                        "icmp-denied"
                                                                                            .equals(
                                                                                                arguments!!
                                                                                                    .getString(
                                                                                                        "mode"
                                                                                                    )
                                                                                            ))
                                                                                ) {
                                                                                    icmpDeniedFlow()
                                                                                } else {
                                                                                    if (
                                                                                        ((arguments !==
                                                                                            null) &&
                                                                                            "icmp"
                                                                                                .equals(
                                                                                                    arguments!!
                                                                                                        .getString(
                                                                                                            "mode"
                                                                                                        )
                                                                                                ))
                                                                                    ) {
                                                                                        icmpFlow()
                                                                                    } else {
                                                                                        if (
                                                                                            ((arguments !==
                                                                                                null) &&
                                                                                                "performance"
                                                                                                    .equals(
                                                                                                        arguments!!
                                                                                                            .getString(
                                                                                                                "mode"
                                                                                                            )
                                                                                                    ))
                                                                                        ) {
                                                                                            performance()
                                                                                        } else {
                                                                                            if (
                                                                                                ((arguments !==
                                                                                                    null) &&
                                                                                                    "confirmed-fixes"
                                                                                                        .equals(
                                                                                                            arguments!!
                                                                                                                .getString(
                                                                                                                    "mode"
                                                                                                                )
                                                                                                        ))
                                                                                            ) {
                                                                                                confirmedFixes()
                                                                                            } else {
                                                                                                if (
                                                                                                    ((arguments !==
                                                                                                        null) &&
                                                                                                        "lifecycle"
                                                                                                            .equals(
                                                                                                                arguments!!
                                                                                                                    .getString(
                                                                                                                        "mode"
                                                                                                                    )
                                                                                                            ))
                                                                                                ) {
                                                                                                    lifecycleFlow()
                                                                                                } else {
                                                                                                    if (
                                                                                                        ((arguments !==
                                                                                                            null) &&
                                                                                                            "options"
                                                                                                                .equals(
                                                                                                                    arguments!!
                                                                                                                        .getString(
                                                                                                                            "mode"
                                                                                                                        )
                                                                                                                ))
                                                                                                    ) {
                                                                                                        optionsFlow()
                                                                                                    } else {
                                                                                                        if (
                                                                                                            ((arguments !==
                                                                                                                null) &&
                                                                                                                "scan-details"
                                                                                                                    .equals(
                                                                                                                        arguments!!
                                                                                                                            .getString(
                                                                                                                                "mode"
                                                                                                                            )
                                                                                                                    ))
                                                                                                        ) {
                                                                                                            scanDetailsFlow()
                                                                                                        } else {
                                                                                                            if (
                                                                                                                ((arguments !==
                                                                                                                    null) &&
                                                                                                                    "ws-discovery"
                                                                                                                        .equals(
                                                                                                                            arguments!!
                                                                                                                                .getString(
                                                                                                                                    "mode"
                                                                                                                                )
                                                                                                                        ))
                                                                                                            ) {
                                                                                                                wsDiscovery()
                                                                                                            } else {
                                                                                                                if (
                                                                                                                    ((arguments !==
                                                                                                                        null) &&
                                                                                                                        "discovery-upgrade"
                                                                                                                            .equals(
                                                                                                                                arguments!!
                                                                                                                                    .getString(
                                                                                                                                        "mode"
                                                                                                                                    )
                                                                                                                            ))
                                                                                                                ) {
                                                                                                                    discoveryUpgrade()
                                                                                                                } else {
                                                                                                                    if (
                                                                                                                        ((arguments !==
                                                                                                                            null) &&
                                                                                                                            "navigation"
                                                                                                                                .equals(
                                                                                                                                    arguments!!
                                                                                                                                        .getString(
                                                                                                                                            "mode"
                                                                                                                                        )
                                                                                                                                ))
                                                                                                                    ) {
                                                                                                                        navigation()
                                                                                                                    } else {
                                                                                                                        if (
                                                                                                                            ((arguments !==
                                                                                                                                null) &&
                                                                                                                                "wifi-name"
                                                                                                                                    .equals(
                                                                                                                                        arguments!!
                                                                                                                                            .getString(
                                                                                                                                                "mode"
                                                                                                                                            )
                                                                                                                                    ))
                                                                                                                        ) {
                                                                                                                            wifiNameFlow()
                                                                                                                        } else {
                                                                                                                            if (
                                                                                                                                ((arguments !==
                                                                                                                                    null) &&
                                                                                                                                    "permission"
                                                                                                                                        .equals(
                                                                                                                                            arguments!!
                                                                                                                                                .getString(
                                                                                                                                                    "mode"
                                                                                                                                                )
                                                                                                                                        ))
                                                                                                                            ) {
                                                                                                                                permissionFlow()
                                                                                                                            } else {
                                                                                                                                if (
                                                                                                                                    ((arguments !==
                                                                                                                                        null) &&
                                                                                                                                        "identification"
                                                                                                                                            .equals(
                                                                                                                                                arguments!!
                                                                                                                                                    .getString(
                                                                                                                                                        "mode"
                                                                                                                                                    )
                                                                                                                                            ))
                                                                                                                                ) {
                                                                                                                                    identification()
                                                                                                                                } else {
                                                                                                                                    if (
                                                                                                                                        ((arguments !==
                                                                                                                                            null) &&
                                                                                                                                            "grouping"
                                                                                                                                                .equals(
                                                                                                                                                    arguments!!
                                                                                                                                                        .getString(
                                                                                                                                                            "mode"
                                                                                                                                                        )
                                                                                                                                                ))
                                                                                                                                    ) {
                                                                                                                                        groupingStatus()
                                                                                                                                    } else {
                                                                                                                                        full()
                                                                                                                                    }
                                                                                                                                }
                                                                                                                            }
                                                                                                                        }
                                                                                                                    }
                                                                                                                }
                                                                                                            }
                                                                                                        }
                                                                                                    }
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            result.putString(
                "stream",
                (((((("PASS · " + checks) + " Android checks · ") +
                    ((if ((arguments === null)) "full"
                    else arguments!!.getString("mode", "full")))) + " · API ") +
                    Build.VERSION.SDK_INT) + "\n"),
            )
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString(
                "stream",
                ((("FAIL: " + e) + "\n") + android.util.Log.getStackTraceString(e)),
            )
            finish(Activity.RESULT_CANCELED, result)
        }
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

    private fun recoveryReachable(label: String): Unit {
        onMain(
            callback2@{
                var control: View? = find((activity!!.window)!!.getDecorView(), label, false)
                check((control !== null), ("Recovery action exists: " + label))
                (control)!!.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, (control)!!.width, (control)!!.getHeight()),
                    true,
                )
            }
        )
        settle()
        onMain(
            callback3@{
                var control: View? = find((activity!!.window)!!.getDecorView(), label, false)
                var visible: android.graphics.Rect = android.graphics.Rect()
                check(
                    (((control)!!.getGlobalVisibleRect(visible) &&
                        (visible.height() == (control)!!.getHeight())) &&
                        (((control)!!.getHeight()).toFloat() >=
                            ((48 * ((activity!!.getResources())!!.getDisplayMetrics())!!.density))
                                .toFloat())),
                    ("Recovery action remains fully reachable: " + label),
                )
            }
        )
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
        var manager: android.net.ConnectivityManager? =
            (target.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager?)
        var original: android.net.LinkProperties? = (manager)!!.getLinkProperties(live.network)
        var base: android.net.LinkProperties = copyProperties((original)!!)
        var links: MutableList<android.net.LinkAddress> = ArrayList((original)!!.getLinkAddresses())
        links.removeIf(callback4@{ a -> (a.address is Inet6Address) })
        links.add(fixtureAddress((original)!!, "fd55:1234::10", 64))
        base.setLinkAddresses(links)
        var before: NetworkState = networkFixture(target, (live.network)!!, base)
        var rotated: android.net.LinkProperties = copyProperties(base)
        var changed: MutableList<android.net.LinkAddress> = ArrayList(links)
        changed.removeIf(callback5@{ a -> (a.address is Inet6Address) })
        changed.add(fixtureAddress((original)!!, "fd55:1234::99", 64))
        rotated.setLinkAddresses(changed)
        rotated.setDnsServers(Collections.singletonList(InetAddress.getByName("192.0.2.53")))
        var after: NetworkState = networkFixture(target, (live.network)!!, rotated)
        check(
            ((before)!!.sameConnection(after) && (before)!!.sameScope(after)),
            "DNS updates and privacy-address rotation retain connection and normalized prefixes",
        )
        var route: android.net.LinkProperties = copyProperties(rotated, false)
        var routed: NetworkState = networkFixture(target, (live.network)!!, route)
        check(
            ((after)!!.sameConnection(routed) && (after)!!.sameScope(routed)),
            "IPv6 route metadata alone does not erase direct-link results",
        )
        check(
            !((routed)!!.local(InetAddress.getByName("fd77::20"))),
            "Ignoring route churn does not expand scan scope",
        )
        var added: android.net.LinkProperties = copyProperties(rotated)
        var extra: MutableList<android.net.LinkAddress> = ArrayList(changed)
        extra.add(fixtureAddress((original)!!, "fd66::10", 64))
        added.setLinkAddresses(extra)
        var expanded: NetworkState = networkFixture(target, (live.network)!!, added)
        check(
            (((after)!!.retainsScope(expanded) && (after)!!.refresh((expanded)!!)) &&
                (after)!!.local(InetAddress.getByName("fd66::20"))),
            "A new IPv6 prefix updates the atomic admission snapshot",
        )
        var withdrawn: NetworkState = networkFixture(target, (live.network)!!, rotated)
        check(
            (((after)!!.sameConnection(withdrawn) && !((after)!!.retainsScope(withdrawn))) &&
                !((after)!!.refresh((withdrawn)!!))),
            "Prefix withdrawal cannot silently continue obsolete work",
        )
        var v4: Device = Device()
        v4.ip = live.gateway
        v4.name = "Unchanged IPv4 fixture"
        var v6: Device = Device()
        v6.ip = "fd66::20"
        v6.name = "Withdrawn IPv6 fixture"
        var oldSelf: Device = Device()
        oldSelf.ip = "fd55:1234::10"
        oldSelf.self = true
        check(
            !((withdrawn)!!.contains(oldSelf)),
            "Retired phone privacy address is excluded despite a matching subnet",
        )
        store.retainIfCurrent(store.token(), (before)!!.key, Arrays.asList(v4, v6), true)
        activity = launch(false)
        settle()
        onMain(callback6@{ invoke("stopWatchingConnection") })
        onMain(
            callback7@{
                setField("network", before)
                setField("devices", Collections.singletonList(v4))
                setField("range", (live.ip + "/32"))
                setField("query", "")
            }
        )
        var running: Discovery = Discovery(target, (before)!!)
        onMain(
            callback8@{
                setField("discovery", running)
                setField("scanning", true)
            }
        )
        reconcileFixture(networkFixture(target, (live.network)!!, rotated))
        check(
            (((field("scanning") as Boolean) && (field("discovery") === running)) &&
                ((((field("devices") as List<Device>?)))!!.size == 1)),
            "Harmless address changes preserve the active operation and device list",
        )
        check(
            (field("range"))!!.equals((live.ip + "/32")),
            "Harmless changes preserve the session scan range",
        )
        running.cancel()
        onMain(
            callback9@{
                setField("discovery", null)
                setField("scanning", false)
                setField("network", expanded)
                setField("devices", Arrays.asList(v4, v6))
            }
        )
        store.retainIfCurrent(store.token(), (expanded)!!.key, Arrays.asList(v4, v6), true)
        var oldToken: Long = store.token()
        var obsolete: Discovery = Discovery(target, (expanded)!!)
        onMain(
            callback10@{
                setField("discovery", obsolete)
                setField("scanning", true)
            }
        )
        reconcileFixture(withdrawn)
        check(
            (!((field("scanning") as Boolean)) && (field("discovery") === null)),
            "Withdrawal cancels the affected scan",
        )
        check(
            (((((field("devices") as List<Device>?)))!!.size == 1) &&
                ((((field("devices") as List<Device>?)))!!.get(0))!!.name.equals(v4.name)),
            "Withdrawal removes affected IPv6 results while preserving IPv4 responses",
        )
        check(
            (!(store.isCurrent(oldToken)) &&
                !(store.retainIfCurrent(
                    oldToken,
                    (expanded)!!.key,
                    Collections.singletonList(v6),
                    true,
                ))),
            "Obsolete callbacks cannot restore removed addresses",
        )
        check(
            (!((store.report((withdrawn)!!.key))!!.complete) &&
                (store.report((withdrawn)!!.key))!!.reason.contains("IPv6")),
            "Withdrawal leaves an honest Partial report",
        )
        var replaced: android.net.LinkProperties = copyProperties(rotated)
        replaced.setInterfaceName((live.iface + "-fixture"))
        var other: NetworkState = networkFixture(target, (live.network)!!, replaced)
        onMain(
            callback11@{
                setField("query", "Old filter")
                setField("range", (live.ip + "/32"))
                setField("message", "Previous scan fixture")
            }
        )
        reconcileFixture(other)
        check(
            ((((((field("devices") as List<Device>?)))!!.isEmpty() &&
                (field("range"))!!.equals("")) && (field("query"))!!.equals("")) &&
                (field("message"))!!.equals("")),
            "A genuine attachment replacement clears results, range, filter and status",
        )
        onMain(
            callback12@{
                setField("range", "192.0.2.0/24")
                setField("message", "Old background summary")
                invoke("loadNetwork")
                invoke("render")
            }
        )
        check(
            (((field("range"))!!.equals("") && (field("message"))!!.equals("")) &&
                (((field("network") as NetworkState?)))!!.sameConnection(
                    NetworkState.current(target)
                )),
            "Resume-time reconciliation resets old-network controls before callbacks",
        )
        reconcileFixture(null)
        onMain(callback13@{ setField("message", "Connect to Wi-Fi to scan for devices.") })
        reconcileFixture(live)
        check(
            ((field("message"))!!.equals("") &&
                (((field("devices") as List<Device>?)))!!.isEmpty()),
            "Reconnect clears the no-Wi-Fi error without restoring results",
        )
        check(
            (!((field("scanning") as Boolean)) && (field("discovery") === null)),
            "Reconnect after a failed scan attempt never starts discovery automatically",
        )
        // Copied selections and queued publications must obey the latest ownership snapshot.
        var selectedBefore: NetworkState = networkFixture(target, (live.network)!!, base)
        var selectedAfter: NetworkState = networkFixture(target, (live.network)!!, rotated)
        var phone4: Device = Device()
        phone4.ip = live.ip
        phone4.self = true
        phone4.name = "Grouped phone fixture"
        var phone6: Device = Device()
        phone6.ip = "fd55:1234::10"
        phone6.self = true
        phone6.name = phone4.name
        var oldPhone: List<Device> = Arrays.asList(phone4, phone6)
        store.retainIfCurrent(store.token(), (selectedBefore)!!.key, oldPhone, true)
        onMain(
            callback14@{
                setField("network", selectedBefore)
                setField("devices", oldPhone)
                setField("selected", (Device.grouped(oldPhone))!!.get(0))
            }
        )
        reconcileFixture(selectedAfter)
        var keptSelection: Device? = (field("selected") as Device?)
        check(
            (((keptSelection !== null) && ((keptSelection.endpoints())!!.size == 1)) &&
                keptSelection.ip.equals(live.ip)),
            "Grouped detail selection drops the retired IPv6 member",
        )
        onMain(
            callback15@{
                try {
                    var copy: Method =
                        (activity!!.javaClass)!!.getDeclaredMethod("deviceText", Device::class.java)
                    copy.setAccessible(true)
                    check(
                        !((((copy.invoke(activity, field("selected")) as String?)))!!.contains(
                            phone6.ip
                        )),
                        "Copied grouped details exclude the retired member",
                    )
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            }
        )
        check(
            !((texts())!!.contains(phone6.ip)),
            "Rendered grouped details exclude the retired member",
        )
        var updateType: Class<*> = Class.forName("dev.netlens.MainActivity\$ScanUpdate")
        var update: Constructor<*> =
            updateType.getDeclaredConstructor(
                Discovery::class.java,
                Long::class.javaPrimitiveType!!,
                Long::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                List::class.java,
                Boolean::class.javaPrimitiveType!!,
                String::class.java,
            )
        update.setAccessible(true)
        var groups: Field? = updateType.getDeclaredField("groups")
        groups!!.setAccessible(true)
        for (terminal in booleanArrayOf(false, true)) {
            var pendingBefore: NetworkState = networkFixture(target, (live.network)!!, base)
            var pendingAfter: NetworkState = networkFixture(target, (live.network)!!, rotated)
            var pending: Discovery = Discovery(target, (pendingBefore)!!)
            var generation: Long = store.token()
            store.retainIfCurrent(generation, (pendingBefore)!!.key, oldPhone, true)
            var publication: Any =
                update.newInstance(
                    pending,
                    generation,
                    (((field("displaySequence") as Long)) + 1),
                    1,
                    1,
                    oldPhone,
                    terminal,
                    "",
                )
            groups!!.set(publication, Device.grouped(oldPhone))
            onMain(
                callback16@{
                    setField("selected", null)
                    setField("network", pendingBefore)
                    setField("discovery", pending)
                    setField("scanning", true)
                    setField("devices", oldPhone)
                }
            )
            reconcileFixture(pendingAfter)
            // Model a worker's final write occurring after reconciliation but before its terminal
            // callback.
            if (terminal) {
                store.retainIfCurrent(generation, (pendingBefore)!!.key, oldPhone, true)
            }
            onMain(
                callback17@{
                    setField("pendingDisplay", publication)
                    invoke("displayScan")
                }
            )
            settle()
            var published: List<Device>? = (field("devices") as List<Device>?)
            check(
                (((published)!!.size == 1) && ((published)!!.get(0))!!.ip.equals(live.ip)),
                ("Queued publication cannot restore a retired phone address: terminal=" + terminal),
            )
            check(
                ((store.devices((pendingAfter)!!.key))!!.size == 1),
                ("Retained final inventory also excludes the retired address: terminal=" +
                    terminal),
            )
            pending.cancel()
        }
        store.retainIfCurrent(store.token(), (expanded)!!.key, Arrays.asList(v4, v6), true)
        store.observeConnection(expanded)
        onMain(
            callback18@{
                setField("discovery", null)
                setField("scanning", false)
                setField("network", expanded)
                setField("devices", Arrays.asList(v4, v6))
                setField("selected", v6)
                setField("showingSettings", true)
                invoke("render")
            }
        )
        var restoredMonitor: ActivityMonitor = addMonitor("dev.netlens.MainActivity", null, false)
        onMain(callback19@{ activity!!.recreate() })
        var restored: Activity? =
            nullablePlatformResult(waitForMonitorWithTimeout(restoredMonitor, 8000))
        removeMonitor(restoredMonitor)
        check((restored !== null), "Android recreates Settings with a selected withdrawn endpoint")
        activity = restored
        settle()
        check(
            (field("selected") === null),
            "Retained Settings selection cannot restore a withdrawn endpoint",
        )
        description("Back to devices")
        check(!((texts())!!.contains(v6.ip)), "Back after recreation excludes the withdrawn device")
        onMain(callback20@{ activity!!.finish() })
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
        var monitor: ActivityMonitor = addMonitor("dev.netlens.MainActivity", null, false)
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

    class ServiceCacheContext : ContextWrapper {
        var cache: Any = Any()

        var entered: CountDownLatch? = CountDownLatch(1)
        var release: CountDownLatch? = CountDownLatch(1)

        var rootNsd: AtomicInteger = AtomicInteger()
        var isolatedNsd: AtomicInteger = AtomicInteger()
        var contexts: AtomicInteger = AtomicInteger()

        constructor(base: Context) : super(base) {}

        override fun getApplicationContext(): Context {
            return this
        }

        private fun waitForNsd(): Any? {
            entered!!.countDown()
            try {
                release!!.await()
            } catch (e: InterruptedException) {
                (Thread.currentThread())!!.interrupt()
            }
            return null
        }

        override fun getSystemService(name: String): Any? {
            synchronized((cache)) {
                if (Context.NSD_SERVICE.equals(name)) {
                    rootNsd.incrementAndGet()
                    return waitForNsd()
                }
                return super.getSystemService(name)
            }
        }

        override fun createConfigurationContext(configuration: Configuration): Context {
            contexts.incrementAndGet()
            var isolated: Context = super.createConfigurationContext(configuration)
            return object : ContextWrapper(isolated) {
                var ownCache: Any = Any()

                override fun getSystemService(name: String): Any? {
                    synchronized((ownCache)) {
                        if (Context.NSD_SERVICE.equals(name)) {
                            isolatedNsd.incrementAndGet()
                            return waitForNsd()
                        }
                        return super.getSystemService(name)
                    }
                }
            }
        }
    }

    private fun isolatedServiceCache(target: Context, live: NetworkState): Unit {
        var context: ServiceCacheContext = ServiceCacheContext(target)
        var scan: Discovery = Discovery(context, live)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        accepting.setAccessible(true)
        accepting.set(scan, true)
        var start: Method = Discovery::class.java.getDeclaredMethod("startNsd")
        start.setAccessible(true)
        var lookups: ExecutorService = Executors.newSingleThreadExecutor()
        try {
            var began: Long = SystemClock.uptimeMillis()
            start.invoke(scan)
            check(
                ((((SystemClock.uptimeMillis() - began)).toLong() < (2500).toLong()) &&
                    context.entered!!.await(100, TimeUnit.MILLISECONDS)),
                "Stalled OS discovery constructor retains its bounded caller wait",
            )
            check(
                (((context.contexts.get() == 1) && (context.isolatedNsd.get() == 1)) &&
                    (context.rootNsd.get() == 0)),
                "NSD construction uses its own configuration Context cache",
            )
            var lookup: Future<Any?> =
                lookups.submit(
                    Callable<Any?>(
                        callback27@{ context.getSystemService(Context.CONNECTIVITY_SERVICE) }
                    )
                )
            check(
                (lookup.get(500, TimeUnit.MILLISECONDS) !== null),
                "Other application service lookups remain usable while NSD construction is stalled",
            )
            var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
            types.setAccessible(true)
            check(
                (((types.get(scan) as Set<*>?)))!!.contains("_hap._udp."),
                "The native-service failure leaves raw accessory queries admitted",
            )
        } finally {
            context.release!!.countDown()
            scan.cancel()
            lookups.shutdownNow()
        }
    }

    private fun icmpPeerReceivePath(live: NetworkState, own: InetAddress): Unit {
        var channelType: Class<*> = Class.forName("dev.netlens.AndroidIcmp\$SocketChannel")
        var constructor: Constructor<*> =
            channelType.getDeclaredConstructor(
                java.io.FileDescriptor::class.java,
                Int::class.javaPrimitiveType!!,
                InetAddress::class.java,
            )
        constructor.setAccessible(true)
        for (matches in booleanArrayOf(false, true)) {
            var fd: java.io.FileDescriptor? =
                android.system.Os.socket(
                    android.system.OsConstants.AF_INET6,
                    android.system.OsConstants.SOCK_DGRAM,
                    android.system.OsConstants.IPPROTO_UDP,
                )
            var channel: IcmpEcho.Channel? = null
            try {
                (live.network)!!.bindSocket(fd)
                android.system.Os.bind(fd, own, 0)
                android.system.Os.fcntlInt(
                    (fd)!!,
                    android.system.OsConstants.F_SETFL,
                    ((android.system.Os.fcntlInt((fd)!!, android.system.OsConstants.F_GETFL, 0))
                        .toInt() or (android.system.OsConstants.O_NONBLOCK).toInt()),
                )
                var receiver: InetSocketAddress? =
                    (android.system.Os.getsockname(fd) as InetSocketAddress?)
                channel =
                    (constructor.newInstance(
                        fd,
                        123,
                        (if (matches) own else InetAddress.getByName("fd70::dead")),
                    ) as IcmpEcho.Channel?)
                DatagramSocket(null).use { sender ->
                    sender.bind(InetSocketAddress(own, 0))
                    (live.network)!!.bindSocket(sender)
                    var marker: ByteArray = byteArrayOf(11, 22, 33, 44)
                    sender.send(DatagramPacket(marker, marker.size, receiver))
                    var received: ByteArray? = channel!!.receive(1000)
                    check(
                        ((received !== null) &&
                            ((if (matches) Arrays.equals(received, marker)
                            else (received.size == 0)))),
                        (if (matches) "Actual recvfrom source extraction accepts the expected peer"
                        else
                            "Actual recvfrom path discards a different source before echo parsing"),
                    )
                }
            } finally {
                if ((channel !== null)) {
                    channel!!.close()
                } else {
                    android.system.Os.close(fd)
                }
            }
        }
    }

    private fun ipv6LateTcpFallback(
        target: Context,
        live: NetworkState,
        own: InetAddress,
        afterEcho: Boolean,
    ): Unit {
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var enriching: Field = Discovery::class.java.getDeclaredField("enriching")
        accepting.setAccessible(true)
        enriching.setAccessible(true)
        var resolve: Method =
            Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
        resolve.setAccessible(true)
        var echoes: AtomicInteger = AtomicInteger()
        var connections: AtomicInteger = AtomicInteger()
        var read: CountDownLatch = CountDownLatch(1)
        var echo: V6FixtureChannel = V6FixtureChannel(false, read, null, echoes)
        var scan: Discovery = Discovery(target, live, callback28@{ n, a -> echo })
        var serverWork: ExecutorService = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 4, own).use { server ->
                server.setSoTimeout(7000)
                var accepted: Future<*> =
                    serverWork.submit(
                        callback29@{
                            try {
                                run {
                                    var n: Int = 0
                                    while ((n < 2)) {
                                        server.accept().use { client ->
                                            connections.incrementAndGet()
                                        }
                                        n++
                                    }
                                }
                            } catch (error: IOException) {
                                throw RuntimeException(error)
                            }
                        }
                    )
                accepting.set(scan, true)
                enriching.set(scan, true)
                var udp: NsdServiceInfo = NsdServiceInfo()
                udp.setServiceName("Late TCP fixture")
                udp.setServiceType("_hap._udp.")
                udp.setHost(own)
                udp.setPort(server.getLocalPort())
                if ((Build.VERSION.SDK_INT >= 34)) {
                    udp.setHostAddresses(Collections.singletonList(own))
                }
                resolve.invoke(scan, udp)
                check(
                    read.await(2, TimeUnit.SECONDS),
                    "UDP advertisement starts the IPv6 echo before a TCP port is known",
                )
                if (afterEcho) {
                    var end: Long = (SystemClock.uptimeMillis() + 5500)
                    while (((SystemClock.uptimeMillis() < end) && !(echo.closed))) {
                        SystemClock.sleep(20)
                    }
                    check(echo.closed, "Echo completed before the later TCP advertisement")
                }
                var tcp: NsdServiceInfo = NsdServiceInfo()
                tcp.setServiceName("Late TCP fixture")
                tcp.setServiceType("_http._tcp.")
                tcp.setHost(own)
                tcp.setPort(server.getLocalPort())
                if ((Build.VERSION.SDK_INT >= 34)) {
                    tcp.setHostAddresses(Collections.singletonList(own))
                }
                resolve.invoke(scan, tcp)
                accepted.get(8, TimeUnit.SECONDS)
                var until: Long = (SystemClock.uptimeMillis() + 2000)
                while (
                    ((SystemClock.uptimeMillis() < until) &&
                        ((scan.snapshot())!!.stream())!!.noneMatch(
                            callback30@{ d -> d.ip.equals(own.getHostAddress()) }
                        ))
                ) {
                    SystemClock.sleep(20)
                }
                check(
                    ((echoes.get() == 2) && (connections.get() == 2)),
                    "UDP-first discovery retains a later TCP port through echo timeout",
                )
                check(
                    ((scan.snapshot())!!.stream())!!.anyMatch(
                        callback31@{ d -> d.ip.equals(own.getHostAddress()) }
                    ),
                    "The twice-confirmed direct IPv6 TCP endpoint remains discoverable",
                )
            }
        } finally {
            scan.cancel()
            serverWork.shutdownNow()
        }
    }

    private fun routedFixture(
        prefix: String,
        length: Int,
        gateway: String,
        iface: String,
        type: Int,
    ): android.net.RouteInfo {
        var parcel: Parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(android.net.IpPrefix(InetAddress.getByName(prefix), length), 0)
            parcel.writeByteArray((InetAddress.getByName(gateway))!!.address)
            parcel.writeString(iface)
            parcel.writeInt(type)
            parcel.writeInt(0)
            parcel.setDataPosition(0)
            var route: android.net.RouteInfo =
                android.net.RouteInfo.CREATOR.createFromParcel(parcel)
            check(
                (((route.destination)!!.getPrefixLength() == length) &&
                    (route.getInterface())!!.equals(iface)),
                "Route fixture decoded by this Android version",
            )
            return route
        } finally {
            parcel.recycle()
        }
    }

    class V6FixtureChannel : IcmpEcho.Channel {
        var sent: ByteArray? = null

        var reply: Boolean = false

        var entered: CountDownLatch? = null
        var release: CountDownLatch? = null

        var closed: Boolean = false

        var requests: AtomicInteger? = null

        constructor(
            answer: Boolean,
            entered: CountDownLatch?,
            release: CountDownLatch?,
            requests: AtomicInteger?,
        ) {
            reply = answer
            this.entered = entered
            this.release = release
            this.requests = requests
        }

        override fun identifier(): Int {
            return 23123
        }

        override fun send(packet: ByteArray): Unit {
            sent = packet.clone()
            requests!!.incrementAndGet()
        }

        override fun receive(wait: Int): ByteArray? {
            try {
                if ((entered !== null)) {
                    entered!!.countDown()
                }
                if (
                    ((release !== null) &&
                        !(release!!.await((wait).toLong(), TimeUnit.MILLISECONDS)))
                ) {
                    return null
                }
                if (!(reply)) {
                    Thread.sleep(wait.toLong())
                    return null
                }
                var packet: ByteArray = sent!!.clone()
                packet[0] = (129).toByte()
                return packet
            } catch (e: InterruptedException) {
                (Thread.currentThread())!!.interrupt()
                throw IOException(e)
            }
        }

        override fun close(): Unit {
            closed = true
        }
    }

    private fun routedIpv6(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        live.check(target)
        isolatedServiceCache(target, live)
        var manager: android.net.ConnectivityManager? =
            (target.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager?)
        var original: android.net.LinkProperties? = (manager)!!.getLinkProperties(live.network)
        var properties: android.net.LinkProperties = copyProperties((original)!!)
        properties.addRoute(routedFixture("fd70::", 64, "fe80::1", live.iface, 1))
        var routed: NetworkState = networkFixture(target, (live.network)!!, properties)
        var endpoint: InetAddress? = InetAddress.getByName("fd70::20")
        var proxy: InetAddress = InetAddress.getByName(live.gateway)
        check(
            (live.sameConnection(routed) && live.retainsScope(routed)),
            "Adding an OMR route preserves this attachment and existing work",
        )
        check(
            ((!((routed)!!.local(endpoint)) && (routed)!!.routed((endpoint)!!)) &&
                (routed)!!.admitted((endpoint)!!)),
            "Explicit routed prefix is admitted separately from direct Wi-Fi prefixes",
        )
        check(!((routed)!!.retainsScope(live)), "Route withdrawal fences existing routed work")
        check(
            !(live.admitted((endpoint)!!)),
            "The default IPv6 route never supplies discovery scope",
        )
        var self6: InetAddress? = null
        for (value in live.addresses) {
            if ((InetAddress.getByName(value) is Inet6Address)) {
                self6 = InetAddress.getByName(live.scoped(InetAddress.getByName(value)))
                break
            }
        }
        check((self6 !== null), "The native fixture has an assigned Wi-Fi IPv6 address")
        var own: InetAddress? = self6
        icmpPeerReceivePath(live, (own)!!)
        ipv6LateTcpFallback(target, live, (own)!!, false)
        ipv6LateTcpFallback(target, live, (own)!!, true)
        var real: IcmpEcho.Result =
            IcmpEcho.probeV6(
                callback32@{ AndroidIcmp.open(live, own) },
                callback33@{ false },
                500,
                2,
                100,
            )
        check(
            (((real.status === IcmpEcho.Status.REPLIED) && (real.requests >= 1)) ||
                ((real.status === IcmpEcho.Status.ERROR) &&
                    (((real.problem === IcmpEcho.Problem.PERMISSION) ||
                        (real.problem === IcmpEcho.Problem.UNSUPPORTED))))),
            "Native IPv6 echo replies or exposes a specific platform restriction",
        )
        var capability: Bundle = Bundle()
        capability.putString(
            "stream",
            (((((("NATIVE_ICMPV6: " + real.status) + " / ") + real.problem) + " / API ") +
                Build.VERSION.SDK_INT) + "\n"),
        )
        sendStatus(0, capability)
        var receive: Method =
            Discovery::class
                .java
                .getDeclaredMethod(
                    "mdnsResolved",
                    InetAddress::class.java,
                    MdnsPacket.Result::class.java,
                )
        receive.setAccessible(true)
        var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
        var enriching: Field = Discovery::class.java.getDeclaredField("enriching")
        var types: Field = Discovery::class.java.getDeclaredField("nsdTypes")
        for (field in arrayOf(accepting, enriching, types)) {
            field.setAccessible(true)
        }
        if ((real.status === IcmpEcho.Status.ERROR)) {
            var limited: Discovery = Discovery(target, live)
            try {
                accepting.set(limited, true)
                enriching.set(limited, true)
                var service: NsdServiceInfo = NsdServiceInfo()
                service.setServiceName("Restricted native IPv6 fixture")
                service.setServiceType("_hap._udp.")
                service.setHost(own)
                service.setPort(51827)
                if ((Build.VERSION.SDK_INT >= 34)) {
                    service.setHostAddresses(Collections.singletonList(own))
                }
                var resolve: Method =
                    Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
                resolve.setAccessible(true)
                resolve.invoke(limited, service)
                var end: Long = (SystemClock.uptimeMillis() + 2500)
                while (
                    ((SystemClock.uptimeMillis() < end) &&
                        !(((limited.sources())!!.getOrDefault("IPv6 service checks", ""))!!
                            .startsWith("Failed")))
                ) {
                    SystemClock.sleep(20)
                }
                check(
                    ((limited.sources())!!.getOrDefault("IPv6 service checks", ""))!!.startsWith(
                        "Failed"
                    ),
                    "Platform restriction produces a failed discovery source",
                )
                check(
                    (limited.snapshot())!!.isEmpty(),
                    "A restricted native echo never promotes an advertisement",
                )
            } finally {
                limited.cancel()
            }
        }
        var packet: ByteArray = hkPacket(endpoint!!.getHostAddress(), "_hap._udp.", true)
        var graph: MdnsPacket.Result = MdnsPacket.parse(packet, packet.size)
        var requests: AtomicInteger? = AtomicInteger()
        var guards: AtomicInteger = AtomicInteger()
        var channels: MutableList<V6FixtureChannel> = Collections.synchronizedList(ArrayList())
        var positive: Discovery =
            Discovery(
                target,
                (routed)!!,
                callback34@{ n, a ->
                    check(
                        (a.equals(endpoint) && n.routed(a)),
                        "Fixture transport receives only the selected routed endpoint",
                    )
                    var c: V6FixtureChannel = V6FixtureChannel(true, null, null, requests)
                    channels.add(c)
                    return@callback34 c
                },
                callback35@{ c, n ->
                    check(
                        ((n === routed) && n.routed(endpoint)),
                        "Route guard receives the selected immutable snapshot",
                    )
                    guards.incrementAndGet()
                },
            )
        try {
            accepting.set(positive, true)
            enriching.set(positive, true)
            (((types.get(positive) as MutableSet<String>?)))!!.add("_hap._udp.")
            receive.invoke(positive, proxy, graph)
            var until: Long = (SystemClock.uptimeMillis() + 2500)
            while (
                ((SystemClock.uptimeMillis() < until) &&
                    ((positive.snapshot())!!.stream())!!.noneMatch(
                        callback36@{ d -> d.ip.equals(endpoint!!.getHostAddress()) }
                    ))
            ) {
                SystemClock.sleep(20)
            }
            var accessory: Device? =
                nullablePlatformResult(
                    ((((positive.snapshot())!!.stream())!!.filter(
                                callback37@{ d -> d.ip.equals(endpoint!!.getHostAddress()) }
                            ))!!
                            .findFirst())!!
                        .orElse(null)
                )
            check((accessory !== null), "Routed accessory is listed after a fresh endpoint echo")
            until = (SystemClock.uptimeMillis() + 1500)
            while (
                ((SystemClock.uptimeMillis() < until) &&
                    !((accessory)!!.model.equals("Current HomeKit model")))
            ) {
                SystemClock.sleep(20)
                accessory =
                    ((((positive.snapshot())!!.stream())!!.filter(
                                callback38@{ d -> d.ip.equals(endpoint!!.getHostAddress()) }
                            ))!!
                            .findFirst())!!
                        .orElseThrow(::AssertionError)
            }
            check(
                ((accessory)!!.model.equals("Current HomeKit model") &&
                    (accessory)!!.kind!!.equals("HomeKit light")),
                "Current proxy graph adds advertised model and category only after confirmation",
            )
            check((accessory)!!.mac.isEmpty(), "HomeKit pairing ID never becomes a MAC or vendor")
            check(
                ((accessory)!!.details.containsKey("ICMPv6 response time") &&
                    ((accessory)!!.details.get("Network path"))!!.contains("Routed IPv6")),
                "Response protocol and routed path keep distinct provenance",
            )
            check(
                (((requests!!.get() == 1) && (guards.get() >= 3)) && (channels.get(0))!!.closed),
                "One correlated reply closes the channel; route is checked before opening, sending and publishing",
            )
            var shown: Device = accessory!!
            activity = launch(false)
            settle()
            onMain(
                callback39@{
                    invoke("stopWatchingConnection")
                    setField("network", routed)
                    setField("devices", Collections.singletonList(shown))
                    setField("selected", shown)
                    invoke("render")
                }
            )
            assertText("RESPONSE TIME (ICMPv6)")
            capture("routed-accessory-light")
            description("Switch to dark mode")
            assertText("RESPONSE TIME (ICMPv6)")
            click("Show discovery evidence")
            assertText("NETWORK PATH")
            assertText("Routed IPv6 through Wi-Fi")
            capture("routed-accessory-dark")
        } finally {
            positive.cancel()
        }
        run {
            var scenario: Int = 0
            while ((scenario < 4)) {
                var which: Int = scenario
                var sent: AtomicInteger? = AtomicInteger()
                var entered: CountDownLatch? = CountDownLatch(1)
                var release: CountDownLatch? = CountDownLatch(1)
                var routeGone: AtomicBoolean = AtomicBoolean()
                var channel: V6FixtureChannel? =
                    V6FixtureChannel(
                        (scenario != 0),
                        entered,
                        (if ((scenario == 0)) null else release),
                        sent,
                    )
                var scan: Discovery =
                    Discovery(
                        target,
                        (routed)!!,
                        callback40@{ n, a -> channel!! },
                        callback41@{ c, n ->
                            if (routeGone.get()) {
                                throw IllegalStateException("Controlled route withdrawal")
                            }
                        },
                    )
                try {
                    accepting.set(scan, true)
                    enriching.set(scan, true)
                    (((types.get(scan) as MutableSet<String>?)))!!.add("_hap._udp.")
                    receive.invoke(scan, proxy, graph)
                    check(
                        entered!!.await(2, TimeUnit.SECONDS),
                        "Confirmation reached its bounded receive loop",
                    )
                    var pending: Field = Discovery::class.java.getDeclaredField("ipv6Pending")
                    pending.setAccessible(true)
                    check(
                        ((((pending.get(scan) as AtomicInteger?)))!!.get() > 0),
                        "A running IPv6 confirmation keeps the scan settlement window open",
                    )
                    if ((scenario == 1)) {
                        var goodbye: ByteArray =
                            txtWire(
                                "HomeKit fixture",
                                "_hap._udp.",
                                0,
                                "md=Current HomeKit model",
                                "ci=5",
                                "sf=5",
                                "id=AA:BB:CC:DD:EE:FF",
                            )
                        receive.invoke(scan, proxy, MdnsPacket.parse(goodbye, goodbye.size))
                    }
                    if ((scenario == 2)) {
                        scan.cancel()
                    }
                    if ((scenario == 3)) {
                        routeGone.set(true)
                    }
                    release!!.countDown()
                    var end: Long = (SystemClock.uptimeMillis() + 5500)
                    while (((SystemClock.uptimeMillis() < end) && !(channel!!.closed))) {
                        SystemClock.sleep(20)
                    }
                    check(
                        channel!!.closed,
                        "IPv6 confirmation closes after reply, silence, Stop or route change",
                    )
                    var drained: Long = (SystemClock.uptimeMillis() + 1500)
                    while (
                        ((SystemClock.uptimeMillis() < drained) &&
                            ((((pending.get(scan) as AtomicInteger?)))!!.get() != 0))
                    ) {
                        SystemClock.sleep(10)
                    }
                    check(
                        ((((pending.get(scan) as AtomicInteger?)))!!.get() == 0),
                        "The settlement counter drains after every terminal path",
                    )
                    var found: Device? =
                        ((((scan.snapshot())!!.stream())!!.filter(
                                    callback42@{ d -> d.ip.equals(endpoint!!.getHostAddress()) }
                                ))!!
                                .findFirst())!!
                            .orElse(null)
                    if ((scenario == 1)) {
                        check(
                            (((found !== null) && found!!.model.isEmpty()) &&
                                found!!.services.isEmpty()),
                            "Goodbye before echo prevents withdrawn metadata returning; live IP remains independently confirmed",
                        )
                    } else {
                        check(
                            (found === null),
                            "Silent, cancelled and withdrawn-route endpoints stay out of results",
                        )
                    }
                    if ((scenario == 0)) {
                        check(
                            ((sent!!.get() == 2) &&
                                ((scan.sources())!!.get("IPv6 service checks"))!!.contains(
                                    "unconfirmed 1"
                                )),
                            "Two bounded sleepy-device attempts and honest unconfirmed count",
                        )
                    }
                    if ((scenario == 3)) {
                        check(
                            ((scan.sources())!!.get("IPv6 service checks"))!!.startsWith("Failed"),
                            "Route failure is distinct from a silent accessory",
                        )
                    }
                } finally {
                    release!!.countDown()
                    scan.cancel()
                }
                scenario++
            }
        }
        var excludedCalls: AtomicInteger = AtomicInteger()
        var excluded: Discovery =
            Discovery(
                target,
                live,
                callback43@{ n, a ->
                    excludedCalls.incrementAndGet()
                    throw IOException("Must not open")
                },
            )
        try {
            accepting.set(excluded, true)
            enriching.set(excluded, true)
            (((types.get(excluded) as MutableSet<String>?)))!!.add("_hap._udp.")
            receive.invoke(excluded, proxy, graph)
            check(
                ((excludedCalls.get() == 0) &&
                    ((excluded.snapshot())!!.stream())!!.noneMatch(
                        callback44@{ d -> d.ip.equals(endpoint!!.getHostAddress()) }
                    )),
                "Missing OMR route causes no probe and no listed accessory",
            )
            check(
                ((excluded.sources())!!.get("IPv6 service checks"))!!.contains("excluded 1"),
                "Out-of-scope accessory address is counted",
            )
        } finally {
            excluded.cancel()
            store.clear()
        }
    }

    private fun ipv6Feasibility(): Unit {
        var net: NetworkState = NetworkState.current(getTargetContext())
        check((net.network !== null), "IPv6 feasibility uses the selected Wi-Fi network")
        var fd: java.io.FileDescriptor? = null
        var stage: String = "socket"
        var socketOpened: Boolean = false
        var bound: Boolean = false
        var replied: Boolean = false
        try {
            fd =
                android.system.Os.socket(
                    android.system.OsConstants.AF_INET6,
                    android.system.OsConstants.SOCK_DGRAM,
                    android.system.OsConstants.IPPROTO_ICMPV6,
                )
            socketOpened = true
            stage = "network binding"
            (net.network)!!.bindSocket(fd)
            bound = true
            var local: InetAddress? = null
            for (ip in net.addresses) {
                if (ip.contains(":")) {
                    var candidate: InetAddress = InetAddress.getByName(ip)
                    if ((candidate is Inet6Address)) {
                        local = candidate
                        break
                    }
                }
            }
            if ((local === null)) {
                System.out.println(
                    "FEASIBILITY: IPv6 datagram socket opened and Wi-Fi bound; no IPv6 interface address to test"
                )
            } else {
                if (local!!.isLinkLocalAddress()) {
                    local =
                        Inet6Address.getByAddress(
                            null,
                            local!!.address,
                            NetworkInterface.getByName(net.iface),
                        )
                }
                stage = "local echo"
                android.system.Os.bind(fd, local, 0)
                android.system.Os.connect(fd, local, 0)
                var packet: ByteArray = ByteArray(24)
                packet[0] = (128).toByte()
                packet[7] = 1
                var nonce: ByteArray = ByteArray(16)
                java.security.SecureRandom().nextBytes(nonce)
                System.arraycopy(nonce, 0, packet, 8, 16)
                android.system.Os.write(fd, packet, 0, packet.size)
                var poll: android.system.StructPollfd = android.system.StructPollfd()
                poll.fd = fd
                poll.events = (android.system.OsConstants.POLLIN).toShort()
                if ((android.system.Os.poll(arrayOf(poll), 800) > 0)) {
                    var reply: ByteArray? = ByteArray(256)
                    var count: Int = android.system.Os.read(fd, reply, 0, reply!!.size)
                    replied =
                        (((count == 24) && ((((reply!![0]).toInt() and (255).toInt())) == 129)) &&
                            Arrays.equals(Arrays.copyOfRange(reply, 8, 24), nonce))
                }
                System.out.println(
                    (("FEASIBILITY: IPv6 datagram socket opened, Wi-Fi bound, own-address correlated echo=" +
                        replied) + "; multicast and other-device discovery NOT established")
                )
            }
        } catch (e: Exception) {
            if (e !is android.system.ErrnoException && e !is SecurityException && e !is IOException)
                throw e
            System.out.println(
                ((((((((("FEASIBILITY: IPv6 probe stopped at " + stage) + "; type=") +
                    (e.javaClass)!!.getSimpleName()) + "; errno=") +
                    ((if ((e is android.system.ErrnoException))
                        (((e as android.system.ErrnoException?)))!!.errno
                    else "n/a"))) + "; opened=") + socketOpened) + "; bound=") + bound)
            )
        } finally {
            if ((fd !== null)) {
                android.system.Os.close(fd)
            }
        }
        check(((fd === null) || !(fd!!.valid())), "IPv6 experiment closes its socket")
        check(true, "Diagnostic records capability without claiming discovery support")
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
                .getDeclaredMethod("startMdnsFamily", Boolean::class.javaPrimitiveType!!)
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
        var sixPeer: InetAddress = InetAddress.getByName("fd00::20")
        var now: Long = System.nanoTime()
        for (part in intArrayOf(0, 1, 3)) {
            var p: ByteArray = splitPacket("fd00::20", part)
            burst.accept(sixPeer, MdnsPacket.parse(p, p.size), now)
        }
        var alternate: ByteArray = splitPacket("fd00::21", 3)
        var txt: ByteArray = splitPacket("fd00::20", 2)
        burst.accept(sixPeer, MdnsPacket.parse(alternate, alternate.size), (now + 200000000L))
        check(
            ((burst.accept(sixPeer, MdnsPacket.parse(txt, txt.size), (now + 400000000L)))!!.size ==
                1),
            "Split AAAA cache-flush burst retains its recent matching address",
        )
        burst.accept(sixPeer, MdnsPacket.parse(alternate, alternate.size), (now + 2000000000L))
        check(
            (burst.accept(sixPeer, MdnsPacket.parse(txt, txt.size), (now + 3000000000L)))!!
                .isEmpty(),
            "Older unrefreshed address expires after cache-flush grace",
        )
        check(
            ((burst.due(true, (now + 3000000000L), 4))!!.stream())!!.anyMatch(
                callback45@{ q -> (q.kind == 28) }
            ),
            "Missing matching AAAA triggers repair even when an alternate exists",
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
            worker = (raw.invoke(wired, false) as Thread?)
            var sawSrv: Boolean = false
            var sawTxt: Boolean = false
            var deadline: Long = (SystemClock.uptimeMillis() + 5000)
            while (((SystemClock.uptimeMillis() < deadline) && !(((sawSrv && sawTxt))))) {
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
                var requestClass: Class<*> = Class.forName("dev.netlens.Discovery\$ModernRequest")
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
                            "dev.netlens",
                        )),
                    ("Correct Lucide role for " + d.kind),
                )
                check(
                    icon.label.equals(d.kind),
                    "Accessibility label preserves the actual reported category",
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
            !((texts())!!.contains("HOMEKIT SETUP (ADVERTISED)")),
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
        click("Show discovery evidence")
        assertText("HOMEKIT SETUP (ADVERTISED)")
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
        assertText("1 device group matching")
        click("Vendor fixture")
        assertText("MAC VENDOR")
        assertText(vendor)
        assertText("Reported maker")
        assertText("Reported model")
        check(
            !((texts())!!.contains("NETWORK IDENTIFIER (DNS)")),
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
            !((texts())!!.contains("MAC VENDOR")),
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
                    Boolean::class.javaPrimitiveType!!,
                    Long::class.javaPrimitiveType!!,
                    Map::class.java,
                    Map::class.java,
                )
        batch.setAccessible(true)
        var normalAttempts: Map<String, Int> = HashMap()
        var normalNext: Map<String, Long> = HashMap()
        try {
            var now: Long = System.nanoTime()
            var packet: Any = batch.invoke(batching, false, now, normalAttempts, normalNext)
            var questions: Field = (packet.javaClass)!!.getDeclaredField("questions")
            var isRetry: Field = (packet.javaClass)!!.getDeclaredField("retry")
            questions.setAccessible(true)
            isRetry.setAccessible(true)
            check(
                ((isRetry.getBoolean(packet) &&
                    (questions.get(packet))!!.equals(
                        Collections.singletonList("_devicelens-fixture._tcp.local")
                    )) && normalAttempts.isEmpty()),
                "A recovery packet contains only retry types and spends no normal query attempts",
            )
            var normal: Any =
                batch.invoke(
                    batching,
                    false,
                    (now + TimeUnit.MILLISECONDS.toNanos(100)),
                    normalAttempts,
                    normalNext,
                )
            check(
                ((!(isRetry.getBoolean(normal)) &&
                    (((questions.get(normal) as List<String>?)))!!.contains(
                        ReverseDns.question(live.gateway)
                    )) && (normalAttempts.get("_http._tcp.") == 1)),
                "Deferred normal reverse/type questions resume in the next available batch",
            )
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
                .getDeclaredMethod("startMdnsFamily", Boolean::class.javaPrimitiveType!!)
        start.setAccessible(true)
        var thread: Thread? = null
        try {
            thread = (start.invoke(scan, false) as Thread?)
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
            var requestClass: Class<*> = Class.forName("dev.netlens.Discovery\$ModernRequest")
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
                    ((gatePlan)!!.due(false, System.nanoTime(), 8))!!.isEmpty(),
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
                    ((gatePlan)!!.due(false, System.nanoTime(), 8))!!.equals(
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
                ((((plan.get(cancelled) as MdnsRetries?)))!!.due(false, System.nanoTime(), 8))!!
                    .isEmpty(),
                "A timeout after Stop cannot schedule discovery traffic",
            )
        }
    }

    private fun detailsUpgrade(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        var device: Device? = Device()
        device!!.ip = live.gateway
        device!!.name = "Office workstation"
        device!!.hostname = "workstation.local"
        device!!.seen = true
        device!!.lastSeen = System.currentTimeMillis()
        device!!.evidence("Explicit detail-layout fixture")
        device!!.detail("ICMP response time", "12 ms")
        device!!.detail("Network identifier (DNS)", "02:11:22:33:44:55")
        store.retainIfCurrent(store.token(), live.key, Collections.singletonList(device), true)
        activity = launch(false)
        settle()
        click("Office workstation")
        assertText("RESPONSE TIME (ICMP)")
        assertText("12 ms")
        onMain(
            callback54@{
                var label: View? =
                    find((activity!!.window)!!.getDecorView(), "RESPONSE TIME (ICMP)", false)
                var row: View? = ((label)!!.getParent() as View?)
                check(
                    ((label)!!.getBottom() <= ((row)!!.getHeight() - (row)!!.getPaddingBottom())),
                    "Wrapped response label remains inside its row without baseline clipping",
                )
            }
        )
        assertText("MAC ADDRESS")
        assertText("Not available")
        check(
            (!((texts())!!.contains("TCP RESPONSE TIME")) &&
                !((texts())!!.contains("Not measured"))),
            "A measured ICMP result replaces the irrelevant unmeasured TCP field",
        )
        check(
            ((!((texts())!!.contains("MODEL\n")) && !((texts())!!.contains("MANUFACTURER\n"))) &&
                (texts())!!.contains("Not reported: manufacturer, model, device type.")),
            "Unavailable identity fields have one honest summary",
        )
        check(
            !((texts())!!.contains("NETWORK IDENTIFIER (DNS)")),
            "Technical evidence is collapsed by default",
        )
        capture("details-compact-light")
        click("Show discovery evidence")
        assertText("NETWORK IDENTIFIER (DNS)")
        assertText("02:11:22:33:44:55")
        onMain(callback55@{ (detailScroll())!!.scrollTo(0, 350) })
        settle()
        var oldY: IntArray = intArrayOf(0)
        onMain(callback56@{ oldY[0] = (detailScroll())!!.getScrollY() })
        description("Switch to dark mode")
        settle()
        assertText("Hide discovery evidence")
        assertText("NETWORK IDENTIFIER (DNS)")
        onMain(
            callback57@{
                check(
                    (Math.abs(((detailScroll())!!.getScrollY() - oldY[0])) < 30),
                    "Theme changes preserve the expanded detail scroll position",
                )
            }
        )
        capture("details-evidence-dark")
        click("Hide discovery evidence")
        check(
            !((texts())!!.contains("NETWORK IDENTIFIER (DNS)")),
            "Collapsing removes technical evidence from the content tree",
        )
        click("Copy device details")
        onMain(
            callback58@{
                var c: ClipboardManager? =
                    (target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                var value: String = ((((c)!!.getPrimaryClip())!!.getItemAt(0))!!.text)!!.toString()
                check(
                    ((value.contains(("IP: " + device!!.ip)) &&
                        value.contains("MAC: Not available")) &&
                        value.contains("Network identifier (DNS): 02:11:22:33:44:55")),
                    "Copied details retain addresses and evidence while the disclosure is collapsed",
                )
            }
        )
        description("Back to devices")
        device!!.name = "Known printer"
        device!!.model = "Compact 500"
        device!!.manufacturer = "Fixture Inc"
        device!!.kind = "Printer service"
        device!!.mac = "00:11:22:33:44:55"
        device!!.macSource = "Explicit fixture adapter"
        device!!.latency = 27
        device!!.details.remove("ICMP response time")
        store.retainIfCurrent(store.token(), live.key, Collections.singletonList(device), true)
        onMain(callback59@{ activity!!.finish() })
        waitForIdleSync()
        activity = launch(false)
        settle()
        click("Known printer")
        assertText("Compact 500")
        assertText("Fixture Inc")
        assertText("RESPONSE TIME (TCP)")
        assertText("27 ms")
        check(
            (!((texts())!!.contains("Not reported:")) &&
                !((texts())!!.contains("RESPONSE TIME (ICMP)"))),
            "Known identity and TCP measurements display without invented missing values",
        )
        check(
            !((texts())!!.contains("NETWORK IDENTIFIER (DNS)")),
            "A new selection resets the previous evidence disclosure",
        )
        capture("details-known-dark")
        onMain(
            callback60@{
                var config: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                config.fontScale = 2f
                (activity!!.getResources())!!.updateConfiguration(
                    config,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                invoke("render")
            }
        )
        settle()
        assertText("Compact 500")
        onMain(
            callback61@{
                var label: TextView? =
                    (find((activity!!.window)!!.getDecorView(), "MANUFACTURER", false) as TextView?)
                check(
                    (((label)!!.getLayout())!!.getLineCount() == 1),
                    "Large-text detail labels use a full-width stacked layout",
                )
            }
        )
        check(
            (texts())!!.contains("Copy device details"),
            "Large-text layout keeps the final action in the scrollable content",
        )
        capture("details-large-dark")
        onMain(
            callback62@{
                var config: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                config.fontScale = 1f
                (activity!!.getResources())!!.updateConfiguration(
                    config,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
            }
        )
        store.clear()
    }

    private fun detailScroll(): ScrollView? {
        return (field("detailScroll") as ScrollView?)
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
            var requestType: Class<*> = Class.forName("dev.netlens.Discovery\$ModernRequest")
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
                activity = launch(false)
                settle()
                onMain(callback75@{ invoke("scanDetails") })
                SystemClock.sleep(200)
                check(
                    ((dialogTexts())!!.contains("Ping response checks") &&
                        (dialogTexts())!!.contains("Some ping checks could not run.")),
                    "Failed echo source has plain-language modal copy",
                )
                captureDialog("scan-details-icmp-error-light")
                dialogClick("Done")
                description("Switch to dark mode")
                onMain(callback76@{ invoke("scanDetails") })
                SystemClock.sleep(200)
                captureDialog("scan-details-icmp-error-dark")
                dialogClick("Done")
                onMain(callback77@{ activity!!.finish() })
                waitForIdleSync()
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
                var serving: Future<*>? =
                    serverWorker.submit(
                        callback81@{
                            try {
                                server.accept().use { socket ->
                                    var `in`: BufferedReader =
                                        BufferedReader(
                                            InputStreamReader(
                                                socket.getInputStream(),
                                                java.nio.charset.StandardCharsets.US_ASCII,
                                            )
                                        )
                                    line!!.set(`in`.readLine())
                                    var header: String? = null
                                    while (
                                        ((run {
                                            header = `in`.readLine()
                                            header
                                        } !== null) && !(header!!.isEmpty()))
                                    ) {}
                                    var body: ByteArray =
                                        "{\"name\":\"Fixture Cast speaker\",\"ssid\":\"PRIVATE_SSID\",\"location\":{\"latitude\":123},\"mac_address\":\"00:00:00:00:00:00\",\"ip_address\":\"203.0.113.9\"}"
                                            .toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                                    (socket.getOutputStream())!!.write(
                                        (((("HTTP/1.1 200 OK\r\nContent-Length: " + body.size) +
                                                "\r\nConnection: close\r\n\r\n")))!!
                                            .toByteArray(java.nio.charset.StandardCharsets.US_ASCII)
                                    )
                                    (socket.getOutputStream())!!.write(body)
                                }
                            } catch (e: IOException) {
                                throw RuntimeException(e)
                            }
                        }
                    )
                var device: Device? =
                    (observe.invoke(names, live.ip, "Explicit test responder") as Device?)
                device!!.hint("Name", "Chromecast", "Fixture TXT friendly name", 90)
                device!!.service("_googlecast._tcp")
                cast.invoke(names, device)
                serving!!.get(5, TimeUnit.SECONDS)
                var end: Long = (SystemClock.uptimeMillis() + 3000)
                while (
                    (!(device!!.name.equals("Fixture Cast speaker")) &&
                        (SystemClock.uptimeMillis() < end))
                ) {
                    SystemClock.sleep(25)
                }
                check(
                    (device!!.name.equals("Fixture Cast speaker") &&
                        "GET /setup/eureka_info?options=detail HTTP/1.1".equals(line!!.get())),
                    "Bound local Cast fallback supplies a missing friendly name",
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

        var ipv6: String? = null
        for (address in live.addresses) {
            if (((Address.bytes(address) !== null) && ((Address.bytes(address))!!.size == 16))) {
                ipv6 = live.scoped(InetAddress.getByName(address))
                if ((InetAddress.getByName(address))!!.isLinkLocalAddress()) {
                    break
                }
            }
        }
        if ((ipv6 !== null)) {
            var v6bytes: ByteArray? = Address.bytes(ipv6)
            var selected: NetworkInterface = NetworkInterface.getByName(live.iface)
            check(
                (live.scoped(Inet6Address.getByAddress(null, v6bytes, selected.index)))!!.equals(
                    live.scoped(Inet6Address.getByAddress(null, v6bytes, selected))
                ),
                "Numeric and named IPv6 zones use one device-address key",
            )
            var multicast: Discovery = Discovery(target, live)
            accepting.set(multicast, true)
            try {
                MulticastSocket(null).use { sender ->
                    var wireTarget: Device? =
                        (observe.invoke(multicast, ipv6, "Explicit IPv6 fixture target") as Device?)
                    var queueName: Method =
                        Discovery::class.java.getDeclaredMethod("queueMdnsName", Device::class.java)
                    queueName.setAccessible(true)
                    queueName.invoke(multicast, wireTarget)
                    var questions: Field = Discovery::class.java.getDeclaredField("mdnsQuestions")
                    questions.setAccessible(true)
                    var question: Any? =
                        (((questions.get(multicast) as MutableMap<*, *>?)))!!.get(ipv6)
                    var attempts: Field = ((question)!!.javaClass)!!.getDeclaredField("attempts")
                    attempts.setAccessible(true)
                    attempts.setInt(question, 2)
                    var startV6: Method =
                        Discovery::class
                            .java
                            .getDeclaredMethod(
                                "startMdnsFamily",
                                Boolean::class.javaPrimitiveType!!,
                            )
                    startV6.setAccessible(true)
                    var receiving: Thread? = (startV6.invoke(multicast, true) as Thread?)
                    SystemClock.sleep(200)
                    sender.setReuseAddress(true)
                    sender.bind(InetSocketAddress(InetAddress.getByName("::"), 5353))
                    (live.network)!!.bindSocket(sender)
                    sender.setNetworkInterface(NetworkInterface.getByName(live.iface))
                    sender.setTimeToLive(255)
                    sender.setLoopbackMode(false)
                    var targetBytes: ByteArrayOutputStream = ByteArrayOutputStream()
                    var targetName: DataOutputStream = DataOutputStream(targetBytes)
                    for (label in arrayOf("engine-fixture", "local")) {
                        var part: ByteArray =
                            label.toByteArray(java.nio.charset.StandardCharsets.US_ASCII)
                        targetName.writeByte(part.size)
                        targetName.write(part)
                    }
                    targetName.writeByte(0)
                    var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
                    var packet: DataOutputStream = DataOutputStream(bytes)
                    packet.writeShort(0)
                    packet.writeShort(33792)
                    packet.writeShort(0)
                    packet.writeShort(1)
                    packet.writeInt(0)
                    for (label in
                        java.util.regex.Pattern.compile("\\.")
                            .split(ReverseDns.question(ipv6), 0)) {
                        var part: ByteArray =
                            label.toByteArray(java.nio.charset.StandardCharsets.US_ASCII)
                        packet.writeByte(part.size)
                        packet.write(part)
                    }
                    packet.writeByte(0)
                    packet.writeShort(12)
                    packet.writeShort(32769)
                    packet.writeInt(120)
                    packet.writeShort(targetBytes.size())
                    packet.write(targetBytes.toByteArray())
                    var response: ByteArray = bytes.toByteArray()
                    var group: InetAddress = InetAddress.getByName(("ff02::fb%" + live.iface))
                    run {
                        var attempt: Int = 0
                        while ((attempt < 6)) {
                            sender.send(DatagramPacket(response, response.size, group, 5353))
                            SystemClock.sleep(250)
                            if ((wireTarget)!!.hostname.equals("engine-fixture.local")) {
                                break
                            }
                            attempt++
                        }
                    }
                    check(
                        ((wireTarget)!!.hostname.equals("engine-fixture.local") &&
                            (wireTarget)!!.evidence.contains("Matching mDNS reverse-PTR reply")),
                        ("Actual IPv6 multicast PTR fixture supplies the unique expected hostname; sources=" +
                            multicast.sources()),
                    )
                    multicast.cancel()
                    (receiving)!!.join(1500)
                    check(
                        !((receiving)!!.isAlive()),
                        "IPv6 multicast listener closes promptly on Stop",
                    )
                }
            } finally {
                multicast.cancel()
            }
        } else {
            var skipped: Bundle = Bundle()
            skipped.putString("stream", "PLATFORM: no IPv6 Wi-Fi address for multicast fixture\n")
            sendStatus(0, skipped)
        }
        store.clear()
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

    private fun performance(): Unit {
        activity = launch(true)
        settle()
        var results: JSONArray = JSONArray()
        for (rich in booleanArrayOf(false, true)) {
            for (count in intArrayOf(40, 250, 1024)) {
                var raw: MutableList<Device> = ArrayList()
                run {
                    var n: Int = 0
                    while ((n < count)) {
                        var d: Device = Device()
                        d.ip = ((("198.18." + ((n / 250))) + ".") + (((n % 250) + 1)))
                        d.name = ("Fixture device " + n)
                        d.port(80)
                        d.service("_http._tcp.")
                        if (rich) {
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
                                d.hint(
                                    field,
                                    ((("Fixture " + field) + " ") + n),
                                    "Fixture advertised source",
                                    80,
                                )
                            }
                            d.detail("UPnP model number", "Fixture model number")
                            d.evidence("Synthetic fixture")
                        }
                        raw.add(d)
                        n++
                    }
                }
                Device.grouped(raw)
                var grouping: DoubleArray = DoubleArray(3)
                var groups: MutableList<Device>? = null
                run {
                    var r: Int = 0
                    while ((r < 3)) {
                        var start: Long = System.nanoTime()
                        groups = Device.grouped(raw)
                        grouping[r] = (((System.nanoTime() - start)) / 1e6)
                        r++
                    }
                }
                Arrays.sort(grouping)
                check((groups!!.size == count), "Dense grouping preserves every unrelated endpoint")
                var prepared: List<Device> = groups
                var initial: DoubleArray = doubleArrayOf((0).toDouble())
                var reuse: DoubleArray = DoubleArray(3)
                onMain(
                    callback84@{
                        setField("query", "")
                        setField("devices", raw)
                        setField("displayGroups", prepared)
                        setField("groupedSource", raw)
                        (((field("rowCache") as MutableMap<*, *>?)))!!.clear()
                        setField("rowPanel", null)
                        var start: Long = System.nanoTime()
                        invoke("fillDevices")
                        initial[0] = (((System.nanoTime() - start)) / 1e6)
                    }
                )
                run {
                    var r: Int = 0
                    while ((r < 3)) {
                        var sample: Int = r
                        onMain(
                            callback85@{
                                var start: Long = System.nanoTime()
                                invoke("fillDevices")
                                reuse[sample] = (((System.nanoTime() - start)) / 1e6)
                            }
                        )
                        r++
                    }
                }
                Arrays.sort(reuse)
                check(
                    ((((field("deviceRows") as MutableMap<*, *>?)))!!.size == count),
                    "Dense list retains every endpoint row",
                )
                results.put(
                    ((((JSONObject().put("endpointCount", count))!!.put("richMetadata", rich))!!
                                .put("groupMedianMs", grouping[1]))!!
                            .put("initialCachedRowsMs", initial[0]))!!
                        .put("reusedRowsMedianMs", reuse[1])
                )
            }
        }
        var report: Bundle = Bundle()
        report.putString("stream", (("PERFORMANCE_RESULT=" + results.toString()) + "\n"))
        sendStatus(0, report)
        onMain(callback86@{ activity!!.finish() })
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
            (live.sameConnection(withdrawn) && !(live.retainsScope(withdrawn))),
            "IPv6 withdrawal preserves connection identity but invalidates affected scope",
        )
        check(
            !(live.refresh((withdrawn)!!)),
            "Active work refuses a withdrawn prefix without replacing its snapshot",
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
        activity = launch(false)
        settle()
        assertText("IPv4  192.0.2.5")
        assertText(("IPv6  " + v6.ip))
        check(
            !((texts())!!.contains("more IP")),
            "List uses labelled addresses instead of a more-IP counter",
        )
        onMain(
            callback87@{
                var a: View? = find((activity!!.window)!!.getDecorView(), "IPv4  192.0.2.5", false)
                var b: View? = find((activity!!.window)!!.getDecorView(), ("IPv6  " + v6.ip), false)
                check(
                    (((a !== null) && (b !== null)) && (top(b) > top(a))),
                    "IPv6 appears below IPv4",
                )
                var rows: Map<String, View>? = (field("deviceRows") as Map<String, View>?)
                var before: View? = (rows)!!.get(v4.ip)
                invoke("fillDevices")
                check(
                    ((((field("deviceRows") as Map<String, View>?)))!!.get(v4.ip) === before),
                    "Unchanged list updates reuse the same row view",
                )
            }
        )
        capture("address-rows-light")
        description("Switch to dark mode")
        capture("address-rows-dark")
        click("Address layout fixture")
        assertText(linkLocal.ip)
        click("Copy device details")
        onMain(
            callback88@{
                var clipboard: ClipboardManager? =
                    (target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                var text: String =
                    ((((clipboard)!!.getPrimaryClip())!!.getItemAt(0))!!.text)!!.toString()
                check(
                    ((text.contains(v4.ip) && text.contains(v6.ip)) && text.contains(linkLocal.ip)),
                    "Copy includes all addresses beyond the list summary",
                )
            }
        )
        description("Back to devices")
        onMain(
            callback89@{
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
        assertText(("IPv6  " + v6.ip))
        capture("address-rows-large-text")
        onMain(
            callback90@{
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
        var chosen: NetworkState = live
        var stale: Discovery = Discovery(target, chosen)
        var active: Discovery = Discovery(target, chosen)
        var offer: Method =
            (activity!!.javaClass)!!.getDeclaredMethod(
                "offerScan",
                Discovery::class.java,
                Long::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                List::class.java,
                Boolean::class.javaPrimitiveType!!,
                String::class.java,
            )
        offer.setAccessible(true)
        var token: Long = store.token()
        var inScopeUpdate: Device = v4.copy()
        inScopeUpdate.ip = live.gateway
        onMain(
            callback91@{
                try {
                    setField("discovery", active)
                    setField("scanning", true)
                    offer.invoke(
                        activity,
                        active,
                        token,
                        1,
                        1,
                        Collections.singletonList(inScopeUpdate),
                        true,
                        "Fixture finished",
                    )
                    offer.invoke(
                        activity,
                        stale,
                        token,
                        0,
                        0,
                        Collections.emptyList<Any>(),
                        true,
                        "Stale fixture",
                    )
                    SystemClock.sleep(150)
                    offer.invoke(
                        activity,
                        active,
                        token,
                        0,
                        1,
                        Collections.emptyList<Any>(),
                        false,
                        "Late progress",
                    )
                    SystemClock.sleep(350)
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            }
        )
        SystemClock.sleep(800)
        settle()
        check(
            (!((field("scanning") as Boolean)) &&
                ((((field("devices") as List<Device>?)))!!.size == 1)),
            "Obsolete operation and late progress cannot evict a newer scan's final update",
        )
        stale.cancel()
        active.cancel()
        store.retainIfCurrent(store.token(), live.key, Collections.singletonList(v6), true)
        onMain(
            callback92@{
                invoke("loadNetwork")
                invoke("render")
            }
        )
        settle()
        assertText(("IPv6  " + v6.ip))
        check(
            !((texts())!!.contains("IPv4  ")),
            "IPv6-only devices do not get a fabricated IPv4 line",
        )
        capture("address-rows-ipv6-only")
        onMain(callback93@{ activity!!.finish() })
        store.clear()
    }

    private fun launch(demo: Boolean): Activity {
        return startActivitySync(
            ((Intent(Intent.ACTION_MAIN).setClassName("dev.netlens", "dev.netlens.MainActivity"))!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))!!
                .putExtra("demo", demo)
        )
    }

    private fun awaitScanIdle(timeout: Long): Unit {
        var until: Long = (SystemClock.uptimeMillis() + timeout)
        while (((SystemClock.uptimeMillis() < until) && (field("scanning") as Boolean))) {
            SystemClock.sleep(100)
        }
        check(
            !((field("scanning") as Boolean)),
            "Foreground scan returns to idle within its budget",
        )
        waitForIdleSync()
    }

    private fun home(): Unit {
        // Starting Android's HOME activity avoids dropped accessibility/key injection on API36.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
                (getUiAutomation())!!.executeShellCommand(
                    "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"
                )
            )
            .use { `in` ->
                var buffer: ByteArray = ByteArray(256)
                while ((`in`.read(buffer) != -(1))) {}
            }
        var until: Long = (SystemClock.uptimeMillis() + 6000)
        var moved: Boolean = false
        while ((SystemClock.uptimeMillis() < until)) {
            var active: AccessibilityNodeInfo? =
                nullablePlatformResult((getUiAutomation())!!.getRootInActiveWindow())
            if (((active !== null) && !("dev.netlens".contentEquals(active.getPackageName())))) {
                moved = true
                break
            }
            SystemClock.sleep(100)
        }
        check(moved, "Android HOME activity takes foreground from DeviceLens")
    }

    private fun tapScan(): Unit {
        onMain(
            callback94@{
                check(
                    (((field("scanButton") as View?)))!!.performClick(),
                    "Real Scan/Stop control accepts tap",
                )
            }
        )
    }

    private fun lifecycleFlow(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        store.dark(false)
        var live: NetworkState = NetworkState.current(target)
        var paused: java.util.concurrent.atomic.AtomicInteger =
            java.util.concurrent.atomic.AtomicInteger()
        var stops: java.util.concurrent.atomic.AtomicInteger =
            java.util.concurrent.atomic.AtomicInteger()
        (((target.getApplicationContext() as Application?)))!!.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?): Unit {}

                override fun onActivityStarted(a: Activity): Unit {}

                override fun onActivityResumed(a: Activity): Unit {}

                override fun onActivityPaused(a: Activity): Unit {
                    if ((a === activity)) {
                        paused.incrementAndGet()
                    }
                }

                override fun onActivityStopped(a: Activity): Unit {
                    if ((a === activity)) {
                        stops.incrementAndGet()
                    }
                }

                override fun onActivitySaveInstanceState(a: Activity, b: Bundle): Unit {}

                override fun onActivityDestroyed(a: Activity): Unit {}
            }
        )
        activity = launch(false)
        settle()
        check(
            (!((field("scanning") as Boolean)) &&
                ((store.last(live.key)).toLong() == (0).toLong())),
            "First launch starts no discovery or saved report",
        )
        description("Options")
        menuChoose("Settings")
        settingsClick("Scan range")
        onMain(
            callback95@{
                (((find(((optionDialog())!!.window)!!.getDecorView(), "Custom IPv4 range", true)
                        as EditText?)))!!
                    .setText((live.ip + "/32"))
            }
        )
        optionClick("Save range")
        settingsBack()
        tapScan()
        SystemClock.sleep(500)
        check((field("scanning") as Boolean), "Requested scan is visibly running")
        var appeared: Long = (SystemClock.uptimeMillis() + 5000)
        while (((SystemClock.uptimeMillis() < appeared) && !((texts())!!.contains("This phone")))) {
            SystemClock.sleep(100)
        }
        check(
            (((field("scanning") as Boolean) && (texts())!!.contains("Stop scan")) &&
                (texts())!!.contains("This phone")),
            "Self device appears incrementally while scan is running",
        )
        tapScan()
        awaitScanIdle(12000)
        var stopped: ScanReport? = store.report(live.key)
        check(
            (((stopped !== null) && !(stopped.complete)) && stopped.reason.contains("stopped")),
            "Stop saves an honest partial result",
        )
        check(
            ((texts())!!.contains("Partial scan") && !((texts())!!.contains("Stop scan"))),
            "Stopped UI returns to Scan and Partial status",
        )
        tapScan()
        awaitScanIdle(65000)
        var finished: ScanReport? = store.report(live.key)
        check(
            ((((finished !== null) && (finished.finished > (stopped)!!.finished)) &&
                (finished.checked == 1)) && (finished.total == 1)),
            "A new scan replaces the stopped report with its actual range coverage",
        )
        check(
            (finished)!!.scope.startsWith((live.ip + "/32")),
            "Saved coverage retains the chosen custom range",
        )
        var endpoints: Int = (store.devices(live.key))!!.size
        check((endpoints > 0), "Completed foreground scan saves live endpoint observations")
        var monitor: ActivityMonitor = addMonitor("dev.netlens.MainActivity", null, false)
        onMain(callback96@{ activity!!.recreate() })
        var replacement: Activity? =
            nullablePlatformResult(waitForMonitorWithTimeout(monitor, 8000))
        removeMonitor(monitor)
        check((replacement !== null), "Android can recreate the Activity")
        activity = replacement
        settle()
        check(
            (!((field("scanning") as Boolean)) &&
                ((((field("devices") as List<*>?)))!!.size == endpoints)),
            "Activity recreation reloads saved observations without starting a scan",
        )
        check(
            ((store.report(live.key))!!.finished == (finished)!!.finished),
            "Recreation preserves saved report timestamp",
        )
        check(
            (field("range"))!!.equals((live.ip + "/32")),
            "Activity recreation preserves the chosen range",
        )
        tapScan()
        SystemClock.sleep(500)
        onMain(callback97@{ invoke("scanDetails") })
        settle()
        check((scanDialog())!!.isShowing(), "Running scan details opens before backgrounding")
        paused.set(0)
        stops.set(0)
        home()
        awaitScanIdle(12000)
        var active: AccessibilityNodeInfo? =
            nullablePlatformResult((getUiAutomation())!!.getRootInActiveWindow())
        var focused: String? =
            (if ((active === null)) "none" else java.lang.String.valueOf(active.getPackageName()))
        check(
            !((scanDialog())!!.isShowing()),
            ((((("Leaving foreground dismisses the scan dialog; paused=" + paused.get()) +
                ", stopped=") + stops.get()) + ", active=") + focused),
        )
        var background: ScanReport? = store.report(live.key)
        check(
            (((background !== null) && !(background.complete)) &&
                background.reason.contains("stopped")),
            "Leaving foreground cancels discovery and records Partial",
        )
        var ended: Long = (background)!!.finished
        SystemClock.sleep(1700)
        check(
            ((store.report(live.key))!!.finished == ended),
            "No background scan refreshes the stopped result",
        )
        target.startActivity(
            (Intent(Intent.ACTION_MAIN).setClassName("dev.netlens", "dev.netlens.MainActivity"))!!
                .addFlags(
                    ((Intent.FLAG_ACTIVITY_NEW_TASK).toInt() or
                        (Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).toInt())
                )
        )
        SystemClock.sleep(1000)
        settle()
        check(
            ((activity!!.window)!!.getDecorView())!!.hasWindowFocus(),
            "Returning brings the app to foreground",
        )
        check(
            (!((field("scanning") as Boolean)) && (texts())!!.contains("Partial scan")),
            "Returning shows saved Partial results without automatic scanning",
        )
        tapScan()
        awaitScanIdle(65000)
        check(
            ((store.report(live.key))!!.finished > ended),
            "Explicit scan works after background cancellation",
        )
        onMain(callback98@{ invoke("resetSessionData") })
        check(
            ((store.report(live.key) === null) && (store.devices(live.key))!!.isEmpty()),
            "Deleting results leaves no report or devices after lifecycle transitions",
        )
        check(
            ((((target.getSystemService(Context.JOB_SCHEDULER_SERVICE)
                        as android.app.job.JobScheduler?)))!!
                    .getAllPendingJobs())!!
                .isEmpty(),
            "No background jobs were created during the journey",
        )
        capture("e2e-lifecycle-finished")
    }

    private fun full(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var prefs: SharedPreferences = target.getSharedPreferences("netlens", 0)
        ((((prefs.edit())!!.putBoolean("monitor", true))!!.putString("devices:legacy", "[]"))!!
                .putString("events:legacy", "[]"))!!
            .commit()
        store.migrate()
        check(
            noLegacyFiles(target),
            "Upgrade removes old monitoring and historical data without writing a marker",
        )
        var d: Device = Device()
        d.ip = "192.0.2.5"
        d.name = "Test printer"
        d.identity = "printer-one"
        d.port(631)
        d.hardware("001122334455", "NetBIOS reported adapter address")
        d.manufacturer = "Fixture manufacturer"
        d.model = "Fixture model"
        d.firstSeen = 1000
        d.lastSeen = 2000
        check(
            store.retainIfCurrent(store.token(), "test", Collections.singletonList(d), true),
            "Inventory saves",
        )
        var loaded: Device = (store.devices("test"))!!.get(0)
        check(
            ((loaded.ports.contains(631) && loaded.mac.equals("00:11:22:33:44:55")) &&
                loaded.model.equals(d.model)),
            "IP/MAC/model/port persistence",
        )
        check(!(loaded.seen), "Cached records are not marked live")
        check((store.devices("different-network"))!!.isEmpty(), "Inventory network isolation")
        var reused: Device = Device()
        reused.ip = d.ip
        check(
            store.retainIfCurrent(store.token(), "test", Collections.singletonList(reused), true),
            "Address reuse saves",
        )
        loaded = (store.devices("test"))!!.get(0)
        check(
            ((loaded.name.isEmpty() && loaded.mac.isEmpty()) && loaded.model.isEmpty()),
            "Reassigned IP never inherits old metadata",
        )
        var stale: Long = store.token()
        Store(target).clear()
        check(
            !(store.retainIfCurrent(stale, "test", Collections.singletonList(d), false)),
            "Cross-instance deletion rejects stale writer",
        )
        check(
            ((store.devices("test"))!!.isEmpty() &&
                ((store.last("test")).toLong() == (0).toLong())),
            "Deleted inventory/timestamp stays deleted",
        )
        check(noLegacyFiles(target), "Session reset never recreates removed legacy history")
        check(
            store.retainIfCurrent(store.token(), "test", Collections.singletonList(d), true),
            "Fresh scan after deletion saves",
        )
        store.clear()
        d.hardware("00:22:33:44:55:66", "AirPlay advertised device ID")
        check(
            (d.mac.equals("00:11:22:33:44:55") && d.details.containsKey("Other reported MAC")),
            "Advertised interface cannot replace NetBIOS MAC",
        )
        var pkg: PackageInfo =
            (target.getPackageManager())!!.getPackageInfo(
                "dev.netlens",
                ((((((((PackageManager.GET_ACTIVITIES).toInt() or
                                (PackageManager.GET_SERVICES).toInt()))
                            .toInt() or (PackageManager.GET_RECEIVERS).toInt()))
                        .toInt() or (PackageManager.GET_PROVIDERS).toInt()))
                    .toInt() or (PackageManager.GET_PERMISSIONS).toInt()),
            )
        check(
            (((((pkg.activities)!!.size == 1) && (pkg.services === null)) &&
                (pkg.receivers === null)) && (pkg.providers === null)),
            "Only launcher Activity remains",
        )
        check(
            ((pkg.requestedPermissions)!!.size == 7),
            "Exact seven-permission policy including optional foreground SSID access",
        )
        check(
            !((Arrays.asList(*pkg.requestedPermissions))!!.contains(
                "android.permission.ACCESS_BACKGROUND_LOCATION"
            )),
            "No background location permission",
        )
        check(
            ((((((target.getApplicationInfo())!!.flags).toInt() and
                (ApplicationInfo.FLAG_DEBUGGABLE).toInt())) == 0) &&
                (((((target.getApplicationInfo())!!.flags).toInt() and
                    (ApplicationInfo.FLAG_ALLOW_BACKUP).toInt())) == 0)),
            "Release disables debugging and backup",
        )
        activity = launch(true)
        waitForIdleSync()
        assertText("Your devices")
        assertText("Sample devices · Tap to use your Wi-Fi")
        assertText("Work computer")
        check(
            (!((texts())!!.contains("Your network toolkit")) &&
                !((texts())!!.contains("The little changes"))),
            "Tools and monitoring navigation removed",
        )
        capture("light-devices")
        setSearch("00:11:22:33:44:55")
        assertText("1 device group matching")
        check(!((texts())!!.contains("Office printer")), "MAC search filters device list")
        setSearch("")
        click("Work computer")
        assertText("Device details")
        assertText("00:11:22:33:44:55")
        assertText("WORK-PC")
        capture("light-details")
        click("Copy device details")
        onMain(
            callback99@{
                var c: ClipboardManager? =
                    (target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                var clip: ClipData? = (c)!!.getPrimaryClip()
                check(
                    ((clip !== null) &&
                        (((clip.getItemAt(0))!!.text)!!.toString())!!.contains(
                            "MAC: 00:11:22:33:44:55"
                        )),
                    "Copy includes observed IP and MAC",
                )
            }
        )
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForIdleSync()
        assertText("Your devices")
        description("Switch to dark mode")
        check(store.dark(), "Dark preference saved")
        capture("dark-devices")
        click("Work computer")
        capture("dark-details")
        description("Back to devices")
        click("Office printer")
        assertText("Not available")
        assertText("Android limits MAC access.")
        description("Back to devices")
        onMain(
            callback100@{
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
        waitForIdleSync()
        capture("dark-large-text")
        onMain(
            callback101@{
                var page: ScrollView? = findScroll((activity!!.window)!!.getDecorView())
                check(
                    ((page !== null) && (page.getHeight() > 100)),
                    "Large text leaves a scrollable content viewport",
                )
                check(
                    (((page)!!.getChildAt(0))!!.getHeight() > (page)!!.getHeight()),
                    "Large content scrolls rather than clipping to viewport",
                )
                (page)!!.fullScroll(View.FOCUS_DOWN)
            }
        )
        capture("dark-large-text-bottom")
        onMain(
            callback102@{
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
        onMain(
            callback103@{
                activity!!.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            }
        )
        SystemClock.sleep(1200)
        waitForIdleSync()
        capture("dark-landscape")
        assertText("Your devices")
        onMain(
            callback104@{
                activity!!.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            }
        )
        SystemClock.sleep(1200)
        description("Switch to light mode")
        check(!(store.dark()), "Light preference saved")
        click("Sample devices · Tap to use your Wi-Fi")
        check(
            !((texts())!!.contains("Sample devices · Tap to use your Wi-Fi")),
            "Sample exit removes sample data",
        )
        var live: NetworkState = NetworkState.current(target)
        check(!(live.ip.isEmpty()), "Actual emulator Wi-Fi has IPv4")
        scanCheck(target, live, (live.ip + "/32"), live.ip, true)
        if ((!(live.gateway.isEmpty()) && ((Address.bytes(live.gateway))!!.size == 4))) {
            scanCheck(target, live, (live.gateway + "/32"), live.gateway, false)
        }
        metadataFixture(target, live)
        deletionDuringScan(target, live, false)
        deletionDuringScan(target, live, true)
        onMain(
            callback105@{
                setField("range", (live.ip + "/32"))
                invoke("beginScan")
                invoke("resetSessionData")
            }
        )
        SystemClock.sleep(2500)
        waitForIdleSync()
        check(
            (store.devices(live.key))!!.isEmpty(),
            "Activity delete remains empty after in-flight completion",
        )
        onMain(
            callback106@{
                check(
                    (((field("devices") as List<*>?)))!!.isEmpty(),
                    "Activity discards deleted scan UI results",
                )
            }
        )
        check(
            ((((target.getSystemService(Context.JOB_SCHEDULER_SERVICE)
                        as android.app.job.JobScheduler?)))!!
                    .getAllPendingJobs())!!
                .isEmpty(),
            "No background jobs after upgrade/startup",
        )
        capture("live-empty")
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
            assertText("REPORTED HARDWARE")
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
                )
                description.invoke(
                    fixture,
                    described,
                    InetAddress.getByName(live.ip),
                    (base + "/good.xml"),
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

    private fun navigation(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        store.dark(false)
        var live: NetworkState = NetworkState.current(target)
        var fixture: MutableList<Device> = ArrayList()
        run {
            var n: Int = 1
            while ((n <= 45)) {
                var d: Device = Device()
                d.ip = ("192.0.2." + n)
                d.name = String.format(Locale.ROOT, "Test device %02d", n)
                d.model = (if (((n % 3) == 0)) "Fixture model" else "")
                d.lastSeen = System.currentTimeMillis()
                fixture.add(d)
                n++
            }
        }
        var sources: MutableMap<String, String> = LinkedHashMap()
        sources.put("mDNS fixture", "Truncated: unresolved service")
        sources.put("IPv4 checks", "OK")
        var report: ScanReport =
            ScanReport(
                "192.0.2.0/24",
                false,
                "One source was truncated.",
                254,
                254,
                System.currentTimeMillis(),
                sources,
            )
        store.retainIfCurrent(store.token(), live.key, fixture, report)
        activity = launch(false)
        waitForIdleSync()
        capture("hierarchy-light")
        assertText("Partial scan")
        assertText("45 device groups · 45 IPs")
        check(
            (!((texts())!!.contains("Checked 254")) &&
                !((texts())!!.contains("One source was truncated."))),
            "Technical coverage stays out of the main list",
        )
        onMain(
            callback114@{
                var heading: View? =
                    find((activity!!.window)!!.getDecorView(), "Your devices", false)
                var search: View? =
                    find((activity!!.window)!!.getDecorView(), "Search devices", true)
                var scan: View? = (field("scanButton") as View?)
                check(
                    ((top((scan)!!) < top((heading)!!)) && (top((heading)!!) < top((search)!!))),
                    "Scan controls precede devices heading and search",
                )
                var status: TextView? = (field("status") as TextView?)
                check(((status)!!.getLineCount() <= 2), "Saved scan summary uses at most two lines")
                check(
                    ((((((status)!!.getParent() as View?)))!!.getHeight()).toFloat() >=
                        ((48 * ((activity!!.getResources())!!.getDisplayMetrics())!!.density))
                            .toFloat()),
                    "Scan-details target is at least 48dp",
                )
                (status)!!.performClick()
            }
        )
        SystemClock.sleep(200)
        var dialog: AccessibilityNodeInfo? = waitForDialog("192.0.2.0/24")
        var dialogImage: Bitmap? = nullablePlatformResult((getUiAutomation())!!.takeScreenshot())
        if ((dialogImage !== null)) {
            save(dialogImage, "hierarchy-scan-details")
        }
        check(
            ((dialog !== null) &&
                !((dialog.findAccessibilityNodeInfosByText("192.0.2.0/24"))!!.isEmpty())),
            "Full range is available in scan details",
        )
        check(
            !(((dialog)!!.findAccessibilityNodeInfosByText("254 / 254"))!!.isEmpty()),
            "Full coverage is available in scan details",
        )
        check(
            !(((dialog)!!.findAccessibilityNodeInfosByText("What may be missing"))!!.isEmpty()),
            "Incomplete source is explained before technical details",
        )
        dialogClick("Show method details (2)")
        check(
            (dialogTexts())!!.contains("Truncated: unresolved service"),
            "Exact incomplete source reason remains available after expansion",
        )
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForIdleSync()
        var offset: IntArray = intArrayOf(0)
        onMain(
            callback115@{
                var item: View? =
                    (((field("deviceRows") as Map<String, View>?)))!!.get("192.0.2.25")
                var page: ScrollView? = (field("deviceScroll") as ScrollView?)
                (page)!!.scrollBy(0, ((top((item)!!) - top((page)!!)) - 120))
            }
        )
        waitForIdleSync()
        capture("navigation-before-details")
        onMain(callback116@{ offset[0] = rowOffset("192.0.2.25") })
        click("Test device 25")
        assertText("Device details")
        description("Back to devices")
        settle()
        onMain(
            callback117@{
                check(
                    (Math.abs((rowOffset("192.0.2.25") - offset[0])) <= 2),
                    "Toolbar Back restores the selected row at its original offset",
                )
            }
        )
        capture("navigation-return-arrow")
        click("Test device 25")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        onMain(
            callback118@{
                check(
                    (Math.abs((rowOffset("192.0.2.25") - offset[0])) <= 2),
                    "Android Back restores the selected row at its original offset",
                )
            }
        )
        click("Test device 25")
        onMain(
            callback119@{
                for (d in (field("devices") as List<Device>)) {
                    if ((Integer.parseInt(d.ip.substring((d.ip.lastIndexOf('.') + 1))) < 25)) {
                        d.model = "Newly discovered details that make this row taller"
                    }
                }
            }
        )
        description("Back to devices")
        settle()
        onMain(
            callback120@{
                check(
                    (Math.abs((rowOffset("192.0.2.25") - offset[0])) <= 2),
                    "Changing row heights above the selection does not move the return target",
                )
            }
        )
        description("Switch to dark mode")
        settle()
        onMain(
            callback121@{
                check(
                    (Math.abs((rowOffset("192.0.2.25") - offset[0])) <= 2),
                    "Appearance change preserves the visible row",
                )
            }
        )
        capture("navigation-return-dark")
        setSearch("Test device 2")
        onMain(
            callback122@{
                var page: ScrollView? = (field("deviceScroll") as ScrollView?)
                (page)!!.scrollTo(0, 400)
            }
        )
        settle()
        onMain(callback123@{ offset[0] = rowOffset("192.0.2.25") })
        click("Test device 25")
        description("Back to devices")
        settle()
        onMain(
            callback124@{
                check(
                    (Math.abs((rowOffset("192.0.2.25") - offset[0])) <= 2),
                    "Filtered list restores its selected row",
                )
            }
        )
        check(
            ((texts())!!.contains("Test device 2") && !((texts())!!.contains("Test device 01"))),
            "Search query and filtering survive Back",
        )
        setSearch("")
        onMain(
            callback125@{
                check(
                    ((((field("deviceScroll") as ScrollView?)))!!.getScrollY() == 0),
                    "Changing search starts at the first matching device",
                )
            }
        )
        onMain(
            callback126@{
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
        capture("hierarchy-dark-large-text")
        onMain(
            callback127@{
                check(
                    (((((field("deviceScroll") as ScrollView?)))!!.getChildAt(0))!!.getHeight() >
                        (((field("deviceScroll") as ScrollView?)))!!.getHeight()),
                    "Large text remains fully scrollable",
                )
            }
        )
        onMain(
            callback128@{
                var c: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                c.fontScale = 1f
                (activity!!.getResources())!!.updateConfiguration(
                    c,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                setField("demo", false)
                invoke("loadNetwork")
                invoke("resetListPosition")
                invoke("render")
            }
        )
        description("Switch to light mode")
        settle()
        onMain(
            callback129@{
                setField("scanning", true)
                setField("done", 100)
                setField("total", 254)
                invoke("render")
            }
        )
        settle()
        assertText("Scanning · 100 / 254 addresses")
        capture("hierarchy-scanning")
        onMain(
            callback130@{
                setField("scanning", false)
                invoke("resetSessionData")
            }
        )
        settle()
        capture("hierarchy-empty")
        assertText("Your devices will appear here")
    }

    private fun scanDialog(): Dialog? {
        return (field("scanDetailsDialog") as Dialog?)
    }

    private fun dialogTexts(): String {
        var text: StringBuilder = StringBuilder()
        onMain(callback131@{ collectVisible(((scanDialog())!!.window)!!.getDecorView(), text) })
        return text.toString()
    }

    private fun collectVisible(view: View, text: StringBuilder): Unit {
        if ((view.getVisibility() != View.VISIBLE)) {
            return
        }
        if ((view is TextView)) {
            (text.append((((view as TextView?)))!!.text))!!.append('\n')
        }
        if ((view is ViewGroup)) {
            run {
                var i: Int = 0
                while ((i < (((view as ViewGroup?)))!!.getChildCount())) {
                    collectVisible((((view as ViewGroup?)))!!.getChildAt(i), text)
                    i++
                }
            }
        }
    }

    private fun dialogClick(label: String): Unit {
        onMain(
            callback132@{
                var view: View? = find(((scanDialog())!!.window)!!.getDecorView(), label, false)
                check(((view !== null) && view.performClick()), ("Modal control: " + label))
            }
        )
        settle()
    }

    private fun captureDialog(name: String): Unit {
        settle()
        var expected: String =
            (if (name.startsWith("scan-details"))
                ((if (name.endsWith("sample")) "Sample results"
                else (if (name.endsWith("empty")) "Ready to scan" else "Scan details")))
            else
                (if ((name.startsWith("options-menu") || name.startsWith("options-wifi")))
                    "Wi-Fi details"
                else
                    (if (name.startsWith("options-range")) "Scan range"
                    else
                        (if (name.startsWith("options-clear")) "Clear current results?"
                        else "Privacy & device details"))))
        var current: AccessibilityNodeInfo? = waitForDialog(expected)
        var matches: List<AccessibilityNodeInfo>? =
            (if ((current === null)) java.util.Collections.emptyList()
            else current.findAccessibilityNodeInfosByText(expected))
        check(
            ((matches)!!.stream())!!.anyMatch(
                callback133@{ n -> "dev.netlens".contentEquals(n.getPackageName()) }
            ),
            ((("Capture shows intended DeviceLens state: " + name) + "; root=") +
                ((if ((current === null)) "null" else current.getPackageName()))),
        )
        var image: Bitmap? = nullablePlatformResult((getUiAutomation())!!.takeScreenshot())
        check((image !== null), "Modal screenshot available")
        if ((image !== null)) {
            save(image, name)
        }
    }

    private fun assertModalFits(): Unit {
        onMain(
            callback134@{
                var decor: View = ((scanDialog())!!.window)!!.getDecorView()
                var done: View? = find(decor, "Done", false)
                check(
                    ((decor.getHeight()).toFloat() <=
                        ((((activity!!.getResources())!!.getDisplayMetrics())!!.heightPixels * .9f))
                            .toFloat()),
                    "Modal fits within the available screen height",
                )
                check(
                    ((done !== null) &&
                        ((done.getHeight()).toFloat() >=
                            ((48 * ((activity!!.getResources())!!.getDisplayMetrics())!!.density))
                                .toFloat())),
                    "Done remains at least a 48dp target",
                )
                check(
                    ((top((done)!!) >= top(decor)) &&
                        ((top((done)!!) + (done)!!.getHeight()) <=
                            (top(decor) + decor.getHeight()))),
                    "Done remains inside the modal viewport",
                )
            }
        )
    }

    private fun failureReporting(): Unit {
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
                "Device details are partial: 2 discovery sources failed, 1 truncated. Tap scan status for details.",
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
            while ((i < 1000)) {
                longValue.append('x')
                i++
            }
        }
        oversized.put(source, longValue.toString())
        check(
            ((ScanReport("", false, "", 0, 0, 1, oversized).sources.get(source))!!.length == 512),
            "Untrusted overlong source values remain capped",
        )
        check(
            store.retainIfCurrent(store.token(), live.key, Collections.emptyList(), report),
            "Failure fixture retains a report without inventing devices",
        )
        activity = launch(false)
        settle()
        onMain(callback135@{ invoke("scanDetails") })
        settle()
        var text: String = dialogTexts()
        check(
            text.contains("Needs attention: HomeKit accessories, Web services and 1 other method"),
            "Summary names affected methods and limits the list with readable singular wording",
        )
        check(
            (text.contains("Partial scan") && !(text.contains("timeout attempts 2"))),
            "Named summary preserves Partial status and keeps raw diagnostics collapsed",
        )
        assertModalFits()
        captureDialog("scan-details-failure-reporting-light")
        dialogClick("Show method details (3)")
        check(
            ((dialogTexts())!!.contains((full)!!) &&
                (dialogTexts())!!.contains("_fixture-unknown._tcp.")),
            "Expanded method details expose the complete timeout evidence and unknown service type",
        )
        dialogClick("Done")
        description("Switch to dark mode")
        onMain(callback136@{ invoke("scanDetails") })
        settle()
        check(
            (dialogTexts())!!.contains(
                "Needs attention: HomeKit accessories, Web services and 1 other method"
            ),
            "Dark appearance retains the same failure summary",
        )
        assertModalFits()
        captureDialog("scan-details-failure-reporting-dark")
        dialogClick("Done")
        onMain(
            callback137@{
                var config: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                config.fontScale = 2f
                (activity!!.getResources())!!.updateConfiguration(
                    config,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                invoke("render")
                invoke("scanDetails")
            }
        )
        settle()
        assertModalFits()
        captureDialog("scan-details-failure-reporting-large-text")
        dialogClick("Show method details (3)")
        check(
            (dialogTexts())!!.contains((full)!!),
            "Large text retains full diagnostics in scrollable method details",
        )
        dialogClick("Done")
        var ok: MutableMap<String, String> = LinkedHashMap()
        ok.put("IPv4 checks", "OK")
        ok.put("IPv6 service checks", "Not used · No separate IPv6 echo checks were needed")
        store.retainIfCurrent(
            store.token(),
            live.key,
            Collections.emptyList(),
            ScanReport("Fixture coverage", true, "", 254, 254, System.currentTimeMillis(), ok),
        )
        onMain(
            callback138@{
                invoke("loadNetwork")
                invoke("render")
                invoke("scanDetails")
            }
        )
        settle()
        check(
            ((dialogTexts())!!.contains("Complete scan") &&
                !((dialogTexts())!!.contains("Needs attention:"))),
            "A subsequent successful current scan does not retain a previous failure summary",
        )
        dialogClick("Show method details (2)")
        check(
            ((dialogTexts())!!.contains(
                "IPv6 replies received by other discovery methods can still appear"
            ) && !((dialogTexts())!!.contains("No usable advertised IPv6"))),
            "An unused extra-echo method does not imply IPv6 devices were absent",
        )
        dialogClick("Done")
        onMain(callback139@{ activity!!.finish() })
    }

    private fun scanDetailsFlow(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        store.dark(false)
        var live: NetworkState = NetworkState.current(target)
        var sources: MutableMap<String, String> = LinkedHashMap()
        sources.put("IPv4 checks", "OK")
        sources.put(
            "UPnP descriptions",
            "Failed · 1 responding device(s) supplied no usable description; see device details · failed attempts 1, received 6, requests 7",
        )
        sources.put("mDNS _googlecast._tcp.", "OK · found 16, resolved 16")
        sources.put("WS-Discovery", "OK · requests 4")
        sources.put("Reverse DNS", "Not used · No on-link DNS server")
        var report: ScanReport =
            ScanReport(
                "192.0.2.0/24 · IPv4 + IPv6 advertised services",
                false,
                "Device details are partial: 1 discovery source failed, 0 truncated. Tap scan status for details.",
                254,
                254,
                System.currentTimeMillis(),
                sources,
            )
        check(
            store.retainIfCurrent(store.token(), live.key, Collections.emptyList(), report),
            "Partial fixture saves without fabricating devices",
        )
        activity = launch(false)
        waitForIdleSync()
        onMain(callback140@{ invoke("scanDetails") })
        settle()
        var text: String = dialogTexts()
        check(
            (text.contains("Partial scan") && text.contains("Devices or details may be missing")),
            "Partial status explains its practical meaning",
        )
        check(
            (text.contains("254 / 254 addresses checked") && text.contains("not a device count")),
            "Address coverage is distinguished from device count",
        )
        check(
            (text.contains("192.0.2.0/24") && text.contains("IPv6 devices are also included")),
            "Actual range and IPv6 discovery limit remain disclosed",
        )
        check(
            ((text.contains("What may be missing") && text.contains("Device descriptions")) &&
                text.contains("description could not be read")),
            "Description failure has a readable explanation",
        )
        check(
            (!(text.contains("failed attempts 1")) && !(text.contains("_googlecast._tcp."))),
            "Raw protocol diagnostics are collapsed initially",
        )
        check(
            text.contains("3 finished · 1 failed · 1 not used"),
            "Finished, failed and unused methods are distinguished",
        )
        assertModalFits()
        captureDialog("scan-details-partial-light")
        dialogClick("Show method details (5)")
        text = dialogTexts()
        check(
            ((text.contains("Google Cast devices") && text.contains("mDNS _googlecast._tcp.")) &&
                text.contains("failed attempts 1")),
            "Expanded methods retain readable names and exact troubleshooting data",
        )
        check(
            ((text.contains("WS-Discovery") && text.contains("requests 4")) &&
                text.contains("no reply does not mean a device is offline")),
            "A finished discovery method does not claim that devices replied",
        )
        captureDialog("scan-details-expanded-light")
        dialogClick("Hide method details")
        check(
            !((dialogTexts())!!.contains("failed attempts 1")),
            "Collapse removes diagnostics from the visible content",
        )
        dialogClick("Done")
        check(!((scanDialog())!!.isShowing()), "Done dismisses the modal")
        assertText("Your devices")
        description("Switch to dark mode")
        onMain(callback141@{ invoke("scanDetails") })
        settle()
        assertModalFits()
        captureDialog("scan-details-partial-dark")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        check(!((scanDialog())!!.isShowing()), "Android Back dismisses the modal")
        var ok: MutableMap<String, String> = LinkedHashMap()
        ok.put("IPv4 checks", "OK")
        ok.put("WS-Discovery", "OK · requests 4")
        var complete: ScanReport =
            ScanReport(
                "192.0.2.0/24 · IPv4 + IPv6 advertised services",
                true,
                "Scan finished. Silent or isolated devices may be missing.",
                254,
                254,
                System.currentTimeMillis(),
                ok,
            )
        store.retainIfCurrent(store.token(), live.key, Collections.emptyList(), complete)
        onMain(
            callback142@{
                invoke("loadNetwork")
                invoke("render")
                invoke("scanDetails")
            }
        )
        settle()
        text = dialogTexts()
        check(
            (text.contains("Complete scan") &&
                text.contains("does not guarantee that every device was found")),
            "Complete means planned work finished, not guaranteed device completeness",
        )
        check(
            (!(text.contains("What may be missing")) && text.contains("2 finished")),
            "Successful scan omits a failure section",
        )
        captureDialog("scan-details-complete-dark")
        dialogClick("Done")
        onMain(
            callback143@{
                setField("scanning", true)
                setField("done", 42)
                setField("total", 254)
                invoke("scanDetails")
            }
        )
        settle()
        check(
            ((dialogTexts())!!.contains("Scan in progress") &&
                (dialogTexts())!!.contains("42 / 254 addresses checked")),
            "Running modal shows current progress",
        )
        dialogClick("Show method details (2)")
        onMain(callback144@{ setField("done", 84) })
        SystemClock.sleep(1000)
        waitForIdleSync()
        check(
            ((dialogTexts())!!.contains("84 / 254 addresses checked") &&
                (dialogTexts())!!.contains("Hide method details")),
            "Live progress updates without losing disclosure state",
        )
        onMain(
            callback145@{
                setField("scanning", false)
                invoke("render")
            }
        )
        SystemClock.sleep(1000)
        waitForIdleSync()
        check(
            ((dialogTexts())!!.contains("Complete scan") &&
                !((dialogTexts())!!.contains("Scan in progress"))),
            "An open modal follows scan completion",
        )
        dialogClick("Done")
        onMain(
            callback146@{
                var config: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                config.fontScale = 2f
                (activity!!.getResources())!!.updateConfiguration(
                    config,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                invoke("render")
                invoke("scanDetails")
            }
        )
        settle()
        assertModalFits()
        captureDialog("scan-details-dark-large-text")
        onMain(
            callback147@{
                check(
                    (((findScroll(((scanDialog())!!.window)!!.getDecorView()))!!.getChildAt(0))!!
                        .getHeight() >
                        (findScroll(((scanDialog())!!.window)!!.getDecorView()))!!.getHeight()),
                    "Large text can scroll independently of the fixed Done action",
                )
            }
        )
        dialogClick("Done")
        onMain(
            callback148@{
                var config: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                config.fontScale = 1f
                (activity!!.getResources())!!.updateConfiguration(
                    config,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                activity!!.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            }
        )
        SystemClock.sleep(1200)
        waitForIdleSync()
        onMain(
            callback149@{
                var config: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                config.fontScale = 2f
                (activity!!.getResources())!!.updateConfiguration(
                    config,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                invoke("render")
                invoke("scanDetails")
            }
        )
        settle()
        assertModalFits()
        captureDialog("scan-details-dark-landscape-large-text")
        dialogClick("Done")
        onMain(
            callback150@{
                var config: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                config.fontScale = 1f
                (activity!!.getResources())!!.updateConfiguration(
                    config,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                activity!!.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            }
        )
        SystemClock.sleep(1200)
        waitForIdleSync()
        onMain(
            callback151@{
                setField("demo", true)
                invoke("render")
                invoke("scanDetails")
            }
        )
        settle()
        text = dialogTexts()
        check(
            (((text.contains("Sample results") && !(text.contains("Saved "))) &&
                !(text.contains("254 / 254"))) && !(text.contains("Show method details"))),
            "Sample mode cannot present saved real-network coverage or sources",
        )
        captureDialog("scan-details-sample")
        dialogClick("Done")
        onMain(
            callback152@{
                setField("demo", false)
                invoke("resetSessionData")
                invoke("scanDetails")
            }
        )
        settle()
        check(
            ((dialogTexts())!!.contains("Ready to scan") &&
                !((dialogTexts())!!.contains("Complete scan"))),
            "First-use modal does not invent scan completion",
        )
        captureDialog("scan-details-empty")
        dialogClick("Done")
        store.retainIfCurrent(store.token(), live.key, Collections.emptyList(), report)
        onMain(callback153@{ activity!!.finish() })
        settle()
        activity = launch(false)
        waitForIdleSync()
        onMain(
            callback154@{
                check(
                    (!(activity!!.isDestroyed()) && !(activity!!.isFinishing())),
                    "Lifecycle check uses an active Activity after configuration fixtures",
                )
                invoke("scanDetails")
            }
        )
        settle()
        home()
        var stopped: Long = (SystemClock.uptimeMillis() + 4000)
        while (((SystemClock.uptimeMillis() < stopped) && (scanDialog())!!.isShowing())) {
            SystemClock.sleep(100)
        }
        waitForIdleSync()
        onMain(
            callback155@{
                check(
                    !((scanDialog())!!.isShowing()),
                    "Leaving DeviceLens with Home dismisses the modal",
                )
            }
        )
        onMain(callback156@{ activity!!.finish() })
    }

    private fun optionDialog(): AppDialog? {
        return (field("optionsDialog") as AppDialog?)
    }

    private fun optionTexts(): String {
        var text: StringBuilder = StringBuilder()
        onMain(callback157@{ collectVisible(((optionDialog())!!.window)!!.getDecorView(), text) })
        return text.toString()
    }

    private fun optionClick(label: String): Unit {
        onMain(
            callback158@{
                var view: View? = find(((optionDialog())!!.window)!!.getDecorView(), label, false)
                check(((view !== null) && view.performClick()), ("Option dialog control: " + label))
            }
        )
        settle()
    }

    private fun menuChoose(label: String): Unit {
        onMain(
            callback159@{
                var popup: PopupWindow? = (field("optionsMenu") as PopupWindow?)
                var view: View? = find((popup)!!.getContentView(), label, false)
                check((view !== null), ("Menu item: " + label))
                while ((!((view)!!.isClickable()) && ((view)!!.getParent() is View))) {
                    view = ((view)!!.getParent() as View?)
                }
                check((view)!!.performClick(), ("Menu action: " + label))
            }
        )
        settle()
    }

    private fun optionFits(): Unit {
        onMain(
            callback160@{
                var decor: View = ((optionDialog())!!.window)!!.getDecorView()
                var done: View? = find(decor, "Done", false)
                check(
                    ((decor.getHeight()).toFloat() <=
                        ((((activity!!.getResources())!!.getDisplayMetrics())!!.heightPixels * .9f))
                            .toFloat()),
                    "Option dialog stays within screen height",
                )
                if ((done !== null)) {
                    check(
                        (((top(done) + done.getHeight()) <= (top(decor) + decor.getHeight())) &&
                            ((done.getHeight()).toFloat() >=
                                ((48 *
                                        ((activity!!.getResources())!!.getDisplayMetrics())!!
                                            .density))
                                    .toFloat())),
                        "Option dialog Done is a visible 48dp target",
                    )
                }
            }
        )
    }

    private fun legacyFile(c: Context): java.io.File {
        return java.io.File(c.getDataDir(), "shared_prefs/netlens.xml")
    }

    private fun noLegacyFiles(c: Context): Boolean {
        return (((!((legacyFile(c))!!.exists()) &&
            !(java.io.File(((legacyFile(c))!!.path + ".bak")).exists())) &&
            !((legacyFile(c.createDeviceProtectedStorageContext()))!!.exists())) &&
            !(java.io
                .File(((legacyFile(c.createDeviceProtectedStorageContext()))!!.path + ".bak"))
                .exists()))
    }

    private fun brandBitmap(
        original: android.graphics.drawable.Drawable,
        size: Int,
    ): android.graphics.Bitmap {
        var d: android.graphics.drawable.Drawable =
            ((original.getConstantState())!!.newDrawable((getTargetContext())!!.getResources()))!!
                .mutate()
        d.setBounds(0, 0, size, size)
        var b: android.graphics.Bitmap =
            android.graphics.Bitmap.createBitmap(
                size,
                size,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
        d.draw(android.graphics.Canvas(b))
        return b
    }

    private fun sameBrandRendering(a: Bitmap, b: Bitmap): Boolean {
        if (((a.width != b.width) || (a.getHeight() != b.getHeight()))) {
            return false
        }
        // VectorDrawable's platform cache can rasterize the same vector at fractional
        // scales differently. The unchanged Java release also differs by one edge pixel.
        var intersection: Int = 0
        var union: Int = 0
        var areaA: Int = 0
        var areaB: Int = 0
        var alphaError: Long = 0
        run {
            var y: Int = 0
            while ((y < a.getHeight())) {
                run {
                    var x: Int = 0
                    while ((x < a.width)) {
                        var p: Int = a.getPixel(x, y)
                        var q: Int = b.getPixel(x, y)
                        var pa: Int = android.graphics.Color.alpha(p)
                        var qa: Int = android.graphics.Color.alpha(q)
                        var ap: Boolean = (pa > 127)
                        var bp: Boolean = (qa > 127)
                        if (ap) {
                            areaA++
                        }
                        if (bp) {
                            areaB++
                        }
                        if ((ap && bp)) {
                            intersection++
                        }
                        if ((ap || bp)) {
                            union++
                        }
                        alphaError += Math.abs((pa - qa))
                        if (
                            ((ap && bp) &&
                                ((((Math.abs(
                                    (android.graphics.Color.red(p) - android.graphics.Color.red(q))
                                ) > 2) ||
                                    (Math.abs(
                                        (android.graphics.Color.green(p) -
                                            android.graphics.Color.green(q))
                                    ) > 2)) ||
                                    (Math.abs(
                                        (android.graphics.Color.blue(p) -
                                            android.graphics.Color.blue(q))
                                    ) > 2))))
                        ) {
                            return false
                        }
                        x++
                    }
                }
                y++
            }
        }
        return ((((union > 0) && ((intersection / (union).toDouble()) >= .97)) &&
            ((Math.abs((areaA - areaB)) / (Math.max(1, areaB)).toDouble()) < .01)) &&
            (((alphaError / (((a.width * a.getHeight()))).toDouble())).toDouble() < (3).toDouble()))
    }

    private fun branding(): Unit {
        var c: Context = getTargetContext()
        var info: android.content.pm.ApplicationInfo =
            (c.getPackageManager())!!.getApplicationInfo("dev.netlens", 0)
        check(
            ((info.loadLabel(c.getPackageManager()))!!.toString())!!.equals("Network Lens"),
            "Installed launcher and settings label is Network Lens",
        )
        var loaded: android.graphics.drawable.Drawable = info.loadIcon(c.getPackageManager())
        check(
            (loaded is android.graphics.drawable.AdaptiveIconDrawable),
            "Launcher uses the adaptive artwork",
        )
        var icon: android.graphics.drawable.AdaptiveIconDrawable? =
            (((loaded.getConstantState())!!.newDrawable(c.getResources()))!!.mutate()
                as android.graphics.drawable.AdaptiveIconDrawable?)
        var size: Int = 512
        (icon)!!.setBounds(0, 0, size, size)
        var art: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        var canvas: android.graphics.Canvas = android.graphics.Canvas(art)
        ((icon)!!.background)!!.draw(canvas)
        ((icon)!!.getForeground())!!.draw(canvas)
        save(art.copy(Bitmap.Config.ARGB_8888, false), "brand-full-bleed")
        var opaque: Int = 0
        var white: Int = 0
        run {
            var y: Int = 0
            while ((y < size)) {
                run {
                    var x: Int = 0
                    while ((x < size)) {
                        var color: Int = art.getPixel(x, y)
                        if ((android.graphics.Color.alpha(color) == 255)) {
                            opaque++
                        }
                        if (
                            (((android.graphics.Color.red(color) > 235) &&
                                (android.graphics.Color.green(color) > 235)) &&
                                (android.graphics.Color.blue(color) > 235))
                        ) {
                            white++
                        }
                        x++
                    }
                }
                y++
            }
        }
        check(
            ((opaque == (size * size)) && (white == 0)),
            "Icon artwork fills its entire surface with no white field",
        )
        var mark: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        ((icon)!!.getForeground())!!.draw(android.graphics.Canvas(mark))
        for (shape in arrayOf("circle", "squircle", "rounded-square")) {
            var masked: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            var mc: android.graphics.Canvas = android.graphics.Canvas(masked)
            var path: android.graphics.Path = android.graphics.Path()
            if (shape.equals("circle")) {
                path.addCircle(
                    (size / 2f),
                    (size / 2f),
                    (size / 2f),
                    android.graphics.Path.Direction.CW,
                )
            } else {
                if (shape.equals("rounded-square")) {
                    path.addRoundRect(
                        (0).toFloat(),
                        (0).toFloat(),
                        (size).toFloat(),
                        (size).toFloat(),
                        (112).toFloat(),
                        (112).toFloat(),
                        android.graphics.Path.Direction.CW,
                    )
                } else {
                    path.moveTo((256).toFloat(), (0).toFloat())
                    path.cubicTo(
                        (480).toFloat(),
                        (0).toFloat(),
                        (512).toFloat(),
                        (32).toFloat(),
                        (512).toFloat(),
                        (256).toFloat(),
                    )
                    path.cubicTo(
                        (512).toFloat(),
                        (480).toFloat(),
                        (480).toFloat(),
                        (512).toFloat(),
                        (256).toFloat(),
                        (512).toFloat(),
                    )
                    path.cubicTo(
                        (32).toFloat(),
                        (512).toFloat(),
                        (0).toFloat(),
                        (480).toFloat(),
                        (0).toFloat(),
                        (256).toFloat(),
                    )
                    path.cubicTo(
                        (0).toFloat(),
                        (32).toFloat(),
                        (32).toFloat(),
                        (0).toFloat(),
                        (256).toFloat(),
                        (0).toFloat(),
                    )
                    path.close()
                }
            }
            mc.clipPath(path)
            mc.drawBitmap(art, 0f, 0f, null)
            var clipped: Boolean = false
            run {
                var y: Int = 0
                while ((y < size)) {
                    run {
                        var x: Int = 0
                        while ((x < size)) {
                            if (
                                ((android.graphics.Color.alpha(mark.getPixel(x, y)) > 200) &&
                                    (android.graphics.Color.alpha(masked.getPixel(x, y)) < 200))
                            ) {
                                clipped = true
                            }
                            x++
                        }
                    }
                    y++
                }
            }
            check(
                !(clipped),
                (("The longer handle and Wi-Fi symbol survive the " + shape) + " mask"),
            )
            save(masked, ("brand-" + shape))
        }
        if ((Build.VERSION.SDK_INT >= 33)) {
            var mono: android.graphics.drawable.Drawable? = (icon)!!.getMonochrome()
            check((mono !== null), "Android themed icons have a dedicated silhouette")
            (mono)!!.setBounds(((icon)!!.getForeground())!!.getBounds())
            var b: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            (mono)!!.draw(android.graphics.Canvas(b))
            check(
                ((android.graphics.Color.alpha(b.getPixel(0, 0)) == 0) &&
                    (android.graphics.Color.alpha(b.getPixel(256, 256)) == 0)),
                "The themed layer has genuine empty space, with no background disc",
            )
            save(b, "brand-monochrome")
        }
        var store: Store = Store(c)
        store.dark(false)
        activity = launch(true)
        settle()
        for (theme in arrayOf("light", "dark")) {
            if (theme.equals("dark")) {
                description("Switch to dark mode")
            }
            assertText("Network Lens")
            onMain(
                callback161@{
                    var brand: ImageView? =
                        nullablePlatformResult(
                            ((activity!!.window)!!.getDecorView())!!.findViewWithTag("app-brand")
                        )
                    check(
                        ((brand !== null) && (brand.getVisibility() == View.VISIBLE)),
                        "The main-screen header includes the brand image",
                    )
                    check(
                        ((((brand)!!.width /
                                ((activity!!.getResources())!!.getDisplayMetrics())!!.density))
                            .toFloat() > (40).toFloat()),
                        "Header logo is larger than the former 40dp badge",
                    )
                    check(
                        ((brand)!!.getImageTintList() !== null),
                        (("The transparent logo follows the readable theme accent in " + theme) +
                            " mode"),
                    )
                    var shared: android.graphics.drawable.Drawable =
                        (((activity!!.getDrawable(info.icon)
                                as android.graphics.drawable.AdaptiveIconDrawable?)))!!
                            .getForeground()
                    var reference: android.graphics.drawable.Drawable =
                        android.graphics.drawable.InsetDrawable(shared, -(0.25f))
                    reference.setTintList((brand)!!.getImageTintList())
                    var actual: Bitmap = brandBitmap((brand)!!.drawable, 128)
                    var expected: Bitmap = brandBitmap(reference, 128)
                    try {
                        save(actual.copy(Bitmap.Config.ARGB_8888, false), ("brand-header-" + theme))
                        save(
                            expected.copy(Bitmap.Config.ARGB_8888, false),
                            ("brand-launcher-" + theme),
                        )
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    }
                    check(
                        sameBrandRendering(actual, expected),
                        (("Header matches the launcher foreground geometry and tint without its background or mask in " +
                            theme) + " mode"),
                    )
                }
            )
            capture(("branding-" + theme))
        }
        onMain(
            callback162@{
                var cfg: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                cfg.fontScale = 2f
                (activity!!.getResources())!!.updateConfiguration(
                    cfg,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                invoke("render")
            }
        )
        settle()
        assertText("Network Lens")
        capture("branding-dark-large-text")
        onMain(
            callback163@{
                var cfg: Configuration =
                    Configuration((activity!!.getResources())!!.getConfiguration())
                cfg.fontScale = 1f
                (activity!!.getResources())!!.updateConfiguration(
                    cfg,
                    (activity!!.getResources())!!.getDisplayMetrics(),
                )
                activity!!.finish()
            }
        )
        waitForIdleSync()
    }

    private fun legacySeed(): Unit {
        var c: Context = getTargetContext()
        for (storage in arrayOf(c, c.createDeviceProtectedStorageContext())) {
            check(
                ((((((storage.getSharedPreferences("netlens", 0))!!.edit())!!.putBoolean(
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
        var monitor: ActivityMonitor = addMonitor("dev.netlens.MainActivity", null, false)
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
        onMain(
            callback166@{
                var popup: PopupWindow? = (field("optionsMenu") as PopupWindow?)
                check(
                    (find((popup)!!.getContentView(), "Delete saved data", false) === null),
                    "The main menu has no delete action",
                )
            }
        )
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

    private fun settingsScrollRecreation(): Unit {
        activity = launch(false)
        settle()
        description("Options")
        menuChoose("Settings")
        onMain(
            callback168@{
                (((field("settingsScroll") as ScrollView?)))!!.fullScroll(View.FOCUS_DOWN)
            }
        )
        settle()
        var before: Int = (((field("settingsScroll") as ScrollView?)))!!.getScrollY()
        check((before > 100), "Settings is genuinely scrolled before recreation")
        var monitor: ActivityMonitor = addMonitor("dev.netlens.MainActivity", null, false)
        onMain(callback169@{ activity!!.recreate() })
        var replacement: Activity? =
            nullablePlatformResult(waitForMonitorWithTimeout(monitor, 8000))
        removeMonitor(monitor)
        check((replacement !== null), "Activity recreation completed")
        activity = replacement
        settle()
        var after: Int = (((field("settingsScroll") as ScrollView?)))!!.getScrollY()
        check(
            (Math.abs((after - before)) <= 2),
            ((("Settings scroll before=" + before) + ", after=") + after),
        )
    }

    private fun settingsClick(label: String): Unit {
        onMain(
            callback170@{
                var v: View? = find((activity!!.window)!!.getDecorView(), label, false)
                check((v !== null), ("Settings control exists: " + label))
                (v)!!.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, (v)!!.width, (v)!!.getHeight()),
                    true,
                )
            }
        )
        settle()
        onMain(
            callback171@{
                var v: View? = find((activity!!.window)!!.getDecorView(), label, false)
                var page: ScrollView? = (field("settingsScroll") as ScrollView?)
                check(
                    ((top((v)!!) >= top((page)!!)) &&
                        ((top((v)!!) + (v)!!.getHeight()) <=
                            (top((page)!!) + (page)!!.getHeight()))),
                    ("Settings action is reachable: " + label),
                )
                check((v)!!.performClick(), ("Settings action: " + label))
            }
        )
        settle()
    }

    private fun settingsBack(): Unit {
        description(
            (if (((field("selected") as Device?) === null)) "Back to devices"
            else "Back to device details")
        )
        settle()
    }

    private fun optionsFlow(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        store.dark(false)
        var live: NetworkState = NetworkState.current(target)
        var fixture: MutableList<Device> = ArrayList()
        run {
            var n: Int = 1
            while ((n <= 35)) {
                var d: Device = Device()
                d.ip = ("192.0.2." + n)
                d.name = String.format(Locale.ROOT, "Settings device %02d", n)
                d.seen = true
                fixture.add(d)
                n++
            }
        }
        store.retainIfCurrent(store.token(), live.key, fixture, true)
        activity = launch(false)
        settle()
        description("Options")
        captureDialog("options-menu-light")
        onMain(
            callback172@{
                var popup: PopupWindow? = (field("optionsMenu") as PopupWindow?)
                var items: ViewGroup? =
                    (((((popup)!!.getContentView() as ScrollView?)))!!.getChildAt(0) as ViewGroup?)
                check(
                    ((((items)!!.getChildCount() == 2) &&
                        (find((items)!!, "Wi-Fi details", false) !== null)) &&
                        (find((items)!!, "Settings", false) !== null)),
                    "Menu contains exactly Wi-Fi details and Settings",
                )
                check(
                    (((find((items)!!, "Wi-Fi name", false) === null) &&
                        (find((items)!!, "Light appearance", false) === null)) &&
                        (find((items)!!, "Dark appearance", false) === null)),
                    "Redundant name and appearance actions are absent",
                )
            }
        )
        menuChoose("Wi-Fi details")
        check(
            (((optionTexts())!!.contains(live.ip) &&
                (optionTexts())!!.contains("Router address")) &&
                (optionTexts())!!.contains("Name servers (DNS)")),
            "Connection details retains actual addresses",
        )
        optionFits()
        captureDialog("options-wifi-light")
        if (
            (target.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED)
        ) {
            optionClick("Allow Wi-Fi name")
            check(
                ((optionTexts())!!.contains("GPS coordinates") &&
                    (optionTexts())!!.contains("optional")),
                "Missing-name access explains its optional purpose",
            )
            optionClick("Not now")
        } else {
            optionClick("Done")
        }
        description("Options")
        menuChoose("Settings")
        check(
            ((field("showingSettings") as Boolean) && (field("optionsMenu") === null)),
            "Settings is a screen, not a second overflow menu",
        )
        assertText("Scanning")
        assertText("Permissions")
        assertText("Privacy & about")
        check(!((field("scanning") as Boolean)), "Opening settings never starts discovery")
        check(
            (texts())!!.contains(
                (if ((Build.VERSION.SDK_INT >= 37)) "Allowed" else "No permission prompt")
            ),
            "Network permission status matches this Android version",
        )
        check(
            ((texts())!!.contains("Open Android settings") &&
                (texts())!!.contains(
                    ("Version " +
                        (((getTargetContext())!!.getPackageManager())!!.getPackageInfo(
                                "dev.netlens",
                                0,
                            ))!!
                            .versionName)
                )),
            "System settings link is explicit and app version is visible",
        )
        capture("settings-light")
        settingsClick("Scan range")
        onMain(
            callback173@{
                (((find(((optionDialog())!!.window)!!.getDecorView(), "Custom IPv4 range", true)
                        as EditText?)))!!
                    .setText("not-a-range")
            }
        )
        optionClick("Save range")
        check(
            ((optionTexts())!!.contains("Enter an IPv4 address and prefix") &&
                (field("range"))!!.equals("")),
            "Invalid range stays open without changing the setting",
        )
        onMain(
            callback174@{
                (((find(((optionDialog())!!.window)!!.getDecorView(), "Custom IPv4 range", true)
                        as EditText?)))!!
                    .setText("203.0.113.0/24")
            }
        )
        optionClick("Save range")
        check(
            (optionTexts())!!.contains("inside your current Wi-Fi"),
            "Off-network custom range is rejected",
        )
        captureDialog("options-range-error-light")
        onMain(
            callback175@{
                (((find(((optionDialog())!!.window)!!.getDecorView(), "Custom IPv4 range", true)
                        as EditText?)))!!
                    .setText((live.ip + "/32"))
            }
        )
        optionClick("Save range")
        check(
            (((field("showingSettings") as Boolean) &&
                (field("range"))!!.equals((live.ip + "/32"))) &&
                (texts())!!.contains((live.ip + "/32"))),
            "Saving returns to settings with the current session range",
        )
        settingsClick("Scan range")
        onMain(
            callback176@{
                (((find(((optionDialog())!!.window)!!.getDecorView(), "Custom IPv4 range", true)
                        as EditText?)))!!
                    .setText("")
            }
        )
        optionClick("Cancel")
        check((field("range"))!!.equals((live.ip + "/32")), "Cancel preserves the range")
        settingsClick("Scan range")
        onMain(
            callback177@{
                (((find(((optionDialog())!!.window)!!.getDecorView(), "Custom IPv4 range", true)
                        as EditText?)))!!
                    .setText("")
            }
        )
        optionClick("Save range")
        check((field("range"))!!.equals(""), "Empty range restores automatic scanning")
        settingsClick("Privacy & device details")
        check(
            ((optionTexts())!!.contains("Session-only results") &&
                (optionTexts())!!.contains("Why some MAC addresses are unavailable")),
            "Privacy and discovery explanations remain available",
        )
        optionFits()
        optionClick("Done")
        check((field("showingSettings") as Boolean), "Closing privacy returns to Settings")
        settingsClick("Open Android settings")
        var until: Long = (SystemClock.uptimeMillis() + 5000)
        var focused: AccessibilityNodeInfo? = null
        while ((SystemClock.uptimeMillis() < until)) {
            focused = (getUiAutomation())!!.getRootInActiveWindow()
            if (
                ((focused !== null) &&
                    "com.android.settings".contentEquals(focused!!.getPackageName()))
            ) {
                break
            }
            SystemClock.sleep(100)
        }
        check(
            ((focused !== null) &&
                "com.android.settings".contentEquals(focused!!.getPackageName())),
            "Explicit link opens Android's actual app settings",
        )
        check(
            (getUiAutomation())!!.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            ),
            "System Back returns from Android settings",
        )
        settle()
        check(
            ((field("showingSettings") as Boolean) && !((field("scanning") as Boolean))),
            "Returning from Android settings retains Settings without starting a scan",
        )
        description("Switch to dark mode")
        check(
            (store.dark() && (field("showingSettings") as Boolean)),
            "Header theme icon works on Settings",
        )
        onMain(callback178@{ (((field("settingsScroll") as ScrollView?)))!!.scrollTo(0, 0) })
        settle()
        capture("settings-dark")
        settingsClick("Clear current results")
        optionFits()
        captureDialog("options-clear-dark")
        optionClick("Cancel")
        check(((store.devices(live.key))!!.size == 35), "Cancel keeps existing results")
        settingsClick("Clear current results")
        optionClick("Clear results")
        check(
            ((store.dark() && (store.devices(live.key))!!.isEmpty()) &&
                (field("showingSettings") as Boolean)),
            "Clearing results keeps dark mode and Settings open",
        )
        description("Switch to light mode")
        settingsClick("Clear current results")
        optionClick("Clear results")
        check(!(store.dark()), "Clearing results keeps light mode")
        onMain(
            callback179@{
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
        onMain(callback180@{ (((field("settingsScroll") as ScrollView?)))!!.scrollTo(0, 0) })
        capture("settings-light-large-text")
        settingsClick("Clear current results")
        optionClick("Cancel")
        check(
            (field("showingSettings") as Boolean),
            "Last action remains reachable at 200 percent text size",
        )
        description("Switch to dark mode")
        onMain(callback181@{ (((field("settingsScroll") as ScrollView?)))!!.scrollTo(0, 0) })
        capture("settings-dark-large-text")
        onMain(
            callback182@{
                activity!!.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            }
        )
        SystemClock.sleep(1200)
        settle()
        check((field("showingSettings") as Boolean), "Landscape retains Settings")
        settingsClick("Clear current results")
        optionFits()
        optionClick("Cancel")
        capture("settings-dark-landscape-large-text")
        onMain(
            callback183@{
                activity!!.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            }
        )
        SystemClock.sleep(1200)
        settle()
        onMain(
            callback184@{
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
        settingsBack()
        check(
            (!((field("showingSettings") as Boolean)) &&
                (texts())!!.contains("Your devices will appear here")),
            "Toolbar Back returns to current empty list",
        )
        store.retainIfCurrent(store.token(), live.key, fixture, true)
        onMain(
            callback185@{
                invoke("loadNetwork")
                invoke("render")
            }
        )
        setSearch("Settings device 2")
        onMain(callback186@{ (((field("deviceScroll") as ScrollView?)))!!.scrollTo(0, 400) })
        settle()
        var offset: IntArray = intArrayOf(0)
        onMain(callback187@{ offset[0] = rowOffset("192.0.2.25") })
        description("Options")
        menuChoose("Settings")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        onMain(
            callback188@{
                check(
                    ((!((field("showingSettings") as Boolean)) &&
                        (field("query"))!!.equals("Settings device 2")) &&
                        (Math.abs((rowOffset("192.0.2.25") - offset[0])) <= 2)),
                    "Android Back from Settings preserves filtered list position",
                )
            }
        )
        click("Settings device 25")
        description("Options")
        menuChoose("Settings")
        onMain(
            callback189@{
                (((field("settingsScroll") as ScrollView?)))!!.fullScroll(View.FOCUS_DOWN)
            }
        )
        settle()
        var savedSettingsY: Int = (((field("settingsScroll") as ScrollView?)))!!.getScrollY()
        check((savedSettingsY > 100), "Settings is scrolled before recreation")
        var monitor: ActivityMonitor = addMonitor("dev.netlens.MainActivity", null, false)
        onMain(callback190@{ activity!!.recreate() })
        var replacement: Activity? =
            nullablePlatformResult(waitForMonitorWithTimeout(monitor, 8000))
        removeMonitor(monitor)
        check((replacement !== null), "Settings supports Activity recreation")
        activity = replacement
        settle()
        check(
            (Math.abs(
                ((((field("settingsScroll") as ScrollView?)))!!.getScrollY() - savedSettingsY)
            ) <= 2),
            "Settings retains its own scroll position across Activity recreation",
        )
        check(
            ((field("showingSettings") as Boolean) &&
                (((field("selected") as Device?)))!!.ip.equals("192.0.2.25")),
            "Recreation retains Settings and its same-network detail return target in RAM",
        )
        settingsBack()
        assertText("Device details")
        assertText("Settings device 25")
        description("Back to devices")
        settle()
        onMain(
            callback191@{
                check(
                    (Math.abs((rowOffset("192.0.2.25") - offset[0])) <= 2),
                    "Settings recreation and detail Back preserve the original list position",
                )
            }
        )
        description("Options")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        check((field("optionsMenu") === null), "Android Back dismisses the two-item menu")
        check(noLegacyFiles(target), "Settings creates no saved preferences")
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

    private fun top(v: View): Int {
        var xy: IntArray = IntArray(2)
        v.getLocationInWindow(xy)
        return xy[1]
    }

    private fun rowOffset(ip: String): Int {
        return (top(((((field("deviceRows") as Map<String, View>?)))!!.get(ip))!!) -
            top(((field("deviceScroll") as View?)!!)))
    }

    private fun settle(): Unit {
        waitForIdleSync()
        SystemClock.sleep(350)
        waitForIdleSync()
    }

    private fun groupingStatus(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var live: NetworkState = NetworkState.current(target)
        var ipv4: Device = Device()
        ipv4.ip = live.ip
        ipv4.self = true
        ipv4.name = "This Android phone"
        ipv4.port(80)
        ipv4.evidence("IPv4 fixture response")
        var ownV6: String? = null
        for (value in live.addresses) {
            if (((Address.bytes(value))!!.size == 16)) {
                ownV6 = live.scoped(InetAddress.getByName(value))
                break
            }
        }
        check((ownV6 !== null), "Grouping fixture uses an actually assigned phone IPv6 address")
        var assignedV6: String? = ownV6
        var ipv6: Device? = Device()
        ipv6!!.ip = assignedV6!!
        ipv6!!.self = true
        ipv6!!.port(631)
        ipv6!!.evidence("IPv6 fixture response")
        var raw: MutableList<Device> = Arrays.asList(ipv4, ipv6)
        var grouped: List<Device> = Device.grouped(raw)
        check(
            ((grouped.size == 1) && (((grouped.get(0))!!.endpoints())!!.size == 2)),
            "Phone IPv4 and IPv6 display as one group",
        )
        check(
            ((((grouped.get(0))!!.copy())!!.endpoints())!!.size == 2),
            "Group details survive selection copy",
        )
        check(
            ((Device.currentGroup(raw, ipv6))!!.ip.equals(ipv4.ip) &&
                (((Device.currentGroup(raw, ipv6))!!.endpoints())!!.size == 2)),
            "Open group refreshes when a new lower primary IP arrives",
        )
        check(
            ((((grouped.get(0))!!.endpoints())!!.get(0))!!
                .ports
                .equals(Collections.singletonList(80)) &&
                (((grouped.get(0))!!.endpoints())!!.get(1))!!
                    .ports
                    .equals(Collections.singletonList(631))),
            "Group retains per-IP port evidence",
        )
        var a: Device = Device()
        a.ip = "192.0.2.1"
        a.name = "Same printer"
        a.identity = "_ipp._tcp.|Same printer"
        var b: Device = Device()
        b.ip = "fd00::1"
        b.name = a.name
        b.identity = a.identity
        check(
            ((Device.grouped(Arrays.asList(a, b)))!!.size == 2),
            "Old name-based identity does not merge devices",
        )
        a.link("id:fixture")
        b.link("id:fixture")
        check(
            ((Device.grouped(Arrays.asList(Device.from(a.json()), Device.from(b.json()))))!!.size ==
                1),
            "Explicit grouping evidence survives JSON round trip",
        )
        var partial: ScanReport =
            ScanReport(
                "10.0.2.0/27 · IPv4 + IPv6 advertised services",
                false,
                "Scan time limit reached. Results are partial.",
                12,
                30,
                System.currentTimeMillis(),
            )
        check(
            store.retainIfCurrent(store.token(), live.key, raw, partial),
            "Partial scan stores observations and report together",
        )
        var reopened: Store = Store(target)
        var loaded: ScanReport? = reopened.report(live.key)
        check(
            ((((((loaded !== null) && !(loaded.complete)) && loaded.scope.equals(partial.scope)) &&
                (loaded.checked == 12)) && (loaded.total == 30)) &&
                loaded.reason.equals(partial.reason)),
            "New Store retains exact partial scope and status",
        )
        check((reopened.report("other-network") === null), "Report is isolated by network")
        activity = launch(false)
        waitForIdleSync()
        assertText("1 device group · 2 IPs")
        assertText("Partial scan")
        onMain(callback192@{ (((field("status") as View?)))!!.performClick() })
        SystemClock.sleep(200)
        check(
            !(((waitForDialog("12 / 30"))!!.findAccessibilityNodeInfosByText("12 / 30"))!!
                .isEmpty()),
            "Partial coverage is disclosed in scan details",
        )
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForIdleSync()
        capture("partial-grouped-light")
        setSearch((assignedV6)!!)
        assertText("1 device group matching · 2 IPs")
        setSearch("")
        click("This Android phone")
        assertText("2 IP addresses grouped together")
        assertText((assignedV6)!!)
        capture("grouped-details-light")
        click("Copy device details")
        onMain(
            callback193@{
                var c: ClipboardManager? =
                    (target.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?)
                var text: String = ((((c)!!.getPrimaryClip())!!.getItemAt(0))!!.text)!!.toString()
                check(
                    (text.contains(("IP: " + live.ip)) && text.contains(("IP: " + assignedV6))),
                    "Copy includes every grouped address",
                )
            }
        )
        description("Back to devices")
        description("Switch to dark mode")
        capture("partial-grouped-dark")
        onMain(callback194@{ activity!!.finish() })
        waitForIdleSync()
        activity = launch(false)
        waitForIdleSync()
        assertText("Partial scan")
        check(
            !((texts())!!.contains("Scan time limit reached")),
            "Reopened list keeps diagnostics in scan details",
        )
        check(reopened.dark(), "Dark mode survives reopening")
        capture("partial-reopened-dark")
        var stale: Long = store.token()
        store.clear()
        check(
            (!(store.retainIfCurrent(stale, live.key, raw, partial)) &&
                (store.report(live.key) === null)),
            "Deletion rejects stale inventory and status together",
        )
        var prefs: SharedPreferences = target.getSharedPreferences("netlens", 0)
        (((((prefs.edit())!!.putString("inventory-network", live.key))!!.putString(
                        "inventory",
                        "[]",
                    ))!!
                    .putLong("inventory-last", 1234))!!
                .putBoolean("inventory-complete", false))!!
            .commit()
        store.migrate()
        var legacy: ScanReport? = store.report(live.key)
        check(
            ((legacy === null) && noLegacyFiles(target)),
            "Legacy data is removed rather than being restored into the session",
        )
        store.clear()
        scanCheck(target, live, (live.ip + "/32"), live.ip, true)
        loaded = store.report(live.key)
        check(
            ((((loaded !== null) && loaded.scope.startsWith((live.ip + "/32"))) &&
                (loaded.checked == 1)) && (loaded.total == 1)),
            "Live scan persists actual /32 range and counters",
        )
        var own: Int = 0
        for (d in store.devices(live.key)) {
            if (d.self) {
                own++
            }
        }
        var ownGroups: Int = 0
        for (d in Device.grouped(store.devices(live.key))) {
            if (d.self) {
                ownGroups++
            }
        }
        check(
            ((own >= 1) && (ownGroups == 1)),
            "Actual phone addresses are grouped after live discovery",
        )
        var done: CountDownLatch = CountDownLatch(1)
        var failed: kotlin.Array<List<Device>?> = arrayOf(null)
        Discovery(target, live)
            .scan(
                "not-a-range",
                object : Discovery.Listener {
                    override fun progress(n: Int, t: Int, d: List<Device>): Unit {}

                    override fun finished(d: List<Device>, c: Boolean, m: String): Unit {
                        failed[0] = d
                        done.countDown()
                    }
                },
            )
        check(done.await(10, TimeUnit.SECONDS), "Invalid range returns promptly")
        check(
            (((store.report(live.key) === null) && (failed[0])!!.isEmpty()) &&
                (store.devices(live.key))!!.isEmpty()),
            "An invalid new scan never falls back to the previous inventory",
        )
        store.retainIfCurrent(store.token(), live.key, raw, partial)
        onMain(callback195@{ activity!!.finish() })
        waitForIdleSync()
    }

    private fun scanCheck(
        target: Context,
        live: NetworkState,
        range: String,
        expected: String,
        self: Boolean,
    ): Unit {
        var finished: CountDownLatch = CountDownLatch(1)
        var result: kotlin.Array<List<Device>?> = arrayOf(null)
        var summary: kotlin.Array<String> = arrayOf("")
        Discovery(target, live)
            .scan(
                range,
                object : Discovery.Listener {
                    override fun progress(n: Int, total: Int, ds: List<Device>): Unit {}

                    override fun finished(
                        ds: List<Device>,
                        complete: Boolean,
                        message: String,
                    ): Unit {
                        result[0] = ds
                        summary[0] = message
                        finished.countDown()
                    }
                },
            )
        check(
            finished.await((FULL_SCAN_SECONDS).toLong(), TimeUnit.SECONDS),
            ("Live scan completes: " + range),
        )
        var found: Device? = null
        for (d in result[0]!!) {
            if (d.ip.equals(expected)) {
                found = d
            }
        }
        if ((((found === null) && !(self)) && (Build.VERSION.SDK_INT <= 28))) {
            var denied: IcmpEcho.Result =
                IcmpEcho.probe(
                    callback196@{ AndroidIcmp.open(live, InetAddress.getByName(expected)) },
                    callback197@{ false },
                    300,
                    2,
                    120,
                )
            check(
                ((denied.status === IcmpEcho.Status.ERROR) &&
                    (((denied.problem === IcmpEcho.Problem.PERMISSION) ||
                        (denied.problem === IcmpEcho.Problem.UNSUPPORTED)))),
                "An absent legacy gateway is explained by independently confirmed platform ICMP restrictions",
            )
            var report: ScanReport? = Store(target).report(live.key)
            check(
                (((report !== null) && !(report.complete)) &&
                    (report.sources.getOrDefault("ICMP echo", ""))!!.startsWith("Failed")),
                "Legacy platform restrictions stay visible as Partial rather than inventing a gateway response",
            )
            var platform: Bundle = Bundle()
            platform.putString(
                "stream",
                (("PLATFORM: gateway produced no qualifying TCP response; app ICMP unavailable: " +
                    denied.problem) + ". Verified honest absence and Partial status.\n"),
            )
            sendStatus(0, platform)
            return
        }
        check(
            (found !== null),
            ((((("Live " + ((if (self) "self" else "non-self gateway"))) + " response found: ") +
                expected) + " / ") + summary[0]),
        )
        check((found!!.self == self), "Live endpoint self classification")
        if (!(self)) {
            check(!(found!!.evidence.isEmpty()), "Non-self endpoint has response evidence")
        }
    }

    private fun metadataFixture(target: Context, live: NetworkState): Unit {
        var store: Store = Store(target)
        store.clear()
        var paused: CountDownLatch = CountDownLatch(1)
        var release: CountDownLatch? = CountDownLatch(1)
        var finished: CountDownLatch = CountDownLatch(1)
        var scan: Discovery = Discovery(target, live)
        scan.scan(
            (live.ip + "/32"),
            object : Discovery.Listener {
                override fun progress(n: Int, t: Int, ds: List<Device>): Unit {
                    paused.countDown()
                    try {
                        release!!.await(10, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        (Thread.currentThread())!!.interrupt()
                    }
                }

                override fun finished(d: List<Device>, c: Boolean, m: String): Unit {
                    finished.countDown()
                }
            },
        )
        try {
            check(paused.await(15, TimeUnit.SECONDS), "Fixture scan accepting service data")
            var service: NsdServiceInfo = NsdServiceInfo()
            service.setServiceName("Fixture printer")
            service.setServiceType("_ipp._tcp.")
            service.setPort(631)
            service.setHost(InetAddress.getByName(live.ip))
            if ((Build.VERSION.SDK_INT >= 34)) {
                service.setHostAddresses(Collections.singletonList(InetAddress.getByName(live.ip)))
            }
            service.setAttribute("model", "Fixture 500")
            service.setAttribute("manufacturer", "Fixture Inc")
            service.setAttribute("mac", "00:11:22:33:44:55")
            var resolved: Method =
                Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
            resolved.setAccessible(true)
            resolved.invoke(scan, service)
            freshNsd(scan, service)
            var d: Device = (scan.snapshot())!!.get(0)
            check(
                (((d.model.equals(Build.MODEL) && d.manufacturer.equals(Build.MANUFACTURER)) &&
                    d.mac.equals("00:11:22:33:44:55")) &&
                    ((d.observations())!!.stream())!!.anyMatch(
                        callback198@{ h -> h.value.equals("Fixture 500") }
                    )),
                "Platform TXT parsed while self build identity retains precedence",
            )
            var before: Int = (scan.snapshot())!!.size
            service.setHost(InetAddress.getByName("203.0.113.40"))
            if ((Build.VERSION.SDK_INT >= 34)) {
                service.setHostAddresses(
                    Collections.singletonList(InetAddress.getByName("203.0.113.40"))
                )
            }
            resolved.invoke(scan, service)
            check(
                (((scan.snapshot())!!.size == before) &&
                    !(((scan.snapshot())!!.stream())!!.anyMatch(
                        callback199@{ item -> item.ip.equals("203.0.113.40") }
                    ))),
                "Out-of-network service address rejected",
            )
        } finally {
            release!!.countDown()
        }
        check(
            finished.await((FULL_SCAN_SECONDS).toLong(), TimeUnit.SECONDS),
            "Fixture scan cleanup completes",
        )
    }

    private fun identification(): Unit {
        var target: Context = getTargetContext()
        var store: Store = Store(target)
        store.clear()
        var xml: String =
            "<root xmlns='urn:schemas-upnp-org:device-1-0'><device><friendlyName>Fixture room</friendlyName><manufacturer>Fixture Inc</manufacturer><modelName>Fixture 500</modelName></device></root>"
        var parsed: Map<String, String> =
            UpnpDescription.parse(xml.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        check(
            ("Fixture room".equals(parsed.get("friendlyName")) &&
                "Fixture 500".equals(parsed.get("modelName"))),
            "Android SAX supports secure root-device description parsing",
        )
        var requestLine: java.util.concurrent.atomic.AtomicReference<String> =
            java.util.concurrent.atomic.AtomicReference()
        var body: String = xml
        var serverWorker: ExecutorService = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.setSoTimeout(3000)
                var serving: Future<*>? =
                    serverWorker.submit(
                        callback200@{
                            try {
                                server.accept().use { client ->
                                    client.setSoTimeout(2000)
                                    var reader: BufferedReader =
                                        BufferedReader(
                                            InputStreamReader(
                                                client.getInputStream(),
                                                java.nio.charset.StandardCharsets.US_ASCII,
                                            )
                                        )
                                    var first: String? = nullablePlatformResult(reader.readLine())
                                    requestLine.set(first)
                                    var line: String? = null
                                    while (
                                        ((run {
                                            line = reader.readLine()
                                            line
                                        } !== null) && !(line!!.isEmpty()))
                                    ) {}
                                    if (((first !== null) && first.endsWith(" HTTP/1.1"))) {
                                        var bytes: ByteArray =
                                            body.toByteArray(
                                                java.nio.charset.StandardCharsets.UTF_8
                                            )
                                        (client.getOutputStream())!!.write(
                                            (((("HTTP/1.1 200 OK\r\nContent-Length: " +
                                                    bytes.size) +
                                                    "\r\nConnection: close\r\n\r\n")))!!
                                                .toByteArray(
                                                    java.nio.charset.StandardCharsets.US_ASCII
                                                )
                                        )
                                        (client.getOutputStream())!!.write(bytes)
                                    }
                                }
                            } catch (e: IOException) {
                                throw RuntimeException(e)
                            }
                        }
                    )
                Socket(InetAddress.getLoopbackAddress(), server.getLocalPort()).use { client ->
                    client.setSoTimeout(2000)
                    (client.getOutputStream())!!.write(
                        UpnpDescription.request(
                            URI((("http://127.0.0.1:" + server.getLocalPort()) + "/description"))
                        )
                    )
                    var fields: Map<String, String> =
                        UpnpDescription.parse(UpnpDescription.readResponse(client.getInputStream()))
                    check(
                        "Fixture 500".equals(fields.get("modelName")),
                        "Actual Android socket retrieves a description from an HTTP/1.1-only fixture",
                    )
                }
                serving!!.get(3, TimeUnit.SECONDS)
                check(
                    "GET /description HTTP/1.1".equals(requestLine.get()),
                    "Wire request uses Cast-compatible HTTP version",
                )
            }
        } finally {
            serverWorker.shutdownNow()
        }

        var xxe: String =
            "<!DOCTYPE root [<!ENTITY e SYSTEM 'file:///proc/version'>]><root><device><friendlyName>&e;</friendlyName></device></root>"
        check(
            (UpnpDescription.parse(xxe.toByteArray(java.nio.charset.StandardCharsets.UTF_8)))!!
                .isEmpty(),
            "Android XML rejects DTD/external entity",
        )
        var d: Device = Device()
        d.ip = "192.0.2.5"
        d.hint("Model", "Xserve", "Compatibility advertisement", 20)
        d.hint("Model", "Fixture 500", "Printer TXT", 80)
        d.hint("Name", "Printer", "mDNS instance", 65)
        d.hint("Name", "Office printer", "mDNS friendly name", 90)
        var copy: Device = d.copy()
        check(
            ((copy.model.equals("Fixture 500") && copy.name.equals("Office printer")) &&
                ((copy.observations())!!.size == 4)),
            "Metadata winner and alternatives survive snapshot/JSON round trip",
        )
        var sources: MutableMap<String, String> = LinkedHashMap()
        sources.put("mDNS _ipp._tcp.", "Failed · Fixture failure")
        var report: ScanReport =
            ScanReport(
                "192.0.2.0/24",
                false,
                "Fixture partial result",
                254,
                254,
                System.currentTimeMillis(),
                sources,
            )
        check(
            store.retainIfCurrent(store.token(), "fixture", Collections.singletonList(d), report),
            "Source report saves atomically with inventory",
        )
        var restored: ScanReport? = store.report("fixture")
        check(
            (((!((restored)!!.complete) && ((restored)!!.checked == 254)) &&
                (restored)!!.sources.equals(sources)) &&
                ((((store.devices("fixture"))!!.get(0))!!.observations())!!.size == 4)),
            "Source failures, coverage and identity observations survive storage",
        )
        var live: NetworkState = NetworkState.current(target)
        var fixture: Discovery = Discovery(target, live)
        try {
            var accepting: Field = Discovery::class.java.getDeclaredField("accepting")
            accepting.setAccessible(true)
            accepting.set(fixture, true)
            var resolved: Method =
                Discovery::class.java.getDeclaredMethod("resolved", NsdServiceInfo::class.java)
            resolved.setAccessible(true)
            var service: NsdServiceInfo = NsdServiceInfo()
            service.setServiceName("Fixture Cast")
            service.setServiceType("_googlecast._tcp.")
            var address: InetAddress = InetAddress.getByName(live.gateway)
            service.setHost(address)
            if ((Build.VERSION.SDK_INT >= 34)) {
                service.setHostAddresses(Collections.singletonList(address))
            }
            service.setAttribute("MD", "Fixture 500")
            service.setAttribute("MANUFACTURER", "Fixture Inc")
            service.setAttribute("FN", "Office display")
            service.setAttribute("FV", "1.2.3")
            var fixtureObserve: Method =
                Discovery::class
                    .java
                    .getDeclaredMethod("observe", String::class.java, String::class.java)
            fixtureObserve.setAccessible(true)
            fixtureObserve.invoke(fixture, address.getHostAddress(), "Explicit test responder")
            resolved.invoke(fixture, service)
            freshNsd(fixture, service)
            var compatibility: NsdServiceInfo = NsdServiceInfo()
            compatibility.setServiceName("Generic compatibility service")
            compatibility.setServiceType("_device-info._tcp.")
            compatibility.setHost(address)
            if ((Build.VERSION.SDK_INT >= 34)) {
                compatibility.setHostAddresses(Collections.singletonList(address))
            }
            compatibility.setAttribute("model", "Xserve")
            resolved.invoke(fixture, compatibility)
            freshNsd(fixture, compatibility)
            var identified: Device = (fixture.snapshot())!!.get(0)
            check(
                ((identified.name.equals("Office display") &&
                    identified.model.equals("Fixture 500")) &&
                    identified.manufacturer.equals("Fixture Inc")),
                "Native case-insensitive TXT mapping retains explicit vendor/model and stable friendly name",
            )
            check(
                ((identified.details.get("Firmware (advertised)"))!!.equals("1.2.3") &&
                    ((identified.observations())!!.stream())!!.anyMatch(
                        callback201@{ h -> h.value.equals("Xserve") }
                    )),
                "Firmware and lower-priority compatibility report retained",
            )
            var id: String = "12345678-1234-1234-1234-123456789abc"
            var inquiry: String =
                (((("<ProbeMatch><Uuid>" + id) + "</Uuid><Types>inquiry</Types><IPv4Address>") +
                    address.getHostAddress()) +
                    "</IPv4Address><DeviceDescription>Fixture recorder</DeviceDescription><SoftwareVersion>4.5.6</SoftwareVersion><MAC>00-22-33-44-55-66</MAC><HttpPort>8082</HttpPort><DigitalChannelNum>8</DigitalChannelNum><SpecificDeviceType>HIK</SpecificDeviceType><DeviceSN>excluded serial</DeviceSN><Salt>excluded security field</Salt></ProbeMatch>")
            var bytes: ByteArray = inquiry.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            var camera: Map<String, String> = Sadp.parse(bytes, bytes.size, id, address)
            check(
                (("Fixture recorder".equals(camera.get("DeviceDescription")) &&
                    !(camera.containsKey("DeviceSN"))) && !(camera.containsKey("Salt"))),
                "Android SAX validates SADP model without retaining serial/security fields",
            )
            var sadp: Method =
                Discovery::class
                    .java
                    .getDeclaredMethod("sadpResolved", InetAddress::class.java, Map::class.java)
            sadp.setAccessible(true)
            sadp.invoke(fixture, address, camera)
            identified = (fixture.snapshot())!!.get(0)
            check(
                ((((identified.model.equals("Fixture recorder") &&
                    identified.name.equals("Office display")) &&
                    identified.mac.equals("00:22:33:44:55:66")) &&
                    identified.ports.contains(8082)) &&
                    (identified.details.get("Firmware (advertised)"))!!.equals("4.5.6")),
                "SADP response contributes model/MAC/firmware/ports while preserving the better friendly name",
            )
            check(
                (identified.manufacturer.equals("Fixture Inc") &&
                    identified.kind!!.equals("Video surveillance device")),
                "SADP protocol does not overwrite manufacturer with an inferred vendor",
            )
        } finally {
            fixture.cancel()
        }
        var scan: Discovery = Discovery(target, live)
        var hf: Field = Discovery::class.java.getDeclaredField("health")
        hf.setAccessible(true)
        var health: DiscoveryHealth? = (hf.get(scan) as DiscoveryHealth?)
        (health)!!.fail("Fixture source", "Injected discovery failure")
        var finished: CountDownLatch = CountDownLatch(1)
        var complete: BooleanArray = booleanArrayOf(true)
        scan.scan(
            (live.ip + "/32"),
            object : Discovery.Listener {
                override fun progress(n: Int, t: Int, ds: List<Device>): Unit {}

                override fun finished(ds: List<Device>, c: Boolean, message: String): Unit {
                    complete[0] = c
                    finished.countDown()
                }
            },
        )
        check(
            finished.await((FULL_SCAN_SECONDS).toLong(), TimeUnit.SECONDS),
            "Scan with failed source completes within its window",
        )
        check(!(complete[0]), "IPv4 coverage cannot hide service-source failure")
        check(
            (!((store.report(live.key))!!.complete) &&
                ((store.report(live.key))!!.sources.get("Fixture source"))!!.startsWith("Failed")),
            "Actual scan path persists partial source result",
        )
        activity = launch(false)
        waitForIdleSync()
        assertText("Partial scan")
        onMain(
            callback202@{
                var v: View? = (field("status") as View?)
                check(((v !== null) && v.performClick()), "Status opens source details")
            }
        )
        SystemClock.sleep(250)
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        capture("identification-source-report")
    }

    private fun deletionDuringScan(target: Context, live: NetworkState, cancel: Boolean): Unit {
        var store: Store = Store(target)
        store.clear()
        var paused: CountDownLatch = CountDownLatch(1)
        var release: CountDownLatch? = CountDownLatch(1)
        var finished: CountDownLatch = CountDownLatch(1)
        var returned: kotlin.Array<List<Device>?> = arrayOf(null)
        var scan: Discovery = Discovery(target, live)
        scan.scan(
            (live.ip + "/32"),
            object : Discovery.Listener {
                override fun progress(n: Int, total: Int, ds: List<Device>): Unit {
                    paused.countDown()
                    try {
                        release!!.await(10, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        (Thread.currentThread())!!.interrupt()
                    }
                }

                override fun finished(ds: List<Device>, complete: Boolean, message: String): Unit {
                    returned[0] = ds
                    finished.countDown()
                }
            },
        )
        try {
            check(
                paused.await(15, TimeUnit.SECONDS),
                ("Scan reaches deletion boundary; cancel=" + cancel),
            )
            store.clear()
            if (cancel) {
                scan.cancel()
            }
        } finally {
            release!!.countDown()
        }
        check(
            finished.await(((if (cancel) 15 else FULL_SCAN_SECONDS).toLong()), TimeUnit.SECONDS),
            ("Deleted scan finishes; cancel=" + cancel),
        )
        check(
            ((store.devices(live.key))!!.isEmpty() &&
                ((store.last(live.key)).toLong() == (0).toLong())),
            ("Deleted scan cannot restore data; cancel=" + cancel),
        )
        check(
            ((returned[0] !== null) && (returned[0])!!.isEmpty()),
            ("Deleted callback has no stale results; cancel=" + cancel),
        )
    }

    private fun wifiNameFlow(): Unit {
        var automation: android.accessibilityservice.AccessibilityServiceInfo =
            (getUiAutomation())!!.getServiceInfo()
        automation.flags =
            ((automation.flags).toInt() or
                (android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS)
                    .toInt())
        (getUiAutomation())!!.setServiceInfo(automation)
        var target: Context = getTargetContext()
        check(
            (target.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED),
            "Optional location access starts denied",
        )
        check(
            ((WifiName.clean("\"Office Wi-Fi\""))!!.equals("Office Wi-Fi") &&
                (WifiName.clean(android.net.wifi.WifiManager.UNKNOWN_SSID))!!.isEmpty()),
            "SSID removes platform quotes and never displays unknown sentinel",
        )
        check(
            !((WifiName.clean("\"Office\u202Ename\""))!!.contains("\u202E")),
            "SSID strips misleading bidi controls",
        )
        Store(target).dark(false)
        activity = launch(false)
        waitForIdleSync()
        var live: NetworkState = NetworkState.current(target)
        onMain(
            callback203@{
                check(
                    (((((field("scanButton") as TextView?)))!!.text)!!.toString())!!.equals(
                        "Scan Wi-Fi"
                    ),
                    "Denied optional access keeps the scan action usable",
                )
                invoke("wifiNameAccess")
            }
        )
        SystemClock.sleep(250)
        dialogContinue()
        osButton("permission_deny_button")
        waitForIdleSync()
        check(
            (target.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED),
            "Declining optional access leaves it denied",
        )
        onMain(
            callback204@{
                setField("range", (live.ip + "/32"))
                invoke("beginScan")
            }
        )
        var end: Long =
            (SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis((FULL_SCAN_SECONDS).toLong()))
        while ((SystemClock.uptimeMillis() < end)) {
            SystemClock.sleep(200)
            if (
                (!((field("scanning") as Boolean)) &&
                    ((Store(target).last(live.key)).toLong() > (0).toLong()))
            ) {
                break
            }
        }
        check(
            (((Store(target).last(live.key)).toLong() > (0).toLong()) &&
                (target.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED)),
            "Discovery saves a real scan with location access denied",
        )
        description("Options")
        menuChoose("Settings")
        check(
            (texts())!!.contains("Not allowed · optional"),
            "Settings shows the denied optional name permission",
        )
        settingsClick("Allow Wi-Fi name")
        SystemClock.sleep(250)
        dialogContinue()
        osButton("permission_allow_foreground_only_button")
        waitForIdleSync()
        check(
            (target.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED),
            "Foreground precise permission can be allowed separately",
        )
        check(
            (((field("showingSettings") as Boolean) && (texts())!!.contains("Allowed")) &&
                !((field("scanning") as Boolean))),
            "Grant refreshes Settings without starting discovery",
        )
        settingsBack()
        end = (SystemClock.uptimeMillis() + 5000)
        while ((SystemClock.uptimeMillis() < end)) {
            SystemClock.sleep(100)
            var ready: BooleanArray = booleanArrayOf(false)
            onMain(
                callback205@{
                    ready[0] =
                        !(((((field("wifiName") as WifiName?)))!!.get(live.network))!!.isEmpty())
                }
            )
            if (ready[0]) {
                break
            }
        }
        var observed: kotlin.Array<String> = arrayOf("")
        onMain(
            callback206@{
                observed[0] = (((field("wifiName") as WifiName?)))!!.get(live.network)
                check(
                    (!((observed[0])!!.isEmpty()) &&
                        (((((field("scanButton") as TextView?)))!!.text)!!.toString())!!.equals(
                            ("Scan Wi-Fi\n" + observed[0])
                        )),
                    "Actual connected emulator SSID arrives through callback and appears on scan button",
                )
            }
        )
        capture("ssid-light")
        description("Switch to dark mode")
        capture("ssid-dark")
        onMain(
            callback207@{
                try {
                    var names: WifiName? = (field("wifiName") as WifiName?)
                    var f: Field = WifiName::class.java.getDeclaredField("names")
                    f.setAccessible(true)
                    var map: MutableMap<android.net.Network, String>? =
                        (f.get(names) as MutableMap<android.net.Network, String>?)
                    (map)!!.put(
                        (live.network)!!,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    )
                    var c: Configuration =
                        Configuration((activity!!.getResources())!!.getConfiguration())
                    c.fontScale = 2f
                    (activity!!.getResources())!!.updateConfiguration(
                        c,
                        (activity!!.getResources())!!.getDisplayMetrics(),
                    )
                    invoke("render")
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            }
        )
        waitForIdleSync()
        onMain(
            callback208@{
                check(
                    ((((field("scanButton") as TextView?)))!!.getLineCount() <= 2),
                    "Long SSID stays within two lines at 200 percent text size",
                )
            }
        )
        capture("ssid-large-text")
        onMain(
            callback209@{
                var names: WifiName? = (field("wifiName") as WifiName?)
                (names)!!.stop()
                check(
                    ((names)!!.get(live.network))!!.isEmpty(),
                    "Leaving foreground clears cached SSID",
                )
                setField("demo", true)
                invoke("render")
                check(
                    (((((field("scanButton") as TextView?)))!!.text)!!.toString())!!.equals(
                        "Scan Wi-Fi"
                    ),
                    "Sample mode does not label a fake network as the connected SSID",
                )
            }
        )
    }

    private fun dialogContinue(): Unit {
        var end: Long = (SystemClock.uptimeMillis() + 5000)
        while ((SystemClock.uptimeMillis() < end)) {
            var root: AccessibilityNodeInfo? = (getUiAutomation())!!.getRootInActiveWindow()
            if ((root !== null)) {
                var nodes: List<AccessibilityNodeInfo> =
                    root!!.findAccessibilityNodeInfosByText("Continue")
                for (n in nodes) {
                    if (
                        ((n.text !== null) &&
                            ((n.text)!!.toString())!!.equals("Continue", ignoreCase = true))
                    ) {
                        check(
                            n.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                            "Optional SSID explanation confirmed",
                        )
                        SystemClock.sleep(300)
                        return
                    }
                }
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("SSID explanation Continue button missing")
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
        tapScan()
        osButton("permission_deny_button")
        waitForIdleSync()
        assertText("Local-network access is needed to find devices.")
        check(
            (target.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED),
            "Deny leaves permission denied",
        )
        check(!((field("scanning") as Boolean)), "Deny does not start discovery")
        capture("permission-denied")
        tapScan()
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

    private fun setSearch(value: String): Unit {
        onMain(
            callback212@{
                var v: View? = find((activity!!.window)!!.getDecorView(), "Search devices", true)
                (((v as EditText?)))!!.setText(value)
            }
        )
        waitForIdleSync()
    }

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
        var text: StringBuilder = StringBuilder()
        onMain(callback214@{ collect((activity!!.window)!!.getDecorView(), text) })
        return text.toString()
    }

    private fun collect(view: View, text: StringBuilder): Unit {
        if ((view is TextView)) {
            (text.append((((view as TextView?)))!!.text))!!.append('\n')
        }
        if ((view is ViewGroup)) {
            run {
                var i: Int = 0
                while ((i < (((view as ViewGroup?)))!!.getChildCount())) {
                    collect((((view as ViewGroup?)))!!.getChildAt(i), text)
                    i++
                }
            }
        }
    }

    private fun click(text: String): Unit {
        activate(text, false)
    }

    private fun description(text: String): Unit {
        activate(text, true)
    }

    private fun activate(text: String, description: Boolean): Unit {
        onMain(
            callback215@{
                var v: View? = find((activity!!.window)!!.getDecorView(), text, description)
                if ((v === null)) {
                    throw AssertionError(("No control: " + text))
                }
                while ((!((v)!!.isClickable()) && ((v)!!.getParent() is View))) {
                    v = ((v)!!.getParent() as View?)
                }
                check((v)!!.performClick(), ("Click: " + text))
            }
        )
        waitForIdleSync()
    }

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

    private fun findScroll(view: View): ScrollView? {
        if ((view is ScrollView)) {
            return (view as ScrollView?)
        }
        if ((view is ViewGroup)) {
            run {
                var i: Int = 0
                while ((i < (((view as ViewGroup?)))!!.getChildCount())) {
                    var found: ScrollView? = findScroll((((view as ViewGroup?)))!!.getChildAt(i))
                    if ((found !== null)) {
                        return found
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

        private fun splitPacket(address: String, part: Int): ByteArray {
            var type: String = "_networklens-fixture._tcp.local"
            var instance: String = ("Split fixture." + type)
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
                        for (field in arrayOf("fn=Fresh split name", "md=Fresh split model")) {
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
