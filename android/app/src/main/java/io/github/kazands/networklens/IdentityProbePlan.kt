package io.github.kazands.networklens

/** Credential-free identity opportunities; independent of a general service's first-open-port exit. */
internal object IdentityProbePlan {
    fun remaining(checked: Set<Int>): List<Int> = listOf(80, AppleDeviceInfo.PORT, 8001).filterNot { it in checked }
}
