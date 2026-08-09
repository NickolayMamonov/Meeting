package dev.whysoezzy.auth.presentation.email

import androidx.compose.runtime.Immutable
import com.whysoezzy.auth.domain.models.AuthFailure

@Immutable
data class EmailInputUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: AuthFailure? = null,
    val resendAvailableAtEpochMillis: Long = 0L,
)

sealed interface EmailInputEvent {
    data class UpdateEmail(
        val value: String,
    ) : EmailInputEvent

    data object Submit : EmailInputEvent
}
