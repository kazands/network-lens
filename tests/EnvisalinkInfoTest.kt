import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

object EnvisalinkInfoTest {
    private var checks = 0
    private fun check(ok: Boolean, message: String) { if (!ok) throw AssertionError(message); checks++ }
    private fun server(handler: (Socket) -> Unit, client: (Int) -> Unit) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 3000
            val pool = Executors.newSingleThreadExecutor()
            val task = pool.submit(Callable { listener.accept().use { it.soTimeout = 2000; handler(it) } })
            try { client(listener.localPort); task.get(4, TimeUnit.SECONDS) }
            finally { pool.shutdownNow() }
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        val valid = "5053CD\r\n".toByteArray()
        check(EnvisalinkInfo.matches(valid), "Documented login greeting including checksum and CRLF")
        check("%02X".format("5053".sumOf { it.code } and 255) == "CD", "Checksum agrees with vendor specification")
        for (bad in listOf("", "Login:", "5053", "5053CD", "5053CC\r\n", "5051CB\r\n", "5053CD\n", "x5053CD\r\n", "5053CD\r\nextra"))
            check(!EnvisalinkInfo.matches(bad.toByteArray()), "Reject ambiguous/malformed frame: ${bad.length}")
        val evidence = DeviceCatalog.Evidence().apply { offerEnvisalink() }
        check(evidence.match()?.product?.title == "Envisalink" && evidence.match()!!.product.familyOnly, "Family recognition does not invent EVL generation")
        check(evidence.categories().single().category == DeviceCatalog.Category.ALARM_INTERFACE, "Physical alarm-interface category")
        check(RecognitionCopy.explanation(evidence.match()!!).contains("exact model was not reported"), "Explanation distinguishes model from family")
        val copy = evidence.copy()
        evidence.offer("Model", "Google Home", "Synthetic model")
        check(evidence.match() == null && evidence.categories().isEmpty(), "Contradictory product cannot restore a different hardware category")
        check(copy.match() != null, "Snapshot remains independent")
        val combined = DeviceCatalog.Evidence().apply { include(copy); include(evidence) }
        check(combined.match() == null && combined.categories().isEmpty() && combined.hasEnvisalinkIdentity(), "Grouped contradiction retains protocol constraint")
        val other = DeviceCatalog.Evidence().apply { offerEnvisalink(); offer("Manufacturer", "Synthetic maker", "Synthetic response") }
        check(other.match() == null && other.categories().isEmpty(), "Conflicting manufacturer suppresses match")

        val timer = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
        try {
            server({ peer ->
                for (b in valid) { peer.getOutputStream().write(b.toInt()); peer.getOutputStream().flush(); Thread.sleep(5) }
                check(peer.getInputStream().read() == -1, "No application bytes sent, connection promptly closed")
            }) { port -> Socket().use { socket ->
                check(EnvisalinkInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(750), timer, port) { true } == EnvisalinkInfo.Report.IDENTIFIED, "Fragmented greeting accepted")
            } }
            for (data in listOf("Login:\r\n", "5053CC\r\n", "5051CB\r\n", "HTTP/1.1 200 OK\r\n")) {
                server({ peer -> peer.getOutputStream().write(data.toByteArray()); check(peer.getInputStream().read() == -1, "No authentication or retry for other service") }) { port ->
                    Socket().use { check(EnvisalinkInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(750), timer, port) { true } == EnvisalinkInfo.Report.OTHER, "Other service rejected") }
                }
            }
            server({ peer -> check(peer.getInputStream().read() == -1, "Silent peer released after read timeout") }) { port ->
                Socket().use { check(EnvisalinkInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(750), timer, port) { true } == EnvisalinkInfo.Report.NO_GREETING, "Busy or silent service supplies no identity") }
            }
            server({ peer -> check(peer.getInputStream().read() == -1, "Deadline closes stalled connection") }) { port ->
                var reason: MetadataHttp.Reason? = null
                Socket().use { try { EnvisalinkInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(80), timer, port) { true } }
                    catch (e: MetadataHttp.Failure) { reason = e.reason } }
                check(reason == MetadataHttp.Reason.DEADLINE, "Whole exchange deadline enforced")
            }
            val active = AtomicBoolean(true)
            Socket().use { socket ->
                server({ peer -> active.set(false); socket.close(); check(peer.getInputStream().read() == -1, "Cancellation releases connection") }) { port ->
                    var reason: MetadataHttp.Reason? = null
                    try { EnvisalinkInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(750), timer, port) { active.get() } }
                    catch (e: MetadataHttp.Failure) { reason = e.reason }
                    check(reason == MetadataHttp.Reason.CANCELLED, "Cancellation wins over socket failure")
                }
            }
            Socket().use { socket ->
                var reason: MetadataHttp.Reason? = null
                try { EnvisalinkInfo.exchange(socket, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(1), timer) { false } }
                catch (e: MetadataHttp.Failure) { reason = e.reason }
                check(reason == MetadataHttp.Reason.CANCELLED && !socket.isConnected, "Cancelled check never connects")
            }
            val refused = object : Socket() { override fun connect(endpoint: SocketAddress, timeout: Int) { throw ConnectException() } }
            refused.use { check(EnvisalinkInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(1), timer) { true } == EnvisalinkInfo.Report.UNAVAILABLE, "Closed port is normal absence, not identification") }
            val denied = object : Socket() { override fun connect(endpoint: SocketAddress, timeout: Int) { throw SecurityException("Synthetic denied") } }
            denied.use {
                var threw = false
                try { EnvisalinkInfo.exchange(it, InetAddress.getLoopbackAddress(), System.nanoTime()+TimeUnit.SECONDS.toNanos(1), timer) { true } } catch (_: SecurityException) { threw = true }
                check(threw, "Permission failure preserved for discovery health")
            }
            check(timer.queue.isEmpty(), "No deadline tasks leaked")
        } finally { timer.shutdownNow() }
        println("Envisalink checks passed: $checks")
    }
}
