package dev.whysoezzy.auth.presentation.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.usecase.SendOtpUseCase
import com.whysoezzy.auth.domain.usecase.VerifyOtpUseCase
import com.whysoezzy.network.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CodeVerificationNavEvent {
    /** Пользователь существующий — сразу в Main */
    data object NavigateToMain : CodeVerificationNavEvent()

    /** Новый пользователь — нужно ввести имя */
    data class NavigateToNameInput(
        val phone: String,
        val code: String,
    ) : CodeVerificationNavEvent()
}

class CodeVerificationViewModel(
    private val phoneNumber: String,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val sendOtpUseCase: SendOtpUseCase,
) : ViewModel() {
    companion object {
        private const val OTP_RESEND_TIMEOUT_SECONDS = 60
        private const val TIMER_POLL_INTERVAL_MS = 1000L
        private const val TIMER_DURATION_MS = OTP_RESEND_TIMEOUT_SECONDS * 1000L
    }

    private val _uiState = MutableStateFlow(CodeVerificationUiState())
    val uiState: StateFlow<CodeVerificationUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<CodeVerificationNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<CodeVerificationNavEvent> = _navEvent.asSharedFlow()

    private var timerJob: Job? = null

    init {
        startTimer()
    }

    fun onEvent(event: CodeVerificationEvent) {
        when (event) {
            is CodeVerificationEvent.UpdateCode -> updateCode(event.code)
            CodeVerificationEvent.VerifyCode -> verifyCode()
            CodeVerificationEvent.ResendCode -> resendCode()
        }
    }

    private fun updateCode(code: String) {
        _uiState.value =
            _uiState.value.copy(
                code = code,
                error = null,
                isVerified = false,
            )
        // Авто-верификация при вводе 4 цифр
        if (code.length == 4) verifyCode()
    }

    private fun verifyCode() {
        if (!_uiState.value.isValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            verifyOtpUseCase(phoneNumber, _uiState.value.code)
                .onSuccess { result: AuthResult ->
                    _uiState.value = _uiState.value.copy(isLoading = false, isVerified = true)

                    if (result.isNewUser) {
                        // Новый пользователь — нужно ввести имя на следующем экране
                        _navEvent.emit(
                            CodeVerificationNavEvent.NavigateToNameInput(phoneNumber, _uiState.value.code),
                        )
                    } else {
                        // Существующий — токены уже сохранены в TokenManager, идём в Main
                        _navEvent.emit(CodeVerificationNavEvent.NavigateToMain)
                    }
                }.onFailure { exception ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = exception.toUserMessage(),
                        )
                }
        }
    }

    private fun resendCode() {
        if (!_uiState.value.canResend) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)

            sendOtpUseCase(phoneNumber)
                .onSuccess {
                    _uiState.value =
                        _uiState.value.copy(
                            remainingTime = OTP_RESEND_TIMEOUT_SECONDS,
                            canResend = false,
                            code = "",
                        )
                    startTimer()
                }.onFailure { exception ->
                    _uiState.value =
                        _uiState.value.copy(
                            error = exception.toUserMessage(),
                        )
                }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob =
            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                val durationMs = TIMER_DURATION_MS

                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = ((durationMs - elapsed) / 1000).toInt().coerceAtLeast(0)
                    _uiState.value =
                        _uiState.value.copy(
                            remainingTime = remaining,
                            canResend = remaining <= 0,
                        )
                    if (remaining <= 0) break
                    delay(TIMER_POLL_INTERVAL_MS)
                }
            }
    }
}
