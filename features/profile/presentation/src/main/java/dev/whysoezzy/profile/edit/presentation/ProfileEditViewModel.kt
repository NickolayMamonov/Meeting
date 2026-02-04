package dev.whysoezzy.profile.edit.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileEditViewModel(
    private val getUserProfileUseCase: GetCurrentUserUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null

    init {
        loadProfile()
    }

    fun onEvent(event: ProfileEditEvent) {
        when (event) {
            is ProfileEditEvent.UpdateName -> updateName(event.name)
            is ProfileEditEvent.UpdateSurname -> updateSurname(event.surname)
            is ProfileEditEvent.UpdateEmail -> updateEmail(event.email)
            is ProfileEditEvent.UpdateCity -> updateCity(event.city)
            is ProfileEditEvent.UpdateDescription -> updateDescription(event.description)
            ProfileEditEvent.Save -> saveProfile()
            ProfileEditEvent.LoadProfile -> loadProfile()
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                getUserProfileUseCase()
                    .onSuccess { user ->
                        currentUser = user
                        _uiState.value = _uiState.value.copy(
                            name = user.name,
                            surname = user.surname,
                            city = user.city,
                            description = user.bio,
                            email = user.email,
                            imageUrl = user.avatar,
                            interests = user.interests,
                            socialMedias = user.socialMedia,
                            isLoading = false
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "Не удалось загрузить профиль"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Произошла ошибка"
                )
            }
        }
    }

    private fun updateName(name: String) {
        val nameError = validateName(name)
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = nameError,
            isSaved = false
        )
    }

    private fun updateSurname(surname: String) {
        val surnameError = validateSurname(surname)
        _uiState.value = _uiState.value.copy(
            surname = surname,
            surnameError = surnameError,
            isSaved = false
        )
    }

    private fun updateEmail(email: String) {
        val emailError = validateEmail(email)
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = emailError,
            isSaved = false
        )
    }

    private fun updateCity(city: String) {
        _uiState.value = _uiState.value.copy(
            city = city,
            isSaved = false
        )
    }

    private fun updateDescription(description: String) {
        val descriptionError = validateDescription(description)
        _uiState.value = _uiState.value.copy(
            description = description,
            descriptionError = descriptionError,
            isSaved = false
        )
    }

    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Имя не может быть пустым"
            name.length < 2 -> "Имя должно содержать минимум 2 символа"
            else -> null
        }
    }

    private fun validateSurname(surname: String): String? {
        return when {
            surname.isBlank() -> "Фамилия не может быть пустой"
            surname.length < 2 -> "Фамилия должна содержать минимум 2 символа"
            else -> null
        }
    }

    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "Email не может быть пустым"
            !email.contains("@") -> "Введите корректный email"
            else -> null
        }
    }

    private fun validateDescription(description: String): String? {
        return when {
            description.length > 500 -> "Описание не может быть длиннее 500 символов"
            else -> null
        }
    }

    private fun saveProfile() {
        val currentState = _uiState.value

        // Validate all fields
        val nameError = validateName(currentState.name)
        val surnameError = validateSurname(currentState.surname)
        val emailError = validateEmail(currentState.email)
        val descriptionError = validateDescription(currentState.description)

        _uiState.value = currentState.copy(
            nameError = nameError,
            surnameError = surnameError,
            emailError = emailError,
            descriptionError = descriptionError
        )

        if (nameError != null || surnameError != null || emailError != null || descriptionError != null) {
            return
        }

        val user = currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val updatedUser = user.copy(
                    name = currentState.name,
                    surname = currentState.surname,
                    city = currentState.city,
                    bio = currentState.description,
                    email = currentState.email,
                    avatar = currentState.imageUrl,
                    interests = currentState.interests,
                    socialMedia = currentState.socialMedias
                )

                updateUserProfileUseCase(updatedUser)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            isSaved = true
                        )
                    }
                    .onFailure { exception ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            error = exception.message ?: "Не удалось сохранить профиль"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Произошла ошибка при сохранении"
                )
            }
        }
    }
}