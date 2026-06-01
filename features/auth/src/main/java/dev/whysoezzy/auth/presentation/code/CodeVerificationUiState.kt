package dev.whysoezzy.auth.presentation.code

import androidx.compose.runtime.Immutable
import com.whysoezzy.common.error.ErrorType

@Immutable
data class CodeVerificationUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val isVerified: Boolean = false,
    val error: ErrorType? = null,
    val remainingTime: Int = 60, // seconds for resend
    val canResend: Boolean = false,
) {
    val isValid: Boolean
        get() = code.length == 4 && error == null
}

sealed interface CodeVerificationEvent {
    data class UpdateCode(
        val code: String,
    ) : CodeVerificationEvent

    data object VerifyCode : CodeVerificationEvent

    data object ResendCode : CodeVerificationEvent
}
