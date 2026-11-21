package io.github.kazands.networklens

import java.io.FilterInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/** Caller registers and binds the socket. No redirects, authentication, crawling or application TLS bypass. */
internal object PublicIdentityReader {
    private val RANDOM=SecureRandom()
    private val SSH=Regex("SSH-(?:2\\.0|1\\.99)-([!-~]{1,160})(?: [ -~]{0,80})?")
    fun exchange(socket: Socket,peer: InetAddress,request: PublicIdentity.Request,deadline: Long,
                 timer: ScheduledExecutorService,active: ()->Boolean): PublicIdentity.Result {
        fun remaining(maximum: Int=600): Int {
            if(!active() || Thread.currentThread().isInterrupted) throw MetadataHttp.Failure(MetadataHttp.Reason.CANCELLED)
            val nanos=deadline-System.nanoTime();if(nanos<=0) throw MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE)
            return minOf(maximum,maxOf(1,nanos/1_000_000).toInt())
        }
        remaining()
        val closer=timer.schedule({try {socket.close()} catch(_:IOException) {}},maxOf(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS)
        var transport=socket;var stage=MetadataHttp.Reason.CONNECTION_FAILED
        try {
            socket.connect(InetSocketAddress(peer,request.port),remaining(400));socket.soTimeout=remaining()
            if(request.kind==PublicIdentity.Kind.TLS) {
                stage=MetadataHttp.Reason.TLS_FAILED
                return certificate(socket,peer,request.port,::remaining)
            }
            if(request.kind==PublicIdentity.Kind.HTTP && request.port in setOf(443,8443)) {
                stage=MetadataHttp.Reason.TLS_FAILED
                val tls=(SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket,peer.hostAddress!!.substringBefore('%'),request.port,false) as SSLSocket
                transport=tls;tls.sslParameters=tls.sslParameters.apply {endpointIdentificationAlgorithm="HTTPS"};tls.soTimeout=remaining();tls.startHandshake()
            }
            var wireBytes=0
            val raw=transport.getInputStream()
            val input=object: FilterInputStream(raw) {
                private fun check() {transport.soTimeout=remaining();if(wireBytes>=32768) throw IOException("Public response wire limit")}
                override fun read(): Int {check();val b=raw.read();if(b>=0) wireBytes++;return b}
                override fun read(b: ByteArray,off: Int,len: Int): Int {check();val n=raw.read(b,off,minOf(len,32768-wireBytes));if(n>0) wireBytes+=n;return n}
            }
            stage=MetadataHttp.Reason.WRITE_FAILED
            val output=transport.getOutputStream()
            when(request.kind) {
                PublicIdentity.Kind.HTTP -> {
                    val ip=peer.hostAddress!!.substringBefore('%');val authority=(if(':' in ip) "[$ip]" else ip)+":${request.port}"
                    output.write(PublicHttpInfo.request(authority));remaining();stage=MetadataHttp.Reason.READ_FAILED
                    val report=PublicHttpInfo.read(input);remaining()
                    return PublicIdentity.Result(report.outcome,report.fields + mapOf("HTTP response status" to report.status.toString(),
                        "HTTP body capture" to if(report.prefixLimited) "First 8192 bytes only; remainder not read" else "Selected fields only; raw response not retained"),report.software)
                }
                PublicIdentity.Kind.SSH -> {
                    // Servers normally identify themselves first. Send no client banner, auth or key exchange.
                    stage=MetadataHttp.Reason.READ_FAILED
                    repeat(8) {
                        val bytes=java.io.ByteArrayOutputStream()
                        while(true) {
                            val b=input.read();if(b<0) throw IOException("SSH greeting ended")
                            if(b==13) {if(input.read()!=10) throw IOException("Invalid SSH line");break}
                            if(b !in 32..126 || bytes.size()>=253) throw IOException("SSH greeting limit")
                            bytes.write(b)
                        }
                        val line=bytes.toString("US-ASCII")
                        if(line.startsWith("SSH-")) {
                            val version=SSH.matchEntire(line)?.groupValues?.get(1) ?: throw IOException("Unsupported SSH greeting")
                            remaining();return PublicIdentity.Result("SSH software advertised; hardware and operating system not inferred",mapOf("SSH software label" to version))
                        }
                    }
                    throw IOException("SSH greeting line limit")
                }
                PublicIdentity.Kind.LLMNR -> {
                    val id=RANDOM.nextInt(65536);val ip=peer.hostAddress!!.substringBefore('%')
                    output.write(LlmnrInfo.query(ip,id));remaining();stage=MetadataHttp.Reason.READ_FAILED
                    val host=LlmnrInfo.response(LlmnrInfo.read(input),ip,id);remaining()
                    return if(host==null) PublicIdentity.Result("No single validated LLMNR hostname")
                        else PublicIdentity.Result("Hostname returned directly by this endpoint; no product inferred",hostname=host)
                }
                else -> throw IOException("Unsupported public reader")
            }
        } catch(e:Exception) {
            if(e is MetadataHttp.Failure) throw e
            val reason=when {
                !active() || Thread.currentThread().isInterrupted -> MetadataHttp.Reason.CANCELLED
                System.nanoTime()>=deadline -> MetadataHttp.Reason.DEADLINE
                e is java.net.ConnectException -> MetadataHttp.Reason.CONNECTION_REFUSED
                e is java.net.NoRouteToHostException -> MetadataHttp.Reason.ROUTING_FAILED
                e is SocketTimeoutException -> if(stage==MetadataHttp.Reason.CONNECTION_FAILED) MetadataHttp.Reason.CONNECTION_TIMEOUT else MetadataHttp.Reason.READ_TIMEOUT
                else -> stage
            }
            throw MetadataHttp.Failure(reason,cause=e)
        } finally {closer.cancel(false);if(transport!==socket) try {transport.close()} catch(_:IOException) {}}
    }
    /** Observe a bounded presented chain, then reject it. Never finish TLS or send application data. */
    private fun certificate(socket: Socket,peer: InetAddress,port: Int,remaining: (Int)->Int): PublicIdentity.Result {
        var fields: Map<String,String>?=null
        val observer=object: X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>,authType: String) {throw CertificateException("Observation only")}
            override fun checkServerTrusted(chain: Array<X509Certificate>,authType: String) {
                if(chain.isNotEmpty() && chain.size<=8 && chain[0].encoded.size<=32768) fields=certificateFields(chain[0])
                throw CertificateException("Observation only")
            }
        }
        val context=SSLContext.getInstance("TLS");context.init(null,arrayOf(observer),null)
        val engine=context.createSSLEngine(peer.hostAddress!!.substringBefore('%'),port).apply {useClientMode=true}
        val incoming=ByteBuffer.allocate(65536);val outgoing=ByteBuffer.allocate(32768);val app=ByteBuffer.allocate(32768)
        var received=0;var sent=0
        try {
            engine.beginHandshake()
            repeat(192) {
                socket.soTimeout=remaining(600)
                when(engine.handshakeStatus) {
                    SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                        outgoing.clear();val result=engine.wrap(ByteBuffer.allocate(0),outgoing)
                        if(result.status!=SSLEngineResult.Status.OK) throw SSLException("TLS observation wrap limit")
                        if(outgoing.position()>65536-sent) throw SSLException("TLS observation output limit")
                        if(outgoing.position()>0) {socket.getOutputStream().write(outgoing.array(),0,outgoing.position());sent+=outgoing.position()}
                    }
                    SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                        incoming.flip();app.clear();val result=engine.unwrap(incoming,app);incoming.compact()
                        if(result.status==SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                            if(received>=65536 || !incoming.hasRemaining()) throw SSLException("TLS observation wire limit")
                            val n=socket.getInputStream().read(incoming.array(),incoming.position(),minOf(incoming.remaining(),65536-received,8192))
                            if(n<1) throw SSLException("TLS observation ended")
                            incoming.position(incoming.position()+n);received+=n
                        } else if(result.status!=SSLEngineResult.Status.OK) throw SSLException("TLS observation record limit")
                    }
                    SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                        var count=0
                        while(true) {val task=engine.delegatedTask ?: break;if(++count>16) throw SSLException("TLS task limit");task.run();remaining(600)}
                    }
                    else -> throw SSLException("TLS observation ended")
                }
            }
        } catch(_:SSLException) {
            fields?.let {remaining(600);return PublicIdentity.Result("Certificate presented; unverified; handshake stopped before application data",it)}
            throw MetadataHttp.Failure(MetadataHttp.Reason.TLS_FAILED)
        } finally {engine.closeOutbound()}
        throw MetadataHttp.Failure(MetadataHttp.Reason.TLS_FAILED)
    }
    internal fun certificateFields(cert: X509Certificate): Map<String,String> = buildMap {
        val selected=CertificateSubject.select(cert.subjectX500Principal.getName("RFC2253"))
        val notices=ArrayList<String>()
        if(selected.notice.isNotEmpty()) notices.add(selected.notice)
        selected.values["CN"]?.let {put("TLS common name (unverified)",it)}
        selected.values["O"]?.let {put("TLS organization (unverified)",it)}
        var retained=0
        try {for(name in cert.subjectAlternativeNames.orEmpty().take(32)) {
            if(retained>=8) break
            val type=name.getOrNull(0) as? Int ?: continue
            val value=name.getOrNull(1) as? String ?: continue
            if(type==2 || type==7) {retained++;put("TLS ${if(type==2) "DNS" else "IP"} name $retained (unverified)",SafeText.label(value,160))}
        }} catch(_:java.security.cert.CertificateParsingException) {notices.add("Alternative names could not be decoded")}
        if(notices.isNotEmpty()) put("TLS selected fields",notices.joinToString("; "))
        put("TLS capture policy","Selected CN/organization/DNS/IP names only; no serials, keys, raw certificates or application data")
    }
}
