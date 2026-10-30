package dev.netlens.test

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import dev.netlens.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Behavioral guards for same-scan presence reuse and deferred presentation. */
internal object ScanEfficiency {
    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner) }
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(owner, value) }
    private fun main(test: Instrumentation, action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
        failure?.let { throw it }
    }
    private fun network() = NetworkState().apply {
        // No Android Network: an accidental real socket path fails before any traffic.
        ip = "198.18.0.1"; prefix = 24; key = "synthetic-efficiency"
        prefixes[ip] = prefix; addresses.add(ip)
    }
    private fun probe(op: Discovery) = Discovery::class.java.getDeclaredMethod("probe", String::class.java,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).let {
        it.isAccessible = true; it.invoke(op, "198.18.0.2", 160, 220)
    }
    private fun blockedDetails(op: Discovery): CountDownLatch {
        val hold = CountDownLatch(1); val started = CountDownLatch(4)
        repeat(4) { (get(op, "enrichment") as DiscoveryWork).submit(DiscoveryWork.Lane.DETAILS) {
            started.countDown(); try { hold.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        } }
        check(started.await(3, TimeUnit.SECONDS)); return hold
    }
    fun run(test: Instrumentation, check: (Boolean, String) -> Unit) {
        val opens = AtomicInteger()
        val factory = Discovery.EchoFactory { _, _ ->
            opens.incrementAndGet()
            object : IcmpEcho.Channel {
                var reply: ByteArray? = null
                override fun identifier() = 222
                override fun send(packet: ByteArray) {
                    reply = packet.clone().also {
                        it[0] = 0; it[2] = 0; it[3] = 0
                        val sum = IcmpEcho.checksum(it); it[2] = (sum ushr 8).toByte(); it[3] = sum.toByte()
                    }
                }
                override fun receive(waitMillis: Int) = reply.also { reply = null }
                override fun close() {}
            }
        }
        fun operation(): Discovery = Discovery(test.targetContext, network(), factory).also {
            set(it, "accepting", true); set(it, "enriching", true)
        }
        val first = operation(); val hold = blockedDetails(first)
        try {
            val candidate = Device().apply { ip = "198.18.0.2" }
            (get(first, "found") as MutableMap<String, Device>)[candidate.ip] = candidate
            check(first.snapshot().isEmpty(), "An unconfirmed native candidate remains absent")
            probe(first)
            check(opens.get() == 1 && first.snapshot().size == 1, "A candidate needs a freshly validated echo before presence reuse")
            candidate.hint("Model", "Synthetic model", "Synthetic current response", 80)
            repeat(8) { probe(first) }
            check(opens.get() == 1, "A current same-scan reply avoids duplicate echo sockets")
            check(first.snapshot().single().model == "Synthetic model", "Reuse preserves current device details")
            check((get(first, "detailsQueued") as Set<*>).contains(candidate.ip), "Reuse preserves independent name and MAC enrichment")
            check(candidate.mac.isEmpty(), "Unknown hardware addresses remain unknown")
        } finally { first.cancel(); hold.countDown() }
        val second = operation(); val secondHold = blockedDetails(second)
        try {
            probe(second)
            check(opens.get() == 2, "A new scan independently confirms the same address")
        } finally { second.cancel(); secondHold.countDown() }
        val stopped = operation(); stopped.cancel(); probe(stopped)
        check(opens.get() == 2, "Cancelled work opens no echo socket")

        for (reset in listOf(false, true)) {
            Store(test.targetContext).clear()
            val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            SystemClock.sleep(350)
            val active = operation(); val stale = operation()
            val gate = CountDownLatch(1); val began = CountDownLatch(1)
            try {
                main(test) { set(activity, "network", network()); set(activity, "discovery", active); set(activity, "scanning", true) }
                val store = get(activity, "store") as Store; val token = store.token()
                val executor = get(activity, "presentation") as ScheduledExecutorService
                executor.execute { began.countDown(); try { gate.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() } }
                check(began.await(3, TimeUnit.SECONDS), "Presentation fixture starts before the update burst")
                val offer = MainActivity::class.java.declaredMethods.single { it.name == "offerScanSnapshot" }.apply { isAccessible = true }
                val builds = AtomicInteger(); val discarded = AtomicInteger()
                val result = Device().apply { ip = "198.18.0.2"; name = "Synthetic final peer" }
                repeat(100) { n -> offer.invoke(activity, active, token, n, 100, { builds.incrementAndGet(); listOf(result.copy()) }, false, "") }
                offer.invoke(activity, stale, token, 100, 100, { discarded.incrementAndGet(); emptyList<Device>() }, false, "")
                if (reset) {
                    store.clear()
                } else {
                    offer.invoke(activity, active, token, 100, 100, { builds.incrementAndGet(); listOf(result.copy()) }, true, "Synthetic terminal")
                    offer.invoke(activity, active, token, 0, 100, { discarded.incrementAndGet(); emptyList<Device>() }, false, "Late progress")
                }
                check(builds.get() == 0, "Deferred updates make no snapshots while presentation is occupied")
                gate.countDown()
                val end = SystemClock.uptimeMillis() + 5000
                var idle = false
                while (!idle && SystemClock.uptimeMillis() < end) {
                    main(test) { idle = !(get(activity, "preparing") as Boolean) && !(get(activity, "displayPosted") as Boolean) }
                    SystemClock.sleep(20)
                }
                check(idle, "Presentation settles within its bounded fixture wait")
                check(discarded.get() == 0, "Stale operations and post-terminal updates never evaluate snapshots")
                if (reset) {
                    check(builds.get() == 0, "Reset fencing rejects queued snapshots before reading device data")
                    main(test) { check((get(activity, "devices") as List<*>).isEmpty(), "Reset fencing keeps old devices out of the list") }
                } else {
                    check(builds.get() == 1, "A burst creates only its terminal snapshot")
                    main(test) {
                        check(!(get(activity, "scanning") as Boolean), "The final update ends scanning")
                        check((get(activity, "devices") as List<Device>).single().name == result.name, "The final device list survives batching")
                    }
                }
            } finally { active.cancel(); stale.cancel(); gate.countDown(); main(test) { activity.finish() } }
        }
    }
}
