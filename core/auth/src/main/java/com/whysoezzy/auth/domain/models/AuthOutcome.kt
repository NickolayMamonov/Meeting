package com.whysoezzy.auth.domain.models

sealed interface AuthOutcome<out T> {
    data class Success<T>(
        val value: T,
    ) : AuthOutcome<T>

    data class Failure(
        val reason: AuthFailure,
    ) : AuthOutcome<Nothing>
}

sealed interface AuthFailure {
    data object InvalidEmail : AuthFailure

    data object InvalidCode : AuthFailure

    data object RateLimited : AuthFailure

    data object DeliveryUnavailable : AuthFailure

    data object ActivationUnavailable : AuthFailure

    data object InvalidOrExpiredCode : AuthFailure

    data object NoConnection : AuthFailure

    data object Unauthorized : AuthFailure

    data object Server : AuthFailure

    data object Unknown : AuthFailure

    data object SessionPersistenceFailure : AuthFailure

    data object MissingOrExpiredAttempt : AuthFailure

    data class ResendNotAvailable(
        val availableAtEpochMillis: Long,
    ) : AuthFailure
}

internal fun <T> AuthOutcome<T>.asResult(): Result<T> =
    when (this) {
        is AuthOutcome.Success -> Result.success(value)
        is AuthOutcome.Failure -> Result.failure(AuthFailureException(reason))
    }

class AuthFailureException(
    val failure: AuthFailure,
) : Exception("Authentication request failed: ${failure::class.simpleName}")
