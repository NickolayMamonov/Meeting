package dev.whysoezzy.auth.presentation.code

import androidx.compose.runtime.Immutable
import com.whysoezzy.auth.domain.models.AuthFailure

@Immutable
data class CodeVerificationUiState(
    val maskedEmail: String = "",
    val code: String = "",
    val isLoading: Boolean = false,
    val error: AuthFailure? = null,
    val remainingTime: Int = 60,
    val canResend: Boolean = false,
) {
    val isValid: Boolean
        get() = code.length == 6 && error == null && !isLoading
}

sealed interface CodeVerificationEvent {
    data class UpdateCode(
        val code: String,
    ) : CodeVerificationEvent

    data object VerifyCode : CodeVerificationEvent

    data object ResendCode : CodeVerificationEvent
}
