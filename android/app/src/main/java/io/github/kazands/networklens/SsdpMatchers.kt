package io.github.kazands.networklens

import java.net.URI

/** Join only one response and its same-peer description, never fields from unrelated services. */
internal class SsdpMatchers {
    private data class Record(val headers: MutableList<Map<String, String>> = ArrayList(), var description: Map<String, String>? = null)
    private val records = LinkedHashMap<Pair<String, URI>, Record>()
    @Synchronized fun headers(peer: String, target: URI, values: Map<String, String>): Set<String> {
        val key = peer to target
        val record = records[key] ?: if (records.size < 96) Record().also { records[key] = it } else return emptySet()
        val fields = values.filterKeys { it in setOf("st", "nt") }
        if (fields !in record.headers && record.headers.size < 16) record.headers.add(fields)
        return match(record)
    }
    @Synchronized fun description(peer: String, target: URI, values: Map<String, String>): Set<String> {
        val record = records[peer to target] ?: return DiscoveryMatchers.match("ssdp", values)
        record.description = values.filterKeys { it in setOf("manufacturer", "manufacturerURL", "deviceType", "modelName", "modelDescription", "X_MqttOnLan", "X_MqttTokenPairing") }
        return match(record)
    }
    private fun match(record: Record): Set<String> {
        val values = record.description ?: return emptySet()
        return (listOf(values) + record.headers.map { it + values }).flatMap { DiscoveryMatchers.match("ssdp", it) }.toSet()
    }
}
