package dev.whysoezzy.auth.presentation.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.models.EmailOtpRequestOutcome
import com.whysoezzy.auth.domain.usecase.RecoverEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.RequestEmailOtpUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface EmailInputNavEvent {
    data class NavigateToCode(
        val attemptId: String,
    ) : EmailInputNavEvent
}

class EmailInputViewModel(
    private val requestEmailOtp: RequestEmailOtpUseCase,
    private val recoverEmailOtp: RecoverEmailOtpAttemptUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmailInputUiState())
    val uiState: StateFlow<EmailInputUiState> = _uiState.asStateFlow()

    private val _navEvent = Channel<EmailInputNavEvent>(capacity = Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

    fun onEvent(event: EmailInputEvent) {
        when (event) {
            is EmailInputEvent.UpdateEmail -> {
                _uiState.value = _uiState.value.copy(email = event.value, error = null)
            }
            EmailInputEvent.Submit -> submit()
        }
    }

    private fun submit() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = requestEmailOtp(_uiState.value.email)) {
                is EmailOtpRequestOutcome.ProceedToVerification -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _navEvent.send(EmailInputNavEvent.NavigateToCode(result.attempt.attemptId))
                }
                is EmailOtpRequestOutcome.StayOnEmail -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.failure,
                    )
                }
            }
        }
    }

    @Suppress("UnusedPrivateMember")
    private suspend fun recover(attemptId: String) {
        recoverEmailOtp(attemptId)
    }
}
