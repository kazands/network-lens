package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kazands.networklens.*
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor

/** Synthetic endpoints and no Android Network: never contacts household equipment. */
internal object EnvisalinkChecks {
    private fun get(o: Any, n: String): Any = o.javaClass.getDeclaredField(n).let { it.isAccessible = true; it.get(o)!! }
    private fun set(o: Any, n: String, v: Any) = o.javaClass.getDeclaredField(n).let { it.isAccessible = true; it.set(o, v) }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val op = Discovery(test.targetContext, NetworkState().apply {
            ip = "198.18.0.1"; prefix = 24; key = "synthetic-envisalink"
            prefixes[ip] = prefix; addresses.add(ip)
        })
        set(op, "accepting", true); set(op, "enriching", true)
        val found = get(op, "found") as MutableMap<String, Device>
        val fresh = get(op, "freshResponders") as MutableSet<String>
        val probe = Discovery::class.java.getDeclaredMethod("checkEnvisalink", Device::class.java).apply { isAccessible = true }
        val apply = Discovery::class.java.getDeclaredMethod("applyEnvisalinkReport", Device::class.java, EnvisalinkInfo.Report::class.java).apply { isAccessible = true }
        fun peer(last: Int) = Device().apply { ip = "198.18.0.$last"; found[ip] = this }
        fun waiting(d: Device) {
            // Explicit consent belongs to one address in this scan, never every fresh peer.
            set(op, "envisalinkTarget", d.ip)
            fresh.add(d.ip); d.detail(EnvisalinkInfo.SOURCE, "Waiting or checking")
        }
        try {
            val candidate = peer(2)
            probe.invoke(op, candidate)
            check((get(op, "envisalinkRequests") as ServiceAdmission).size() == 0, "Cached candidate cannot initiate an Envisalink connection")
            candidate.detail(EnvisalinkInfo.SOURCE, "Waiting or checking")
            check(apply.invoke(op, candidate, EnvisalinkInfo.Report.IDENTIFIED) == false, "Presence required before accepting greeting")
            fresh.add(candidate.ip)
            probe.invoke(op, candidate)
            check((get(op, "envisalinkRequests") as ServiceAdmission).size() == 0, "Normal scans never connect to TPI, even for a fresh responder")
            check(apply.invoke(op, candidate, EnvisalinkInfo.Report.IDENTIFIED) == false, "An unsolicited greeting cannot bypass per-device consent")
            waiting(candidate)
            val neighbor = peer(4); fresh.add(neighbor.ip); neighbor.detail(EnvisalinkInfo.SOURCE, "Waiting or checking")
            probe.invoke(op, neighbor)
            check((get(op, "envisalinkRequests") as ServiceAdmission).size() == 0 &&
                apply.invoke(op, neighbor, EnvisalinkInfo.Report.IDENTIFIED) == false, "Consent for one address cannot initiate or identify its neighbor")
            check(apply.invoke(op, candidate, EnvisalinkInfo.Report.IDENTIFIED) == true, "Current in-scope greeting accepted")
            check(candidate.presentation().title == "Envisalink" && candidate.presentation().subtitle == "Alarm interface", "Useful family name and physical type")
            check(candidate.model.isEmpty() && candidate.manufacturer.isEmpty() && candidate.mac.isEmpty() && candidate.name.isEmpty(), "Recognition does not fabricate reported fields")
            check(candidate.classification().category == DeviceCatalog.Category.ALARM_INTERFACE && DeviceIcon.forGroup(candidate).drawable == R.drawable.ic_device_lock, "Alarm icon and category")
            check(candidate.services.contains(EnvisalinkInfo.SERVICE) && candidate.ports.contains(4025), "Verified service/port retained")
            check(candidate.identifiedProduct()!!.source.contains("TPI greeting") && candidate.identifiedProduct()!!.product.familyOnly, "Source-labelled family interpretation")
            check(apply.invoke(op, candidate, EnvisalinkInfo.Report.IDENTIFIED) == false, "Completed lookup cannot be replayed")
            val snapshot = candidate.copy()
            candidate.hint("Model", "Google Home", "Synthetic contradictory model", 80)
            check(candidate.identifiedProduct() == null && candidate.classification().conflicted, "Contradiction suppresses name and category")
            check(snapshot.presentation().title == "Envisalink", "Snapshot keeps independent recognition")
            val apple = peer(3); waiting(apple); apply.invoke(op, apple, EnvisalinkInfo.Report.IDENTIFIED)
            apple.recognizeAppleModel("iPhone17,1")
            apple.hint("Model", "iPhone17,1", "Synthetic Apple ProductType", 80)
            check(apple.identifiedProduct() == null && apple.locallyRecognized() == null && apple.classification().conflicted, "Apple fallback cannot bypass Envisalink conflict")
            for ((i, report) in listOf(EnvisalinkInfo.Report.OTHER, EnvisalinkInfo.Report.NO_GREETING, EnvisalinkInfo.Report.UNAVAILABLE).withIndex()) {
                val d = peer(10+i); waiting(d); apply.invoke(op, d, report)
                check(d.identifiedProduct() == null && d.services.isEmpty(), "Open, busy and closed ports never identify Envisalink")
                check((4025 in d.ports) == report.connected, "Only an actual connection records an open port")
                check((EnvisalinkInfo.SOURCE in d.details) == report.connected, "Absent service does not clutter device details")
            }
            val outside = peer(20).apply { ip = "203.0.113.20"; found[ip] = this }; waiting(outside)
            val self = peer(21).apply { this.self = true }; waiting(self)
            val stale = peer(22); waiting(stale); found[stale.ip] = Device().apply { ip = stale.ip }
            for (d in listOf(outside, self, stale)) {
                set(op, "envisalinkTarget", d.ip)
                check(apply.invoke(op, d, EnvisalinkInfo.Report.IDENTIFIED) == false, "Consent cannot bypass scope, self or replaced occupant rejection")
            }
            val pending = peer(23); waiting(pending)
            op.cancel()
            check(pending.details[EnvisalinkInfo.SOURCE] == "Stopped before lookup finished", "Stop seals pending greeting status")
            check(apply.invoke(op, pending, EnvisalinkInfo.Report.IDENTIFIED) == false, "No late result after Stop")
            check(Device().apply { ip = snapshot.ip }.identifiedProduct() == null, "New scan has no inherited identity")
        } finally { op.cancel() }
        requestedScheduling(test, check)
        // Exercise the actual per-device entry point without ever confirming a network check.
        val store = Store(test.targetContext); store.clear()
        val current = NetworkState.current(test.targetContext)
        val device = Device().apply { ip = current.ip.substringBeforeLast('.') + ".72"; seen = true }
        store.retainIfCurrent(store.token(), current.key, listOf(device), ScanReport("Synthetic", true, "", 1, 1, System.currentTimeMillis()))
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (t: Throwable) { failure = t } }
            failure?.let { throw it }
        }
        fun texts(view: View): List<TextView> = buildList {
            if (view is TextView) add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
        }
        try {
            main {
                set(activity, "selected", device.copy())
                MainActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
                texts(activity.window.decorView).single { it.text.toString() == "Show technical details" }.performClick()
                texts(activity.window.decorView).single { it.text.toString() == "Check for Envisalink" }.performClick()
                val dialog = get(activity, "optionsDialog") as AppDialog
                val words = texts(dialog.window!!.decorView).map { it.text.toString() }
                check(words.any { it.contains(device.ip) && it.contains("fresh Wi-Fi scan") } &&
                    words.any { it.contains("may delay another integration reconnecting") }, "Per-device action explains its target, fresh results and integration risk")
                check(words.contains("Scan and check") && words.contains("Cancel"), "Alarm check requires an explicit action after the explanation")
                texts(dialog.window!!.decorView).single { it.text.toString() == "Cancel" }.performClick()
                check(!dialog.isShowing && get(activity, "scanning") == false, "Cancel neither starts a scan nor occupies the alarm service")
                texts(activity.window.decorView).single { it.text.toString() == "Check for Envisalink" }.performClick()
                val staleDialog = get(activity, "optionsDialog") as AppDialog
                store.clear()
                texts(staleDialog.window!!.decorView).single { it.text.toString() == "Scan and check" }.performClick()
                check(get(activity, "scanning") == false && store.devices(current.key).isEmpty(), "A retired inventory cannot reuse the pending alarm-check request")
            }
        } finally { main { activity.finish() }; store.clear() }
    }

    /** Exercise real response-to-worker scheduling; the synthetic Network has no socket binding. */
    private fun requestedScheduling(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val target = "198.18.0.72"
        val observe = Discovery::class.java.getDeclaredMethod("observe", String::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val mdns = Discovery::class.java.getDeclaredMethod("mdnsResolved", InetAddress::class.java, MdnsPacket.Result::class.java).apply { isAccessible = true }
        val confirm = Discovery::class.java.getDeclaredMethod("confirmExisting", Device::class.java, String::class.java).apply { isAccessible = true }
        fun operation(chosen: String = target): Discovery {
            val network = NetworkState().apply {
                ip = "198.18.0.40"; prefix = 21; key = "synthetic-envisalink-service-only"
                prefixes[ip] = prefix; addresses.add(ip)
            }
            val plan = ScanPlan.create(network.ip, network.prefix, null)
            check(plan.sweepOmitted && plan.hosts.isEmpty() && plan.range!!.contains(target), "Requested fixture uses a service-only /21 with no IPv4 sweep")
            return Discovery(test.targetContext, network, chosen).also {
                set(it, "range", plan.range!!); set(it, "accepting", true); set(it, "enriching", true)
            }
        }
        fun emit(op: Discovery, ip: String, fresh: Boolean = true): Device =
            observe.invoke(op, ip, "SSDP / UPnP discovery response", fresh) as Device
        fun queued(op: Discovery): Int {
            val work = get(op, "enrichment") as DiscoveryWork
            val pools = get(work, "pools") as Map<DiscoveryWork.Lane, ThreadPoolExecutor>
            return pools.getValue(DiscoveryWork.Lane.DETAILS).queue.size
        }
        fun attempts(op: Discovery) = (get(op, "envisalinkRequests") as ServiceAdmission).size()
        for (source in listOf("SSDP", "mDNS", "TCP")) {
            val op = operation()
            val work = get(op, "enrichment") as DiscoveryWork
            val entered = CountDownLatch(4); val release = CountDownLatch(1)
            try {
                repeat(4) { check(work.submit(DiscoveryWork.Lane.DETAILS, Runnable {
                    entered.countDown(); try { release.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }), "Synthetic blocker admitted") }
                check(entered.await(3, TimeUnit.SECONDS), "DETAILS workers wait for deterministic scheduling assertions")
                emit(op, "198.18.0.73")
                val d = emit(op, target, false)
                check(queued(op) == 0, "A neighbor or cached candidate cannot schedule the requested check")
                repeat(3) {
                    when (source) {
                        "SSDP" -> emit(op, target)
                        "mDNS" -> mdns.invoke(op, InetAddress.getByName(target), MdnsPacket.Result().apply { freshResponse = true })
                        else -> confirm.invoke(op, d, "Connection to advertised service (confirmed twice)")
                    }
                }
                check(queued(op) == 1, "$source schedules only the explicit target without an IPv4 sweep")
                check(attempts(op) == 0, "Queued work waits for a worker")
                if (source == "SSDP") {
                    Discovery::class.java.getDeclaredMethod("queueDetails", Device::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(op, d, 160, 220)
                    check(queued(op) == 2 && (get(op, "detailsQueued") as Set<String>) == setOf(target),
                        "Requested alarm work leaves ordinary device-detail scheduling independent")
                }
                release.countDown(); work.shutdown()
                check(work.awaitTermination(5, TimeUnit.SECONDS), "Requested $source work drains within a bound")
                check(attempts(op) == 1, "$source duplicate replies perform exactly one target check")
                check(d.identifiedProduct() == null, "The synthetic unavailable network cannot invent an alarm identity")
            } finally { release.countDown(); op.cancel() }
        }
        val cancelled = operation()
        val work = get(cancelled, "enrichment") as DiscoveryWork
        val entered = CountDownLatch(4); val release = CountDownLatch(1)
        try {
            repeat(4) { check(work.submit(DiscoveryWork.Lane.DETAILS, Runnable {
                entered.countDown(); try { release.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }), "Synthetic cancellation blocker admitted") }
            check(entered.await(3, TimeUnit.SECONDS), "Cancellation fixture owns every DETAILS worker")
            emit(cancelled, target)
            check(queued(cancelled) == 1, "Explicit check is queued before cancellation")
            cancelled.cancel(); release.countDown()
            check(work.awaitTermination(5, TimeUnit.SECONDS) && attempts(cancelled) == 0, "Cancellation discards the queued check before any attempt")
            check(observe.invoke(cancelled, target, "Late fixture reply", true) == null && attempts(cancelled) == 0,
                "Late responses cannot revive the cancelled request")
        } finally { release.countDown(); cancelled.cancel() }
        for (chosen in listOf("", "198.18.0.40", "203.0.113.72")) {
            val op = operation(chosen)
            try {
                val d = emit(op, if (chosen.isEmpty()) target else chosen)
                val work = get(op, "enrichment") as DiscoveryWork
                work.shutdown()
                check(work.awaitTermination(5, TimeUnit.SECONDS), "Negative request fixture drains any admitted work")
                val eligible = Discovery::class.java.getDeclaredMethod("currentEnvisalinkPeer", Device::class.java)
                    .apply { isAccessible = true }.invoke(op, d) as Boolean
                check(!eligible && queued(op) == 0 && attempts(op) == 0,
                    "Ordinary scans, own addresses and out-of-scope targets cannot perform alarm work")
            } finally { op.cancel() }
        }
    }
}
