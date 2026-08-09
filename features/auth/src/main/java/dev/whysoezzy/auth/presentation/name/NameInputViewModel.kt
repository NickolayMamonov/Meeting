package dev.whysoezzy.auth.presentation.name

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.common.error.ErrorType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NameInputNavEvent {
    data object NavigateToSuccess : NameInputNavEvent

    data object NavigateToProfile : NameInputNavEvent

    data class ResolveFromDurableSession(
        val session: AuthSession,
    ) : NameInputNavEvent
}

class NameInputViewModel(
    private val mode: NameInputMode,
    private val userProfileUpdater: UserProfileUpdater,
    private val sessionRepository: AuthSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NameInputUiState())
    val uiState: StateFlow<NameInputUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<NameInputNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<NameInputNavEvent> = _navEvent.asSharedFlow()
    private var submission: Job? = null

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
        if (submission?.isActive == true) return

        val state = _uiState.value
        val nameError = validateName(state.name)
        val surnameError = validateSurname(state.surname)
        _uiState.value = state.copy(nameError = nameError, surnameError = surnameError)
        if (nameError != null || surnameError != null) return

        val name = state.name
        val surname = state.surname
        submission = viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    nameError = null,
                    surnameError = null,
                )

            try {
                val current = sessionRepository.read()
                when (mode) {
                    NameInputMode.Onboarding -> submitOnboarding(current, name, surname)
                    NameInputMode.ProfileCompletion -> submitProfileCompletion(current, name, surname)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showFailure(com.whysoezzy.auth.domain.models.AuthFailure.Unknown)
            }
        }
    }

    private suspend fun submitOnboarding(
        current: AuthSession,
        name: String,
        surname: String,
    ) {
        if (current.stage != AuthSession.Stage.NeedsName) {
            resolveCurrentSession()
            return
        }

        val result = userProfileUpdater.updateName(name, surname)
        if (result is AuthOutcome.Failure) {
            showFailure(result.reason)
            return
        }

        if (sessionRepository.compareAndSetStage(
                expected = AuthSession.Stage.NeedsName,
                next = AuthSession.Stage.Welcome,
            )
        ) {
            markSubmitted()
            _navEvent.emit(NameInputNavEvent.NavigateToSuccess)
        } else {
            resolveCurrentSession()
        }
    }

    private suspend fun submitProfileCompletion(
        current: AuthSession,
        name: String,
        surname: String,
    ) {
        if (current.stage != AuthSession.Stage.Ready) {
            resolveCurrentSession()
            return
        }

        val result = userProfileUpdater.updateName(name, surname)
        if (result is AuthOutcome.Failure) {
            showFailure(result.reason)
            return
        }

        if (sessionRepository.read().stage == AuthSession.Stage.Ready) {
            markSubmitted()
            _navEvent.emit(NameInputNavEvent.NavigateToProfile)
        } else {
            resolveCurrentSession()
        }
    }

    private suspend fun resolveCurrentSession() {
        _uiState.value = _uiState.value.copy(isLoading = false)
        _navEvent.emit(NameInputNavEvent.ResolveFromDurableSession(sessionRepository.read()))
    }

    private fun showFailure(failure: com.whysoezzy.auth.domain.models.AuthFailure) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                nameError = NameFieldError.Remote(failure.toErrorType()),
            )
    }

    private fun com.whysoezzy.auth.domain.models.AuthFailure.toErrorType(): ErrorType =
        when (this) {
            com.whysoezzy.auth.domain.models.AuthFailure.NoConnection -> ErrorType.NoConnection
            com.whysoezzy.auth.domain.models.AuthFailure.Unauthorized -> ErrorType.Unauthorized
            com.whysoezzy.auth.domain.models.AuthFailure.Server -> ErrorType.Server
            else -> ErrorType.Unknown
        }

    private suspend fun markSubmitted() {
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                isSubmitted = true,
            )
    }
}
