import io.github.kazands.networklens.*
import java.util.concurrent.*

/** Synthetic endpoint/target names. No LAN access or captured device data. */
object RecognitionPlanningTest {
    private var checks = 0
    private fun check(value: Boolean, why: String) { checks++; if (!value) error(why) }
    @JvmStatic fun main(args: Array<String>) {
        check(IdentityProbePlan.remaining(setOf(80)) == listOf(62078, 8001), "Web service cannot suppress Apple/Samsung opportunities")
        check(IdentityProbePlan.remaining(setOf(80, 443)) == listOf(62078, 8001), "HTTPS service cannot suppress model probes")
        check(IdentityProbePlan.remaining(emptySet()) == listOf(80, 62078, 8001), "Reused presence still gets independent model opportunities")
        check(IdentityProbePlan.remaining(setOf(80, 62078, 8001)).isEmpty(), "Already checked identity ports are not repeated")
        val first = LocalMetadata.Request(LocalMetadata.Kind.SPOTIFY, 8080, "/first")
        val second = first.copy(port = 8081, path = "/second")
        val plan = MetadataCandidates()
        check(plan.offer(first, 1) == MetadataCandidates.Offer.START, "First validated target starts one worker")
        check(plan.offer(second.copy(secure = true), 2) == MetadataCandidates.Offer.REJECTED, "Alternatives cannot change transport security")
        val secure = MetadataCandidates(); secure.offer(first.copy(kind = LocalMetadata.Kind.IPP, secure = true), 1)
        check(secure.offer(second.copy(kind = LocalMetadata.Kind.IPP), 2) == MetadataCandidates.Offer.REJECTED, "IPPS cannot downgrade to plaintext after failure")
        check(plan.offer(second.copy(kind = LocalMetadata.Kind.WLED), 2) == MetadataCandidates.Offer.REJECTED, "A target cannot change the admitted protocol")
        check(plan.offer(second, 2) == MetadataCandidates.Offer.WAIT, "Alternative waits behind the active worker")
        check(plan.offer(first, 3) == MetadataCandidates.Offer.DUPLICATE, "Repeated advertisements cannot grow work")
        check(plan.offer(first.copy(port = 8082), 3) == MetadataCandidates.Offer.LIMIT, "Untrusted churn has a two-target cap")
        check(plan.next(10, Long.MAX_VALUE) == first, "First target selected")
        val deadline = plan.deadline()
        check(plan.next(20, Long.MAX_VALUE) == second && plan.deadline() == deadline, "Fallback shares original deadline")
        check(plan.next(30, Long.MAX_VALUE) == null, "Exhaustion stops the worker")
        plan.close()
        check(plan.offer(second.copy(path = "/late"), 40) == MetadataCandidates.Offer.CLOSED, "Closed scan rejects late targets")
        val late = MetadataCandidates(); late.offer(first, 1); late.next(10, 500)
        check(late.next(20, 500) == null && late.offer(second, 30) == MetadataCandidates.Offer.START,
            "A later target can restart an exhausted worker within the original window")
        check(late.next(40, 900) == second && late.deadline() == 500L, "Restart cannot extend the original scan deadline")
        check(late.offer(first.copy(path = "/expired"), 500) == MetadataCandidates.Offer.CLOSED, "Expired plans do not admit work")
        val lookups = MetadataLookups(); lookups.queue(first)
        lookups.failure(first.kind, MetadataHttp.Failure(MetadataHttp.Reason.HTTP_STATUS, 404))
        check(lookups.beginCandidate(second) && lookups.snapshot().single().port == 8081, "Failed endpoint replaced with admitted alternate")
        lookups.success(second.kind, LocalMetadata.Report(mapOf("Model" to "Synthetic model")))
        check(!lookups.beginCandidate(first), "Success cannot be overwritten by another target")
        lookups.finish(false); check(!lookups.beginCandidate(second), "Terminal diagnostics cannot be reopened")
        val q = FairWorkQueue(10, 4)
        val order = ArrayList<String>()
        fun task(owner: String, n: Int) = FairWorkQueue.Task(owner, Runnable { order.add("$owner$n") })
        for (n in 1..4) check(q.offer(task("busy", n)), "Bounded noisy endpoint admitted")
        check(!q.offer(task("busy", 5)), "One endpoint cannot occupy every slot")
        q.offer(task("other", 1)); q.offer(task("third", 1))
        repeat(4) { q.poll()!!.run() }
        check(order == listOf("busy1", "other1", "third1", "busy2"), "Queued endpoints receive round-robin opportunities")
        val it = q.iterator(); it.next(); it.remove(); check(q.size == 1, "Iterator removal affects actual queue")
        q.clear(); check(q.size == 0 && q.remainingCapacity() == 10, "Cancellation clearing releases queue capacity")
        val woke = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val f = executor.submit { q.take().run(); woke.countDown() }
            q.put(task("last", 1)); check(woke.await(2, TimeUnit.SECONDS), "Blocked consumers wake for admitted work")
            f.get(2, TimeUnit.SECONDS)
        } finally { executor.shutdownNow() }
        check(q.poll(1, TimeUnit.MILLISECONDS) == null, "Timed empty polls remain bounded")
        val work = DiscoveryWork(); val gate = CountDownLatch(1); val entered = CountDownLatch(2); val served = CountDownLatch(1)
        try {
            repeat(2) { work.submit(DiscoveryWork.Lane.METADATA, "slow", Runnable { entered.countDown(); gate.await() }) }
            check(entered.await(2, TimeUnit.SECONDS), "Slow metadata workers held")
            repeat(8) { check(work.submit(DiscoveryWork.Lane.METADATA, "busy", Runnable {}), "One owner bounded queue") }
            check(!work.submit(DiscoveryWork.Lane.METADATA, "busy", Runnable {}), "Owner overflow is explicit")
            check(work.submit(DiscoveryWork.Lane.METADATA, "other", Runnable { served.countDown() }), "Another owner retains admission")
            gate.countDown(); check(served.await(2, TimeUnit.SECONDS), "Other owner runs despite burst")
        } finally { gate.countDown(); work.shutdownNow(); check(work.awaitTermination(2, TimeUnit.SECONDS), "Workers and queues clean up") }
        println("PASS: $checks recognition planning and fairness checks")
    }
}
