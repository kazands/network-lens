package io.github.kazands.networklens

import java.net.InetAddress
import java.util.Arrays
import java.util.concurrent.TimeUnit

/** Short-lived, same-sender DNS-SD assembly. One instance belongs to one scan. */
class MdnsAssembly {
    data class RoutedCandidate(val sender: InetAddress, val address: InetAddress, val service: MdnsPacket.Service)
    private data class Entry(val record: MdnsPacket.Record, val expires: Long, val received: Long)
    private class Peer(val address: InetAddress) {
        val sets = LinkedHashMap<String, MutableList<Entry>>()
        var lastComplete: Set<String> = emptySet()
    }
    private class Attempt { var sent = 0; var next = 0L }
    private val peers = LinkedHashMap<String, Peer>()
    private val attempts = HashMap<String, Attempt>()
    @JvmField var limited = false
    private var closed = false

    @Synchronized fun close() { closed = true; peers.clear(); attempts.clear() }
    private fun setKey(owner: List<String>, kind: Int) = MdnsPacket.key(owner) + "|" + kind
    private fun peerKey(peer: InetAddress) = peer.address.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun equal(a: MdnsPacket.Record, b: MdnsPacket.Record): Boolean =
        a.kind == b.kind && MdnsPacket.key(a.owner) == MdnsPacket.key(b.owner) &&
            MdnsPacket.key(a.target) == MdnsPacket.key(b.target) && a.port == b.port && a.address == b.address &&
            a.attributes.keys == b.attributes.keys && a.attributes.all { (k, v) -> Arrays.equals(v, b.attributes[k]) }
    private fun expire(now: Long) {
        for (p in peers.values) {
            for (entries in p.sets.values) entries.removeAll { now - it.expires >= 0 }
            p.sets.entries.removeAll { it.value.isEmpty() }
        }
        // Keep peer and query admission counts for the entire scan; churn cannot reset budgets.
    }
    private fun usedRecords() = peers.values.sumOf { p -> p.sets.values.sumOf { it.size } }
    private fun weight(r: MdnsPacket.Record) = 64 + (r.owner + r.target).sumOf { it.length * 2 + 8 } +
        r.attributes.entries.sumOf { it.key.length * 2 + it.value.size + 32 }
    private fun usedBytes() = peers.values.sumOf { p -> p.sets.values.sumOf { entries -> entries.sumOf { weight(it.record) } } }

    @Synchronized fun accept(sender: InetAddress, packet: MdnsPacket.Result, now: Long): List<MdnsPacket.Service> {
        if (closed || sender.isAnyLocalAddress || sender.isLoopbackAddress || sender.isMulticastAddress || packet.records.isEmpty()) return emptyList()
        expire(now)
        val key = peerKey(sender)
        if (!peers.containsKey(key) && peers.size >= 128) { limited = true; return emptyList() }
        val peer = peers.getOrPut(key) { Peer(sender) }
        var count = usedRecords(); var bytes = usedBytes()
        fun remove(entries: MutableList<Entry>, predicate: (Entry) -> Boolean) {
            val removed = entries.filter(predicate)
            count -= removed.size; bytes -= removed.sumOf { weight(it.record) }
            entries.removeAll(removed.toSet())
        }
        // Process a whole packet's RRsets together, including multi-address records.
        for ((rrset, records) in packet.records.groupBy { setKey(it.owner, it.kind) }) {
            if (!peer.sets.containsKey(rrset) && peer.sets.size >= 64) { limited = true; continue }
            val entries = peer.sets.getOrPut(rrset) { ArrayList() }
            if (records.any { it.flush && it.ttl > 0 && it.kind != 12 }) {
                // RFC 6762 section 10.2: protect a split RRset's recent members.
                // Older members get one second to be refreshed by the same burst.
                val grace = TimeUnit.SECONDS.toNanos(1)
                for (i in entries.indices) {
                    val entry = entries[i]
                    if (now - entry.received >= grace)
                        entries[i] = entry.copy(expires = minOf(entry.expires, now + grace))
                }
            }
            for (r in records) {
                remove(entries) { equal(it.record, r) }
                if (r.ttl == 0L) {
                    if (r.kind == 12) {
                        for (kind in listOf(33, 16)) peer.sets.remove(setKey(r.target, kind))?.let { remove(it) { true } }
                    }
                    continue
                }
                val size = weight(r)
                if (entries.size >= (if (r.kind == 12) 32 else 8) || count >= 1024 || bytes + size > 524288) { limited = true; continue }
                val copy = r.copy(owner = r.owner.toList(), target = r.target.toList(),
                    attributes = r.attributes.mapValues { it.value.clone() })
                entries.add(Entry(copy, now + TimeUnit.SECONDS.toNanos(minOf(10L, r.ttl)), now))
                count++; bytes += size
            }
        }
        val complete = complete(peer)
        peer.lastComplete = complete.map { signature(it) }.toSet()
        return complete
    }

    private fun signature(service: MdnsPacket.Service): String =
        listOf(service.name, service.type, service.host, service.port.toString()).joinToString("\u0000") + "\u0000" +
            service.attributes.entries.joinToString("|") { it.key + "=" + java.util.Base64.getEncoder().encodeToString(it.value) }

    /** Expiry can resolve a conflict without another incoming packet. Emit each new result once. */
    @Synchronized fun ready(now: Long): Map<InetAddress, List<MdnsPacket.Service>> {
        if (closed) return emptyMap()
        expire(now)
        val out = LinkedHashMap<InetAddress, List<MdnsPacket.Service>>()
        for (peer in peers.values) {
            val current = complete(peer)
            val newlyReady = current.filter { signature(it) !in peer.lastComplete }
            peer.lastComplete = current.map { signature(it) }.toSet()
            if (newlyReady.isNotEmpty()) out[peer.address] = newlyReady
        }
        return out
    }

    /** Advertisements only. Callers must separately admit the route and obtain a fresh endpoint reply. */
    @Synchronized fun routedCandidates(now: Long): List<RoutedCandidate> {
        if (closed) return emptyList()
        expire(now)
        val out = ArrayList<RoutedCandidate>()
        for (peer in peers.values) for (instance in instances(peer)) {
            val type = MdnsPacket.instanceType(instance)
            if (!LocalServices.routedAccessory(type)) continue
            if (records(peer, instance.drop(1), 12).none { MdnsPacket.key(it.target) == MdnsPacket.key(instance) }) continue
            val srv = records(peer, instance, 33).singleOrNull() ?: continue
            val txt = records(peer, instance, 16).singleOrNull() ?: continue
            if (srv.port !in 1..65535) continue
            for (record in records(peer, srv.target, 28)) {
                val bytes = Address.bytes(record.address) ?: continue
                if (bytes.size != 16 || Arrays.equals(bytes, peer.address.address)) continue
                val service = MdnsPacket.Service(SafeText.label(instance[0], 128), type)
                service.host = srv.target.joinToString("."); service.port = srv.port
                service.ptrReceived = true; service.txtReceived = true
                for ((key, value) in txt.attributes) service.attributes[key] = value.clone()
                out.add(RoutedCandidate(peer.address, InetAddress.getByAddress(bytes), service))
            }
        }
        return out
    }

    private fun records(peer: Peer, labels: List<String>, kind: Int) = peer.sets[setKey(labels, kind)]?.map { it.record } ?: emptyList()
    private fun instances(peer: Peer): List<List<String>> = peer.sets.values.flatten().map { it.record }
        .filter { it.kind == 12 }.map { it.target }.distinctBy { MdnsPacket.key(it) }
    private fun complete(peer: Peer): List<MdnsPacket.Service> {
        val out = ArrayList<MdnsPacket.Service>()
        for (instance in instances(peer)) {
            val srv = records(peer, instance, 33).singleOrNull() ?: continue
            val txt = records(peer, instance, 16).singleOrNull() ?: continue
            if (srv.port !in 1..65535) continue
            val addresses = records(peer, srv.target, 1) + records(peer, srv.target, 28)
            if (addresses.none { Arrays.equals(Address.bytes(it.address), peer.address.address) }) continue
            val type = MdnsPacket.instanceType(instance)
            if (type.isEmpty()) continue
            val service = MdnsPacket.Service(SafeText.label(instance[0], 128), type)
            service.host = srv.target.joinToString("."); service.port = srv.port
            service.ptrReceived = true; service.txtReceived = true
            for ((k, v) in txt.attributes) service.attributes[k] = v.clone()
            out.add(service)
        }
        return out
    }
    private fun missing(peer: Peer): List<MdnsPacket.Question> {
        val out = ArrayList<MdnsPacket.Question>()
        for (instance in instances(peer)) {
            val srvs = records(peer, instance, 33)
            if (srvs.isEmpty()) out.add(MdnsPacket.Question(instance, 33))
            if (records(peer, instance, 16).isEmpty()) out.add(MdnsPacket.Question(instance, 16))
            val srv = srvs.singleOrNull() ?: continue
            if (srv.port !in 1..65535) continue
            val kind = if (peer.address.address.size == 4) 1 else 28
            // An alternate advertised address does not satisfy this sender's association.
            // A bounded retry may recover the missing member, but never trusts the proxy.
            if (records(peer, srv.target, kind).none { Arrays.equals(Address.bytes(it.address), peer.address.address) })
                out.add(MdnsPacket.Question(srv.target, kind))
            if (LocalServices.routedAccessory(MdnsPacket.instanceType(instance)) && records(peer, srv.target, 28).isEmpty())
                out.add(MdnsPacket.Question(srv.target, 28))
        }
        return out
    }
    private fun queryKey(v6: Boolean, q: MdnsPacket.Question) = "$v6|" + setKey(q.labels, q.kind)
    @Synchronized fun due(v6: Boolean, now: Long, limit: Int): List<MdnsPacket.Question> {
        require(limit in 0..4)
        if (closed || limit == 0) return emptyList()
        expire(now)
        val out = LinkedHashMap<String, MdnsPacket.Question>()
        for (peer in peers.values) {
            if ((peer.address.address.size == 16) != v6) continue
            for (q in missing(peer)) {
                val key = queryKey(v6, q)
                if (!attempts.containsKey(key) && attempts.size >= 256) { limited = true; continue }
                val attempt = attempts.getOrPut(key) { Attempt() }
                if (attempt.sent >= 2 || attempt.sent > 0 && now - attempt.next < 0 || out.containsKey(key)) continue
                if (out.size >= limit) return out.values.toList()
                attempt.sent++; attempt.next = now + TimeUnit.MILLISECONDS.toNanos(1200)
                out[key] = q
            }
        }
        return out.values.toList()
    }
    @Synchronized fun pending(now: Long): Boolean {
        if (closed) return false
        expire(now)
        return peers.values.any { p -> p.sets.values.any { entries ->
            entries.size > 1 && entries.first().record.kind in listOf(16, 33) &&
                entries.any { it.expires - now in 1..TimeUnit.SECONDS.toNanos(1) }
        } || missing(p).any { q ->
            val attempt = attempts[queryKey(p.address.address.size == 16, q)]
            if (attempt == null) attempts.size < 256 else attempt.sent < 2
        } }
    }
}
