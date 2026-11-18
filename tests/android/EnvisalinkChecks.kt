package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*

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
        fun waiting(d: Device) { fresh.add(d.ip); d.detail(EnvisalinkInfo.SOURCE, "Waiting or checking") }
        try {
            val candidate = peer(2)
            probe.invoke(op, candidate)
            check((get(op, "envisalinkRequests") as ServiceAdmission).size() == 0, "Cached candidate cannot initiate an Envisalink connection")
            candidate.detail(EnvisalinkInfo.SOURCE, "Waiting or checking")
            check(apply.invoke(op, candidate, EnvisalinkInfo.Report.IDENTIFIED) == false, "Presence required before accepting greeting")
            waiting(candidate)
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
            for (d in listOf(outside, self, stale)) check(apply.invoke(op, d, EnvisalinkInfo.Report.IDENTIFIED) == false, "Scope, self and replaced occupant rejected")
            val pending = peer(23); waiting(pending)
            op.cancel()
            check(pending.details[EnvisalinkInfo.SOURCE] == "Stopped before lookup finished", "Stop seals pending greeting status")
            check(apply.invoke(op, pending, EnvisalinkInfo.Report.IDENTIFIED) == false, "No late result after Stop")
            check(Device().apply { ip = snapshot.ip }.identifiedProduct() == null, "New scan has no inherited identity")
        } finally { op.cancel() }
    }
}
