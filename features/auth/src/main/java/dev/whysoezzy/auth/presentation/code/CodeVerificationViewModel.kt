package dev.whysoezzy.auth.presentation.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CodeVerificationViewModel(
    private val phoneNumber: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeVerificationUiState())
    val uiState: StateFlow<CodeVerificationUiState> = _uiState.asStateFlow()

    init {
        startTimer()
    }

    fun onEvent(event: CodeVerificationEvent) {
        when (event) {
            is CodeVerificationEvent.UpdateCode -> {
                updateCode(event.code)
            }

            CodeVerificationEvent.VerifyCode -> {
                verifyCode()
            }

            CodeVerificationEvent.ResendCode -> {
                resendCode()
            }

            CodeVerificationEvent.TickTimer -> {
                tickTimer()
            }
        }
    }

    private fun updateCode(code: String) {
        _uiState.value = _uiState.value.copy(
            code = code,
            error = null,
            isVerified = false
        )

        // Auto-verify when 4 digits are entered
        if (code.length == 4) {
            verifyCode()
        }
    }

    private fun verifyCode() {
        if (!_uiState.value.isValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Simulate API call
                delay(2000)

                // TODO: Implement actual code verification
                // authRepository.verifyCode(phoneNumber, code)

                // For demo, accept "1234" as valid code
                if (_uiState.value.code == "1234") {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isVerified = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Неверный код подтверждения"
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось проверить код"
                )
            }
        }
    }

    private fun resendCode() {
        if (!_uiState.value.canResend) return

        viewModelScope.launch {
            try {
                // TODO: Implement actual code resending
                // authRepository.sendSmsCode(phoneNumber)

                _uiState.value = _uiState.value.copy(
                    remainingTime = 60,
                    canResend = false,
                    error = null
                )
                startTimer()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Не удалось отправить код повторно"
                )
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            repeat(60) {
                delay(1000)
                tickTimer()
            }
        }
    }

    private fun tickTimer() {
        val currentState = _uiState.value
        val newTime = currentState.remainingTime - 1

        _uiState.value = currentState.copy(
            remainingTime = maxOf(0, newTime),
            canResend = newTime <= 0
        )
    }
}