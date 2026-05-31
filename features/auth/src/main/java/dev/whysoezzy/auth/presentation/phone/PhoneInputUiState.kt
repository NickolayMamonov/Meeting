package dev.whysoezzy.auth.presentation.phone

import androidx.compose.runtime.Immutable
import com.whysoezzy.common.utils.ValidationUtils

@Immutable
data class PhoneInputUiState(
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val isCodeSent: Boolean = false,
    val error: PhoneInputError? = null,
) {
    val isValid: Boolean
        get() = ValidationUtils.isValidPhoneNumber(phoneNumber) && error == null
}

sealed interface PhoneInputEvent {
    data class UpdatePhoneNumber(
        val phoneNumber: String,
    ) : PhoneInputEvent

    data object SendCode : PhoneInputEvent
}

sealed interface PhoneInputError {
    data object Invalid : PhoneInputError

    data class Remote(
        val message: String,
    ) : PhoneInputError
}

sealed interface PhoneInputError {
    data object Invalid : PhoneInputError
    data class Remote(val message: String) : PhoneInputError
}