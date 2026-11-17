package io.github.kazands.networklens

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Receive-only public discovery envelopes. The public broadcast key is not a device local key.
 * No account API, device authentication, DPS query, registration or retained device identifier. */
internal object TuyaAnnouncement {
    private val broadcastKey = SecretKeySpec(MessageDigest.getInstance("MD5")
        .digest("yGAdlopoPVldABfn".toByteArray(Charsets.US_ASCII)), "AES")
    fun parse(bytes: ByteArray, peer: InetAddress): LanIdentity.Report? {
        if (bytes.size !in 28..4096) return null
        val frame = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val body = try {
            when (frame.getInt(0)) {
                0x55aa -> {
                    if (frame.getInt(8) !in setOf(0,19,35) || frame.getInt(12) != bytes.size-16 ||
                        frame.getInt(bytes.size-4) != 0xaa55 || frame.getInt(16) != 0 ||
                        CRC32().apply { update(bytes,0,bytes.size-8) }.value != (frame.getInt(bytes.size-8).toLong() and 0xffffffffL)) return null
                    val payload = bytes.copyOfRange(20,bytes.size-8)
                    if (payload.firstOrNull() == '{'.code.toByte()) payload else {
                        if (payload.isEmpty() || payload.size % 16 != 0) return null
                        Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE,broadcastKey) }.doFinal(payload)
                    }
                }
                0x6699 -> {
                    if (bytes.size < 50 || frame.getShort(4).toInt()!=0 || frame.getInt(10) !in setOf(19,35) ||
                        frame.getInt(14)!=bytes.size-22 || frame.getInt(bytes.size-4)!=0x9966) return null
                    val cipher=Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE,broadcastKey,GCMParameterSpec(128,bytes.copyOfRange(18,30)))
                    cipher.updateAAD(bytes,4,14)
                    val decrypted=cipher.doFinal(bytes,30,bytes.size-34)
                    if (decrypted.firstOrNull()=='{'.code.toByte()) decrypted else {
                        if(decrypted.size<5 || ByteBuffer.wrap(decrypted).int!=0) return null
                        decrypted.copyOfRange(4,decrypted.size)
                    }
                }
                else -> return null
            }
        } catch (_: Exception) { return null }
        val end=body.indexOfLast {it.toInt()!=0}+1
        val values=BoundedJson.fields(body.copyOf(end),setOf("ip","version","gwId","productKey","productId")) ?: return null
        if (!Address.bytes(values["ip"]).contentEquals(peer.address) ||
            !values["version"].orEmpty().matches(Regex("3\\.[1-5]")) ||
            !values["gwId"].orEmpty().matches(Regex("[A-Za-z0-9_-]{8,80}"))) return null
        val details=linkedMapOf("Tuya protocol version" to values.getValue("version"))
        values["productKey"]?.takeIf {it.matches(Regex("[A-Za-z0-9_-]{1,80}"))}?.let {details["Tuya product key (not a retail model)"]=it}
        values["productId"]?.takeIf {it.matches(Regex("[A-Za-z0-9_-]{1,80}"))}?.let {details["Tuya product ID (not a retail model)"]=it}
        return LanIdentity.Report("tuya",LocalMetadata.Report(details=details))
    }
}
