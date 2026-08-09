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
        val pending = operationMutex.withLock {
            val now = clock.nowEpochMillis()
            PendingEmailOtpAttempt(
                attemptId = idGenerator.generate(),
                email = email,
                resendAvailableAtEpochMillis = now + RESEND_DELAY_MILLIS,
                expiresAtEpochMillis = now + ATTEMPT_LIFETIME_MILLIS,
                challengeMayBeActive = true,
                dispatchOutcome = DispatchOutcome.Unconfirmed,
                dispatchGeneration = 1L,
            ).also { store.replace(it) }
        }

        val result = repository.requestEmailOtp(email.canonical)
        val completed = pending.complete(result)
        return if (
            store.replaceIfCurrent(pending.attemptId, pending.dispatchGeneration, completed)
        ) {
            completed.toRequestOutcome()
        } else {
            resolveCurrentRequestOutcome()
        }
    }

    suspend fun load(attemptId: String): EmailOtpAttemptResult =
        pendingResult(active(attemptId))

    suspend fun loadActive(): EmailOtpAttemptResult =
        store.getActive()?.let { pendingResult(active(it.attemptId)) }
            ?: EmailOtpAttemptResult.MissingOrExpired

    suspend fun clearActive() = store.clearActive()

    suspend fun resend(attemptId: String): EmailOtpResendOutcome {
        val previous = active(attemptId)
            ?: return EmailOtpResendOutcome.Failed(
                null,
                AuthFailure.MissingOrExpiredAttempt,
            )
        val now = clock.nowEpochMillis()
        if (now < previous.resendAvailableAtEpochMillis) {
            return EmailOtpResendOutcome.Failed(
                previous.public(),
                AuthFailure.ResendNotAvailable(previous.resendAvailableAtEpochMillis),
            )
        }
        val dispatching = previous.copy(
            resendAvailableAtEpochMillis = now + RESEND_DELAY_MILLIS,
            dispatchOutcome = DispatchOutcome.Unconfirmed,
            dispatchGeneration = previous.dispatchGeneration + 1L,
        )
        val began = operationMutex.withLock {
            store.replaceIfCurrent(
                previous.attemptId,
                previous.dispatchGeneration,
                dispatching,
            )
        }
        if (!began) {
            return EmailOtpResendOutcome.Failed(
                null,
                AuthFailure.MissingOrExpiredAttempt,
            )
        }

        val result = repository.requestEmailOtp(previous.email.canonical)
        val completed = dispatching.completeResend(previous, result, now)
        return if (
            store.replaceIfCurrent(
                dispatching.attemptId,
                dispatching.dispatchGeneration,
                completed,
            )
        ) {
            completed.toResendOutcome(result)
        } else {
            resolveCurrentResendOutcome(dispatching.attemptId)
        }
    }

    suspend fun verify(
        attemptId: String,
        code: String,
        name: String? = null,
        surname: String? = null,
    ): EmailOtpVerifyOutcome {
        if (!ValidationUtils.isValidOtpCode(code)) {
            return EmailOtpVerifyOutcome.Failed(AuthFailure.InvalidCode)
        }
        val pending = active(attemptId)
            ?: return EmailOtpVerifyOutcome.Failed(AuthFailure.MissingOrExpiredAttempt)
        if (!pending.challengeMayBeActive) {
            return EmailOtpVerifyOutcome.Failed(AuthFailure.MissingOrExpiredAttempt)
        }
        return when (
            val result = repository.verifyEmailOtp(
                pending.email.canonical,
                code,
                name,
                surname,
            )
        ) {
            is AuthOutcome.Success -> {
                store.clear(attemptId, pending.dispatchGeneration)
                if (result.value.isNewUser) {
                    EmailOtpVerifyOutcome.NewUser
                } else {
                    EmailOtpVerifyOutcome.ExistingUser
                }
            }

            is AuthOutcome.Failure -> EmailOtpVerifyOutcome.Failed(result.reason)
        }
    }

    suspend fun clear(attemptId: String) = store.clear(attemptId)

    private suspend fun active(attemptId: String): PendingEmailOtpAttempt? {
        val pending = store.get(attemptId) ?: return null
        if (clock.nowEpochMillis() >= pending.expiresAtEpochMillis) {
            store.clear(attemptId, pending.dispatchGeneration)
            return null
        }
        return pending
    }

    private fun pendingResult(pending: PendingEmailOtpAttempt?): EmailOtpAttemptResult =
        when {
            pending == null -> EmailOtpAttemptResult.MissingOrExpired
            pending.challengeMayBeActive -> EmailOtpAttemptResult.Found(pending.public())
            else -> EmailOtpAttemptResult.RecoverOnEmail(
                email = pending.email.canonical,
                attempt = pending.public(),
                failure = pending.dispatchOutcome.failure(),
            )
        }

    private suspend fun resolveCurrentRequestOutcome(): EmailOtpRequestOutcome =
        store.getActive()?.let { it.toRequestOutcome() }
            ?: EmailOtpRequestOutcome.StayOnEmail(
                attempt = emptyAttempt(),
                failure = AuthFailure.MissingOrExpiredAttempt,
            )

    private suspend fun resolveCurrentResendOutcome(
        attemptId: String,
    ): EmailOtpResendOutcome =
        active(attemptId)?.let { it.toCurrentResendOutcome() }
            ?: EmailOtpResendOutcome.Failed(
                attempt = null,
                failure = AuthFailure.MissingOrExpiredAttempt,
            )

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

    private fun PendingEmailOtpAttempt.toResendOutcome(
        result: AuthOutcome<Unit>,
    ): EmailOtpResendOutcome =
        when (result) {
            is AuthOutcome.Success -> EmailOtpResendOutcome.Confirmed(public())
            is AuthOutcome.Failure ->
                if (result.reason.mayHaveDispatched()) {
                    EmailOtpResendOutcome.Unconfirmed(public())
                } else {
                    EmailOtpResendOutcome.Failed(public(), result.reason)
                }
        }

    private fun PendingEmailOtpAttempt.toCurrentResendOutcome(): EmailOtpResendOutcome =
        when (dispatchOutcome) {
            DispatchOutcome.Confirmed -> EmailOtpResendOutcome.Confirmed(public())
            DispatchOutcome.Unconfirmed -> EmailOtpResendOutcome.Unconfirmed(public())
            else -> EmailOtpResendOutcome.Failed(public(), dispatchOutcome.failure())
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
