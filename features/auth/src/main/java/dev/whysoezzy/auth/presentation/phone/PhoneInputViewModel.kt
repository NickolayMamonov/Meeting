package dev.whysoezzy.auth.presentation.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.usecase.SendOtpUseCase
import com.whysoezzy.network.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhoneInputViewModel(
    private val sendOtpUseCase: SendOtpUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhoneInputUiState())
    val uiState: StateFlow<PhoneInputUiState> = _uiState.asStateFlow()

    fun onEvent(event: PhoneInputEvent) {
        when (event) {
            is PhoneInputEvent.UpdatePhoneNumber -> updatePhoneNumber(event.phoneNumber)
            PhoneInputEvent.SendCode -> sendCode()
        }
    }

    private fun updatePhoneNumber(phoneNumber: String) {
        val error = validatePhoneNumber(phoneNumber)
        _uiState.value =
            _uiState.value.copy(
                phoneNumber = phoneNumber,
                error = error,
                isCodeSent = false,
            )
    }

    private fun validatePhoneNumber(phoneNumber: String): String? {
        val digits = phoneNumber.filter { it.isDigit() }
        // local = 10 цифр после кода страны, итого 11 с кодом 7
        return when {
            digits.length < 11 -> null // ещё вводит — не показываем ошибку
            !digits.startsWith("7") -> "Номер должен начинаться с +7"
            else -> null
        }
    }

    private fun sendCode() {
        if (!_uiState.value.isValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            sendOtpUseCase(_uiState.value.phoneNumber)
                .onSuccess {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            isCodeSent = true,
                        )
                }.onFailure { exception ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = exception.toUserMessage(),
                        )
                }
        }
    }
}
