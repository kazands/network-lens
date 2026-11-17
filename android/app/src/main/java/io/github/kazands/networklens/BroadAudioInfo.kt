package io.github.kazands.networklens

import java.net.InetAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import io.github.kazands.networklens.AudioPlayers.Protocol
import io.github.kazands.networklens.AudioPlayers.Relation
import io.github.kazands.networklens.AudioPlayers.Group
import io.github.kazands.networklens.AudioPlayers.Player
import io.github.kazands.networklens.AudioPlayers.Result

/** Protocol-qualified, fixed read-only queries; rosters never create endpoints or supply neighbours' identity. */
internal object BroadAudioInfo {
    data class Plan(val protocol: Protocol, val port: Int) { val key get() = AudioPlayers.key(protocol, port) }
    fun advertisementPlan(service: MdnsPacket.Service): Plan? {
        if (service.port !in 1..65535) return null
        return when (LocalServices.canonicalType(service.type)) {
            "_musc._tcp.", "_musp._tcp." -> Plan(Protocol.BLUOS, service.port)
            "_googlecast._tcp." -> if (CastInfo.isGroup(service)) null else Plan(Protocol.CAST, 8008)
            else -> null
        }
    }
    fun descriptionPlan(values: Map<String, String>): Plan? = when {
        values["deviceType"] == "urn:schemas-denon-com:device:ACT-Denon:1" -> Plan(Protocol.HEOS, 1255)
        DeviceCatalog.key(values["manufacturer"].orEmpty()) in setOf("yamaha", "yamaha corporation") &&
            values["deviceType"] == "urn:schemas-upnp-org:device:MediaRenderer:1" -> Plan(Protocol.MUSICCAST, 80)
        else -> null
    }
    private fun label(s: String?): String = s?.takeIf { it.length <= 160 && SafeText.label(it, 160) == it }.orEmpty()
    private fun uuid(s: String?): String? = s?.takeIf { it.matches(Regex("(?i)[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}")) }
        ?.replace("-", "")?.lowercase(java.util.Locale.ROOT)?.takeUnless { it.all { c -> c == '0' } }?.let(AudioZones::token)
    private fun obj(v: BoundedJson.Value?): Map<String, BoundedJson.Value>? = (v as? BoundedJson.ObjectValue)?.values
    private fun array(v: BoundedJson.Value?): List<BoundedJson.Value>? = (v as? BoundedJson.ArrayValue)?.values?.takeIf { it.size <= 64 }
    private fun text(v: BoundedJson.Value?): String? = (v as? BoundedJson.Scalar)?.takeUnless { it.number }?.text
    private fun number(v: BoundedJson.Value?): String? = (v as? BoundedJson.Scalar)?.takeIf { it.number }?.text
    private fun id(v: BoundedJson.Value?): String? = (v as? BoundedJson.Scalar)?.text?.takeIf { it.matches(Regex("-?(?:0|[1-9][0-9]{0,18})")) && it.toLongOrNull() != null }
    private fun doc(bytes: ByteArray) = BoundedJson.document(bytes)?.values ?: invalid()
    private fun invalid(): Nothing = throw MetadataHttp.Failure(MetadataHttp.Reason.INVALID_METADATA)
    private fun sameIp(a: String, b: String): Boolean {
        val left = Address.bytes(a.substringBefore('%')) ?: return false
        return Address.bytes(b.substringBefore('%'))?.contentEquals(left) == true
    }
    private fun endpoint(value: String): Pair<String, Int>? = try {
        val uri = URI("http://$value")
        val host = uri.host?.removePrefix("[")?.removeSuffix("]")
        if (host == null || Address.bytes(host) == null || uri.port !in 1..65535 || uri.rawUserInfo != null ||
            uri.rawPath.isNotEmpty() || uri.rawQuery != null || uri.rawFragment != null) null else host to uri.port
    } catch (_: Exception) { null }
    private fun endpointKey(ip: String, port: Int) = AudioPlayers.addressKey(ip, port) ?: invalid()

    fun advertisement(service: MdnsPacket.Service, ip: String): Player? {
        if (service.port !in 1..65535) return null
        fun txt(key: String): String? {
            val bytes = service.attributes.filterKeys { it.equals(key, true) }.values.singleOrNull() ?: return null
            if (bytes.size > 512) return null
            return try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() } catch (_: Exception) { null }
        }
        if (CastInfo.isGroup(service)) {
            val group = uuid(txt("id")) ?: return null
            return Player(Protocol.CAST, service.port, group, groups = listOf(Group(group,
                label(txt("fn")).ifEmpty { label(service.name) }, "Advertised playback target", relation = Relation.HOSTED)))
        }
        if (LocalServices.canonicalType(service.type) != "_airplay._tcp.") return null
        val groups = ArrayList<Group>()
        uuid(txt("gid"))?.let { group ->
            val role = when (txt("igl")) { "1" -> "Coordinator"; "0" -> "Member"; else -> "Role not reported" }
            groups.add(Group(group, label(txt("gpn")), role, relation = Relation.ADVERTISED, label = "AirPlay group"))
        }
        uuid(txt("pgid"))?.let { groups.add(Group(it, relation = Relation.ADVERTISED, label = "Parent AirPlay group")) }
        if (groups.isEmpty()) return null
        // Pairing IDs, home IDs and group IDs are deliberately not physical identity keys.
        val device = txt("deviceid")?.takeIf { it.matches(Regex("[A-Za-z0-9:-]{6,64}")) }?.let { AudioZones.token(it.lowercase(java.util.Locale.ROOT)) }
            ?: endpointKey(ip, service.port)
        return Player(Protocol.AIRPLAY, service.port, device, label(service.name), groups = groups)
    }

    /** Partial failures keep earlier self information; cancellation always propagates. */
    fun read(plan: Plan, peer: InetAddress, fetch: (AudioWire.Query) -> ByteArray): Result {
        val notes = ArrayList<String>()
        fun optional(name: String, action: () -> Unit) {
            try { action() } catch (e: Exception) {
                if (e is MetadataHttp.Failure && e.reason == MetadataHttp.Reason.CANCELLED) throw e
                notes.add("$name unavailable: " + ((e as? MetadataHttp.Failure)?.reason?.detail ?: "Unsupported response"))
            }
        }
        val ip = peer.hostAddress!!
        return when (plan.protocol) {
            Protocol.HEOS -> {
                require(plan.port == 1255)
                val root = doc(fetch(AudioWire.Query(1255, command = "player/get_players")))
                val header = obj(root["heos"]) ?: invalid()
                if (text(header["command"]) != "player/get_players" || text(header["result"]) != "success") invalid()
                val players = array(root["payload"])?.map { obj(it) ?: invalid() } ?: invalid()
                val ids = players.map { id(it["pid"]) ?: invalid() }
                if (ids.distinct().size != ids.size) invalid()
                val self = players.singleOrNull { sameIp(text(it["ip"]).orEmpty(), ip) } ?: invalid()
                val pid = id(self["pid"]) ?: invalid()
                var player = Player(Protocol.HEOS, 1255, AudioZones.token(pid), label(text(self["name"])), label(text(self["model"])))
                optional("Group information") {
                    val groupsRoot = doc(fetch(AudioWire.Query(1255, command = "group/get_groups")))
                    val h = obj(groupsRoot["heos"]) ?: invalid()
                    if (text(h["command"]) !in setOf("group/get_groups", "player/get_groups") || text(h["result"]) != "success") invalid()
                    val groups = array(groupsRoot["payload"])?.map { obj(it) ?: invalid() } ?: invalid()
                    val matches = ArrayList<Group>(); val seen = HashSet<String>()
                    for (g in groups) {
                        val gid = id(g["gid"]) ?: invalid()
                        val members = array(g["players"])?.map { obj(it) ?: invalid() } ?: invalid()
                        val memberIds = members.map { id(it["pid"]) ?: invalid() }
                        if (memberIds.any { !seen.add(it) }) invalid()
                        val leader = members.singleOrNull { text(it["role"]) == "leader" }?.let { id(it["pid"]) } ?: invalid()
                        if (members.any { text(it["role"]) !in setOf("leader", "member") } || leader != gid) invalid()
                        if (pid in memberIds) {
                            if (id(self["gid"]) != gid) invalid()
                            matches.add(Group(AudioZones.token(gid), label(text(g["name"])), if (pid == leader) "Coordinator" else "Member",
                                AudioZones.token(leader), memberIds.mapTo(HashSet(), AudioZones::token), Relation.ROSTER))
                        }
                    }
                    if (matches.size > 1 || self.containsKey("gid") && matches.isEmpty()) invalid()
                    player = player.copy(groups = matches)
                }
                Result(player, mapOf("Model" to player.model).filterValues { it.isNotEmpty() }, notes.joinToString("; "))
            }
            Protocol.MUSICCAST -> {
                require(plan.port == 80)
                fun get(part: String): Map<String, BoundedJson.Value> = doc(fetch(AudioWire.Query(80, "/YamahaExtendedControl/v1/$part")))
                    .also { if (number(it["response_code"]) != "0") invalid() }
                val info = get("system/getDeviceInfo")
                val model = label(text(info["model_name"])).takeIf { it.isNotEmpty() } ?: invalid()
                var player = Player(Protocol.MUSICCAST, 80, endpointKey(ip, 80), model = model)
                optional("Hardware zones") {
                    val features = get("system/getFeatures")
                    val zones = array(features["zone"])?.map { text(obj(it)?.get("id")) ?: invalid() } ?: invalid()
                    if (zones.isEmpty() || zones.size > 4 || zones.distinct().size != zones.size || zones.any { it !in setOf("main", "zone2", "zone3", "zone4") }) invalid()
                    player = player.copy(zones = zones)
                }
                optional("Distribution information") {
                    val dist = get("dist/getDistributionInfo")
                    val role = text(dist["role"])
                    if (role == "none") return@optional
                    if (role !in setOf("server", "client")) invalid()
                    if (role == "server" && dist.containsKey("status") && text(dist["status"]) != "working") invalid()
                    val group = uuid(text(dist["group_id"])) ?: invalid()
                    val zone = if (dist.containsKey("server_zone")) text(dist["server_zone"]) else "main"
                    if (zone !in setOf("main", "zone2", "zone3", "zone4")) invalid()
                    val clients = if (role == "server") array(dist["client_list"])?.map { endpointKey(text(obj(it)?.get("ip_address")) ?: invalid(), 80) } ?: invalid() else emptyList()
                    if (clients.distinct().size != clients.size || player.id in clients) invalid()
                    player = player.copy(groups = listOf(Group(group, label(text(dist["group_name"])), if (role == "server") "Coordinator" else "Member",
                        peers = clients.toSet(), relation = Relation.DISTRIBUTION, zone = zone!!)))
                }
                val type = when (number(info["category_code"])) { "1", "3" -> "Yamaha audio receiver"; "2" -> "Yamaha soundbar"; "4" -> "Yamaha subwoofer"; else -> "" }
                Result(player, mapOf("Model" to model, "Device type" to type).filterValues { it.isNotEmpty() }, notes.joinToString("; "))
            }
            Protocol.BLUOS -> {
                val root = BoundedXml.parse(fetch(AudioWire.Query(plan.port, "/SyncStatus"))) ?: invalid()
                if (root.name != "SyncStatus" || root.ns.isNotEmpty()) invalid()
                val self = endpoint(root.attrs["id"].orEmpty()) ?: invalid()
                if (!sameIp(self.first, ip) || self.second != plan.port) invalid()
                val selfKey = endpointKey(ip, plan.port)
                var player = Player(Protocol.BLUOS, plan.port, selfKey, label(root.attrs["name"]),
                    label(root.attrs["modelName"]).ifEmpty { label(root.attrs["model"]) }, label(root.attrs["brand"]))
                optional("Group information") {
                    val masterNodes = root.children.filter { it.name == "master" && it.ns.isEmpty() }
                    if (masterNodes.size > 1) invalid()
                    val master = masterNodes.singleOrNull()?.let { n ->
                        val port = n.attrs["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: invalid()
                        if (n.attrs["reconnecting"] == "true") invalid()
                        endpointKey(n.scalar()?.trim() ?: invalid(), port)
                    }
                    val slaves = root.children.filter { it.name == "slave" && it.ns.isEmpty() }.map { n ->
                        val port = n.attrs["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: invalid()
                        endpointKey(n.attrs["id"] ?: invalid(), port)
                    }
                    if (slaves.size > 64 || slaves.distinct().size != slaves.size || selfKey in slaves || master != null && master != selfKey && slaves.isNotEmpty()) invalid()
                    val groups = ArrayList<Group>()
                    if (master != null && master != selfKey || slaves.isNotEmpty()) {
                        val leader = master ?: selfKey
                        groups.add(Group(leader, label(root.attrs["group"]), if (leader == selfKey) "Coordinator" else "Member", leader,
                            if (leader == selfKey) slaves.toSet() else setOf(leader), Relation.PRIMARY_SECONDARY))
                    }
                    val fixed = label(root.attrs["zone"])
                    if (fixed.isNotEmpty()) groups.add(Group(AudioZones.token("fixed:$selfKey"), fixed,
                        if (root.attrs["zoneMaster"] == "true") "Fixed-group primary" else if (root.attrs["zoneSlave"] == "true") "Fixed-group secondary" else "Role not reported",
                        relation = Relation.HOSTED, label = "Fixed group (reported)"))
                    player = player.copy(groups = groups)
                }
                // Player network-interface IDs, volume, tracks and icon URLs are not retained.
                Result(player, mapOf("Model" to player.model, "Manufacturer" to player.maker).filterValues { it.isNotEmpty() }, notes.joinToString("; "))
            }
            Protocol.CAST -> {
                require(plan.port == 8008)
                val root = doc(fetch(AudioWire.Query(8008, "/setup/eureka_info?params=multizone")))
                val multi = obj(root["multizone"]) ?: run {
                    // Some receivers return an ordinary Eureka identity document and ignore this selector.
                    // This does not establish an empty roster or a malformed response.
                    if (!root.containsKey("multizone") && text(root["name"]) != null &&
                        (text(root["build_version"]) != null || obj(root["device_info"]) != null))
                        throw MetadataHttp.Failure(MetadataHttp.Reason.NOT_REPORTED)
                    invalid()
                }
                val groups = ArrayList<Group>(); val seen = HashSet<String>()
                for (key in listOf("groups", "dynamic_groups")) {
                    val entries = if (multi.containsKey(key)) array(multi[key]) ?: invalid() else emptyList()
                    for (entry in entries) {
                        val g = obj(entry) ?: invalid(); val group = uuid(text(g["uuid"])) ?: invalid()
                        if (!seen.add(group) || seen.size > 16) invalid()
                        // These lists describe targets, not a roster of this receiver's memberships.
                        val leader = text(g["elected_leader"])
                        val port = number(g["cast_port"])?.toIntOrNull()
                        val hosted = leader == "self" && port != null && port in 1..65535
                        groups.add(Group(group, label(text(g["name"])), if (hosted) "Hosted playback target · port $port" else "Reported playback target",
                            relation = Relation.HOSTED, label = if (key == "dynamic_groups") "Dynamic Cast group" else "Cast group"))
                    }
                }
                Result(Player(Protocol.CAST, 8008, endpointKey(ip, 8008), groups = groups))
            }
            Protocol.AIRPLAY -> invalid() // Advertisement only; never pair or authenticate.
        }
    }
}
