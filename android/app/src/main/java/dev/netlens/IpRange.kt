package dev.netlens

/** Unsigned IPv4 arithmetic; reject oversized ranges without truncating them. */
class IpRange(address: String, @JvmField val prefix: Int) {
    @JvmField val first: Long
    @JvmField val last: Long

    init {
        require(prefix in 0..32) { "IPv4 prefix must be 0–32" }
        val ip = number(address)
        val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val base = ip and mask
        val end = base or (mask.inv() and 0xffffffffL)
        first = if (prefix >= 31) base else base + 1
        last = if (prefix >= 31) end else end - 1
    }

    fun size(): Long = maxOf(0, last - first + 1)
    fun contains(address: String): Boolean = number(address).let { it >= first && it <= last }
    fun hosts(maximum: Int): MutableList<String> {
        require(size() <= maximum) {
            "This network has " + size() + " addresses. Enter a smaller range (up to " + maximum + ")."
        }
        val out = ArrayList<String>()
        var n = first
        while (n <= last) { out.add(address(n)); n++ }
        return out
    }

    companion object {
        @JvmStatic fun number(ip: String): Long {
            val parts = ip.javaSplit("\\.", -1)
            require(parts.size == 4) { "Enter an IPv4 address" }
            var n = 0L
            for (part in parts) {
                require(part.matches(Regex("[0-9]{1,3}"))) { "Invalid IPv4 address" }
                val value = Integer.parseInt(part)
                require(value <= 255) { "Invalid IPv4 address" }
                n = (n shl 8) or value.toLong()
            }
            return n
        }

        @JvmStatic fun address(n: Long): String =
            ((n ushr 24) and 255).toString() + "." + ((n ushr 16) and 255) + "." +
                ((n ushr 8) and 255) + "." + (n and 255)

        @JvmStatic fun validHost(input: String): String {
            val s = input.javaTrim()
            require(s.isNotEmpty() && s.length <= 253 &&
                s.matches(Regex("[a-zA-Z0-9.:-]+")) && !s.startsWith("-")) {
                "Enter an IP address or hostname, without a URL or spaces."
            }
            return s
        }
    }
}

