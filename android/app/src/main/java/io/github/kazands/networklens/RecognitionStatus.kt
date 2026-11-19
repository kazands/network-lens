package io.github.kazands.networklens

/** Explain evidence gaps without turning a failed lookup into a claim about the device. */
internal object RecognitionStatus {
    fun explain(identified: Boolean, conflict: Boolean, model: Boolean, families: List<String>,
                lookups: List<MetadataLookups.Entry>, named: Boolean = false): String {
        if(conflict)return "The device supplied conflicting identity information. We have kept the reports separate rather than choosing a device type."
        if(identified)return "Identified from the current scan’s device information."
        val evidence=when {
            model -> "A model was reported, but we could not match it confidently to a product in the local catalogue."
            families.isNotEmpty() -> "Recognised services: ${families.take(4).joinToString(", ")}. We do not yet have enough information to identify the exact product."
            named -> "This device replied and shared a name, but we did not receive a usable model."
            else -> RecognitionCopy.UNNAMED
        }
        val followup=when {
            lookups.any {it.state==MetadataLookups.State.QUEUED} -> "Additional device information is still being checked."
            lookups.any {it.state==MetadataLookups.State.WORK_LIMIT || it.state==MetadataLookups.State.INCOMPLETE || it.state==MetadataLookups.State.STOPPED} -> "Some identity checks did not finish. See technical details for their status."
            lookups.any {it.state==MetadataLookups.State.FAILED} -> "An identity request did not succeed. See technical details for the response."
            else -> ""
        }
        return listOf(evidence,followup).filter {it.isNotEmpty()}.joinToString(" ")
    }
}
