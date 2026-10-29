package dev.netlens

import java.net.InetAddress
import java.util.Locale

/** Numeric addresses and valid unicast identifiers; never DNS guesses. */
class Address private constructor() {
    companion object {
        @JvmStatic fun mac(input: String?): String {
            if (input == null) return ""
            var s = input.javaTrim().replace('-', ':').uppercase(Locale.ROOT)
            if (s.matches(Regex("[0-9A-F]{12}"))) s = s.replace(Regex("(..)(?!$)"), "$1:")
            if (!s.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))) return ""
            if (s == "00:00:00:00:00:00" || s == "02:00:00:00:00:00" ||
                (Integer.parseInt(s.substring(0, 2), 16) and 1) != 0) return ""
            return s
        }

        @JvmStatic fun bytes(input: String?): ByteArray? = try {
            var s = input ?: throw NullPointerException()
            val scope = s.indexOf('%')
            if (scope >= 0) s = s.substring(0, scope)
            if (!s.contains(':')) {
                val n = IpRange.number(s)
                byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
            } else if (!s.matches(Regex("[0-9a-fA-F:.]+"))) null
            else InetAddress.getByName(s).address
        } catch (_: Exception) { null }

        @JvmStatic fun compare(a: String, b: String): Int {
            val x = bytes(a)
            val y = bytes(b)
            if (x == null || y == null) return a.compareTo(b)
            if (x.size != y.size) return x.size - y.size
            for (i in x.indices) {
                val n = (x[i].toInt() and 255) - (y[i].toInt() and 255)
                if (n != 0) return n
            }
            return a.compareTo(b)
        }

        @JvmStatic fun inPrefix(value: String, local: String, prefix: Int): Boolean {
            val a = bytes(value)
            val b = bytes(local)
            if (a == null || b == null || a.size != b.size || prefix < 0 || prefix > a.size * 8) return false
            for (bit in 0 until prefix) {
                if (((a[bit / 8].toInt() xor b[bit / 8].toInt()) and (0x80 ushr (bit % 8))) != 0) return false
            }
            return true
        }
    }
}

