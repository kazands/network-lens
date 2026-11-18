package io.github.kazands.networklens

/** Per-scan admission and alternate-target ownership. Closing is terminal, including late producers. */
internal class MetadataPlans(maximum: Int = 96, perKind: Int = 32) {
    data class Decision(val admission: ServiceAdmission.Result, val offer: MetadataCandidates.Offer,
                        val plan: MetadataCandidates? = null)
    private val admission = ServiceAdmission(maximum, perKind)
    private val plans = HashMap<String, MetadataCandidates>()
    private var closed = false
    @Synchronized fun size(): Int = admission.size()
    @Synchronized fun offer(peer: String, request: LocalMetadata.Request, now: Long): Decision {
        if (closed) return Decision(ServiceAdmission.Result.INVALID, MetadataCandidates.Offer.CLOSED)
        val result = admission.admit(peer, request.kind.name)
        if (result != ServiceAdmission.Result.ADMITTED && result != ServiceAdmission.Result.DUPLICATE)
            return Decision(result, MetadataCandidates.Offer.REJECTED)
        val plan = plans.getOrPut(peer + "|" + request.kind.name) { MetadataCandidates() }
        return Decision(result, plan.offer(request, now), plan)
    }
    @Synchronized fun close() {
        closed = true
        plans.values.forEach { it.close() }
        plans.clear()
    }
}
