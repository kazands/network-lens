import io.github.kazands.networklens.*
import java.net.URI

object DiscoveryMatchersTest {
    private var checks = 0
    private fun check(value: Boolean, reason: String) { checks++; if (!value) error(reason) }
    private fun service(type: String, name: String = "Fixture", vararg attrs: Pair<String, String>) = MdnsPacket.Service(name, type).also {
        attrs.forEach { (k,v) -> it.attributes[k] = v.toByteArray() }
    }
    @JvmStatic fun main(args: Array<String>) {
        check(HaMatchers.types.size == 122, "All reviewed HA service types retained")
        check(DiscoveryMatchers.mdns(service("_lutron._tcp.", "Fixture", "SYSTYPE" to "SmartBridge2" )).contains("lutron_caseta"), "Lutron required TXT matched")
        check("lutron_caseta" !in DiscoveryMatchers.mdns(service("_lutron._tcp.")), "Missing TXT cannot satisfy Lutron rule")
        check("lutron_caseta" !in DiscoveryMatchers.mdns(service("_lutron._tcp.", "Fixture", "systype" to "unrelated")), "Wrong TXT rejected")
        check("apple_tv" in DiscoveryMatchers.mdns(service("_airplay._tcp.", "Fixture", "model" to "AudioAccessory5,1")), "HomePod family without TV classification")
        check(DiscoveryMatchers.family("apple_tv")!!.hardware.isEmpty(), "Apple media family does not force TV hardware")
        check(DiscoveryMatchers.mdns(service("_airplay._tcp.")).isEmpty(), "Generic AirPlay is not an Apple product")
        check("ecobee" in DiscoveryMatchers.homekit(service("_hap._tcp.", "Fixture", "md" to "ecobee Premium")), "HomeKit model matching")
        check("samsungtv" in DiscoveryMatchers.mdns(service("_airplay._tcp.", "Fixture", "manufacturer" to "Samsung Electronics")), "Samsung airplay rule")
        check(DiscoveryMatchers.mdns(service("_airplay._tcp.", "Fixture", "manufacturer" to "Samsung", "MANUFACTURER" to "Other")).isEmpty(), "Conflicting keys rejected")
        check(DiscoveryMatchers.textAttributes(mapOf("model" to byteArrayOf(0xc0.toByte(),0xaf.toByte()))) == null, "Malformed UTF-8 rejected")
        check(DiscoveryMatchers.match("mdns", mapOf("type" to "_airplay._tcp.local.", "txt:manufacturer" to "x".repeat(1025))).isEmpty(), "Bounded input")
        val headers = mapOf("st" to "urn:schemas-sony-com:service:ScalarWebAPI:1")
        val doc = mapOf("manufacturer" to "Sony Corporation")
        val one = URI("http://192.0.2.1/one.xml"); val two = URI("http://192.0.2.1/two.xml")
        val join = SsdpMatchers()
        check("braviatv" !in join.headers("192.0.2.1", one, headers), "Partial conditions do not match")
        check("braviatv" !in join.description("192.0.2.1", two, doc), "Different URL cannot borrow headers")
        check("braviatv" !in join.description("192.0.2.2", one, doc), "Different peer cannot borrow headers")
        check("braviatv" in join.description("192.0.2.1", one, doc), "Same peer URL joins conditions")
        val late = SsdpMatchers(); late.headers("192.0.2.1", one, emptyMap()); late.description("192.0.2.1", one, doc)
        check("braviatv" in late.headers("192.0.2.1", one, headers), "Late current response can complete match")
        val duplicate = "HTTP/1.1 200 OK\r\nST: one\r\nST: two\r\n\r\n".toByteArray()
        check(Ssdp.parse(duplicate,duplicate.size).isEmpty(), "Conflicting SSDP headers rejected")
        val observed = (0 until 100).map { "_fixture$it._tcp." }.toSet()
        val attempts = mutableMapOf<String,Int>()
        repeat(100) { MdnsTypePlan.ordered(observed,attempts).filter { attempts.getOrDefault(it,0)<2 }.take(8).forEach { attempts[it]=attempts.getOrDefault(it,0)+1 } }
        check(observed.all { attempts[it]==2 }, "Types beyond 48 get two opportunities")
        check(HaMatchers.types.all { attempts[it]==2 }, "Catalogue types get fair opportunities")
        check(MdnsTypePlan.CONCURRENT_BROWSES==6 && MdnsTypePlan.MAX_TYPES==512, "Concurrency is separate from coverage")
        val first=MdnsTypePlan.ordered(observed,emptyMap()).take(8)
        check(MdnsTypePlan.ordered(observed,first.associateWith {1}).take(8).none {it in first}, "Untouched types before retries")
        println("PASS: $checks discovery matcher and scheduling checks")
    }
}
