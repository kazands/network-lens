import io.github.kazands.networklens.Recog
import io.github.kazands.networklens.RecogModels
import io.github.kazands.networklens.MdnsPacket
import io.github.kazands.networklens.MdnsAssembly
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.util.Random

/** Public model codes and synthetic metadata only; no sockets or Android dependencies. */
object RecogTest {
    private var checks = 0
    private fun check(value: Boolean, message: String) {
        checks++
        if (!value) throw AssertionError(message)
    }
    private fun attributes(code: String) = mapOf("model" to code.toByteArray(Charsets.UTF_8))

    /** Complete synthetic DNS response, exercising both TXT parsers and assembly. */
    private fun wireServices(first: String, second: String): List<MdnsPacket.Service> {
        fun name(vararg labels: String) = ByteArrayOutputStream().apply {
            for (label in labels) { val bytes = label.toByteArray(Charsets.UTF_8); write(bytes.size); write(bytes) }
            write(0)
        }.toByteArray()
        fun record(owner: ByteArray, kind: Int, payload: ByteArray) = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).apply {
                write(owner); writeShort(kind); writeShort(1); writeInt(120); writeShort(payload.size); write(payload)
            }
        }.toByteArray()
        val type = name("_device-info", "_tcp", "local")
        val instance = name("Synthetic", "_device-info", "_tcp", "local")
        val host = name("synthetic", "local")
        val peer = InetAddress.getByName("192.0.2.20")
        val srv = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).apply { writeInt(0); writeShort(8080); write(host) }
        }.toByteArray()
        val txt = ByteArrayOutputStream().apply {
            for (value in listOf(first, second)) {
                val bytes = value.toByteArray(Charsets.UTF_8); require(bytes.size <= 255)
                write(bytes.size); write(bytes)
            }
        }.toByteArray()
        val records = listOf(record(type, 12, instance), record(instance, 33, srv),
            record(instance, 16, txt), record(host, 1, peer.address))
        val packet = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).apply {
                writeShort(0); writeShort(0x8400); writeShort(0); writeShort(records.size); writeInt(0)
                records.forEach { write(it) }
            }
        }.toByteArray()
        val parsed = MdnsPacket.parse(packet, packet.size)
        return listOf(parsed.directServices(peer).single(), MdnsAssembly().accept(peer, parsed, 1_000_000_000).single())
    }

    @JvmStatic fun main(args: Array<String>) {
        val cases = listOf(
            Triple("MacBookPro18,3", "MacBook Pro (14-Inch, M1, 2021)", Recog.Category.LAPTOP),
            Triple("MacBookAir10,1", "MacBook Air (M1, 2020)", Recog.Category.LAPTOP),
            Triple("Macmini9,1", "Mac mini (M1, 2020)", Recog.Category.DESKTOP),
            Triple("MacPro7,1", "Mac Pro (Late 2019)", Recog.Category.DESKTOP),
            Triple("iMac21,1", "iMac (24-inch, M1, 2021)", Recog.Category.DESKTOP),
            Triple("iPad13,4", "iPad Pro (11-inch, 3rd generation)", Recog.Category.TABLET),
            Triple("iPad14,1", "iPad mini (6th generation)", Recog.Category.TABLET),
            Triple("iPhone14,3", "iPhone 13 Pro Max", Recog.Category.PHONE),
            Triple("AudioAccessory5,1", "HomePod mini", Recog.Category.SPEAKER),
            Triple("AudioAccessory1,2", "HomePod", Recog.Category.SPEAKER),
            Triple("AppleTV11,1", "Apple TV", Recog.Category.TV_PLAYER),
            Triple("AppleTV3,2", "Apple TV", Recog.Category.TV_PLAYER),
            Triple("iPod9,1", "iPod Touch (7th generation)", Recog.Category.HANDHELD)
        )
        for ((code, description, category) in cases) {
            val model = Recog.lookup(code)!!
            check(model.description == description && model.category == category, "Exact $code maps to its intended hardware description and category")
            check(model.source.contains(RecogModels.REVISION) && model.source.contains("rule ${model.rule}"), "Every match retains the pinned rule identity")
            check(model.input.endsWith("model=$code") && model.rule > 0, "Every interpretation retains its original model field")
            check(model.familyOnly == code.startsWith("AppleTV"), "Low-certainty Apple TV mappings remain family-only")
            for (bad in listOf("$code-extra", "$code\n", " $code", "${code}0", "model=$code", code.lowercase()))
                check(Recog.lookup(bad) == null, "Near-match or transformed model code cannot match $code")
            val evidence = Recog.Evidence()
            repeat(200) { evidence.offer(attributes(code)) }
            check(evidence.match() == model, "Repeated current packets retain the same exact interpretation")
            val copy = evidence.copy()
            evidence.offer(attributes("FixtureUnknown99,1"))
            check(evidence.match() == null, "An unmatched conflicting code suppresses prior recognition")
            check(copy.match() == model, "Progress copies do not share mutable recognition state")
            evidence.offer(attributes(code))
            check(evidence.match() == null, "Later callbacks cannot erase an observed same-scan conflict")
            check(Recog.Evidence().match() == null, "A fresh scan starts without previous recognition")
        }
        val sharedModels = mapOf(
            "MacBookAir6,1" to "MacBook Air (11-inch)",
            "MacBookAir6,2" to "MacBook Air (13-inch)",
            "MacBookAir7,2" to "MacBook Air (13-inch)",
            "MacBookPro4,1" to "MacBook Pro",
            "MacBookPro5,2" to "MacBook Pro (17-inch)",
            "MacBookPro8,1" to "MacBook Pro (13-inch, 2011)",
            "MacBookPro8,2" to "MacBook Pro (15-inch, 2011)",
            "MacBookPro8,3" to "MacBook Pro (17-inch, 2011)",
            "MacBookPro10,1" to "MacBook Pro (Retina, 15-inch)",
            "MacBookPro10,2" to "MacBook Pro (Retina, 13-inch)",
            "MacBookPro11,1" to "MacBook Pro (Retina, 13-inch)",
            "MacBookPro11,2" to "MacBook Pro (Retina, 15-inch)",
            "MacBookPro11,3" to "MacBook Pro (Retina, 15-inch)",
            "MacPro5,1" to "Mac Pro",
            "iMac9,1" to "iMac (Early 2009)",
            "iMac15,1" to "iMac (Retina 5K, 27-inch)"
        )
        for ((code, description) in sharedModels) {
            val match = Recog.lookup(code)!!
            check(match.description == description && match.familyOnly,
                "Shared $code does not select one of several model years or screen sizes")
        }
        for (code in listOf("", "Unknown", "MacBookPro999,1", "MacBookPro18,30", "iMac10,1", "iMac11,1", "J305AP", "AirPort4,104", "MacPro", "Phone13,1", "iPhone13,1", "OpenSSH_9.0", "x".repeat(100000)))
            check(Recog.lookup(code) == null, "Unreviewed, ambiguous, board, software or oversized inputs remain unrecognized")
        val valid = "MacBookPro18,3"
        for (bytes in listOf(ByteArray(0), byteArrayOf(0xff.toByte()), "$valid\u0000".toByteArray(), "$valid\u202e".toByteArray(), "x".repeat(65).toByteArray(), " $valid".toByteArray())) {
            val evidence = Recog.Evidence()
            evidence.offer(mapOf("model" to bytes)); evidence.offer(attributes(valid))
            check(evidence.match() == null, "Malformed input is never cleaned into a matching identifier")
        }
        for ((firstKey, secondKey) in listOf("model" to "model", "model" to "MODEL", "MODEL" to "model", "MoDeL" to "MODEL")) {
            for ((first, second) in listOf(valid to "iPad14,1", "iPad14,1" to valid, valid to valid,
                "FixtureUnknown99,1" to valid, "$valid\u0000" to valid, "" to valid,
                valid.lowercase() to valid, " $valid" to valid)) {
                for (service in wireServices("$firstKey=$first", "$secondKey=$second")) {
                    check(service.attributes.keys == setOf("model") && service.attributes.getValue("model").contentEquals(first.toByteArray(Charsets.UTF_8)),
                        "Wire parsing and assembly normalize key case and retain only the first TXT value")
                    val evidence = Recog.Evidence().apply { offer(service.attributes) }
                    check(evidence.match() == Recog.lookup(first), "Later duplicate TXT keys cannot replace the first value during recognition")
                }
            }
        }
        val caseKey = Recog.Evidence()
        caseKey.offer(mapOf("MODEL" to valid.toByteArray()))
        check(caseKey.match()?.code == valid, "DNS-SD key case does not change model-value semantics")
        caseKey.offer(mapOf("unrelated" to "fixture".toByteArray()))
        check(caseKey.match()?.code == valid, "An advertisement without a model cannot invent a conflicting code")
        val unknown = Recog.Evidence()
        unknown.offer(attributes("FixtureUnknown99,1")); unknown.offer(attributes(valid))
        check(unknown.match() == null, "Unknown-before-known conflict is order independent")
        val random = Random(17)
        repeat(3000) {
            val value = "Fixture" + (1..80).map { ('a'.code + random.nextInt(26)).toChar() }.joinToString("")
            check(Recog.lookup(value) == null, "Synthetic arbitrary strings cannot create hardware identities")
        }
        repeat(10000) { Recog.lookup(valid) }
        val start = System.nanoTime()
        var matched = 0
        repeat(50000) { if (Recog.lookup(valid)?.category == Recog.Category.LAPTOP) matched++ }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        check(matched == 50000, "Repeated lookup remains deterministic during the host measurement")
        println("PASS · $checks Recog checks; 50,000 host lookups in $elapsed ms (host timing only)")
    }
}
