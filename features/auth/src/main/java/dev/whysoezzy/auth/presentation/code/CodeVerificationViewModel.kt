package dev.whysoezzy.auth.presentation.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import com.whysoezzy.auth.domain.models.EmailOtpResendOutcome
import com.whysoezzy.auth.domain.models.EmailOtpVerifyOutcome
import com.whysoezzy.auth.domain.usecase.ClearEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.LoadEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.ResendEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.VerifyEmailOtpUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface CodeVerificationNavEvent {
    data object NavigateToMain : CodeVerificationNavEvent

    data object NavigateToNameInput : CodeVerificationNavEvent

    data object NavigateToEmail : CodeVerificationNavEvent
}

class CodeVerificationViewModel(
    private val attemptId: String,
    private val loadAttempt: LoadEmailOtpAttemptUseCase,
    private val resendOtp: ResendEmailOtpUseCase,
    private val verifyOtpUseCase: VerifyEmailOtpUseCase,
    private val clearAttempt: ClearEmailOtpAttemptUseCase,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodeVerificationUiState())
    val uiState: StateFlow<CodeVerificationUiState> = _uiState.asStateFlow()
    private val _navEvent = Channel<CodeVerificationNavEvent>(Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()
    private var operation: Job? = null
    private var submittedCode: String? = null
    private var deadline = 0L

    init {
        viewModelScope.launch {
            when (val result = loadAttempt(attemptId)) {
                is EmailOtpAttemptResult.Found -> {
                    deadline = result.attempt.resendAvailableAtEpochMillis
                    _uiState.value = _uiState.value.copy(
                        maskedEmail = result.attempt.maskedEmail,
                    )
                    startTimer()
                }
                EmailOtpAttemptResult.MissingOrExpired -> _navEvent.send(
                    CodeVerificationNavEvent.NavigateToEmail,
                )
            }
        }
    }

    fun onEvent(event: CodeVerificationEvent) {
        when (event) {
            is CodeVerificationEvent.UpdateCode -> updateCode(event.code)
            CodeVerificationEvent.VerifyCode -> verifyCode()
            CodeVerificationEvent.ResendCode -> resendCode()
        }
    }

    fun clearAndLeave() {
        operation?.cancel()
        viewModelScope.launch {
            clearAttempt(attemptId)
            _navEvent.send(CodeVerificationNavEvent.NavigateToEmail)
        }
    }

    private fun updateCode(value: String) {
        val filtered = value.filter { it in '0'..'9' }.take(6)
        _uiState.value = _uiState.value.copy(code = filtered, error = null)
        if (filtered.length == 6 && submittedCode != filtered) verifyCode()
        if (filtered.length < 6) submittedCode = null
    }

    private fun verifyCode() {
        val code = _uiState.value.code
        if (code.length != 6 || operation?.isActive == true || submittedCode == code) return
        submittedCode = code
        operation = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = verifyOtpUseCase(attemptId, code)) {
                EmailOtpVerifyOutcome.ExistingUser -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _navEvent.send(CodeVerificationNavEvent.NavigateToMain)
                }
                EmailOtpVerifyOutcome.NewUser -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _navEvent.send(CodeVerificationNavEvent.NavigateToNameInput)
                }
                is EmailOtpVerifyOutcome.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.failure,
                    )
                    if (result.failure == AuthFailure.NoConnection ||
                        result.failure == AuthFailure.Server
                    ) {
                        submittedCode = null
                    }
                }
            }
        }
    }

    private fun resendCode() {
        if (!_uiState.value.canResend || operation?.isActive == true) return
        operation = viewModelScope.launch {
            when (val result = resendOtp(attemptId)) {
                is EmailOtpResendOutcome.Confirmed,
                is EmailOtpResendOutcome.Unconfirmed,
                -> {
                    _uiState.value = _uiState.value.copy(
                        code = if (result is EmailOtpResendOutcome.Confirmed) "" else _uiState.value.code,
                        error = null,
                    )
                    submittedCode = null
                    deadline = currentTimeMillis() + 60_000L
                    startTimer()
                }
                is EmailOtpResendOutcome.Failed -> {
                    _uiState.value = _uiState.value.copy(error = result.failure)
                    deadline = currentTimeMillis() + 60_000L
                    startTimer()
                }
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                val remaining = ((deadline - currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                _uiState.value = _uiState.value.copy(
                    remainingTime = remaining,
                    canResend = remaining == 0,
                )
                if (remaining == 0) return@launch
                delay(1_000L)
            }
        }
    }
}
