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
        synchronized(lock) {
            if (active.size >= 2 || lease !== report) { pipes.forEach { it.close() }; throw FileNotFoundException("Report unavailable; share again") }
            active.add(pipes[1])
        }
        val timeout = Runnable { try { pipes[1].close() } catch (_: Exception) {} }
        handler.postDelayed(timeout, 30000)
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { output ->
                    report.pages.forEachIndexed { index, page ->
                        if (index > 0) output.write("\n\n".toByteArray(Charsets.UTF_8))
                        output.write(page.toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (_: java.io.IOException) { /* Recipient cancelled or grant expired. No scan data is logged. */ }
            finally { handler.removeCallbacks(timeout); synchronized(lock) { active.remove(pipes[1]) } }
        }, "report-share").start()
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
