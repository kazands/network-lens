package io.github.kazands.networklens

/** A separate bounded lane for unresolved current responders; never consumes existing metadata workers. */
internal class PublicIdentityDiscovery(
    private val health: DiscoveryHealth,private val current: (Device)->Boolean,private val scanDeadline: ()->Long,
    private val submit: (String,String,Runnable)->Boolean,private val verifyNetwork: ()->Unit,
    private val read: (Device,PublicIdentity.Request,Long)->PublicIdentity.Result,
    private val preparePort80: (Device)->Unit = {}
) {
    private val plans=linkedMapOf<String,PublicIdentity.Plan>()
    private val admission=ServiceAdmission(96,32)
    @Volatile private var closed=false
    @Synchronized fun offer(d: Device) {
        if(closed || !current(d)) return
        val requests=synchronized(d) {if(d.identifiedProduct()!=null || IdentityHints.specific(d.model)) emptyList() else PublicIdentity.candidates(d.ports,d.hostname.isEmpty())}
        // Give the equipment reader the root reservation even when another
        // protocol learned port 80 first. Its root response supplies these clues.
        if(requests.any {it.kind==PublicIdentity.Kind.HTTP && it.port==80}) synchronized(d) {preparePort80(d)}
        for(request in requests) {
            if(!d.publicIdentity.queue(request)) continue
            val source=request.kind.source
            if(plans.size>=32 && d.ip !in plans || admission.admit(d.ip,request.kind.name+":"+request.port)!=ServiceAdmission.Result.ADMITTED) {
                d.publicIdentity.finish(request,PublicIdentity.State.LIMITED);health.truncated(source,"Public-reader admission limit reached");continue
            }
            val plan=plans.getOrPut(d.ip) {PublicIdentity.Plan()}
            when(plan.offer(request)) {
                PublicIdentity.Plan.Offer.START -> {
                    health.workStarted(SOURCE)
                    if(!submit(SOURCE,d.ip,Runnable {run(d,plan)})) {
                        plan.close();d.publicIdentity.finish(request,PublicIdentity.State.LIMITED)
                        health.truncated(source,"Public-reader queue limit reached");health.workFinished(SOURCE)
                    }
                }
                PublicIdentity.Plan.Offer.LIMIT -> {d.publicIdentity.finish(request,PublicIdentity.State.LIMITED);health.truncated(source,"Per-endpoint public-reader limit reached")}
                PublicIdentity.Plan.Offer.CLOSED -> d.publicIdentity.finish(request,PublicIdentity.State.INCOMPLETE)
                else -> {}
            }
        }
    }
    private fun run(d: Device,plan: PublicIdentity.Plan) {
        try {
            while(!closed && current(d)) {
                val now=System.nanoTime();val request=plan.next(now,scanDeadline()) ?: break
                val deadline=minOf(plan.deadline(),now+2_000_000_000L)
                if(synchronized(d) {d.identifiedProduct()!=null || IdentityHints.specific(d.model)}) {
                    d.publicIdentity.finish(request,PublicIdentity.State.NOT_NEEDED);continue
                }
                if(!d.publicIdentity.begin(request,now,deadline)) continue
                health.workStarted(request.kind.source)
                try {
                    verifyNetwork()
                    val result=read(d,request,deadline)
                    verifyNetwork()
                    observe(d,request,result)
                    health.count(request.kind.source,"responses received")
                } catch(e:Exception) {
                    if(closed || !current(d)) return
                    val failure=AndroidMetadataFailure.classify(e,System.nanoTime()>=deadline)
                    val unavailable=request.kind==PublicIdentity.Kind.LLMNR && failure.reason in setOf(MetadataHttp.Reason.CONNECTION_REFUSED,MetadataHttp.Reason.CONNECTION_TIMEOUT,MetadataHttp.Reason.READ_TIMEOUT)
                    d.publicIdentity.finish(request,if(unavailable) PublicIdentity.State.NO_RESPONSE else PublicIdentity.State.FAILED,reason=failure.reason)
                    if(unavailable) health.count(request.kind.source,"no LLMNR response")
                    else MetadataHttp.recordFailure(health,request.kind.source,failure,false)
                } finally {health.workFinished(request.kind.source)}
            }
        } finally {health.workFinished(SOURCE)}
    }
    fun observe(d: Device,request: PublicIdentity.Request,result: PublicIdentity.Result) = synchronized(d) {
        if(closed || !current(d)) return@synchronized
        if(!d.publicIdentity.finish(request,if(result.fields.isEmpty() && result.hostname.isEmpty()) PublicIdentity.State.NO_RESPONSE else PublicIdentity.State.OBSERVED,result)) return@synchronized
        result.software?.let {d.discoveryFamilies(setOf(it.family),request.kind.source+" (software only)")}
        if(result.hostname.isNotEmpty()) {
            d.hint("Hostname",result.hostname,PublicIdentity.Kind.LLMNR.source,40)
            d.hint("Name",result.hostname,PublicIdentity.Kind.LLMNR.source,40)
        }
    }
    @Synchronized fun close() {closed=true;plans.values.forEach {it.close()}}
    companion object {const val SOURCE="Public identity readers"}
}
