import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Synthetic protocol frames and loopback servers only; never contacts LAN devices. */
object AppleDeviceInfoTest {
    private var checks = 0
    private fun check(ok: Boolean, message: String) { checks++; if (!ok) throw AssertionError(message) }
    private fun plist(vararg fields: Pair<String, String>) =
        ("<?xml version=\"1.0\"?><plist version=\"1.0\"><dict>" + fields.joinToString("") {
            "<key>${it.first}</key><string>${it.second}</string>"
        } + "</dict></plist>").toByteArray()
    private fun frame(body: ByteArray) = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(body.size); write(body) } }.toByteArray()
    private fun read(input: InputStream): Map<String, String> {
        val data = DataInputStream(input); val size = data.readInt(); require(size in 1..8192)
        return AppleDeviceInfo.parse(ByteArray(size).also { data.readFully(it) })
    }
    private fun rejects(body: ByteArray) {
        var rejected = false
        try { AppleDeviceInfo.parse(body) } catch (_: IOException) { rejected = true }
        check(rejected, "Malformed/unsafe plist rejected")
    }
    private fun withServer(handler: (Socket) -> Unit, client: (Int) -> Unit) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 3000
            val error = java.util.concurrent.atomic.AtomicReference<Throwable>()
            val worker = thread { try { server.accept().use { it.soTimeout = 1500; handler(it) } } catch (e: Throwable) { error.set(e) } }
            try { client(server.localPort) } finally { worker.join(4000); check(!worker.isAlive, "Fixture finishes within bound") }
            error.get()?.let { throw AssertionError("Fixture failed", it) }
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        check(read(ByteArrayInputStream(AppleDeviceInfo.request(false))) == mapOf("Request" to "QueryType"), "First request only identifies service; label discarded by parser")
        check(read(ByteArrayInputStream(AppleDeviceInfo.request(true))) == mapOf("Request" to "GetValue", "Key" to "ProductType"), "Only model field requested, no pairing/serial/name/session")
        val service = plist("Request" to "QueryType", "Type" to "com.apple.mobile.lockdown")
        check(AppleDeviceInfo.serviceResponse(AppleDeviceInfo.parse(service)), "Exact service identity accepted")
        val dtd = "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
        check(AppleDeviceInfo.parse(String(service).replace("<plist", "$dtd<plist").toByteArray()) == AppleDeviceInfo.parse(service), "Standard plist DTD handled without external access")
        rejects(String(service).replace("QueryType", "Query${dtd}Type").toByteArray())
        rejects(String(service).replace("<plist", "$dtd$dtd<plist").toByteArray())
        check(!AppleDeviceInfo.serviceResponse(mapOf("Type" to "com.apple.mobile.lockdown")), "Request correlation required")
        check(!AppleDeviceInfo.serviceResponse(mapOf("Request" to "QueryType", "Type" to "com.apple.mobile.lockdown", "Error" to "Denied")), "Conflicting error cannot identify service")
        for (bad in listOf("<plist version=\"1.0\"/>", "<plist version=\"1.0\"><dict/><dict/></plist>",
            "<plist version=\"1.0\"><dict><key>Value</key><dict/></dict></plist>",
            String(plist("Value" to "one", "Value" to "two")),
            String(service).replace("<plist", "<!DOCTYPE plist [<!ENTITY x 'value'>]><plist"),
            String(service).replace("<plist", "<!DOCTYPE plist SYSTEM 'file:///must-not-read'><plist"),
            String(service).replace("<string>QueryType", "<string><![CDATA[QueryType]]>"),
            String(service).replace("<plist", "<p:plist xmlns:p=\"urn:foreign\""),
            String(service).replace("QueryType", "x".repeat(257)))) rejects(bad.toByteArray())
        rejects(byteArrayOf(0xc3.toByte(), 0x28)); rejects(ByteArray(8193))
        for (fields in listOf(mapOf("Request" to "GetValue", "Key" to "DeviceName", "Value" to "iPhone17,1"),
            mapOf("Request" to "Other", "Key" to "ProductType", "Value" to "iPhone17,1"),
            mapOf("Request" to "GetValue", "Key" to "ProductType", "Value" to "iPhone17,1", "Error" to "Denied"))) {
            var failed = false; try { AppleDeviceInfo.modelResponse(fields) } catch (_: IOException) { failed = true }
            check(failed, "Uncorrelated or contradictory response rejected")
        }
        check(AppleDeviceInfo.modelResponse(mapOf("Request" to "GetValue", "Key" to "ProductType", "Error" to "GetProhibited")).status == AppleDeviceInfo.Status.WITHHELD, "Denied model is an explicit supported outcome")
        for (code in listOf("iPhone17,1", "iPad13,18", "AppleTV6,2", "AudioAccessory5,1", "iMac14,2")) {
            val report = AppleDeviceInfo.modelResponse(AppleDeviceInfo.parse(plist("Request" to "GetValue", "Key" to "ProductType", "Value" to code)))
            val evidence = Recog.Evidence().apply { offerAppleModel(report.model) }
            check(report.model == code && evidence.match() != null, "Mobile, TV, speaker and computer identifiers share catalogue")
            check(evidence.match()!!.input.contains("ProductType=$code") && evidence.match()!!.source.contains(AppleDeviceInfo.MODEL_SOURCE), "Protocol source is preserved")
            val copy = evidence.copy(); evidence.offerService("_airplay._tcp.", mapOf("model" to code.toByteArray()))
            check(evidence.match()?.code == code, "Agreeing AirPlay and direct response combine")
            evidence.offerService("_companion-link._tcp.", mapOf("rpmd" to "iPad999,1".toByteArray()))
            check(evidence.match() == null && copy.match()?.code == code, "Conflicts survive snapshots without corrupting old copy")
        }
        val unknown = Recog.Evidence().apply { offerAppleModel("iPad999,1") }
        check(unknown.match() == null && unknown.categories().isEmpty(), "Unknown model does not invent retail identity")
        check(Recog.Evidence().apply { observeReport("Manufacturer", "Synthetic Other Maker"); offerAppleModel("iPhone17,1") }.match() == null, "Reported manufacturer conflict suppresses interpretation")

        val timer = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
        try {
            for ((fault, expected) in listOf(
                SecurityException("Synthetic permission failure") to MetadataHttp.Reason.CONNECTION_FAILED,
                NoRouteToHostException("Synthetic routing failure") to MetadataHttp.Reason.ROUTING_FAILED)) {
                val socket = object : Socket() {
                    override fun connect(endpoint: SocketAddress, timeout: Int) { throw fault }
                }
                socket.use {
                    var failure: MetadataHttp.Failure? = null
                    try { AppleDeviceInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(2), timer) { true } }
                    catch (e: MetadataHttp.Failure) { failure = e }
                    check(failure?.cause === fault && failure?.reason == expected,
                        "Typed permission/routing cause survives for Android diagnostics")
                }
            }
            withServer({ peer ->
                check(read(peer.getInputStream())["Request"] == "QueryType", "Wire service query first")
                frame(service).forEach { peer.getOutputStream().write(it.toInt()); peer.getOutputStream().flush() }
                check(read(peer.getInputStream()) == mapOf("Request" to "GetValue", "Key" to "ProductType"), "Wire requests only product model")
                peer.getOutputStream().write(frame(plist("Request" to "GetValue", "Key" to "ProductType", "Value" to "iPhone17,1")))
            }) { port -> Socket().use { socket ->
                check(AppleDeviceInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime() + TimeUnit.SECONDS.toNanos(2), timer, port) { true }.model == "iPhone17,1", "Fragmented responses handled on same connection")
            } }
            withServer({ peer ->
                read(peer.getInputStream()); peer.getOutputStream().write(frame(plist("Request" to "QueryType", "Type" to "synthetic.other")))
                check(peer.getInputStream().read() == -1, "Other service never receives model request")
            }) { port -> Socket().use { check(AppleDeviceInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(2), timer, port) { true }.status == AppleDeviceInfo.Status.OTHER, "Open port alone cannot identify Apple") } }
            for (badFrame in listOf(byteArrayOf(0x7f, -1, -1, -1), byteArrayOf(-1,-1,-1,-1), byteArrayOf(0,0,0,0), byteArrayOf(0,0,0,20,1))) {
                withServer({ peer -> read(peer.getInputStream()); peer.getOutputStream().write(badFrame) }) { port ->
                    var failure: MetadataHttp.Failure? = null
                    Socket().use { try { AppleDeviceInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(2), timer, port) { true } } catch(e: MetadataHttp.Failure) { failure=e } }
                    check(failure != null, "Oversized, negative, empty and truncated frames rejected")
                }
            }
            withServer({ peer -> read(peer.getInputStream()); Thread.sleep(250) }) { port ->
                var failed = false; val start = System.nanoTime()
                Socket().use { try { AppleDeviceInfo.exchange(it, InetAddress.getLoopbackAddress(), start+TimeUnit.MILLISECONDS.toNanos(100), timer, port) { true } } catch (_: IOException) { failed=true } }
                check(failed && System.nanoTime()-start < TimeUnit.SECONDS.toNanos(1), "Whole exchange deadline bounds silent peer")
            }
            withServer({ peer ->
                read(peer.getInputStream()); peer.getOutputStream().write(frame(service))
                read(peer.getInputStream())
                peer.getOutputStream().write(frame(plist("Request" to "GetValue", "Key" to "ProductType", "Error" to "GetProhibited")))
            }) { port -> Socket().use { socket ->
                val diagnostics = DiagnosticEvidence()
                check(AppleDeviceInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(2), timer, port, diagnostics) { true }.status == AppleDeviceInfo.Status.WITHHELD,
                    "Service verification survives a denied model request")
                check(diagnostics.snapshot().any { it.field == "Error" && it.value == "GetProhibited" }, "Exact safe Apple refusal survives in diagnostics")
            } }
            val active = AtomicBoolean(true)
            Socket().use { socket ->
                withServer({ peer ->
                    read(peer.getInputStream())
                    // Discovery.cancel closes its tracked sockets while a response is pending.
                    active.set(false); socket.close()
                    check(peer.getInputStream().read() == -1, "Mid-response cancellation releases the connection")
                }) { port ->
                    var reason: MetadataHttp.Reason? = null
                    try { AppleDeviceInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(2), timer, port) { active.get() } }
                    catch (e: MetadataHttp.Failure) { reason = e.reason }
                    check(reason == MetadataHttp.Reason.CANCELLED, "Socket closure during a pending response is reported as cancellation")
                }
            }
            Socket().use { socket ->
                var cancelled = false
                try { AppleDeviceInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(1), timer) { false } }
                catch(e: MetadataHttp.Failure) { cancelled = e.reason == MetadataHttp.Reason.CANCELLED }
                check(cancelled && !socket.isConnected, "Cancellation prevents connecting")
            }
            for (payload in listOf<ByteArray?>(null, frame(plist("Request" to "GetValue", "Key" to "ProductType", "Value" to "invalid")), byteArrayOf(0, 0, 127, 127))) {
                withServer({ peer ->
                    read(peer.getInputStream()); peer.getOutputStream().write(frame(service))
                    read(peer.getInputStream()); if (payload != null) peer.getOutputStream().write(payload)
                }) { port -> Socket().use { socket ->
                    val r = AppleDeviceInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(2), timer, port) { true }
                    check(r.status == AppleDeviceInfo.Status.UNAVAILABLE && r.model.isEmpty() && r.failure != null,
                        "Verified Apple service survives missing, malformed or oversized model response with its failure")
                } }
            }
        } finally { timer.shutdownNow() }
        println("Apple device info checks passed: $checks")
    }
}
