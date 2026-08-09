package dev.whysoezzy.auth.presentation.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
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
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmailInputUiState())
    val uiState: StateFlow<EmailInputUiState> = _uiState.asStateFlow()

    private val _navEvent = Channel<EmailInputNavEvent>(capacity = Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val result = recoverActiveAttempt()) {
                is EmailOtpAttemptResult.Found ->
                    _navEvent.send(EmailInputNavEvent.NavigateToCode(result.attempt.attemptId))
                is EmailOtpAttemptResult.RecoverOnEmail -> {
                    _uiState.value = _uiState.value.copy(
                        email = result.email,
                        error = result.failure,
                        resendAvailableAtEpochMillis =
                            result.attempt.resendAvailableAtEpochMillis,
                    )
                }
                EmailOtpAttemptResult.MissingOrExpired -> Unit
            }
        }
    }

    fun onEvent(event: EmailInputEvent) {
        when (event) {
            is EmailInputEvent.UpdateEmail -> {
                _uiState.value = _uiState.value.copy(email = event.value, error = null)
            }
            EmailInputEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isLoading) return
        if (currentTimeMillis() < state.resendAvailableAtEpochMillis) {
            _uiState.value = state.copy(
                error = AuthFailure.ResendNotAvailable(state.resendAvailableAtEpochMillis),
            )
            return
        }
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
                        resendAvailableAtEpochMillis =
                            result.attempt.resendAvailableAtEpochMillis,
                    )
                }
            }
        }
    }

    private suspend fun recoverActiveAttempt(): EmailOtpAttemptResult = recoverEmailOtp.invoke()
}
