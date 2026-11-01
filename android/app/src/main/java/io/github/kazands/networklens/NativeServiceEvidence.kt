package io.github.kazands.networklens

/** Correlate terminal modern-NSD failures with this scan's captured replies. No discovery authority. */
internal class NativeServiceEvidence(private val assembly: MdnsAssembly) {
    private data class Failure(val name: String, val type: String, val initial: MdnsAssembly.WireEvidence, val at: Long)
    internal data class Later(val observed: Int, val complete: Int)
    private val failures = LinkedHashMap<String, Failure>()
    private var closed = false

    @Synchronized fun failed(name: String?, type: String, now: Long): DiscoveryHealth.Diagnostic? {
        if (closed) return null
        val labels = MdnsAssembly.diagnosticLabels(name, type)
            ?: return DiscoveryHealth.Diagnostic.NSD_WIRE_UNAVAILABLE
        val key = MdnsPacket.key(labels)
        if (key in failures) return null
        if (failures.size >= 256) return DiscoveryHealth.Diagnostic.NSD_WIRE_UNAVAILABLE
        val evidence = assembly.wireEvidence(name, type, now)
        failures[key] = Failure(name!!, MdnsPacket.instanceType(labels), evidence, now)
        return when (evidence) {
            MdnsAssembly.WireEvidence.NOT_OBSERVED -> DiscoveryHealth.Diagnostic.NSD_WIRE_NOT_OBSERVED
            MdnsAssembly.WireEvidence.HISTORY_ONLY -> DiscoveryHealth.Diagnostic.NSD_WIRE_HISTORY
            MdnsAssembly.WireEvidence.INCOMPLETE -> DiscoveryHealth.Diagnostic.NSD_WIRE_INCOMPLETE
            MdnsAssembly.WireEvidence.COMPLETE -> DiscoveryHealth.Diagnostic.NSD_WIRE_COMPLETE
            MdnsAssembly.WireEvidence.UNAVAILABLE -> DiscoveryHealth.Diagnostic.NSD_WIRE_UNAVAILABLE
        }
    }

    /** Called once after native/multicast admission is fenced; counts only, no identifiers escape. */
    @Synchronized fun finish(now: Long): Map<String, Later> {
        if (closed) return emptyMap()
        closed = true
        val counts = LinkedHashMap<String, Later>()
        for (failure in failures.values) {
            val current = assembly.wireEvidence(failure.name, failure.type, now)
            val observed = failure.initial == MdnsAssembly.WireEvidence.NOT_OBSERVED &&
                current in listOf(MdnsAssembly.WireEvidence.HISTORY_ONLY, MdnsAssembly.WireEvidence.INCOMPLETE, MdnsAssembly.WireEvidence.COMPLETE)
            val complete = failure.initial != MdnsAssembly.WireEvidence.COMPLETE &&
                (current == MdnsAssembly.WireEvidence.COMPLETE || assembly.wireCompleteAfter(failure.name, failure.type, failure.at))
            if (observed || complete) {
                val prior = counts[failure.type] ?: Later(0, 0)
                counts[failure.type] = Later(prior.observed + if (observed) 1 else 0, prior.complete + if (complete) 1 else 0)
            }
        }
        failures.clear()
        return counts
    }
}
