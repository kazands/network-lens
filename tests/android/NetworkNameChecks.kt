package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Indirect evidence must never become presence, a reported model, or a grouping key. */
internal object NetworkNameChecks {
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner)!! }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        fun device(ip: String = "198.18.0.20", name: String = "bhyve-bh1-abcd.home") = Device().apply {
            this.ip = ip
            networkName(LocalNetworkNames.Result(LocalNetworkNames.Status.CONFIRMED, name))
        }
        val hub = device()
        check(hub.presentation().title == "Likely Orbit B-hyve hub", "Wi-Fi-only B-hyve hub suggestion has explicit uncertainty")
        check(hub.presentation().subtitle.contains("Router name"), "List exposes indirect source")
        check(hub.model.isEmpty() && hub.manufacturer.isEmpty() && hub.hostname.isEmpty() && hub.name.isEmpty() && hub.mac.isEmpty(), "No reported identity or MAC invented")
        check(hub.groupProduct() == null && hub.classification().category == null && hub.recognitionReportGroup().level == RecognitionReport.Level.UNKNOWN, "Suggestion is not a verified product, family or primary type")
        check(hub.evidence.isEmpty() && hub.links().isEmpty(), "DNS creates neither presence nor grouping")
        check(hub.copy().presentation() == hub.presentation(), "Scan snapshot preserves suggestion")
        val named = device().apply { hint("Name", "Fixture yard", "Current friendly-name fixture", 90) }
        check(named.presentation().title == "Fixture yard" && named.presentation().subtitle.startsWith("Likely Orbit B-hyve hub"), "Current friendly name remains primary when family evidence is indirect")
        check(Device.from(hub.json()).networkFamily() == null, "Imported or restored diagnostic JSON cannot establish current DNS evidence")
        check(Device().apply { ip = hub.ip }.networkNameRows().isEmpty(), "New scan does not inherit the previous occupant's name")
        check(Device.grouped(listOf(hub, device("198.18.0.21"))).size == 2, "Identical router names cannot join endpoints")
        val hydra = device(name = "hydrawise-a1b2.home")
        check(hydra.presentation().title == "Likely Hunter Hydrawise", "Hydrawise family is explicitly tentative")
        hydra.hardware("00:11:22:33:C3:D4", "Synthetic current adapter")
        check(hydra.networkFamily() == null, "Current MAC suffix disagreement defeats name suggestion")
        for (first in listOf(false, true)) {
            val d = Device().apply { ip = "198.18.0.22" }
            if (first) d.networkName(LocalNetworkNames.Result(LocalNetworkNames.Status.CONFIRMED, "hydrawise-a1b2.home"))
            d.hint("Model", "Synthetic television model", "Current response fixture", 80)
            if (!first) d.networkName(LocalNetworkNames.Result(LocalNetworkNames.Status.CONFIRMED, "hydrawise-a1b2.home"))
            check(d.networkFamily() == null && !d.presentation().title.startsWith("Likely"), "Stale router name cannot override current model in either order")
        }
        val stronger = Device().apply { ip = "2001:db8::20"; link("fixture:same-hardware"); hint("Device type", "Thermostat", "Current response fixture", 80) }
        hub.link("fixture:same-hardware")
        check(Device.grouped(listOf(hub, stronger)).single().networkFamily() == null, "Another already-associated address's direct type suppresses suggestion")
        val alternate = device("2001:db8::21", "other-device.home").apply { link("fixture:conflicting-names") }
        val indirect = device().apply { link("fixture:conflicting-names") }
        check(Device.grouped(listOf(indirect, alternate)).single().networkFamily() == null, "A conflicting associated name cannot restore a tentative family")
        val report = TroubleshootingReport.create("Synthetic report", listOf(device().troubleshootingGroup()), emptyList()).text()
        check(report.contains("Router-reported name") && !report.contains("bhyve-bh1-abcd"), "Default troubleshooting report retains outcome and masks router name")
        check(TroubleshootingReport.create("Synthetic report", listOf(device().troubleshootingGroup()), emptyList(), true).text().contains("bhyve-bh1-abcd.home"), "Explicit names-included report contains the lookup evidence")
        val unmatched = Device().apply {
            networkName(LocalNetworkNames.Result(LocalNetworkNames.Status.NOT_CONFIRMED, returnedNames = listOf("private-fixture-name.home")))
        }
        val masked = TroubleshootingReport.create("Synthetic report", listOf(unmatched.troubleshootingGroup()), emptyList()).text()
        check(unmatched.networkFamily() == null && masked.contains(LocalNetworkNames.Status.NOT_CONFIRMED.description) && !masked.contains("private-fixture-name.home"), "Unconfirmed names remain private diagnostic evidence")
        val checking = Device().apply { networkName(LocalNetworkNames.Result(LocalNetworkNames.Status.CHECKING)); finishNetworkName() }
        check(checking.networkNameRows().first().second == LocalNetworkNames.Status.STOPPED.description, "Queued lookup has terminal outcome on Stop")

        val calls = AtomicInteger()
        fun operation(dns: String, block: Boolean = false): Triple<Discovery, CountDownLatch, CountDownLatch> {
            val entered = CountDownLatch(1); val release = CountDownLatch(if (block) 1 else 0)
            val net = NetworkState().apply {
                ip = "10.123.45.1"; prefix = 24; key = "synthetic-local-dns"; this.dns = dns
                addresses.add(ip); prefixes[ip] = 24
            }
            val op = Discovery(test.targetContext, net, Discovery.EchoFactory { _, _ -> throw AssertionError("No presence probe expected") },
                Discovery.NetworkCheck { _, _ -> }, localNameReader = Discovery.LocalNameReader { _, resolver, _, _ ->
                    calls.incrementAndGet(); check(resolver.hostAddress == "10.123.45.2", "Only configured on-link private resolver selected")
                    entered.countDown(); release.await(2, TimeUnit.SECONDS)
                    LocalNetworkNames.Result(LocalNetworkNames.Status.CONFIRMED, "bhyve-bh1-abcd.home")
                })
            set(op, "accepting", true); set(op, "enriching", true)
            return Triple(op, entered, release)
        }
        val queue = Discovery::class.java.getDeclaredMethod("queueLocalName", Device::class.java).apply { isAccessible = true }
        fun peer(op: Discovery, fresh: Boolean, address: String = "10.123.45.20"): Device = Device().apply {
            ip = address
            @Suppress("UNCHECKED_CAST") val found = get(op, "found") as MutableMap<String, Device>
            found[ip] = this
            if (fresh) { @Suppress("UNCHECKED_CAST") val responders = get(op, "freshResponders") as MutableSet<String>; responders.add(ip) }
        }
        val (op, entered, release) = operation("8.8.8.8, 10.123.45.2", true)
        try {
            val candidate = peer(op, false); queue.invoke(op, candidate)
            check(candidate.networkNameRows().isEmpty() && calls.get() == 0, "Cached service candidate gets no DNS lookup")
            val current = peer(op, true); queue.invoke(op, current)
            check(entered.await(2, TimeUnit.SECONDS), "Current responder admitted to local name lookup")
            queue.invoke(op, current); check(calls.get() == 1, "Repeated service events do not repeat DNS requests")
            op.cancel(); release.countDown()
            check(current.networkFamily() == null && current.networkNameRows().first().second == LocalNetworkNames.Status.STOPPED.description, "Late completion after Stop cannot install suggestion")
        } finally { release.countDown(); op.cancel() }
        for (dns in listOf("8.8.8.8", "10.124.45.2", "")) {
            val (other, _, _) = operation(dns)
            try {
                val peers = (2..130).map { peer(other, true, "10.123.45.$it") }
                peers.forEach { queue.invoke(other, it) }
                check(peers.all { it.networkNameRows().first().second == LocalNetworkNames.Status.NO_RESOLVER.description } && calls.get() == 1,
                    "All 129 responders skip unsupported DNS configurations without a query")
                check((get(other, "localNames") as ServiceAdmission).size() == 0, "No eligible resolver consumes no lookup capacity")
                check((get(other, "health") as DiscoveryHealth).summary().isEmpty(), "Unsupported optional DNS cannot make a finished scan incomplete")
            } finally { other.cancel() }
        }
        val (limited, _, _) = operation("10.123.45.2")
        try {
            val admitted = get(limited, "localNames") as ServiceAdmission
            repeat(LocalNetworkNames.LIMIT) { admitted.reserve("synthetic-admitted-$it", "DNS") }
            val d = peer(limited, true); queue.invoke(limited, d)
            check(d.networkNameRows().first().second == LocalNetworkNames.Status.LIMIT_REACHED.description, "Eligible DNS still enforces its real lookup cap")
            check((get(limited, "health") as DiscoveryHealth).sources()[LocalNetworkNames.SOURCE]?.startsWith("Truncated") == true, "Actual eligible work exhaustion remains visible")
            check(calls.get() == 1, "An exhausted eligible lookup sends no request")
        } finally { limited.cancel() }
        val (success, _, _) = operation("10.123.45.2")
        try {
            val d = peer(success, true); queue.invoke(success, d)
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (d.networkFamily() == null && System.nanoTime() < end) Thread.sleep(10)
            check(d.presentation().title == "Likely Orbit B-hyve hub", "Admitted lookup installs a source-labelled suggestion")
            check(d.evidence.isEmpty() && d.links().isEmpty() && d.lastSeen == 0L, "Successful DNS cannot manufacture response evidence or grouping")
            check(success.snapshot().single().presentation() == d.presentation(), "Discovery snapshot delivers indirect evidence to the UI")
        } finally { success.cancel() }
    }
}
