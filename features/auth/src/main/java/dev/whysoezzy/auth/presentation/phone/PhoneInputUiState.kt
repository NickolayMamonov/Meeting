package dev.whysoezzy.auth.presentation.phone

data class PhoneInputUiState(
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val isCodeSent: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = phoneNumber.length >= 18 && error == null // +7 (999) 999-99-99
}

sealed class PhoneInputEvent {
    data class UpdatePhoneNumber(val phoneNumber: String) : PhoneInputEvent()
    data object SendCode : PhoneInputEvent()
}