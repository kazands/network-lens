package io.github.kazands.networklens

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale

/** Optional local-DNS evidence. It never establishes presence, a reported model or a grouping link. */
internal object LocalNetworkNames {
    const val SOURCE = "Local network name"
    const val LIMIT = 128
    const val TIMEOUT_MS = 700
    enum class Status(val description: String) {
        CHECKING("Checking the selected Wi-Fi's local DNS"),
        CONFIRMED("Reverse and forward DNS agree; the router's name may still be stale"),
        NO_NAME("No usable name returned by local DNS"),
        NOT_CONFIRMED("The returned name did not resolve back to this address"),
        AMBIGUOUS("Local DNS returned multiple names; no name selected"),
        INVALID("The DNS response could not be validated"),
        TIMED_OUT("Local DNS did not respond within the lookup window"),
        FAILED("Local DNS lookup could not be read"),
        NO_RESOLVER("No configured on-link private DNS server; no query sent"),
        LIMIT_REACHED("Local name lookup limit reached; no query sent"),
        STOPPED("Lookup stopped before a name was confirmed"),
        UNFINISHED("Lookup did not finish before the scan ended")
    }
    data class Result(val status: Status, val name: String = "",
        val returnedNames: List<String> = if (name.isEmpty()) emptyList() else listOf(name), val omittedNames: Int = 0)
    data class Family(val title: String, val role: String)
    fun interface Channel { fun exchange(query: ByteArray, timeoutMs: Int): ByteArray }

    /** A public resolver must never receive local reverse questions or returned private hostnames. */
    fun privateResolver(ip: String): Boolean {
        val b = Address.bytes(ip) ?: return false
        fun n(i: Int) = b[i].toInt() and 255
        return if (b.size == 4) n(0) == 10 || n(0) == 172 && n(1) in 16..31 ||
            n(0) == 192 && n(1) == 168 || n(0) == 169 && n(1) == 254
        else (n(0) and 254) == 252 || n(0) == 254 && (n(1) and 192) == 128
    }

    fun lookup(ip: String, id: Int, deadline: Long, active: () -> Boolean, channel: Channel): Result {
        val address = Address.bytes(ip) ?: return Result(Status.INVALID)
        fun remaining(): Int = ((deadline - System.nanoTime()) / 1_000_000).coerceIn(0, 350).toInt()
        fun ask(query: ByteArray): ByteArray {
            if (!active()) throw InterruptedException()
            val left = remaining()
            if (left <= 0) throw SocketTimeoutException()
            val bytes = channel.exchange(query, left)
            if (!active()) throw InterruptedException()
            if (System.nanoTime() >= deadline) throw SocketTimeoutException()
            return bytes
        }
        var names = emptyList<String>()
        var omitted = 0
        fun result(status: Status, name: String = "") = Result(status, name, names, omitted)
        return try {
            val reverseBytes = ask(ReverseDns.query(ip, id))
            val reverse = ReverseDns.response(reverseBytes, reverseBytes.size, id, ip)
            names = reverse.names.take(4); omitted = (reverse.names.size - names.size).coerceAtLeast(0)
            if (reverse.status == ReverseDns.Status.INVALID || reverse.status == ReverseDns.Status.ERROR) result(Status.INVALID)
            else if (reverse.names.size > 1) result(Status.AMBIGUOUS)
            else if (reverse.names.isEmpty() || !validName(reverse.names.single())) result(Status.NO_NAME)
            else {
                val host = reverse.names.single()
                val type = if (address.size == 4) 1 else 28
                val forwardId = (id + 1) and 65535
                val reply = ask(query(host, type, forwardId))
                val matched = confirms(reply, host, type, forwardId, address)
                when (matched) {
                    null -> result(Status.INVALID)
                    false -> result(Status.NOT_CONFIRMED)
                    true -> result(Status.CONFIRMED, host.lowercase(Locale.ROOT))
                }
            }
        } catch (_: InterruptedException) { result(Status.STOPPED) }
        catch (_: SocketTimeoutException) { result(if (active()) Status.TIMED_OUT else Status.STOPPED) }
        catch (_: IOException) { result(if (active()) Status.FAILED else Status.STOPPED) }
    }

    private val LABEL = Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?")
    private val HYDRA = Regex("hydrawise-([0-9a-f]{4})")
    private val BH1 = Regex("bhyve-bh1-[a-z0-9]{4,32}")
    private val BHYVE = Regex("bhyve-[a-z0-9][a-z0-9-]{3,47}")
    private fun validName(name: String): Boolean = name.length in 1..253 && name.split('.').all { LABEL.matches(it) } && Address.bytes(name) == null

    /** Narrow, family-only suggestions. Names never manufacture a MAC or exact retail model. */
    fun family(name: String, mac: String): Family? {
        if (!validName(name)) return null
        val label = name.substringBefore('.').lowercase(Locale.ROOT)
        val hardware = Address.mac(mac).replace(":", "")
        HYDRA.matchEntire(label)?.let {
            if (hardware.isNotEmpty() && !hardware.endsWith(it.groupValues[1], true)) return null
            return Family("Hunter Hydrawise", "Irrigation controller")
        }
        if (BH1.matches(label)) return Family("Orbit B-hyve hub", "Irrigation hub")
        // Other B-hyve names need independently observed Orbit interface evidence.
        if (BHYVE.matches(label) && hardware.startsWith("446755")) return Family("Orbit B-hyve", "Device family")
        return null
    }

    internal fun query(name: String, type: Int, id: Int): ByteArray {
        require(validName(name) && type in listOf(1, 28))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeShort(id); out.writeShort(0x100); out.writeShort(1); out.write(ByteArray(6))
            for (label in name.split('.')) { out.writeByte(label.length); out.write(label.toByteArray(Charsets.US_ASCII)) }
            out.writeByte(0); out.writeShort(type); out.writeShort(1)
        }
        return bytes.toByteArray()
    }

    /** Correlate ID, question and answer owner. CNAMEs are bounded and never followed in another query. */
    internal fun confirms(b: ByteArray, host: String, type: Int, id: Int, target: ByteArray): Boolean? {
        if (b.size !in 12..4096) return null
        return try {
            if (ReverseDns.u16(b, 0) != id || ReverseDns.u16(b, 2) and 0xfa00 != 0x8000 || ReverseDns.u16(b, 4) != 1) return null
            val count = ReverseDns.u16(b, 6)
            val total = count + ReverseDns.u16(b, 8) + ReverseDns.u16(b, 10)
            if (total > 128) return null
            val pos = intArrayOf(12)
            if (!ReverseDns.name(b, b.size, pos).equals(host, true) || pos[0] + 4 > b.size ||
                ReverseDns.u16(b, pos[0]) != type || ReverseDns.u16(b, pos[0] + 2) != 1) return null
            pos[0] += 4
            val aliases = linkedMapOf<String, String>()
            val addresses = linkedMapOf<String, MutableList<ByteArray>>()
            repeat(total) { index ->
                val owner = ReverseDns.name(b, b.size, pos).lowercase(Locale.ROOT)
                if (pos[0] + 10 > b.size) return null
                val t = ReverseDns.u16(b, pos[0]); val cls = ReverseDns.u16(b, pos[0] + 2)
                val size = ReverseDns.u16(b, pos[0] + 8); pos[0] += 10
                val end = pos[0] + size
                if (end > b.size) return null
                // TTL zero is valid in this transaction. Nothing is cached between lookups or scans.
                if (index < count && cls == 1) {
                    if (t == 5) {
                        val aliasPos = intArrayOf(pos[0])
                        val alias = ReverseDns.name(b, b.size, aliasPos).lowercase(Locale.ROOT)
                        if (aliasPos[0] != end || !validName(alias) || aliases.put(owner, alias)?.let { it != alias } == true) return null
                    } else if (t == type) {
                        if (size != target.size) return null
                        addresses.getOrPut(owner) { ArrayList() }.add(b.copyOfRange(pos[0], end))
                    }
                }
                pos[0] = end
            }
            if (pos[0] != b.size) return null
            val rcode = ReverseDns.u16(b, 2) and 15
            if (rcode != 0) return if (rcode == 3 && count == 0) false else null
            var owner = host.lowercase(Locale.ROOT)
            val seen = hashSetOf<String>()
            repeat(9) {
                if (!seen.add(owner)) return null
                val alias = aliases[owner]
                if (alias == null) return addresses[owner]?.any { it.contentEquals(target) } == true
                if (addresses.containsKey(owner)) return null
                owner = alias
            }
            null
        } catch (_: IllegalArgumentException) { null }
        catch (_: IndexOutOfBoundsException) { null }
    }
}
