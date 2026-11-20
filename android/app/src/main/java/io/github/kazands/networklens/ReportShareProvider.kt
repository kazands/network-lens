package io.github.kazands.networklens

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.IOException
import java.io.FileNotFoundException
import java.util.UUID

/** Explicit user-share only. No cache file: a short-lived read grant streams an immutable RAM snapshot. */
class ReportShareProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String { current(uri); return "text/plain" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val report = current(uri)
        val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).filter { it in setOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE) }.toTypedArray()
        return MatrixCursor(columns).apply { addRow(columns.map<String, Any> { if (it == OpenableColumns.DISPLAY_NAME) "Network-Lens-troubleshooting.txt" else report.size }.toTypedArray()) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val report = current(uri)
        val pipes = ParcelFileDescriptor.createPipe()
        try {
            val fd=pipes[1].fileDescriptor
            Os.fcntlInt(fd,OsConstants.F_SETFL,Os.fcntlInt(fd,OsConstants.F_GETFL,0) or OsConstants.O_NONBLOCK)
            synchronized(lock) {
                if (active.size >= 2 || lease !== report) throw FileNotFoundException("Report unavailable; share again")
                active.add(pipes[1])
            }
        } catch (_: Exception) {
            pipes.forEach { try {it.close()} catch (_: IOException) {} }
            throw FileNotFoundException("Report unavailable; share again")
        }
        val deadline=SystemClock.elapsedRealtime()+30000
        val writer=Thread({
            try {
                fun write(bytes: ByteArray) {
                    var offset=0
                    val poll=StructPollfd().apply {fd=pipes[1].fileDescriptor;events=OsConstants.POLLOUT.toShort()}
                    while(offset<bytes.size) {
                        if(SystemClock.elapsedRealtime()>=deadline) throw IOException("Report stream expired")
                        try {
                            // A nonblocking write is serialized with revocation, preventing
                            // an old writer from using a closed/recycled descriptor.
                            val count=synchronized(lock) {
                                if(lease !== report || pipes[1] !in active) throw IOException("Report retired")
                                Os.write(pipes[1].fileDescriptor,bytes,offset,minOf(8192,bytes.size-offset))
                            }
                            if(count<=0) throw IOException("Report stream unavailable")
                            offset+=count
                        } catch(e: ErrnoException) {
                            if(e.errno!=OsConstants.EAGAIN) throw e
                            // Closing a blocking pipe does not wake writers on every Android
                            // release. Poll bounded intervals instead, even if the UI is busy.
                            Os.poll(arrayOf(poll),minOf(100L,(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1L)).toInt())
                        }
                    }
                }
                report.pages.forEachIndexed { index,page ->
                    if(index>0) write("\n\n".toByteArray(Charsets.UTF_8))
                    write(page.toByteArray(Charsets.UTF_8))
                }
            } catch (_: IOException) { /* Recipient cancelled or grant expired. No scan data is logged. */ }
            catch (_: ErrnoException) { /* Pipe closed or recipient stopped reading. */ }
            finally { synchronized(lock) {active.remove(pipes[1]);try {pipes[1].close()} catch (_: IOException) {}} }
        }, "report-share")
        try {writer.start()} catch(e: RuntimeException) {
            synchronized(lock) {active.remove(pipes[1]);pipes.forEach {try {it.close()} catch (_: IOException) {}}}
            throw FileNotFoundException("Report unavailable; share again")
        }
        return pipes[0]
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read only")
    companion object {
        private data class Lease(val uri: Uri, val pages: List<String>, val expires: Long, val size: Long)
        private val lock = Any()
        private val handler = Handler(Looper.getMainLooper())
        private val active = mutableSetOf<ParcelFileDescriptor>()
        private var lease: Lease? = null
        private val expiry: Runnable = Runnable { clear() }
        internal fun publish(packageName: String, document: TroubleshootingReport.Document): Uri = synchronized(lock) {
            clear()
            val uri = Uri.parse("content://$packageName.reports/${UUID.randomUUID()}/report.txt")
            lease = Lease(uri, document.pages.toList(), SystemClock.elapsedRealtime() + 600000,
                document.pages.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } + (document.pages.size - 1).coerceAtLeast(0) * 2L)
            handler.postDelayed(expiry, 600000)
            uri
        }
        internal fun clear(): Unit = synchronized(lock) {
            handler.removeCallbacks(expiry); lease = null
            active.toList().forEach { try { it.close() } catch (_: Exception) {} }; active.clear()
        }
        private fun current(uri: Uri): Lease = synchronized(lock) {
            lease?.takeIf { it.uri == uri && SystemClock.elapsedRealtime() < it.expires }
                ?: throw FileNotFoundException("Report expired; share again from Network Lens")
        }
    }
}
