package dev.whysoezzy.auth.presentation.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.usecase.SendOtpUseCase
import com.whysoezzy.common.error.toErrorType
import com.whysoezzy.common.utils.ValidationUtils
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

    private fun validatePhoneNumber(phoneNumber: String): PhoneInputError? {
        val digits = phoneNumber.filter { it.isDigit() }
        return when {
            digits.length < 10 -> null
            ValidationUtils.isValidPhoneNumber(phoneNumber) -> null
            else -> PhoneInputError.Invalid
        }
    }

    private fun sendCode() {
        if (!_uiState.value.isValid) return
        val normalizedPhoneNumber = normalizePhoneNumber(_uiState.value.phoneNumber)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            sendOtpUseCase(normalizedPhoneNumber)
                .onSuccess {
                    _uiState.value =
                        _uiState.value.copy(
                            phoneNumber = normalizedPhoneNumber,
                            isLoading = false,
                            isCodeSent = true,
                        )
                }.onFailure { exception ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = PhoneInputError.Remote(exception.toErrorType()),
                        )
                }
        }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        val digits = phoneNumber.filter { it.isDigit() }
        return when {
            digits.startsWith("8") && digits.length == 11 -> "+7${digits.drop(1)}"
            digits.startsWith("7") && digits.length == 11 -> "+$digits"
            digits.length == 10 -> "+7$digits"
            else -> phoneNumber
        }
    }
}
