package dev.whysoezzy.auth.presentation.name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NameInputViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NameInputUiState())
    val uiState: StateFlow<NameInputUiState> = _uiState.asStateFlow()

    fun onEvent(event: NameInputEvent) {
        when (event) {
            is NameInputEvent.UpdateName -> {
                updateName(event.name)
            }

            is NameInputEvent.UpdateSurname -> {
                updateSurname(event.surname)
            }

            NameInputEvent.Continue -> {
                validateAndContinue()
            }
        }
    }

    private fun updateName(name: String) {
        val nameError = validateName(name)
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = nameError,
            isSubmitted = false
        )
    }

    private fun updateSurname(surname: String) {
        val surnameError = validateSurname(surname)
        _uiState.value = _uiState.value.copy(
            surname = surname,
            surnameError = surnameError,
            isSubmitted = false
        )
    }

    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Имя не может быть пустым"
            name.length < 2 -> "Имя должно содержать минимум 2 символа"
            !name.all { it.isLetter() || it.isWhitespace() } -> "Имя может содержать только буквы"
            else -> null
        }
    }

    private fun validateSurname(surname: String): String? {
        return when {
            surname.isBlank() -> "Фамилия не может быть пустой"
            surname.length < 2 -> "Фамилия должна содержать минимум 2 символа"
            !surname.all { it.isLetter() || it.isWhitespace() } -> "Фамилия может содержать только буквы"
            else -> null
        }
    }

    private fun validateAndContinue() {
        val currentState = _uiState.value
        val nameError = validateName(currentState.name)
        val surnameError = validateSurname(currentState.surname)

        _uiState.value = currentState.copy(
            nameError = nameError,
            surnameError = surnameError
        )

        if (nameError == null && surnameError == null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Simulate API call to save user data
                delay(1500)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSubmitted = true
                )
            }
        }
    }
}