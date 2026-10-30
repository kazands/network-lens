package io.github.kazands.networklens

/** Network-advertised labels are bounded plain text without bidi controls. */
class SafeText private constructor() {
    companion object {
        @JvmStatic fun label(input: String?, maximum: Int): String {
            if (input == null || maximum <= 0) return ""
            val out = StringBuilder()
            var offset = 0
            var count = 0
            while (offset < input.length && count < maximum) {
                val code = input.codePointAt(offset)
                offset += Character.charCount(code)
                count++
                if (Character.isISOControl(code) || code in 0x202a..0x202e ||
                    code in 0x2066..0x2069 || code == 0x200e || code == 0x200f || code == 0x061c) continue
                out.appendCodePoint(code)
            }
            return out.toString().javaTrim()
        }
    }
}

