package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailAddress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PendingEmailOtpAttempt(
    val attemptId: String,
    val email: EmailAddress,
    val resendAvailableAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val challengeMayBeActive: Boolean,
    val dispatchOutcome: DispatchOutcome,
)

interface PendingEmailOtpStore {
    suspend fun replace(attempt: PendingEmailOtpAttempt)

    suspend fun get(attemptId: String): PendingEmailOtpAttempt?

    suspend fun clear(attemptId: String)
}

class InMemoryPendingEmailOtpStore : PendingEmailOtpStore {
    private val mutex = Mutex()
    private var active: PendingEmailOtpAttempt? = null

    override suspend fun replace(attempt: PendingEmailOtpAttempt) {
        mutex.withLock { active = attempt }
    }

    override suspend fun get(attemptId: String): PendingEmailOtpAttempt? =
        mutex.withLock { active?.takeIf { it.attemptId == attemptId } }

    override suspend fun clear(attemptId: String) {
        mutex.withLock {
            if (active?.attemptId == attemptId) active = null
        }
    }
}
