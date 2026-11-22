package io.github.kazands.networklens.test

import io.github.kazands.networklens.*
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Emulator-only runner; these fixtures bind loopback and never contact the LAN. */
internal object AppleTransportChecks {
    fun run(check: (Boolean, String) -> Unit) {
        val timer = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
        val loopback = InetAddress.getByName("127.0.0.1")
        val service = "<plist version=\"1.0\"><dict><key>Request</key><string>QueryType</string><key>Type</key><string>com.apple.mobile.lockdown</string></dict></plist>".toByteArray()
        fun consume(socket: Socket) {
            val input = DataInputStream(socket.getInputStream())
            val size = input.readInt(); require(size in 1..8192)
            input.readFully(ByteArray(size))
        }
        try {
            for (fault in listOf("eof", "reset", "timeout", "model-eof")) {
                ServerSocket(0, 1, loopback).use { server ->
                    server.soTimeout = 2000
                    val workerError = AtomicReference<Throwable>()
                    val worker = thread {
                        try { server.accept().use { peer ->
                            peer.soTimeout = 2000; consume(peer)
                            when (fault) {
                                "reset" -> peer.setSoLinger(true, 0)
                                "timeout" -> Thread.sleep(800)
                                "model-eof" -> {
                                    DataOutputStream(peer.getOutputStream()).apply { writeInt(service.size); write(service); flush() }
                                    consume(peer)
                                }
                            }
                        } } catch (e: Throwable) { workerError.set(e) }
                    }
                    try {
                        val device = fixtureDevice()
                        val diagnostics = device.diagnostics
                        val attempt = if (fault == "model-eof") 2 else 1
                        var failure: MetadataHttp.Reason? = null
                        var report: AppleDeviceInfo.Report? = null
                        Socket().use { socket ->
                            try { report = AppleDeviceInfo.exchange(socket, loopback, System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                                timer, server.localPort, diagnostics, attempt) { true } }
                            catch (e: MetadataHttp.Failure) { failure = e.reason }
                        }
                        check(if (fault == "model-eof") report?.status == AppleDeviceInfo.Status.UNAVAILABLE
                            else failure == if (fault == "timeout") MetadataHttp.Reason.READ_TIMEOUT else MetadataHttp.Reason.READ_FAILED,
                            "Android $fault retains the existing service/failure decision")
                        val detail = diagnostics.snapshot().single { it.field == "Transport attempt $attempt" }.value
                        val expected = when (fault) { "reset" -> "Connection reset reported by socket"; "timeout" -> "Socket timed out"; else -> "Peer closed the response (EOF)" }
                        check(detail.contains(expected), "Android loopback $fault classified with a fixed label: $detail")
                        check(detail.contains("Apple service verified: ${fault == "model-eof"}"), "Android transport journal distinguishes QueryType from model failure")
                        check(diagnostics.snapshot().any { it.field == "Transport timing $attempt" && it.value.contains("response bytes 0") },
                            "Android empty response retains byte/timing diagnostics")
                        checkExport(device, attempt, expected, check)
                    } finally { worker.join(3000); check(!worker.isAlive, "Android transport fixture released its socket/thread") }
                    workerError.get()?.let { throw AssertionError("Synthetic Apple server failed", it) }
                }
            }
            val device = fixtureDevice()
            val opaqueError = object : Socket() {
                override fun connect(endpoint: SocketAddress, timeout: Int) {}
                override fun getOutputStream() = ByteArrayOutputStream()
                override fun getInputStream(): java.io.InputStream = throw SocketException("PRIVATE_EXCEPTION_TOKEN ${device.ip}")
            }
            opaqueError.use { socket ->
                var failure: MetadataHttp.Reason? = null
                try { AppleDeviceInfo.exchange(socket, loopback, System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                    timer, diagnostics = device.diagnostics) { true } }
                catch (e: MetadataHttp.Failure) { failure = e.reason }
                check(failure == MetadataHttp.Reason.READ_FAILED, "Unreviewed socket text keeps the ordinary read-failure decision")
            }
            checkExport(device, 1, TransportDiagnostic.Failure.SOCKET.text, check)
        } finally { timer.shutdownNow() }
    }

    private fun fixtureDevice() = Device().apply { ip = "192.0.2.44"; name = "Synthetic private room" }

    private fun checkExport(device: Device, attempt: Int, expected: String, check: (Boolean, String) -> Unit) {
        device.diagnostics.record("Synthetic unreviewed reader", "Transport attempt $attempt", "PRIVATE_SOURCE_TOKEN", "Unreviewed observation")
        device.diagnostics.record(AppleDeviceInfo.SOURCE, "Transport payload 1", "PRIVATE_FIELD_TOKEN", "Unreviewed observation")
        device.diagnostics.record(AppleDeviceInfo.SOURCE, "Transport attempt 3", "PRIVATE_ATTEMPT_TOKEN", "Unreviewed observation")
        val group = device.copy().troubleshootingGroup()
        val entries = device.diagnostics.snapshot().filter { it.source == AppleDeviceInfo.SOURCE &&
            it.field in setOf("Transport attempt $attempt", "Transport timing $attempt") }
        check(entries.size == 2, "Exchange produces both transport records before report export")
        for (includeNames in listOf(false, true)) {
            val text = TroubleshootingReport.create("", listOf(group), emptyList(), includeNames).text()
            check(entries.all { text.contains("${it.field}: ${it.value}") } && text.contains(expected),
                "Export preserves the actual transport outcome, verification state, bytes and timings with names=$includeNames")
            check(!text.contains("PRIVATE_EXCEPTION_TOKEN") && !text.contains("PRIVATE_SOURCE_TOKEN") &&
                !text.contains("PRIVATE_FIELD_TOKEN") && !text.contains("PRIVATE_ATTEMPT_TOKEN"),
                "Transport export never reveals arbitrary exception text or unreviewed source/field values with names=$includeNames")
            check(if (includeNames) text.contains("IP address: ${device.ip}") && text.contains("Network name: ${device.name}")
                else !text.contains(device.ip) && !text.contains(device.name),
                "Transport export still respects the names and addresses option with names=$includeNames")
        }
    }
}
