package io.github.kazands.networklens

/** Playback endpoints are protocol + port + reported player ID, not physical grouping links. */
internal object AudioPlayers {
    enum class Protocol(val source: String) {
        HEOS("HEOS player information"), MUSICCAST("MusicCast device information"), BLUOS("BluOS player information"),
        AIRPLAY("AirPlay group advertisement"), CAST("Cast group information")
    }
    enum class Relation { ROSTER, DISTRIBUTION, PRIMARY_SECONDARY, ADVERTISED, HOSTED }
    data class Group(val id: String, val name: String = "", val role: String = "", val leader: String = "",
                     val peers: Set<String> = emptySet(), val relation: Relation, val label: String = "Playback group", val zone: String = "")
    data class Player(val protocol: Protocol, val port: Int, val id: String, val name: String = "",
                      val model: String = "", val maker: String = "", val zones: List<String> = emptyList(),
                      val groups: List<Group> = emptyList())
    data class Current(val ip: String, val player: Player)
    data class Result(val player: Player, val hints: Map<String, String> = emptyMap(), val note: String = "")
    fun key(protocol: Protocol, port: Int) = protocol.name + ":" + port
    fun addressKey(ip: String, port: Int): String? = Address.bytes(ip.substringBefore('%'))?.let {
        AudioZones.token(it.joinToString("") { b -> "%02x".format(b.toInt() and 255) } + ":" + port)
    }
    fun rows(self: Current, all: List<Current>): List<Pair<String, String>> {
        val p = self.player
        val rows = arrayListOf("Player source" to "${p.protocol.source} · port ${p.port}")
        if (p.name.isNotEmpty()) rows.add("Player name" to p.name)
        if (p.model.isNotEmpty()) rows.add("Player model" to p.model)
        if (p.maker.isNotEmpty()) rows.add("Reported manufacturer" to p.maker)
        if (p.zones.isNotEmpty()) rows.add("Hardware zones" to p.zones.joinToString(", "))
        for (g in p.groups) {
            rows.add(g.label to g.name.ifEmpty { "Reported group" })
            if (g.role.isNotEmpty()) rows.add("Player role" to g.role)
            if (g.zone.isNotEmpty()) rows.add("Distribution zone" to g.zone)
            if (g.relation == Relation.HOSTED) {
                rows.add("Group membership" to "Group description only; individual members are not established")
                continue
            }
            val candidates = all.filter { it.player.protocol == p.protocol }.mapNotNull { c ->
                c.player.groups.singleOrNull { it.id == g.id && it.relation == g.relation && it.label == g.label }?.let { c to it }
            }
            // One reported identity with contradictory current roles/rosters cannot corroborate a group.
            val conflicting = candidates.groupBy { it.first.player.id }.values.any { entries -> entries.map { it.second }.distinct().size > 1 }
            val leaders = candidates.filter { (_, x) -> x.role == "Coordinator" }.distinctBy { it.first.player.id }
            val leader = leaders.singleOrNull()
            fun confirmed(c: Current, membership: Group): Boolean = when (g.relation) {
                Relation.ROSTER -> membership.leader == g.leader && membership.leader.isNotEmpty() &&
                    p.id in membership.peers && c.player.id in g.peers
                Relation.DISTRIBUTION -> leader != null && (c.player.id == leader.first.player.id ||
                    c.player.id in leader.second.peers && membership.role == "Member") &&
                    (p.id == leader.first.player.id || p.id in leader.second.peers && g.role == "Member")
                Relation.PRIMARY_SECONDARY -> leader != null && leader.first.player.id == g.leader && membership.leader == g.leader &&
                    (c.player.id == g.leader || c.player.id in leader.second.peers && g.leader in membership.peers) &&
                    (p.id == g.leader || p.id in leader.second.peers && g.leader in g.peers)
                Relation.ADVERTISED -> leaders.size <= 1
                Relation.HOSTED -> false
            }
            val peers = candidates.filter { (c, x) -> !conflicting && c.player.id != p.id && confirmed(c, x) }.distinctBy { it.first.player.id }
                .sortedWith(compareBy({ it.first.ip }, { it.first.player.port }))
            rows.add((if (g.relation == Relation.ADVERTISED) "Other devices advertising this group" else "Other current players in this group") to
                peers.joinToString("\n") { (c, _) -> listOf(c.player.name, "${c.ip} · port ${c.player.port}").filter { it.isNotEmpty() }.joinToString(" · ") }
                    .ifEmpty { "None confirmed in this scan" })
        }
        return rows
    }
}
