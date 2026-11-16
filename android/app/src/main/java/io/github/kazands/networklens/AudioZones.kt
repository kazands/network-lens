package io.github.kazands.networklens

import java.security.MessageDigest

/** Scan-local relationships, deliberately separate from endpoint identity/grouping links. */
internal object AudioZones {
    enum class Kind(val source: String, val port: Int) {
        VSSL("VSSL zone status", 50002), SONOS("Sonos zone topology", 1400)
    }
    data class Member(val id: String, val room: String, val parent: String = "")
    data class Report(val kind: Kind, val hardware: String, val model: String = "", val zone: Int = 0,
                      val group: String = "", val coordinator: String = "", val members: List<Member> = emptyList())
    data class Endpoint(val ip: String, val report: Report)
    fun token(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Both endpoints must independently report their own membership in this scan. */
    fun rows(endpoint: Endpoint, current: List<Endpoint>): List<Pair<String, String>> {
        val r = endpoint.report
        val rows = arrayListOf("Source" to r.kind.source)
        if (r.kind == Kind.VSSL) {
            rows.add("Amplifier" to "VSSL ${r.model}")
            rows.add("Audio zone" to "Zone ${r.zone}")
            val family = current.filter { it.report.kind == r.kind && it.report.hardware == r.hardware }
            if (family.any { it.report.model != r.model }) {
                rows.add("Zone mapping" to "Conflicting zone reports; related addresses are not shown")
            } else {
                val peers = family.filter { it.report.zone != r.zone }.sortedWith(compareBy({ it.report.zone }, { it.ip }))
                rows.add("Other zones found on this amplifier" to peers.joinToString("\n") { "Zone ${it.report.zone} · ${it.ip}" }.ifEmpty { "None confirmed in this scan" })
            }
        } else {
            val self = r.members.singleOrNull { it.id == r.hardware } ?: return emptyList()
            if (self.room.isNotEmpty()) rows.add("Room" to self.room)
            rows.add("Playback role" to when {
                self.parent.isNotEmpty() -> "Bonded player"
                r.coordinator == r.hardware -> "Group coordinator"
                else -> "Group member"
            })
            val peers = current.filter { other ->
                val p = other.report
                p.kind == r.kind && p.hardware != r.hardware && p.group == r.group &&
                    p.coordinator == r.coordinator && p.members.singleOrNull { it.id == p.hardware } != null &&
                    r.members.any { it.id == p.hardware } && p.members.any { it.id == r.hardware }
            }.distinctBy { it.report.hardware }.sortedBy { it.ip }
            rows.add("Other players confirmed in this group" to peers.joinToString("\n") { other ->
                val member = other.report.members.single { it.id == other.report.hardware }
                listOf(member.room, other.ip).filter { it.isNotEmpty() }.joinToString(" · ")
            }.ifEmpty { "None confirmed in this scan" })
            rows.add("Mapping" to "Playback groups do not combine physical players into one device")
        }
        return rows
    }
}
