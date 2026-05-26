package dev.whysoezzy.auth.presentation.phone

import androidx.compose.runtime.Immutable

@Immutable
data class PhoneInputUiState(
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val isCodeSent: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        // +7 (999) 999-99-99 = 18 символов, или 11 цифр (без форматирования)
        get() = phoneNumber.filter { it.isDigit() }.length == 11 && error == null
}

sealed class PhoneInputEvent {
    data class UpdatePhoneNumber(val phoneNumber: String) : PhoneInputEvent()
    data object SendCode : PhoneInputEvent()
}