package dev.whysoezzy.auth.presentation.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhoneInputViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneInputUiState())
    val uiState: StateFlow<PhoneInputUiState> = _uiState.asStateFlow()

    fun onEvent(event: PhoneInputEvent) {
        when (event) {
            is PhoneInputEvent.UpdatePhoneNumber -> {
                updatePhoneNumber(event.phoneNumber)
            }

            PhoneInputEvent.SendCode -> {
                sendCode()
            }
        }
    }

    private fun updatePhoneNumber(phoneNumber: String) {
        val error = validatePhoneNumber(phoneNumber)
        _uiState.value = _uiState.value.copy(
            phoneNumber = phoneNumber,
            error = error,
            isCodeSent = false
        )
    }

    private fun validatePhoneNumber(phoneNumber: String): String? {
        val digits = phoneNumber.filter { it.isDigit() }
        return when {
            digits.length < 11 -> "Введите корректный номер телефона"
            !digits.startsWith("7") -> "Номер должен начинаться с +7"
            else -> null
        }
    }

    private fun sendCode() {
        if (!_uiState.value.isValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Simulate API call
                delay(2000)

                // TODO: Implement actual SMS sending logic
                // authRepository.sendSmsCode(phoneNumber)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isCodeSent = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось отправить код"
                )
            }
        }
    }
}