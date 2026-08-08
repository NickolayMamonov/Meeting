package com.whysoezzy.auth.domain

import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.DispatchOutcome
import com.whysoezzy.auth.domain.models.EmailAddressParser
import com.whysoezzy.auth.domain.models.EmailOtpAttempt
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.models.EmailOtpRequestOutcome
import com.whysoezzy.auth.domain.models.EmailOtpResendOutcome
import com.whysoezzy.auth.domain.models.EmailOtpVerifyOutcome
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.repository.PendingEmailOtpAttempt
import com.whysoezzy.auth.domain.repository.PendingEmailOtpStore
import com.whysoezzy.common.utils.ValidationUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

fun interface AuthClock {
    fun nowEpochMillis(): Long
}

fun interface AttemptIdGenerator {
    fun generate(): String
}

class EmailOtpCoordinator(
    private val repository: AuthRepository,
    private val store: PendingEmailOtpStore,
    private val parser: EmailAddressParser,
    private val clock: AuthClock,
    private val idGenerator: AttemptIdGenerator,
) {
    private val operationMutex = Mutex()

    suspend fun request(rawEmail: String): EmailOtpRequestOutcome {
        val email =
            when (val parsed = parser.parse(rawEmail)) {
                is AuthOutcome.Success -> parsed.value
                is AuthOutcome.Failure ->
                    return EmailOtpRequestOutcome.StayOnEmail(
                        attempt = emptyAttempt(),
                        failure = parsed.reason,
                    )
            }
        return operationMutex.withLock {
            val now = clock.nowEpochMillis()
            val pending =
                PendingEmailOtpAttempt(
                    attemptId = idGenerator.generate(),
                    email = email,
                    resendAvailableAtEpochMillis = now + RESEND_DELAY_MILLIS,
                    expiresAtEpochMillis = now + ATTEMPT_LIFETIME_MILLIS,
                    challengeMayBeActive = true,
                    dispatchOutcome = DispatchOutcome.Unconfirmed,
                )
            store.replace(pending)
            val completed = pending.complete(repository.requestEmailOtp(email.canonical))
            store.replace(completed)
            completed.toRequestOutcome()
        }
    }

    suspend fun load(attemptId: String): EmailOtpAttemptResult =
        operationMutex.withLock {
            val pending = active(attemptId) ?: return@withLock EmailOtpAttemptResult.MissingOrExpired
            EmailOtpAttemptResult.Found(pending.public())
        }

    suspend fun loadActive(): EmailOtpAttemptResult =
        operationMutex.withLock {
            val stored = store.getActive()
                ?: return@withLock EmailOtpAttemptResult.MissingOrExpired
            val pending = active(stored.attemptId)
                ?: return@withLock EmailOtpAttemptResult.MissingOrExpired
            EmailOtpAttemptResult.Found(pending.public())
        }

    suspend fun clearActive() = operationMutex.withLock { store.clearActive() }

    suspend fun resend(attemptId: String): EmailOtpResendOutcome =
        operationMutex.withLock {
            val previous = active(attemptId)
                ?: return@withLock EmailOtpResendOutcome.Failed(
                    null,
                    AuthFailure.MissingOrExpiredAttempt,
                )
            val now = clock.nowEpochMillis()
            if (now < previous.resendAvailableAtEpochMillis) {
                return@withLock EmailOtpResendOutcome.Failed(
                    previous.public(),
                    AuthFailure.ResendNotAvailable(previous.resendAvailableAtEpochMillis),
                )
            }
            val dispatching =
                previous.copy(
                    resendAvailableAtEpochMillis = now + RESEND_DELAY_MILLIS,
                    dispatchOutcome = DispatchOutcome.Unconfirmed,
                )
            store.replace(dispatching)
            val result = repository.requestEmailOtp(previous.email.canonical)
            val completed = dispatching.completeResend(previous, result, now)
            store.replace(completed)
            completed.toResendOutcome(result)
        }

    suspend fun verify(
        attemptId: String,
        code: String,
        name: String? = null,
        surname: String? = null,
    ): EmailOtpVerifyOutcome =
        operationMutex.withLock {
            if (!ValidationUtils.isValidOtpCode(code)) {
                return@withLock EmailOtpVerifyOutcome.Failed(AuthFailure.InvalidCode)
            }
            val pending = active(attemptId)
                ?: return@withLock EmailOtpVerifyOutcome.Failed(AuthFailure.MissingOrExpiredAttempt)
            when (val result = repository.verifyEmailOtp(pending.email.canonical, code, name, surname)) {
                is AuthOutcome.Success -> {
                    store.clear(attemptId)
                    if (result.value.isNewUser) {
                        EmailOtpVerifyOutcome.NewUser
                    } else {
                        EmailOtpVerifyOutcome.ExistingUser
                    }
                }

                is AuthOutcome.Failure -> EmailOtpVerifyOutcome.Failed(result.reason)
            }
        }

    suspend fun clear(attemptId: String) = operationMutex.withLock { store.clear(attemptId) }

    private suspend fun active(attemptId: String): PendingEmailOtpAttempt? {
        val pending = store.get(attemptId) ?: return null
        if (
            clock.nowEpochMillis() >= pending.expiresAtEpochMillis ||
            !pending.challengeMayBeActive
        ) {
            store.clear(attemptId)
            return null
        }
        return pending
    }

    private fun PendingEmailOtpAttempt.complete(result: AuthOutcome<Unit>): PendingEmailOtpAttempt =
        when (result) {
            is AuthOutcome.Success -> copy(dispatchOutcome = DispatchOutcome.Confirmed)
            is AuthOutcome.Failure ->
                copy(
                    challengeMayBeActive = result.reason.mayHaveDispatched(),
                    dispatchOutcome = result.reason.dispatchOutcome(),
                )
        }

    private fun PendingEmailOtpAttempt.completeResend(
        previous: PendingEmailOtpAttempt,
        result: AuthOutcome<Unit>,
        now: Long,
    ): PendingEmailOtpAttempt =
        when (result) {
            is AuthOutcome.Success ->
                copy(
                    expiresAtEpochMillis = now + ATTEMPT_LIFETIME_MILLIS,
                    challengeMayBeActive = true,
                    dispatchOutcome = DispatchOutcome.Confirmed,
                )

            is AuthOutcome.Failure -> {
                val uncertain = result.reason.mayHaveDispatched()
                copy(
                    expiresAtEpochMillis =
                        if (uncertain) now + ATTEMPT_LIFETIME_MILLIS else previous.expiresAtEpochMillis,
                    challengeMayBeActive = previous.challengeMayBeActive || uncertain,
                    dispatchOutcome = result.reason.dispatchOutcome(),
                )
            }
        }

    private fun PendingEmailOtpAttempt.toRequestOutcome(): EmailOtpRequestOutcome =
        if (challengeMayBeActive) {
            EmailOtpRequestOutcome.ProceedToVerification(public())
        } else {
            EmailOtpRequestOutcome.StayOnEmail(public(), dispatchOutcome.failure())
        }

    private fun PendingEmailOtpAttempt.toResendOutcome(result: AuthOutcome<Unit>): EmailOtpResendOutcome =
        when (result) {
            is AuthOutcome.Success -> EmailOtpResendOutcome.Confirmed(public())
            is AuthOutcome.Failure ->
                if (result.reason.mayHaveDispatched()) {
                    EmailOtpResendOutcome.Unconfirmed(public())
                } else {
                    EmailOtpResendOutcome.Failed(public().takeIf { challengeMayBeActive }, result.reason)
                }
        }

    private fun PendingEmailOtpAttempt.public() =
        EmailOtpAttempt(
            attemptId = attemptId,
            maskedEmail = email.masked,
            resendAvailableAtEpochMillis = resendAvailableAtEpochMillis,
            challengeMayBeActive = challengeMayBeActive,
            dispatchOutcome = dispatchOutcome,
        )

    private fun AuthFailure.mayHaveDispatched(): Boolean =
        this == AuthFailure.NoConnection || this == AuthFailure.Server || this == AuthFailure.Unknown

    private fun AuthFailure.dispatchOutcome(): DispatchOutcome =
        when (this) {
            AuthFailure.RateLimited -> DispatchOutcome.RateLimited
            AuthFailure.DeliveryUnavailable -> DispatchOutcome.DeliveryUnavailable
            AuthFailure.ActivationUnavailable -> DispatchOutcome.ActivationUnavailable
            AuthFailure.InvalidEmail -> DispatchOutcome.RejectedValidation
            AuthFailure.NoConnection, AuthFailure.Server, AuthFailure.Unknown -> DispatchOutcome.Unconfirmed
            else -> DispatchOutcome.FailedNoChallenge
        }

    private fun DispatchOutcome.failure(): AuthFailure =
        when (this) {
            DispatchOutcome.RateLimited -> AuthFailure.RateLimited
            DispatchOutcome.DeliveryUnavailable -> AuthFailure.DeliveryUnavailable
            DispatchOutcome.ActivationUnavailable -> AuthFailure.ActivationUnavailable
            DispatchOutcome.RejectedValidation -> AuthFailure.InvalidEmail
            else -> AuthFailure.Unknown
        }

    private fun emptyAttempt() =
        EmailOtpAttempt(
            attemptId = "",
            maskedEmail = "",
            resendAvailableAtEpochMillis = 0,
            challengeMayBeActive = false,
            dispatchOutcome = DispatchOutcome.RejectedValidation,
        )

    companion object {
        private const val RESEND_DELAY_MILLIS = 60_000L
        private const val ATTEMPT_LIFETIME_MILLIS = 15 * 60_000L

        fun defaultIdGenerator(): AttemptIdGenerator = AttemptIdGenerator { UUID.randomUUID().toString() }
    }
}
