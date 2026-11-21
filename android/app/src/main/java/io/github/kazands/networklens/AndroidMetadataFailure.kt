package io.github.kazands.networklens

import android.system.ErrnoException
import android.system.OsConstants

/** Refine connection failures only from typed errno evidence, never exception message text. */
internal object AndroidMetadataFailure {
    /** Refine typed connection errors before the shared retry policy sees them. */
    fun <T> retryReadOnly(deadline: Long, active: () -> Boolean, exchange: () -> T): T =
        MetadataHttp.retryReadOnly(deadline, active) {
            try { exchange() } catch (error: MetadataHttp.Failure) {
                throw classify(error, System.nanoTime() >= deadline)
            }
        }

    fun classify(error: Exception, expired: Boolean): MetadataHttp.Failure {
        val failure = error as? MetadataHttp.Failure
        if (expired) return MetadataHttp.Failure(MetadataHttp.Reason.DEADLINE, cause = error)
        if (failure?.reason != MetadataHttp.Reason.CONNECTION_FAILED)
            return failure ?: MetadataHttp.Failure(MetadataHttp.Reason.REQUEST_FAILED, cause = error)
        var cause: Throwable? = failure.cause
        repeat(16) {
            val current = cause ?: return failure
            if (current is ErrnoException) {
                val reason = when (current.errno) {
                    OsConstants.ECONNREFUSED -> MetadataHttp.Reason.CONNECTION_REFUSED
                    OsConstants.ENETUNREACH, OsConstants.EHOSTUNREACH -> MetadataHttp.Reason.ROUTING_FAILED
                    else -> null
                }
                if (reason != null) return MetadataHttp.Failure(reason, cause = error)
            }
            cause = current.cause
        }
        return failure
    }
}
