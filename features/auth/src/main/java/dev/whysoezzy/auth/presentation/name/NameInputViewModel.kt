package dev.whysoezzy.auth.presentation.name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.common.error.ErrorType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NameInputNavEvent {
    data object NavigateToSuccess : NameInputNavEvent
}

/**
 * NameInputViewModel — экран для новых пользователей.
 * Сессия уже аутентифицирована (verify-otp прошёл на CodeVerificationScreen,
 * токены уже сохранены в TokenManager).
 * Здесь просто обновляем имя/фамилию через PUT /profile.
 */
class NameInputViewModel(
    private val userProfileUpdater: UserProfileUpdater,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NameInputUiState())
    val uiState: StateFlow<NameInputUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<NameInputNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<NameInputNavEvent> = _navEvent.asSharedFlow()

    fun onEvent(event: NameInputEvent) {
        when (event) {
            is NameInputEvent.UpdateName -> updateName(event.name)
            is NameInputEvent.UpdateSurname -> updateSurname(event.surname)
            NameInputEvent.Continue -> validateAndSubmit()
        }
    }

    private fun updateName(name: String) {
        _uiState.value =
            _uiState.value.copy(
                name = name,
                nameError = null,
                isSubmitted = false,
            )
    }

    private fun updateSurname(surname: String) {
        _uiState.value =
            _uiState.value.copy(
                surname = surname,
                surnameError = null,
                isSubmitted = false,
            )
    }

    private fun validateName(name: String): NameFieldError? = when {
        name.isBlank() -> NameFieldError.Blank
        name.length < 2 -> NameFieldError.TooShort
        !name.all { it.isLetter() || it.isWhitespace() } -> NameFieldError.NonLetter
        else -> null
    }

    private fun validateSurname(surname: String): NameFieldError? = when {
        surname.isBlank() -> NameFieldError.Blank
        surname.length < 2 -> NameFieldError.TooShort
        !surname.all { it.isLetter() || it.isWhitespace() } -> NameFieldError.NonLetter
        else -> null
    }

    private fun validateAndSubmit() {
        val state = _uiState.value
        val nameError = validateName(state.name)
        val surnameError = validateSurname(state.surname)

        _uiState.value = state.copy(nameError = nameError, surnameError = surnameError)
        if (nameError != null || surnameError != null) return

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    nameError = null,
                    surnameError = null,
                )

            userProfileUpdater
                .updateName(
                    name = _uiState.value.name,
                    surname = _uiState.value.surname,
                ).onSuccess {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            isSubmitted = true,
                        )
                    _navEvent.emit(NameInputNavEvent.NavigateToSuccess)
                }.onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        nameError = NameFieldError.Remote(ErrorType.Unknown),
                    )
                }
        }
    }
}
