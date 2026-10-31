import io.github.kazands.networklens.*
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

/** Synthetic protocol fixtures and loopback exchanges; never scan a LAN. */
object LocalMetadataTest {
    private var checks = 0
    private fun check(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
    private fun utf(value: String) = value.toByteArray(StandardCharsets.UTF_8)
    private fun attrs(vararg pairs: Pair<String, String>) = pairs.associate { it.first to utf(it.second) }
    private fun service(type: String, vararg pairs: Pair<String, String>) = MdnsPacket.Service("Synthetic fixture", type).apply {
        host = "fixture.local"; port = 8080; attributes.putAll(attrs(*pairs))
    }
    private fun gen1Request(id: String, fromHostname: Boolean): LocalMetadata.Request? =
        LocalMetadata.request(MdnsPacket.Service(if (fromHostname) "Synthetic web service" else id, "_http._tcp.").apply {
            host = if (fromHostname) "$id.local" else "fixture.local"
            port = 8080
        })
    private fun ipp(model: String?, tag: Int = 0x41, status: Int = 0, extra: Boolean = false): ByteArray {
        val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
        out.writeShort(0x0101); out.writeShort(status); out.writeInt(0x4e4c0001); out.writeByte(1)
        fun field(kind: Int, name: String, value: ByteArray) {
            val n = utf(name); out.writeByte(kind); out.writeShort(n.size); out.write(n); out.writeShort(value.size); out.write(value)
        }
        field(0x47, "attributes-charset", utf("utf-8"))
        // An operation-group model is not printer metadata.
        field(0x41, "printer-make-and-model", utf("Wrong operation model"))
        out.writeByte(4)
        if (model != null) {
            val value = if (tag == 0x35) {
                val b = ByteArrayOutputStream(); val d = DataOutputStream(b)
                d.writeShort(2); d.write(utf("en")); d.writeShort(utf(model).size); d.write(utf(model)); b.toByteArray()
            } else utf(model)
            field(tag, "printer-make-and-model", value)
            if (extra) field(tag, "", value)
        }
        field(0x45, "printer-more-info", utf("http://127.0.0.1/private"))
        out.writeByte(3)
        return bytes.toByteArray()
    }
    private fun response(body: ByteArray): ByteArray = utf("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n\r\n") + body

    /** Read one HTTP request, including the IPP body, without relying on TCP packet boundaries. */
    private fun readRequest(socket: Socket): ByteArray {
        socket.soTimeout = 3000
        val input = socket.getInputStream(); val out = ByteArrayOutputStream()
        while (!String(out.toByteArray(), StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            val b = input.read(); if (b < 0 || out.size() > 4096) throw IOException("Fixture request")
            out.write(b)
        }
        val header = String(out.toByteArray(), StandardCharsets.US_ASCII)
        val length = Regex("Content-Length: ([0-9]+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
        repeat(length) { val b = input.read(); if (b < 0) throw EOFException(); out.write(b) }
        return out.toByteArray()
    }
    private fun exchange(kind: LocalMetadata.Kind, reply: ByteArray, inspect: (ByteArray) -> Unit = {}): LocalMetadata.Report? {
        val failure = AtomicReference<Throwable?>()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val thread = Thread {
                try { server.accept().use { socket -> inspect(readRequest(socket)); socket.getOutputStream().write(reply) } }
                catch (e: Throwable) { failure.set(e) }
            }.apply { isDaemon = true; start() }
            val timer = ScheduledThreadPoolExecutor(1)
            try {
                return Socket().use { socket ->
                    MetadataHttp.exchange(socket, InetAddress.getLoopbackAddress(), LocalMetadata.Request(kind, server.localPort,
                        if (kind == LocalMetadata.Kind.IPP) "/ipp/print" else "/metadata"),
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(2), timer) { true }
                }
            } finally { timer.shutdownNow(); thread.join(3000); check(!thread.isAlive, "Loopback fixture finishes"); failure.get()?.let { throw it } }
        }
    }

    private fun diagnostics() {
        val source = LocalMetadata.Kind.SPOTIFY.source
        val health = DiscoveryHealth().apply { workStarted(source) }
        val timer = ScheduledThreadPoolExecutor(1)
        val secret = "fixture-secret http://198.18.0.99/private"
        val request = LocalMetadata.Request(LocalMetadata.Kind.SPOTIFY, 8080, "/info")
        fun failure(socket: Socket, reason: MetadataHttp.Reason, active: Boolean = true) {
            socket.use {
                try {
                    MetadataHttp.exchange(it, InetAddress.getLoopbackAddress(), request,
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(2), timer) { active }
                    throw AssertionError("Expected diagnostic failure")
                } catch (e: MetadataHttp.Failure) {
                    check(e.reason == reason, "Transport failure retains its actual phase: $reason")
                    check(!e.message.orEmpty().contains(secret), "Diagnostic exception message contains no peer text")
                    MetadataHttp.recordFailure(health, source, e, false)
                }
            }
        }
        // Deterministic socket faults identify phases without relying on external network timing.
        fun socket(connect: Exception? = null, write: Boolean = false, read: Exception? = null): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) { connect?.let { throw it } }
            override fun getOutputStream(): OutputStream = object : OutputStream() {
                override fun write(value: Int) { if (write) throw IOException(secret) }
            }
            override fun getInputStream(): InputStream { throw read ?: IOException(secret) }
        }
        try {
            failure(socket(connect = SocketTimeoutException(secret)), MetadataHttp.Reason.CONNECTION_TIMEOUT)
            failure(socket(connect = ConnectException(secret)), MetadataHttp.Reason.CONNECTION_FAILED)
            failure(socket(write = true), MetadataHttp.Reason.WRITE_FAILED)
            failure(socket(read = SocketTimeoutException(secret)), MetadataHttp.Reason.READ_TIMEOUT)
            failure(socket(read = SocketException(secret)), MetadataHttp.Reason.READ_FAILED)
            failure(socket(connect = SecurityException(secret)), MetadataHttp.Reason.CONNECTION_FAILED)
            val cancelledBefore = health.sources()
            failure(socket(), MetadataHttp.Reason.CANCELLED, false)
            check(health.sources() == cancelledBefore, "Cancellation adds neither failure counters nor failure reasons")
            for (code in listOf(302, 401, 403, 404, 500, 503)) {
                try { exchange(LocalMetadata.Kind.SPOTIFY, utf("HTTP/1.1 $code $secret\r\nContent-Length: 0\r\n\r\n")); throw AssertionError("HTTP error accepted") }
                catch (e: MetadataHttp.Failure) {
                    check(e.reason == MetadataHttp.Reason.HTTP_STATUS && e.httpStatus == code, "Keep numeric HTTP status without following redirects or guessing auth")
                    check(!e.message.orEmpty().contains(secret), "HTTP reason phrase is not retained in diagnostic text")
                    MetadataHttp.recordFailure(health, source, e, false)
                }
            }
            for (reply in listOf("NOT-HTTP $secret\r\n", "HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nx", "HTTP/1.1 200 OK\r\nContent-Length: 70000\r\n\r\n")) {
                try { exchange(LocalMetadata.Kind.SPOTIFY, utf(reply)); throw AssertionError("Malformed HTTP accepted") }
                catch (e: MetadataHttp.Failure) {
                    check(e.reason == MetadataHttp.Reason.INVALID_HTTP, "Distinguish malformed, truncated or oversized HTTP from transport failure")
                    MetadataHttp.recordFailure(health, source, e, false)
                }
            }
            for (body in listOf("not JSON $secret", "{\"status\":102,\"spotifyError\":5}", "{}")) {
                try { exchange(LocalMetadata.Kind.SPOTIFY, response(utf(body))); throw AssertionError("Invalid metadata accepted") }
                catch (e: MetadataHttp.Failure) {
                    check(e.reason == MetadataHttp.Reason.INVALID_METADATA, "Separate unusable protocol metadata from HTTP response errors")
                    MetadataHttp.recordFailure(health, source, e, false)
                }
            }
            MetadataHttp.recordFailure(health, source, MetadataHttp.Failure(MetadataHttp.Reason.TLS_FAILED), false)
            MetadataHttp.recordFailure(health, source, IOException(secret), false)
            health.workFinished(source)
            val state = health.sources().getValue(source)
            for (label in listOf("connection timeouts 1", "connection failures 2", "request write failures 1", "read timeouts 1", "response read failures 1", "HTTP errors 6", "invalid HTTP responses 3", "invalid metadata responses 3", "TLS failures 1", "other request failures 1")) {
                check(state.contains(label), "All finite diagnostic counters survive the ordinary six-counter limit: $label")
            }
            check(state.startsWith("Failed") && health.summary().isNotEmpty(), "Failure diagnostics keep the scan Partial after work finishes")
            check(!state.contains("fixture-secret") && !state.contains("198.18.") && !state.contains("/private"), "Scan diagnostics retain no response text or peer identifiers")
            val deadlineHealth = DiscoveryHealth().apply { workStarted(source) }
            MetadataHttp.recordFailure(deadlineHealth, source, MetadataHttp.Failure(MetadataHttp.Reason.CONNECTION_FAILED), true)
            deadlineHealth.workFinished(source)
            check(deadlineHealth.sources().getValue(source).startsWith("Truncated") && deadlineHealth.sources().getValue(source).contains("metadata deadlines 1"), "Overall deadline takes precedence and remains incomplete")
            MetadataHttp.recordFailure(deadlineHealth, source, MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED), true)
            check(deadlineHealth.sources().getValue(source).contains("metadata deadlines 2"), "Stopping enrichment at the deadline is not mistaken for user cancellation")
            check(DiscoveryHealth().sources().isEmpty(), "A fresh scan inherits no diagnostic counters")
        } finally { timer.shutdownNow() }
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val received = CountDownLatch(1)
            val active = java.util.concurrent.atomic.AtomicBoolean(true)
            val result = AtomicReference<Throwable?>()
            val peer = Thread {
                try { server.accept().use { socket -> readRequest(socket); received.countDown(); Thread.sleep(2000) } }
                catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            val cancellationTimer = ScheduledThreadPoolExecutor(1)
            val socket = Socket()
            val client = Thread {
                try { MetadataHttp.exchange(socket, InetAddress.getLoopbackAddress(), request.copy(port = server.localPort),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(2), cancellationTimer) { active.get() } }
                catch (e: Throwable) { result.set(e) }
                finally { socket.close() }
            }.apply { isDaemon = true; start() }
            try {
                check(received.await(1, TimeUnit.SECONDS), "Cancellation fixture receives the metadata request before cancellation")
                active.set(false); socket.close(); client.join(1500)
                check(!client.isAlive && (result.get() as? MetadataHttp.Failure)?.reason == MetadataHttp.Reason.CANCELLED,
                    "Closing an in-flight cancelled socket does not invent a network failure")
            } finally { active.set(false); socket.close(); cancellationTimer.shutdownNow(); client.interrupt(); peer.interrupt(); client.join(1500); peer.join(1500) }
        }
    }

    private fun mdnsTraffic() {
        fun packet(ttl: Int = 120): ByteArray {
            val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
            out.writeShort(0); out.writeShort(0x8400); out.writeShort(0); out.writeShort(1); out.writeInt(0)
            out.writeByte(7); out.write(utf("fixture")); out.writeByte(5); out.write(utf("local")); out.writeByte(0)
            out.writeShort(1); out.writeShort(1); out.writeInt(ttl); out.writeShort(4); out.write(byteArrayOf(198.toByte(), 18, 0, 2))
            return bytes.toByteArray()
        }
        val health = DiscoveryHealth().apply { begin(MdnsTraffic.SOURCE) }
        val v4 = MdnsTraffic(health, false); val v6 = MdnsTraffic(health, true)
        val live = packet(); val empty = byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val parsed = v4.receive(live, live.size, 5353, true)!!
        check(parsed.freshResponse && parsed.addresses == MdnsPacket.parse(live, live.size).addresses, "Diagnostics preserve current-response parsing and address evidence")
        val goodbye = packet(0)
        check(v4.receive(goodbye, goodbye.size, 5353, true)?.freshResponse == false, "A parsed goodbye remains insufficient for presence")
        check(v4.receive(empty, empty.size, 5353, true)?.parsedResponse == true, "Empty responses are counted without asserting presence")
        val query = MdnsPacket.query(listOf("_http._tcp.local"))
        check(v4.receive(query, query.size, 5353, true) == null, "Non-response traffic does not reach presence processing")
        check(v4.receive(live, live.size, 53, true) == null && v4.receive(live, live.size, 5353, false) == null, "Wrong ports and ineligible senders remain ignored")
        val malformed = live.copyOf(live.size - 1)
        check(v4.receive(malformed, malformed.size, 5353, true)?.freshResponse == false, "Malformed replies cannot acquire live evidence")
        check(v4.receive(ByteArray(MdnsPacket.MAX_PACKET + 1), MdnsPacket.MAX_PACKET + 1, 5353, true) == null, "Oversized packets remain bounded and separately counted")
        v6.receive(empty, empty.size, 5353, true)
        val counts = health.sources().getValue(MdnsTraffic.SOURCE)
        for (label in listOf("packets received 9", "IPv4 packets 8", "IPv6 packets 1", "ignored source packets 2", "oversized packets 1", "non-response packets 1", "rejected response packets 1", "responses without live details 3", "live-record responses 1")) {
            check(counts.contains(label), "Aggregate mDNS classification is exact: $label")
        }
        check(!counts.contains("fixture") && !counts.contains("198.18."), "mDNS accounting exports only categories and counts")
        val budgetHealth = DiscoveryHealth().apply { begin(MdnsTraffic.SOURCE) }
        val budget = MdnsTraffic(budgetHealth, false)
        repeat(1024) { check(budget.receive(empty, empty.size, 5353, true) != null && !budget.limited, "All bounded response-decode slots remain available") }
        check(budget.receive(live, live.size, 5353, true) == null && budget.limited, "The 1,025th response stops the bounded decode window")
        val terminal = budgetHealth.sources()
        check(terminal.getValue(MdnsTraffic.SOURCE).startsWith("Truncated") && terminal.getValue(MdnsTraffic.SOURCE).contains("packets received 1025"), "The budget-stopping datagram is counted and remains Partial")
        budget.receive(live, live.size, 5353, true)
        check(terminal == budgetHealth.sources(), "Stopped receive accounting retains no later packets")
        val ignoredBudget = MdnsTraffic(DiscoveryHealth(), false)
        repeat(1025) { ignoredBudget.receive(live, live.size, 1, false) }
        check(!ignoredBudget.limited, "Cheap discarded traffic does not consume the response-processing budget")
        repeat(MdnsTraffic.MAX_DATAGRAMS + 1 - 1025) { ignoredBudget.receive(live, live.size, 1, false) }
        check(ignoredBudget.limited, "Discarded traffic still consumes the independent raw receive budget")
        val mixed = MdnsTraffic(DiscoveryHealth(), false)
        repeat(900) { mixed.receive(query, query.size, 5353, true) }
        repeat(500) { check(mixed.receive(empty, empty.size, 5353, true) != null, "Busy query traffic leaves bounded capacity for responses") }
        check(!mixed.limited, "Mixed receive traffic no longer hits the old combined packet cap")
        val byteHealth = DiscoveryHealth()
        val byteBudget = MdnsTraffic(byteHealth, false)
        val oversized = ByteArray(MdnsPacket.MAX_PACKET + 1)
        repeat(1024) { byteBudget.receive(oversized, oversized.size, 5353, true) }
        check(byteBudget.limited && byteHealth.sources().getValue(MdnsTraffic.SOURCE).contains("8 MiB receive byte limit"), "Large discarded traffic remains independently byte bounded")
        check(MdnsTraffic(DiscoveryHealth(), false).receive(live, live.size, 5353, true)?.freshResponse == true, "A fresh receive window has no inherited budget or packet history")
    }

    @JvmStatic fun main(args: Array<String>) {
        diagnostics()
        mdnsTraffic()
        val thread = LocalMetadata.advertisement("_meshcop._udp.", attrs("MN" to "Fixture Hub 2", "vn" to "Fixture Devices", "nn" to "Do not retain network"))
        check(thread.hints == mapOf("Model" to "Fixture Hub 2", "Manufacturer" to "Fixture Devices"), "Thread model/vendor use dedicated case-insensitive keys only")
        check(LocalMetadata.advertisement("_http._tcp.", attrs("mn" to "Not Thread", "vn" to "Wrong vendor")).hints.isEmpty(), "Thread keys do not apply to unrelated services")
        val esp = LocalMetadata.advertisement("_esphomelib._tcp.", attrs("board" to "fixture_board", "platform" to "ESP32", "version" to "2026.10.0", "friendly_name" to "Fixture sensor", "network" to "Do not retain network"))
        check(esp.hints["Name"] == "Fixture sensor" && esp.hints["Firmware"] == "2026.10.0", "ESPHome reports friendly name and firmware")
        check("Model" !in esp.hints && "Manufacturer" !in esp.hints && esp.details.size == 2, "ESPHome board/platform remain source-labelled details")
        check(LocalServices.INITIAL.containsAll(listOf("_shelly._tcp.", "_esphomelib._tcp.", "_wled._tcp.")), "Direct queries include targeted firmware services")
        check(LocalMetadata.request(service("_esphomelib._tcp.")) == null, "ESPHome API port is never mistaken for an HTTP endpoint")
        val shelly = LocalMetadata.request(service("_shelly._tcp."))!!
        check(shelly.path == "/shelly" && shelly.port == 8080, "Shelly uses fixed endpoint on advertised port")
        check(LocalMetadata.request(service("_http._tcp.")) == null, "An ordinary web service gets no speculative vendor query")
        check(LocalMetadata.request(service("_http._tcp.").apply { host = "shelly1-001122334455.local" })?.kind == LocalMetadata.Kind.SHELLY, "Recognize Gen1 Shelly host form")
        check(LocalMetadata.request(service("_http._tcp.").apply { host = "shelly1-001122334455.attacker.local" })?.kind == LocalMetadata.Kind.SHELLY, "Only same-peer target is used regardless of claimed hostname")
        check(LocalMetadata.request(service("_http._tcp.").apply { host = "not-shelly1-001122334455.local" }) == null, "Gen1 marker is anchored")
        for (model in listOf("shelly1", "shellyplug-s")) {
            for (suffix in listOf("a1b2c3", "001122a1b2c3")) {
                val id = "$model-$suffix"
                for (form in listOf(id, id.uppercase(java.util.Locale.ROOT), "ShElLy" + id.removePrefix("shelly"))) {
                    for (fromHostname in listOf(false, true)) {
                        val plan = gen1Request(form, fromHostname)
                        check(plan == LocalMetadata.Request(LocalMetadata.Kind.SHELLY, 8080, "/shelly"),
                            "Short/full Gen1 IDs select only the fixed metadata endpoint via hostname or instance")
                    }
                }
            }
        }
        for (invalid in listOf(
            "shelly1-", "shelly1-a1b2c", "shelly1-a1b2c3d", "shelly1-11223344",
            "shelly1-112233445", "shelly1-1122334455", "shelly1-001122a1b2c",
            "shelly1-0001122a1b2c3", "shelly1-001122334455667788", "shelly1-a1b2cg",
            "shelly1-001122a1b2cg", "not-shelly1-a1b2c3", "device-a1b2c3", "shelly1-a1b2c3\r\nX: bad"
        )) for (fromHostname in listOf(false, true)) {
            check(gen1Request(invalid, fromHostname) == null, "Reject unrelated prefixes and IDs outside exactly six or twelve hex digits")
        }
        check(LocalMetadata.request(service("_wled._tcp."))?.path == "/json/info", "WLED uses information endpoint only")
        val spotify = LocalMetadata.request(service("_spotify-connect._tcp.", "CPath" to "/spotifyzc"))!!
        check(spotify.path == "/spotifyzc", "Spotify follows its advertised local path")
        check(LocalMetadata.request(service("_spotify-connect._tcp.")) == null, "Missing Spotify path remains unavailable")
        check(LocalMetadata.request(service("_spotify-connect._tcp.", "CPath" to "/one", "cpath" to "/two")) == null, "Reject ambiguous TXT path keys")
        for (bad in listOf("http://example.com/info", "//example.com/info", "/info?action=resetUsers", "/info#x", "/../reboot", "/%2e%2e/reboot", "/info\r\nX: bad", "/info\\path", "/%2felsewhere", "/%250d", "/%00", "/%xx", "/%3faction=addUser", "/" + "a".repeat(512))) {
            check(LocalMetadata.safePath(bad) == null, "Reject unsafe local path: $bad")
            check(LocalMetadata.request(service("_spotify-connect._tcp.", "CPath" to bad)) == null, "Unsafe advertisement never produces a Spotify request")
        }
        for (badPort in listOf(0, -1, 65536)) check(LocalMetadata.request(service("_shelly._tcp.").apply { port = badPort }) == null, "Reject invalid advertised port")
        val printing = LocalMetadata.request(service("_ipp._tcp.", "rp" to "ipp/print"))!!
        val securePrinting = LocalMetadata.request(service("_ipps._tcp.", "rp" to "ipp/print"))!!
        check(printing.path == "/ipp/print" && securePrinting.secure, "IPP resource path and IPPS transport are preserved")
        check(LocalMetadata.request(service("_ipp._tcp.")) == null, "Missing IPP resource path is not guessed")
        val ipv6 = InetAddress.getByName("fd00::20")
        val spotifyRequest = String(spotify.bytes(ipv6), StandardCharsets.US_ASCII)
        check(spotifyRequest.startsWith("GET /spotifyzc?action=getInfo HTTP/1.1\r\nHost: ["), "Spotify only generates getInfo and brackets IPv6")
        check(!spotifyRequest.contains("addUser") && !spotifyRequest.contains("Authorization"), "No account or authentication action is generated")
        val ippRequest = printing.bytes(InetAddress.getByName("192.0.2.20"))
        val ippText = String(ippRequest, StandardCharsets.US_ASCII)
        val bodyStart = ippText.indexOf("\r\n\r\n") + 4
        check(ippText.startsWith("POST /ipp/print HTTP/1.1") && ippRequest[bodyStart + 2].toInt() == 0 && ippRequest[bodyStart + 3].toInt() == 11, "Printer request is Get-Printer-Attributes, never a print job")
        check(ippText.contains("printer-make-and-model") && !ippText.contains("requesting-user-name"), "Request only model metadata without personal user names")

        val gen1 = LocalMetadata.parse(LocalMetadata.Kind.SHELLY, utf("""{"type":"SH-fixture","mac":"001122334455","fw":"1.2.3","auth":true,"cloud_key":"discard"}"""))!!
        check(gen1.hints["Model"] == "SH-fixture" && gen1.mac == "00:11:22:33:44:55" && gen1.hints["Firmware"] == "1.2.3", "Parse Gen1 Shelly type, explicit MAC and firmware")
        val gen2 = LocalMetadata.parse(LocalMetadata.Kind.SHELLY, utf("""{"model":"S-fixture","mac":"001122334466","ver":"2.0","id":"discard","ident":{"model":"Wrong nested model"}}"""))!!
        check(gen2.hints["Model"] == "S-fixture" && "Manufacturer" !in gen2.hints && gen2.details.isEmpty(), "Gen2 parses only allowed root fields without guessing manufacturer")
        check(LocalMetadata.parse(LocalMetadata.Kind.SHELLY, utf("""{"error":"unauthorized"}""")) == null, "An error object is not Shelly model information")
        val wled = LocalMetadata.parse(LocalMetadata.Kind.WLED, utf("""{"name":"Fixture light","ver":"0.15.0","arch":"esp32","mac":"001122334477","brand":"WLED","product":"FOSS","wifi":{"bssid":"001122334488"}}"""))!!
        check(wled.mac == "00:11:22:33:44:77" && "Model" !in wled.hints && "Manufacturer" !in wled.hints, "WLED ignores firmware brand/product and AP BSSID")
        check(wled.details["WLED platform (reported)"] == "esp32", "WLED architecture is a platform detail")
        val spotifyBody = utf("""{"status":101,"spotifyError":0,"brandDisplayName":"Fixture Audio","modelDisplayName":"Fixture Speaker","remoteName":"Fixture room","deviceID":"discard","publicKey":"discard","activeUser":"discard","extra":{"modelDisplayName":"Wrong nested model"}}""")
        val audio = LocalMetadata.parse(LocalMetadata.Kind.SPOTIFY, spotifyBody)!!
        check(audio.hints["Model"] == "Fixture Speaker" && audio.hints["Manufacturer"] == "Fixture Audio" && audio.mac.isEmpty() && audio.details.isEmpty(), "Spotify retains reported brand/model/name without unrelated IDs or account data")
        for (bad in listOf("""{"status":102,"spotifyError":0,"modelDisplayName":"Wrong"}""", """{"status":"101","spotifyError":0,"modelDisplayName":"Wrong"}""", """{"status":101,"spotifyError":4,"modelDisplayName":"Wrong"}""", """{"status":101,"spotifyError":0,"modelDisplayName":"one","modelDisplayName":"two"}""")) {
            check(LocalMetadata.parse(LocalMetadata.Kind.SPOTIFY, utf(bad)) == null, "Spotify requires an unambiguous successful response")
        }
        check(LocalMetadata.parse(LocalMetadata.Kind.SPOTIFY, utf("""{"status":101,"spotifyError":0,"remoteName":"Fixture player"}"""))!!.hints["Model"].isNullOrEmpty(), "Optional model remains unknown")
        check(LocalMetadata.parse(LocalMetadata.Kind.WLED, byteArrayOf(0xc3.toByte(), 40)) == null, "Reject malformed UTF-8 JSON")
        check(LocalMetadata.parse(LocalMetadata.Kind.WLED, ByteArray(32769)) == null, "Bound JSON body before parsing")
        check(LocalMetadata.advertisement("_meshcop._udp.", attrs("mn" to "one", "MN" to "two")).hints["Model"] == null, "Ambiguous Thread model fields stay unknown")

        val printerBody = ipp("Fixture Printer 500")
        check(IppInfo.parse(printerBody) == "Fixture Printer 500", "Read printer-group model and ignore returned URIs")
        check(IppInfo.parse(ipp("Fixture imprimante", 0x35)) == "Fixture imprimante", "Read IPP text with language")
        check(IppInfo.parse(ipp(null)) == "", "Valid IPP omission stays unknown")
        check(IppInfo.parse(ipp("Wrong", status = 0x0401)) == null, "Reject IPP authentication/error status")
        check(IppInfo.parse(ipp("Wrong", extra = true)) == null, "Reject ambiguous multiple model values")
        check(IppInfo.parse(ipp("Wrong", tag = 0x44)) == null, "Reject wrong model syntax")
        check(IppInfo.parse(printerBody.clone().apply { this[7] = 2 }) == null, "Correlate IPP request ID")
        check(IppInfo.parse(printerBody + byteArrayOf(0)) == null, "Reject trailing IPP data")
        for (n in printerBody.indices) check(IppInfo.parse(printerBody.copyOf(n)) == null, "Reject truncated IPP response")

        check(exchange(LocalMetadata.Kind.SPOTIFY, response(spotifyBody))?.hints?.get("Model") == "Fixture Speaker", "Read Spotify through real loopback HTTP transport")
        check(exchange(LocalMetadata.Kind.IPP, response(printerBody)) { request ->
            check(String(request, StandardCharsets.US_ASCII).contains("printer-make-and-model"), "Loopback printer receives model query")
        }?.hints?.get("Model") == "Fixture Printer 500", "Binary IPP response survives HTTP framing")
        val chunked = utf("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n${spotifyBody.size.toString(16)}\r\n") + spotifyBody + utf("\r\n0\r\n\r\n")
        check(exchange(LocalMetadata.Kind.SPOTIFY, chunked)?.hints?.get("Model") == "Fixture Speaker", "Chunked metadata response is supported")
        for (reply in listOf("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1/private\r\n\r\n", "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n", "HTTP/1.1 200 OK\r\nContent-Length: 70000\r\n\r\n", "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\nx")) {
            try { exchange(LocalMetadata.Kind.SPOTIFY, utf(reply)); throw AssertionError("Unsafe HTTP reply accepted") }
            catch (_: IOException) { check(true, "Redirect/auth/oversized/ambiguous metadata response is unavailable") }
        }
        // A slow peer cannot keep an exchange alive by staying inside individual read timeouts.
        for (deadlineMs in listOf(180L, 2000L)) ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val started = CountDownLatch(1)
            val peer = Thread {
                try { server.accept().use { socket -> readRequest(socket); started.countDown(); Thread.sleep(1000) } }
                catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            val timer = ScheduledThreadPoolExecutor(1)
            val began = System.nanoTime()
            try {
                Socket().use { socket -> MetadataHttp.exchange(socket, InetAddress.getLoopbackAddress(), LocalMetadata.Request(LocalMetadata.Kind.WLED, server.localPort, "/json/info"), began + TimeUnit.MILLISECONDS.toNanos(deadlineMs), timer) { true } }
                throw AssertionError("Deadline not enforced")
            } catch (e: MetadataHttp.Failure) {
                check(e.reason == if (deadlineMs == 180L) MetadataHttp.Reason.DEADLINE else MetadataHttp.Reason.READ_TIMEOUT,
                    "Real stalled transport distinguishes deadline $deadlineMs ms from ${e.reason}")
                check(started.await(1, TimeUnit.SECONDS) && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 1500, "A stalled peer remains bounded")
            } finally { timer.shutdownNow(); peer.interrupt(); peer.join(1500) }
        }
        val timer = ScheduledThreadPoolExecutor(1)
        try {
            Socket().use { socket ->
                try { MetadataHttp.exchange(socket, InetAddress.getLoopbackAddress(), shelly, System.nanoTime() + TimeUnit.SECONDS.toNanos(2), timer) { false }; throw AssertionError("Cancelled work connected") }
                catch (_: IOException) { check(!socket.isConnected, "Cancelled metadata opens no connection") }
            }
        } finally { timer.shutdownNow() }
        // IPPS must attempt TLS on the advertised endpoint, without falling back to HTTP.
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val tlsBytes = AtomicReference<ByteArray>(); val extraConnection = AtomicReference(false)
            val peer = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 2000
                        val hello = ByteArray(3); DataInputStream(socket.getInputStream()).readFully(hello); tlsBytes.set(hello)
                        socket.getOutputStream().write(utf("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"))
                    }
                    server.soTimeout = 300
                    try { server.accept().use { extraConnection.set(true) } } catch (_: SocketTimeoutException) {}
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            val tlsTimer = ScheduledThreadPoolExecutor(1)
            try {
                Socket().use { socket -> MetadataHttp.exchange(socket, InetAddress.getLoopbackAddress(), securePrinting.copy(port = server.localPort), System.nanoTime() + TimeUnit.SECONDS.toNanos(2), tlsTimer) { true } }
                throw AssertionError("IPPS accepted a plaintext peer")
            } catch (e: MetadataHttp.Failure) {
                peer.join(3000)
                check(e.reason == MetadataHttp.Reason.TLS_FAILED, "Secure endpoint failure is identified as TLS without a guessed authentication cause")
                check(tlsBytes.get()?.first()?.toInt() == 22 && !extraConnection.get(), "IPPS uses TLS and never retries a failed secure exchange in plaintext")
            } finally { tlsTimer.shutdownNow(); peer.interrupt(); peer.join(1000) }
        }
        val random = java.util.Random(41)
        repeat(2000) { val fuzz = ByteArray(random.nextInt(1024)); random.nextBytes(fuzz); IppInfo.parse(fuzz); LocalMetadata.parse(LocalMetadata.Kind.WLED, fuzz) }
        check(true, "Malformed binary/JSON fixtures do not crash or hang")
        println("PASS · $checks local metadata protocol, privacy, path and bounded transport checks")
    }
}
