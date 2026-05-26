package dev.whysoezzy.auth.presentation.name

import androidx.compose.runtime.Immutable

@Immutable
data class NameInputUiState(
    val name: String = "",
    val surname: String = "",
    val isLoading: Boolean = false,
    val isSubmitted: Boolean = false,
    val nameError: String? = null,
    val surnameError: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() && surname.isNotBlank() && nameError == null && surnameError == null
}

sealed class NameInputEvent {
    data class UpdateName(val name: String) : NameInputEvent()
    data class UpdateSurname(val surname: String) : NameInputEvent()
    data object Continue : NameInputEvent()
}