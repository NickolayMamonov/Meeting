package com.whysoezzy.auth.domain.models

enum class DispatchOutcome {
    Confirmed,
    Unconfirmed,
    RateLimited,
    DeliveryUnavailable,
    ActivationUnavailable,
    RejectedValidation,
    FailedNoChallenge,
}

data class EmailOtpAttempt(
    val attemptId: String,
    val maskedEmail: String,
    val resendAvailableAtEpochMillis: Long,
    val challengeMayBeActive: Boolean,
    val dispatchOutcome: DispatchOutcome,
)

sealed interface EmailOtpRequestOutcome {
    data class ProceedToVerification(
        val attempt: EmailOtpAttempt,
    ) : EmailOtpRequestOutcome

    data class StayOnEmail(
        val attempt: EmailOtpAttempt,
        val failure: AuthFailure,
    ) : EmailOtpRequestOutcome
}

sealed interface EmailOtpResendOutcome {
    data class Confirmed(
        val attempt: EmailOtpAttempt,
    ) : EmailOtpResendOutcome

    data class Unconfirmed(
        val attempt: EmailOtpAttempt,
    ) : EmailOtpResendOutcome

    data class Failed(
        val attempt: EmailOtpAttempt?,
        val failure: AuthFailure,
    ) : EmailOtpResendOutcome
}

sealed interface EmailOtpVerifyOutcome {
    data object ExistingUser : EmailOtpVerifyOutcome

    data object NewUser : EmailOtpVerifyOutcome

    data class Failed(
        val failure: AuthFailure,
    ) : EmailOtpVerifyOutcome
}

sealed interface EmailOtpAttemptResult {
    data class Found(
        val attempt: EmailOtpAttempt,
    ) : EmailOtpAttemptResult

    data class RecoverOnEmail(
        val email: String,
        val attempt: EmailOtpAttempt,
        val failure: AuthFailure,
    ) : EmailOtpAttemptResult

    data object MissingOrExpired : EmailOtpAttemptResult
}
