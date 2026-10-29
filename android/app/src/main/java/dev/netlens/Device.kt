package dev.netlens

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections

/** An observed endpoint; metadata and addresses do not establish ownership. */
class Device : Grouping.Endpoint {
    @JvmField var ip = ""
    @JvmField var name = ""
    @JvmField var hostname = ""
    @JvmField var manufacturer = ""
    @JvmField var model = ""
    @JvmField var kind = ""
    @JvmField var mac = ""
    @JvmField var macSource = ""
    @JvmField var identity = ""
    @JvmField var firstSeen = 0L
    @JvmField var lastSeen = 0L
    @JvmField var latency = -1
    @JvmField var seen = false
    @JvmField var self = false
    @JvmField var gateway = false
    private var identityPriority = 0
    private val groupLinks = ArrayList<String>()
    private val members = ArrayList<Device>()
    @JvmField val ports: MutableList<Int> = ArrayList()
    @JvmField val services: MutableList<String> = ArrayList()
    @JvmField val evidence: MutableList<String> = ArrayList()
    @JvmField val details: MutableMap<String, String> = LinkedHashMap()
    private var hints = IdentityHints()

    @Synchronized fun observations(): MutableList<IdentityHints.Hint> = hints.observations()
    @Synchronized fun hint(field: String?, value: String?, source: String?, priority: Int) {
        val hint = hints.offer(field, value, source, priority) ?: return
        when (field) {
            "Name" -> name = hint.value
            "Hostname" -> hostname = hint.value
            "Manufacturer" -> manufacturer = hint.value
            "Model" -> model = hint.value
            "Device type" -> kind = hint.value
            "OS" -> detail("OS (advertised)", hint.value)
            "Firmware" -> detail("Firmware (advertised)", hint.value)
            else -> return
        }
    }

    @Synchronized fun identify(candidate: String?, priority: Int) {
        val clean = SafeText.label(candidate, 200)
        if (clean.isNotEmpty() && (identity.isEmpty() || priority > identityPriority ||
            (priority == identityPriority && clean.compareTo(identity) < 0))) {
            identity = clean
            identityPriority = priority
        }
    }
    @Synchronized override fun isSelf(): Boolean = self
    @Synchronized override fun hardwareAddress(): String = mac
    @Synchronized override fun links(): Collection<String> = ArrayList(groupLinks)
    @Synchronized fun link(key: String?) {
        if (key != null && key.length <= 200 && groupLinks.size < 32 && !groupLinks.contains(key)) groupLinks.add(key)
    }
    @Synchronized fun endpoints(): MutableList<Device> =
        if (members.isEmpty()) Collections.singletonList(this) else ArrayList(members)

    @Synchronized fun title(): String = when {
        name.isNotEmpty() -> name
        self -> "This Android device"
        gateway -> "Router"
        else -> "Device at " + ip
    }
    @Synchronized fun evidence(value: String?) {
        val clean = SafeText.label(value, 160)
        if (clean.isNotEmpty() && evidence.size < 16 && !evidence.contains(clean)) evidence.add(clean)
    }
    @Synchronized fun service(value: String?) {
        val clean = SafeText.label(value, 128)
        if (clean.isNotEmpty() && services.size < 24 && !services.contains(clean)) services.add(clean)
    }
    @Synchronized fun port(port: Int) {
        if (port in 1..65535 && ports.size < 64 && !ports.contains(port)) ports.add(port)
    }

    @Synchronized fun hardware(address: String?, source: String?) {
        val valid = Address.mac(address)
        if (valid.isNotEmpty()) {
            if (mac.isNotEmpty() && mac != valid) {
                val prefer = source!!.contains("ARP cache") || (source.contains("NetBIOS") && !macSource.contains("ARP cache"))
                if (prefer) detail("Other advertised MAC", mac + " · " + macSource)
                else { detail("Other reported MAC", valid + " · " + source); return }
            }
            mac = valid
            macSource = SafeText.label(source, 128)
        }
    }

    @Synchronized fun detail(key: String?, value: String?) {
        val cleanKey = SafeText.label(key, 64)
        val cleanValue = SafeText.label(value, 160)
        if (cleanKey.isEmpty() || cleanValue.isEmpty() || legacySource(cleanKey)) return
        details[cleanKey] = cleanValue
        if (details.size > 16) {
            val last = Collections.max(details.keys, compareBy<String> { detailRank(it) }.thenBy { it })
            details.remove(last)
        }
    }

    @Synchronized fun json(): JSONObject {
        val out = JSONObject()
        try {
            out.put("ip", ip).put("name", name).put("hostname", hostname).put("manufacturer", manufacturer)
                .put("model", model).put("kind", kind).put("mac", mac).put("macSource", macSource)
                .put("identity", identity).put("identityPriority", identityPriority).put("firstSeen", firstSeen)
                .put("lastSeen", lastSeen).put("latency", latency).put("self", self).put("gateway", gateway)
                .put("ports", JSONArray(ports)).put("services", JSONArray(services)).put("evidence", JSONArray(evidence))
                .put("details", JSONObject(details)).put("groupLinks", JSONArray(groupLinks))
            val observations = JSONArray()
            for (h in hints.observations()) observations.put(JSONObject().put("field", h.field).put("value", h.value)
                .put("source", h.source).put("priority", h.priority))
            out.put("observations", observations)
        } catch (_: JSONException) {}
        return out
    }

    @Synchronized fun copy(): Device {
        val copy = Device()
        copy.ip = ip; copy.name = name; copy.hostname = hostname; copy.manufacturer = manufacturer
        copy.model = model; copy.kind = kind; copy.mac = mac; copy.macSource = macSource
        copy.identity = identity; copy.identityPriority = identityPriority
        copy.firstSeen = firstSeen; copy.lastSeen = lastSeen; copy.latency = latency
        copy.seen = seen; copy.self = self; copy.gateway = gateway
        copy.groupLinks.addAll(groupLinks); copy.ports.addAll(ports); copy.services.addAll(services)
        copy.evidence.addAll(evidence); copy.details.putAll(details); copy.hints = hints.copy()
        for (member in members) copy.members.add(member.copy())
        return copy
    }

    companion object {
        @JvmStatic fun grouped(raw: List<Device>): MutableList<Device> {
            val sorted = ArrayList(raw)
            sorted.sortWith { a, b -> Address.compare(a.ip, b.ip) }
            val out = ArrayList<Device>()
            for (group in Grouping.groups(sorted)) {
                if (group.size == 1) { out.add(group[0].copy()); continue }
                val representative = group[0].copy()
                for (d in group) {
                    for (h in d.observations()) representative.hint(h.field, h.value, h.source, h.priority)
                    representative.hint("Name", d.name, "Observed name", 0)
                    representative.hint("Model", d.model, "Observed model", 0)
                    representative.hint("Manufacturer", d.manufacturer, "Observed manufacturer", 0)
                    representative.hint("Hostname", d.hostname, "Observed hostname", 0)
                    representative.hint("Device type", d.kind, "Observed type", 0)
                    if (representative.mac.isEmpty()) { representative.mac = d.mac; representative.macSource = d.macSource }
                    representative.gateway = representative.gateway || d.gateway
                }
                if (group.size > 1) for (d in group) representative.members.add(d.copy())
                out.add(representative)
            }
            return out
        }

        @JvmStatic fun currentGroup(raw: List<Device>, previous: Device): Device? {
            val addresses = previous.endpoints().mapTo(HashSet()) { it.ip }
            for (group in grouped(raw)) for (endpoint in group.endpoints()) if (endpoint.ip in addresses) return group
            return null
        }

        private fun legacySource(key: String): Boolean =
            key in arrayOf("Name source", "Hostname source", "Manufacturer source", "Model source",
                "Device type source", "OS source", "Firmware source")

        private fun detailRank(key: String): Int = when {
            key == "ONVIF hardware (advertised)" -> 0
            key == "OS (advertised)" || key == "Firmware (advertised)" -> 1
            key.contains("model") || key == "UPnP description status" -> 2
            else -> 3
        }

        @JvmStatic fun from(out: JSONObject): Device {
            val d = Device()
            d.ip = out.optString("ip"); d.name = out.optString("name"); d.hostname = out.optString("hostname")
            d.manufacturer = out.optString("manufacturer"); d.model = out.optString("model"); d.kind = out.optString("kind")
            d.mac = Address.mac(out.optString("mac")); d.macSource = out.optString("macSource")
            d.identity = out.optString("identity"); d.identityPriority = maxOf(0, minOf(100, out.optInt("identityPriority")))
            d.firstSeen = out.optLong("firstSeen"); d.lastSeen = out.optLong("lastSeen")
            d.latency = out.optInt("latency", -1); d.self = out.optBoolean("self"); d.gateway = out.optBoolean("gateway")
            out.optJSONArray("groupLinks")?.let { a -> repeat(minOf(32, a.length())) { d.link(a.optString(it)) } }
            out.optJSONArray("ports")?.let { a -> repeat(minOf(64, a.length())) { d.port(a.optInt(it)) } }
            val services = out.optJSONArray("services")
            if (services != null) repeat(minOf(24, services.length())) { d.service(services.optString(it)) }
            else d.service(out.optString("service"))
            val evidence = out.optJSONArray("evidence")
            if (evidence != null) repeat(minOf(16, evidence.length())) { d.evidence(evidence.optString(it)) }
            else d.evidence(out.optString("evidence"))
            out.optJSONObject("details")?.let { meta ->
                val keys = meta.keys()
                var read = 0
                while (keys.hasNext() && read++ < 64) { val key = keys.next(); d.detail(key, meta.optString(key)) }
            }
            out.optJSONArray("observations")?.let { observations ->
                repeat(minOf(56, observations.length())) { i ->
                    observations.optJSONObject(i)?.let { h ->
                        d.hint(h.optString("field"), h.optString("value"), h.optString("source"),
                            maxOf(0, minOf(100, h.optInt("priority"))))
                    }
                }
            }
            return d
        }
    }
}

