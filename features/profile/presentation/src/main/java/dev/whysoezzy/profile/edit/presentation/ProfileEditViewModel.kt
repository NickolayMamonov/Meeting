package dev.whysoezzy.profile.edit.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.models.SocialMediaInfo
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileEditViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
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
            // Основная информация
            is ProfileEditEvent.UpdateName -> updateName(event.name)
            is ProfileEditEvent.UpdateSurname -> updateSurname(event.surname)
            is ProfileEditEvent.UpdatePhone -> updatePhone(event.phone)
            is ProfileEditEvent.UpdateEmail -> updateEmail(event.email)
            is ProfileEditEvent.UpdateCity -> updateCity(event.city)
            is ProfileEditEvent.UpdateDescription -> updateDescription(event.description)

            // Аватар
            is ProfileEditEvent.ChangeAvatar -> changeAvatar()

            // Интересы
            is ProfileEditEvent.AddInterest -> addInterest()
            is ProfileEditEvent.AddInterestWithText -> addInterestWithText(event.interest)
            is ProfileEditEvent.RemoveInterest -> removeInterest(event.interest)

            // Социальные сети
            is ProfileEditEvent.UpdateSocialMedia -> updateSocialMedia(event.type, event.username)

            // Настройки приватности
            is ProfileEditEvent.ToggleShowCommunities -> toggleShowCommunities()
            is ProfileEditEvent.ToggleShowMeetings -> toggleShowMeetings()
            is ProfileEditEvent.ToggleNotifications -> toggleNotifications()

            // Действия
            is ProfileEditEvent.Save -> saveProfile()
            is ProfileEditEvent.DeleteProfile -> deleteProfile()
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                getCurrentUserUseCase()
                    .onSuccess { user ->
                        currentUser = user
                        _uiState.value = _uiState.value.copy(
                            name = user.name,
                            surname = user.surname,
                            phone = user.phone,
                            email = user.email,
                            city = user.city,
                            description = user.bio,
                            avatarUrl = user.avatar,
                            interests = emptyList(),
                            socialMedias = extractSocialMedias(user.socialMedias),
                            showCommunities = true,
                            showMeetings = true,
                            notificationsEnabled = true,
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

    private fun updatePhone(phone: String) {
        val phoneError = validatePhone(phone)
        _uiState.value = _uiState.value.copy(
            phone = phone,
            phoneError = phoneError,
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

    private fun changeAvatar() {
        // TODO: Открыть галерею/камеру для выбора фото
        println("ChangeAvatar clicked - TODO: implement photo picker")
    }

    private fun addInterest() {
        // Это событие просто открывает диалог в UI
        // Реальное добавление происходит в addInterestWithText
    }

    private fun addInterestWithText(interest: String) {
        if (interest.isBlank()) return

        val currentInterests = _uiState.value.interests.toMutableList()

        if (!currentInterests.contains(interest)) {
            currentInterests.add(interest)
            _uiState.value = _uiState.value.copy(
                interests = currentInterests,
                isSaved = false
            )
        }
    }

    private fun removeInterest(interest: String) {
        val currentInterests = _uiState.value.interests.toMutableList()
        currentInterests.remove(interest)
        _uiState.value = _uiState.value.copy(
            interests = currentInterests,
            isSaved = false
        )
    }


    private fun updateSocialMedia(type: String, username: String) {
        val currentSocialMedias = _uiState.value.socialMedias.toMutableMap()
        if (username.isBlank()) {
            currentSocialMedias.remove(type)
        } else {
            currentSocialMedias[type] = username
        }
        _uiState.value = _uiState.value.copy(
            socialMedias = currentSocialMedias,
            isSaved = false
        )
    }

    private fun extractSocialMedias(socialMedias: List<SocialMediaInfo>): Map<String, String> {
        return socialMedias.associate { socialMedia ->
            val type = socialMedia.type.name.lowercase()
            val username = socialMedia.username
            type to username
        }
    }

    private fun generateSocialMediaUrl(type: SocialMediaType, username: String): String {
        return when (type) {
            SocialMediaType.TELEGRAM -> "https://t.me/$username"
            SocialMediaType.HABR -> "https://habr.com/users/$username"
            SocialMediaType.LINKEDIN -> "https://linkedin.com/in/$username"
            SocialMediaType.GITHUB -> "https://github.com/$username"
        }
    }


    private fun toggleShowCommunities() {
        _uiState.value = _uiState.value.copy(
            showCommunities = !_uiState.value.showCommunities,
            isSaved = false
        )
    }

    private fun toggleShowMeetings() {
        _uiState.value = _uiState.value.copy(
            showMeetings = !_uiState.value.showMeetings,
            isSaved = false
        )
    }

    private fun toggleNotifications() {
        _uiState.value = _uiState.value.copy(
            notificationsEnabled = !_uiState.value.notificationsEnabled,
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
            surname.isBlank() -> null // Фамилия может быть пустой
            surname.length < 2 -> "Фамилия должна содержать минимум 2 символа"
            else -> null
        }
    }

    private fun validatePhone(phone: String): String? {
        return when {
            phone.isBlank() -> null // Телефон опционален
            phone.length < 10 -> "Введите корректный номер телефона"
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

        val nameError = validateName(currentState.name)
        val surnameError = validateSurname(currentState.surname)
        val phoneError = validatePhone(currentState.phone)
        val emailError = validateEmail(currentState.email)
        val descriptionError = validateDescription(currentState.description)

        _uiState.value = currentState.copy(
            nameError = nameError,
            surnameError = surnameError,
            phoneError = phoneError,
            emailError = emailError,
            descriptionError = descriptionError
        )

        if (nameError != null || surnameError != null || phoneError != null ||
            emailError != null || descriptionError != null) {
            return
        }

        val user = currentUser ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val socialMediasList = currentState.socialMedias.mapNotNull { (type, username) ->
                    if (username.isNotBlank()) {
                        try {
                            val socialMediaType = SocialMediaType.valueOf(type.uppercase())
                            val url = generateSocialMediaUrl(socialMediaType, username)
                            SocialMediaInfo(
                                type = socialMediaType,
                                url = url,
                                username = username
                            )
                        } catch (e: IllegalArgumentException) {
                            null // Если тип не распознан
                        }
                    } else {
                        null
                    }
                }

                val updatedUser = user.copy(
                    name = currentState.name,
                    surname = currentState.surname,
                    phone = currentState.phone,
                    email = currentState.email,
                    city = currentState.city,
                    bio = currentState.description,
                    avatar = currentState.avatarUrl ?: "Unexpected avatar",
                    socialMedias = socialMediasList
                    // TODO: Сохранить interests (если добавить в User)
                    // TODO: Сохранить настройки приватности
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


    private fun deleteProfile() {
        // TODO: Показать диалог подтверждения
        println("DeleteProfile clicked - TODO: implement delete confirmation dialog")
    }
}


