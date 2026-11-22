package io.github.kazands.networklens.test

import android.app.Instrumentation
import io.github.kazands.networklens.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic transports only. The fixture has no Android Network and cannot send LAN packets. */
internal object AdditionalTcpChecks {
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner)!! }
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    private fun additional(op: Discovery, host: String = "198.18.0.2") = Discovery::class.java.getDeclaredMethod("additionalTcp", String::class.java).let {
        it.isAccessible = true; it.invoke(op, host)
    }
    private fun probe(op: Discovery, timeout: Int) = Discovery::class.java.getDeclaredMethod("probe", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).let {
        it.isAccessible = true; it.invoke(op, "198.18.0.2", timeout, 220)
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        fun fixture(connect: (Int, Int) -> Int): Discovery {
            val network = NetworkState().apply {
                ip = "198.18.0.1"; prefix = 24; key = "synthetic-additional-tcp"
                addresses.add(ip); prefixes[ip] = prefix
            }
            return Discovery(test.targetContext, network,
                Discovery.EchoFactory { _, _ -> throw IcmpEcho.Failure(IcmpEcho.Problem.SETUP, "Synthetic unavailable echo") },
                Discovery.NetworkCheck { _, _ -> }, tcpConnector = Discovery.TcpConnector { _, port, timeout -> connect(port, timeout) }
            ).also {
                set(it, "accepting", true); set(it, "enriching", true)
                set(it, "scanDeadline", System.nanoTime() + TimeUnit.SECONDS.toNanos(5))
            }
        }
        fun holdDetails(op: Discovery): CountDownLatch {
            val hold = CountDownLatch(1); val started = CountDownLatch(4)
            repeat(4) { (get(op, "enrichment") as DiscoveryWork).submit(DiscoveryWork.Lane.DETAILS) {
                started.countDown(); try { hold.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            } }
            check(started.await(3, TimeUnit.SECONDS), "Fixture owns its enrichment workers")
            return hold
        }
        for (open in listOf(8012, 10101, 11095)) {
            val calls = mutableListOf<Int>()
            val op = fixture { port, timeout ->
                calls.add(port); check(timeout in 1..160, "Additional connect timeout remains bounded")
                if (port != open) throw ConnectException("PRIVATE_SOCKET_TEXT")
                1
            }
            val hold = holdDetails(op)
            try {
                probe(op, 160)
                check(op.snapshot().isEmpty() && calls.none { it in listOf(8012, 10101, 11095) }, "Initial sweep retains its original port coverage")
                // Common recovery ports use their original timeout; check the new pass separately.
                calls.clear(); additional(op)
                val d = op.snapshot().single()
                check(d.ports.contains(open) && d.evidence.any { it.contains("confirmed twice") }, "Additional-only peer enters the current inventory")
                check(d.model.isEmpty() && d.manufacturer.isEmpty() && d.mac.isEmpty() && d.identifiedProduct() == null,
                    "An open additional port supplies no invented camera or vendor identity")
                val before = calls.size; additional(op)
                check(calls.size == before, "Additional checks run only once per address per scan")
                val journal = (get(op, "tcpProbes") as TcpProbeJournal).snapshot()
                val withNames = TroubleshootingReport.create("", listOf(d.troubleshootingGroup()), emptyList(), true, journal).text()
                val redacted = TroubleshootingReport.create("", listOf(d.troubleshootingGroup()), emptyList(), false, journal).text()
                check(withNames.contains("198.18.0.2") && withNames.contains("TCP port: $open") && withNames.contains("Two connections succeeded"), "Report includes confirmed endpoint and transport evidence")
                check(!redacted.contains("198.18.0.2") && !withNames.contains("PRIVATE_SOCKET_TEXT"), "Reports redact addresses and never retain exception text")
            } finally { op.cancel(); hold.countDown() }
        }
        run {
            val calls = mutableListOf<Int>()
            val op = fixture { port, _ -> calls.add(port); if (port == 10101) 1 else throw ConnectException() }
            val hold = holdDetails(op)
            try { probe(op, 250); check(op.snapshot().singleOrNull()?.ports?.contains(10101) == true, "Real unanswered recovery reaches additional ports") }
            finally { op.cancel(); hold.countDown() }
        }
        for (fault in listOf("silent", "transient", "cancelled", "expired", "scope")) {
            var calls = 0
            lateinit var op: Discovery
            op = fixture { _, _ ->
                calls++
                when (fault) {
                    "silent" -> throw SocketTimeoutException()
                    "transient" -> if (calls % 2 == 0) throw ConnectException() else 1
                    "cancelled" -> { op.cancel(); 1 }
                    else -> 1
                }
            }
            try {
                if (fault == "expired") set(op, "scanDeadline", System.nanoTime() - 1)
                additional(op, if (fault == "scope") "198.19.0.2" else "198.18.0.2")
                check(op.snapshot().isEmpty(), "$fault cannot add an unconfirmed device")
                if (fault in listOf("expired", "scope")) check(calls == 0, "$fault opens no connection")
                if (fault == "expired") check((get(op, "health") as DiscoveryHealth).sources().values.any { it.contains("time budget", true) }, "Expired work is reported as incomplete")
                if (fault == "transient") check((get(op, "tcpProbes") as TcpProbeJournal).snapshot().entries.all { !it.confirmed }, "Failed reconfirmation remains diagnostic only")
            } finally { op.cancel() }
        }
        for (presence in listOf("current", "candidate", "absent")) {
            val op = fixture { _, _ -> 7 }
            val hold = holdDetails(op)
            val d = Device().apply { ip = "198.18.0.2"; seen = presence == "current" }
            if (presence != "absent") (get(op, "found") as MutableMap<String, Device>)[d.ip] = d
            if (presence == "current") (get(op, "freshResponders") as MutableSet<String>).add(d.ip)
            set(op, "accepting", false)
            try {
                additional(op)
                if (presence == "current") {
                    val current = op.snapshot().single()
                    check(current.ports.containsAll(listOf(8012, 10101, 11095)) && current.latency == 7,
                        "Final enrichment retains confirmed ports and latency for a current responder")
                    check(current.evidence.any { it.contains("confirmed twice") } && current.model.isEmpty() && current.manufacturer.isEmpty(),
                        "Final enrichment retains source evidence without inventing identity")
                } else check(op.snapshot().isEmpty(), "Closed admission cannot promote a $presence peer during enrichment")
            } finally { op.cancel(); hold.countDown() }
        }
        run {
            var calls = 0
            val op = fixture { _, _ -> calls++; throw ConnectException() }
            val d = Device().apply { ip = "198.18.0.2"; seen = true }
            (get(op, "found") as MutableMap<String, Device>)[d.ip] = d
            (get(op, "freshResponders") as MutableSet<String>).add(d.ip)
            try {
                val method = Discovery::class.java.getDeclaredMethod("queueDetails", Device::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                method.invoke(op, d, 0, 0)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (System.nanoTime() < deadline && synchronized(d) { d.diagnostics.snapshot().count { it.field.startsWith("TCP port ") && it.value == "Connection failed or refused" } } < 3) Thread.sleep(10)
                check(calls == 3, "Existing unidentified responder receives the additional detail checks")
                check(d.ports.isEmpty() && d.model.isEmpty(), "Refused ports change neither observed ports nor identity")
            } finally { op.cancel() }
        }
    }
}
