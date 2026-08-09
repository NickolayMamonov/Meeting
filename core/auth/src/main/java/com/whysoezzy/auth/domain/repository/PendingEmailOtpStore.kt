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
    val dispatchGeneration: Long = 0L,
)

interface PendingEmailOtpStore {
    suspend fun replace(attempt: PendingEmailOtpAttempt)

    suspend fun replaceIfCurrent(
        attemptId: String,
        dispatchGeneration: Long,
        replacement: PendingEmailOtpAttempt,
    ): Boolean

    suspend fun get(attemptId: String): PendingEmailOtpAttempt?

    suspend fun getActive(): PendingEmailOtpAttempt?

    suspend fun clear(attemptId: String, dispatchGeneration: Long? = null)

    suspend fun clearActive(dispatchGeneration: Long? = null)
}

class InMemoryPendingEmailOtpStore : PendingEmailOtpStore {
    private val mutex = Mutex()
    private var active: PendingEmailOtpAttempt? = null

    override suspend fun replace(attempt: PendingEmailOtpAttempt) {
        mutex.withLock { active = attempt }
    }

    override suspend fun replaceIfCurrent(
        attemptId: String,
        dispatchGeneration: Long,
        replacement: PendingEmailOtpAttempt,
    ): Boolean = mutex.withLock {
        if (active?.attemptId != attemptId || active?.dispatchGeneration != dispatchGeneration) {
            false
        } else {
            active = replacement
            true
        }
    }

    override suspend fun get(attemptId: String): PendingEmailOtpAttempt? =
        mutex.withLock { active?.takeIf { it.attemptId == attemptId } }

    override suspend fun getActive(): PendingEmailOtpAttempt? = mutex.withLock { active }

    override suspend fun clear(attemptId: String, dispatchGeneration: Long?) {
        mutex.withLock {
            if (active?.attemptId == attemptId &&
                (dispatchGeneration == null || active?.dispatchGeneration == dispatchGeneration)
            ) {
                active = null
            }
        }
    }

    override suspend fun clearActive(dispatchGeneration: Long?) {
        mutex.withLock {
            if (dispatchGeneration == null || active?.dispatchGeneration == dispatchGeneration) {
                active = null
            }
        }
    }
}
