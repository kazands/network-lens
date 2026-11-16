import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/** Synthetic reports and loopback only. Public model facts contain no household observations. */
object AndroidRecognitionTest {
    private var checks = 0
    private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
    private fun parse(json: String) = CastInfo.parse(json.toByteArray())
    private fun service(vararg attrs: Pair<String, String>) = MdnsPacket.Service("Synthetic Cast", "_googlecast._tcp.").apply {
        port = 8009; attributes.putAll(attrs.associate { it.first to it.second.toByteArray() })
    }
    @JvmStatic fun main(args: Array<String>) {
        val response = """{"name":"Synthetic room","device_info":{"model_name":"SHIELD Android TV","manufacturer":"NVIDIA","name":"Synthetic receiver","mac_address":"00:00:00:00:00:00","ssdp_udn":"synthetic-private"},"ssid":"synthetic-private","location":{"latitude":42},"devices":[{"model_name":"Other model"}]}"""
        val report = parse(response)!!
        check(report.hints == mapOf("Name" to "Synthetic receiver", "Model" to "SHIELD Android TV", "Manufacturer" to "NVIDIA"), "Only reviewed nested identity fields are retained")
        check(report.mac.isEmpty() && report.details.isEmpty() && !report.toString().contains("synthetic-private"), "Identifiers, location and Wi-Fi information are not retained")
        for (json in listOf("""{"device_info.model_name":"Pixel 8"}""", """{"device_info":[{"model_name":"Pixel 8"}]}""",
            """{"outer":{"device_info":{"model_name":"Pixel 8"}}}""", """{"model_name":"Pixel 8","manufacturer":"Google"}"""))
            check(parse(json)!!.hints.isEmpty(), "Unrelated paths cannot masquerade as direct device information")
        for (json in listOf("""{"device_info":{"model_name":"One","model_name":"Two"}}""",
            """{"device_info":{"model_name":"One"},"device_info":{"model_name":"Two"}}""",
            """{"device_info":{"model_name":"One","model\u005fname":"Two"}}"""))
            check(parse(json) == null, "Duplicate nested keys cannot overwrite identity")
        check(parse("""{"device_info":{"model_name":42,"manufacturer":true}}""")!!.hints.isEmpty(), "Wrong scalar types stay unknown")
        check(parse("""{"device_info":{"model_name":"Unknown model name","manufacturer":"Unknown manufacturer"}}""")!!.hints.isEmpty(), "Placeholders stay unknown")
        check(parse("""{"device_info":{"model_name":"${"x".repeat(161)}","manufacturer":"Google"}}""")!!.hints == mapOf("Manufacturer" to "Google"), "Oversized model is not truncated into a catalogue alias")
        check(parse("""{"device_info":{"model_name":"Pixel\n8"}}""")!!.hints.isEmpty(), "Control characters cannot become a different model")
        check(CastInfo.parse(byteArrayOf(-61,40)) == null && CastInfo.parse(ByteArray(32769)) == null, "UTF-8 and size limits remain enforced")
        check(parse("""{"name":"Synthetic room"}""")!!.hints == mapOf("Name" to "Synthetic room"), "Legacy name-only responses stay useful without invented manufacturer")
        check(parse("""{"device_info":{"model_name":"Google Cast Group","manufacturer":"Google","name":"Synthetic group"}}""")!!.hints.isEmpty(), "Virtual group response cannot identify its host")
        for (group in listOf(service("md" to "Google Cast Group"), service("ca" to "36"), service("ca" to "32"))) {
            check(CastInfo.isGroup(group) && LocalMetadata.request(group) == null, "Explicit groups do not trigger physical device metadata")
        }
        for (receiver in listOf(service("ca" to "5"), service("ca" to "4"), service("ca" to "-1"), service("ca" to "unknown")))
            check(!CastInfo.isGroup(receiver), "Audio/video flags and malformed values cannot imply grouping")
        val request = LocalMetadata.request(service("CPath" to "http://203.0.113.1/private"))!!
        val wire = String(request.bytes(InetAddress.getByName("fd00::10")))
        check(request.kind == LocalMetadata.Kind.CAST && request.port == 8008 && wire.startsWith("GET /setup/eureka_info?params=device_info,name HTTP/1.1") && !wire.contains("private"), "Fixed minimal local request ignores advertised paths and playback ports")
        check(wire.contains("Host: [fd00:0:0:0:0:0:0:10]:8008"), "IPv6 request authority stays valid")
        check(LocalServices.INITIAL.contains("_androidtvremote2._tcp.") && HardwareClassification.reported("Android TV remote service") == null, "Android TV service browsing does not guess television versus streaming box")

        val hardware = File("android/app/src/main/res/raw/android_hardware.tsv").inputStream().use(AndroidHardwareIndex::read)
        val products = GZIPInputStream(File("android/app/src/main/res/raw/product_models.gz").inputStream()).use(ProductModelIndex::read)
        check(hardware.size == 651, "All pinned cross-checked category pairs load")
        DeviceCatalog.install(products); DeviceCatalog.installHardware(hardware)
        for ((maker, code, category) in listOf(Triple("Google", "Pixel 8", DeviceCatalog.Category.PHONE),
            Triple("Samsung", "SM-T720", DeviceCatalog.Category.TABLET), Triple("OnePlus", "A0001", DeviceCatalog.Category.PHONE),
            Triple("Sony", "C1904", DeviceCatalog.Category.PHONE), Triple("NVIDIA", "SHIELD Tablet", DeviceCatalog.Category.TABLET))) {
            val evidence = DeviceCatalog.Evidence().apply { offer("Manufacturer", maker, "Synthetic direct report"); offer("Model", code, "Synthetic direct report") }
            check(evidence.match()?.product?.category == category && evidence.categories().single().source.contains("LineageOS@"), "Exact $maker model gets a source-labelled hardware type")
            val copy = evidence.copy(); evidence.offer("Manufacturer", "Synthetic other maker", "Synthetic conflicting report")
            check(evidence.match() == null && evidence.categories().isEmpty() && copy.match()?.product?.category == category, "Manufacturer conflict survives copies and blocks category inference")
            check(hardware.lookup("Synthetic other maker", code) == null && hardware.lookup(maker, "$code extra") == null, "No cross-manufacturer or substring category match")
        }
        check(DeviceCatalog.lookup("Pixel 8", "Google Inc.")?.category == DeviceCatalog.Category.PHONE, "Reported Google corporate spelling qualifies its model")
        check(DeviceCatalog.lookup("Pixel 8") == null && DeviceCatalog.lookup("R7000", "NETGEAR")?.category == DeviceCatalog.Category.UNSPECIFIED, "Missing manufacturer and unrelated catalogue entries do not acquire Android categories")
        val header = "# Network Lens Android hardware categories v1\n"
        val row = "google\tpixel 8\tPHONE\tLineageOS@${"a".repeat(40)}/synthetic\n"
        for (bad in listOf("wrong\n", header+row+row, header+row.replace("PHONE","THERMOSTAT"), header+row.replace("google","Google"), header+row.replace("LineageOS@","unknown@"))) {
            var rejected = false; try { AndroidHardwareIndex.read(ByteArrayInputStream(bad.toByteArray())) } catch (_: IOException) { rejected = true }
            check(rejected, "Malformed category sources cannot silently supply types")
        }
        var tooLarge = false; try { AndroidHardwareIndex.read(ByteArrayInputStream(ByteArray(512*1024+1))) } catch (_: IOException) { tooLarge=true }
        check(tooLarge, "Category table read is bounded")
        DeviceCatalog.installHardware(AndroidHardwareIndex.EMPTY)

        // Production HTTP framing and parser, with the fixed service port redirected only by this fixture socket.
        for ((status, body) in listOf(200 to response, 403 to "")) {
            ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout=3000; val failure=AtomicReference<Throwable?>()
                val worker=thread {
                    try { server.accept().use { peer ->
                        peer.soTimeout=2000; val received=ByteArrayOutputStream()
                        while (!received.toString("US-ASCII").endsWith("\r\n\r\n")) { val b=peer.getInputStream().read(); if(b<0 || received.size()>4096) throw EOFException(); received.write(b) }
                        check(received.toString("US-ASCII").startsWith("GET /setup/eureka_info?params=device_info,name HTTP/1.1"), "Transport sends only the selected information request")
                        val bytes=body.toByteArray();peer.getOutputStream().write("HTTP/1.1 $status Synthetic\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray()+bytes)
                    } } catch(e:Throwable) { failure.set(e) }
                }
                val timer=ScheduledThreadPoolExecutor(1)
                try {
                    val socket=object:Socket() { override fun connect(endpoint:SocketAddress, timeout:Int) { super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(),server.localPort),timeout) } }
                    socket.use {
                        try {
                            val result=MetadataHttp.exchange(it,InetAddress.getLoopbackAddress(),request,System.nanoTime()+TimeUnit.SECONDS.toNanos(2),timer){true}
                            check(status==200 && result==report,"Current HTTP response reaches nested identity parser")
                        } catch(e:MetadataHttp.Failure) { check(status==403 && e.reason==MetadataHttp.Reason.HTTP_STATUS && e.httpStatus==403,"HTTP refusal remains an explicit lookup failure") }
                    }
                } finally { timer.shutdownNow();worker.join(3500) }
                check(!worker.isAlive && failure.get()==null,"Loopback fixture releases all work")
            }
        }
        println("PASS · $checks Android recognition, category and Cast-group checks")
    }
}
