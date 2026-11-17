package io.github.kazands.networklens

import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** One explicit check or download, cancellable across redirects with a whole-operation deadline. */
internal class UpdateTransfer(private val seconds: Long,
    private val open: (String)->HttpsURLConnection = { URL(it).openConnection() as HttpsURLConnection }) : AutoCloseable {
    private val cancelled=AtomicBoolean()
    private val expired=AtomicBoolean()
    @Volatile private var connection: HttpsURLConnection?=null
    private val timer=Executors.newSingleThreadScheduledExecutor()
    private val timeout=timer.schedule({expired.set(true);connection?.disconnect()},seconds,TimeUnit.SECONDS)
    private fun check() {
        if(cancelled.get() || Thread.currentThread().isInterrupted) throw AppUpdates.Failure("Update cancelled.")
        if(expired.get()) throw AppUpdates.Failure("The update request timed out. Please try again.")
    }
    fun feed(): ByteArray {
        val out=ByteArrayOutputStream()
        copy(AppUpdates.FEED,true,2L*1024*1024,null,out) {_,_->}
        return out.toByteArray()
    }
    fun download(release: AppUpdates.Release, file: File, progress: (Long,Long)->Unit) {
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            FileOutputStream(file).use { stream ->
                val out=java.security.DigestOutputStream(stream,digest)
                copy(release.url,false,AppUpdates.MAX_APK,release.bytes,out,progress)
                out.flush();stream.fd.sync()
            }
            check()
            val hash=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
            if(hash!=release.sha256) throw AppUpdates.Failure("The downloaded update failed verification. Please download it again.")
        } catch(e:Exception) {file.delete();throw e}
    }
    private fun copy(initial: String, feed: Boolean, limit: Long, expected: Long?, out: OutputStream, progress:(Long,Long)->Unit) {
        var url=initial
        try {
            repeat(4) { redirect ->
                check()
                if(!AppUpdates.allowed(url,feed)) throw AppUpdates.Failure("The update download address could not be verified.")
                val c=open(url)
                connection=c
                try {
                    c.instanceFollowRedirects=false;c.useCaches=false;c.connectTimeout=8000;c.readTimeout=8000
                    c.setRequestProperty("User-Agent","Network-Lens-Update/1")
                    c.setRequestProperty("Accept",if(feed)"application/vnd.github+json" else "application/vnd.android.package-archive")
                    c.setRequestProperty("Accept-Encoding","identity")
                    check()
                    val status=c.responseCode;check()
                    if(status in setOf(301,302,303,307,308)) {
                        if(feed || redirect==3) throw AppUpdates.Failure("The update download address could not be verified.")
                        url=c.getHeaderField("Location") ?: throw AppUpdates.Failure("The update download address is missing.")
                        return@repeat
                    }
                    if(status==403 || status==429) throw AppUpdates.Failure("GitHub is temporarily limiting update requests. Please try again later.")
                    if(status!=200) throw AppUpdates.Failure("The update service is unavailable. Please try again later.")
                    if(c.contentEncoding!=null && !c.contentEncoding.equals("identity",true)) throw AppUpdates.Failure("Unsupported update response.")
                    val length=c.contentLengthLong
                    if(length>limit || expected!=null && length>=0 && length!=expected) throw AppUpdates.Failure("The update download size did not match.")
                    var count=0L;var previous=-1
                    c.inputStream.use {input ->
                        val buffer=ByteArray(16384)
                        while(true) {
                            check();val n=input.read(buffer);if(n<0)break
                            count+=n
                            if(count>limit || expected!=null && count>expected) throw AppUpdates.Failure("The update download is too large.")
                            out.write(buffer,0,n)
                            if(expected!=null) {val percent=(count*100/expected).toInt();if(percent!=previous){previous=percent;progress(count,expected)}}
                        }
                    }
                    check()
                    if(expected!=null && count!=expected || length>=0 && count!=length) throw AppUpdates.Failure("The update download was interrupted. Please try again.")
                    return
                } finally {c.disconnect();connection=null}
            }
        } catch(e:Exception) {
            check()
            if(e is AppUpdates.Failure) throw e
            throw AppUpdates.Failure("Couldn’t connect to GitHub. Check your internet connection and try again.")
        }
    }
    override fun close() {cancelled.set(true);connection?.disconnect();timeout.cancel(false);timer.shutdownNow()}
}
