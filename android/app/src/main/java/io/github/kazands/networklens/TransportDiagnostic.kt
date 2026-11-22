package io.github.kazands.networklens

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException

/** Fixed diagnostic labels only. Never export exception messages or use these labels for admission/retries. */
object TransportDiagnostic {
    enum class Failure(val text: String) {
        NONE("Connected"), EOF("Peer closed the response (EOF)"), RESET("Connection reset reported by socket"),
        TIMEOUT("Socket timed out"), REFUSED("Connection failed or refused"), NO_ROUTE("No route to endpoint"),
        PERMISSION("Permission denied"), CLOSED("Socket closed"), SOCKET("Socket error; cause not available"),
        IO("I/O error; cause not available"), OTHER("Transport error; cause not available")
    }

    fun failure(error: Throwable): Failure {
        var cause: Throwable? = error
        var fallback = Failure.OTHER
        repeat(16) {
            val current = cause ?: return fallback
            when (current) {
                is EOFException -> return Failure.EOF
                is SocketTimeoutException -> return Failure.TIMEOUT
                is NoRouteToHostException -> return Failure.NO_ROUTE
                is ConnectException -> return Failure.REFUSED
                is SecurityException -> return Failure.PERMISSION
                is SocketException -> {
                    // Some Java/Android socket implementations expose reset/close only through
                    // these exact fixed messages. Do not substring-match or retain arbitrary text.
                    when (current.message) {
                        "Connection reset", "Connection reset by peer" -> return Failure.RESET
                        "Socket closed", "Socket is closed" -> return Failure.CLOSED
                    }
                    fallback = Failure.SOCKET
                }
                is IOException -> if (fallback == Failure.OTHER) fallback = Failure.IO
            }
            cause = current.cause
        }
        return fallback
    }
}
