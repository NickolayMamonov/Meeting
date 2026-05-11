package dev.whysoezzy.auth.presentation.code

data class CodeVerificationUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val isVerified: Boolean = false,
    val error: String? = null,
    val remainingTime: Int = 60, // seconds for resend
    val canResend: Boolean = false
) {
    val isValid: Boolean
        get() = code.length == 4 && error == null
}

sealed class CodeVerificationEvent {
    data class UpdateCode(val code: String) : CodeVerificationEvent()
    data object VerifyCode : CodeVerificationEvent()
    data object ResendCode : CodeVerificationEvent()
}