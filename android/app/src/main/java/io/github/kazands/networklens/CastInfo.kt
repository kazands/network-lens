package io.github.kazands.networklens

import java.net.Inet6Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets

/** Bounded name-only JSON; never retain claimed addresses or location data. */
class CastInfo private constructor() {
    companion object {
        const val MAX_BODY = 32768
        @JvmStatic fun needsName(name: String?): Boolean = !IdentityHints.specificName(name)

        @JvmStatic fun request(responder: InetAddress): ByteArray {
            var host = responder.hostAddress!!.javaSplit("%", 2)[0]
            if (responder is Inet6Address) host = "[" + host + "]"
            return ("GET /setup/eureka_info?options=detail HTTP/1.1\r\nHost: " + host +
                ":8008\r\nAccept: application/json\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        }

        @JvmStatic fun name(bytes: ByteArray?): String {
            val fields = BoundedJson.fields(bytes, setOf("name")) ?: return ""
            val name = SafeText.label(fields["name"], 160)
            return if (needsName(name)) "" else name
        }
    }
}
