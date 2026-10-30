@file:Suppress(
    "UNUSED_PARAMETER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "UNCHECKED_CAST",
    "DEPRECATION",
)

import io.github.kazands.networklens.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.*

object VendorIndexTest {
    var checks: Int = 0

    fun check(value: Boolean, label: String): Unit {
        checks++
        if (!(value)) {
            throw AssertionError(label)
        }
    }

    fun data(): ByteArray {
        var bytes: ByteArrayOutputStream = ByteArrayOutputStream()
        var out: DataOutputStream = DataOutputStream(bytes)
        var names: kotlin.Array<String> =
            arrayOf("Large vendor", "Medium vendor", "Small vendor", "PRIVATE")
        out.writeInt(1145853489)
        out.writeLong(1)
        out.writeInt(names.size)
        out.writeInt(4)
        for (name in names) {
            var value: ByteArray = name.toByteArray(StandardCharsets.UTF_8)
            out.writeShort(value.size)
            out.write(value)
        }
        var prefixes: kotlin.Array<String> = arrayOf("001122", "0011223", "001122334", "001122335")
        run {
            var n: Int = 0
            while ((n < 4)) {
                out.writeLong(
                    (((((java.lang.Long.parseLong(prefixes[n], 16)).toLong() shl (4).toInt())))
                        .toLong() or ((prefixes[n])!!.length).toLong())
                )
                out.writeInt(n)
                n++
            }
        }
        return bytes.toByteArray()
    }

    fun rejected(bytes: ByteArray): Boolean {
        try {
            VendorIndex.read(ByteArrayInputStream(bytes))
            return false
        } catch (e: IOException) {
            return true
        }
    }

    @JvmStatic
    fun main(args: kotlin.Array<String>): Unit {
        var bytes: ByteArray = data()
        var index: VendorIndex = VendorIndex.read(ByteArrayInputStream(bytes))
        check(
            (index.lookup("00:11:22:33:44:55"))!!.equals("Small vendor"),
            "36-bit registry wins over broader prefixes",
        )
        check(
            (index.lookup("00:11:22:3F:00:00"))!!.equals("Medium vendor"),
            "28-bit registry wins over 24 bits",
        )
        check(
            ((index.lookup("00-11-22-90-00-00"))!!.equals("Large vendor") &&
                (index.lookup("001122900000"))!!.equals("Large vendor")),
            "Normalize valid reported MAC formats",
        )
        check(
            (index.lookup("00:11:22:33:50:00"))!!.isEmpty(),
            "A private assignment does not inherit the parent vendor",
        )
        for (value in
            arrayOf(
                "02:11:22:33:44:55",
                "01:11:22:33:44:55",
                "FF:FF:FF:FF:FF:FF",
                "00:00:00:00:00:00",
                "02:00:00:00:00:00",
                "invalid",
                "192.168.2.60",
                "00:11:22:33:44:55:66:77",
                "",
                "null",
            )) {
            check(
                (index.lookup(value))!!.isEmpty(),
                "Never infer vendors from local, multicast, invalid or unknown identifiers",
            )
        }
        check(
            ((index.lookup(null))!!.isEmpty() &&
                (VendorIndex.EMPTY.lookup("00:11:22:33:44:55"))!!.isEmpty()),
            "Missing registry/address is a normal unknown",
        )
        run {
            var n: Int = 0
            while ((n < bytes.size)) {
                check(rejected(Arrays.copyOf(bytes, n)), "Reject truncated index")
                n++
            }
        }
        var trailing: ByteArray = Arrays.copyOf(bytes, (bytes.size + 1))
        check(rejected(trailing), "Reject trailing data")
        var bad: ByteArray = bytes.clone()
        bad[0] = 0
        check(rejected(bad), "Reject unknown format")
        bad = bytes.clone()
        Arrays.fill(bad, 12, 16, (127).toByte())
        check(rejected(bad), "Reject excessive organization count")
        bad = bytes.clone()
        bad[20] = 127
        bad[21] = 127
        check(rejected(bad), "Reject oversized organization strings")
        var random: Random = Random(217)
        run {
            var n: Int = 0
            while ((n < 10000)) {
                var input: ByteArray = ByteArray(random.nextInt(800))
                random.nextBytes(input)
                check(rejected(input), "Malformed input is bounded")
                n++
            }
        }
        Files.newInputStream(Paths.get("android/app/src/main/res/raw/ieee_mac_vendors.bin")).use {
            input ->
            var real: VendorIndex = VendorIndex.read(input)
            check(((real.snapshot).toLong() > (0).toLong()), "Bundled data snapshot is readable")
            check(
                ((real.lookup("30:05:5C:00:00:01"))!!.lowercase(Locale.ROOT))!!.contains("brother"),
                "IEEE prefix fixture resolves to Brother",
            )
            check(
                ((real.lookup("C0:51:7E:00:00:01"))!!.lowercase(Locale.ROOT))!!.contains(
                    "hikvision"
                ),
                "IEEE prefix fixture resolves to Hikvision",
            )
        }
        System.out.println(
            (("PASS · " + checks) + " vendor-index checks (including 10000 malformed inputs)")
        )
    }
}
