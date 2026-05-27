package dev.whysoezzy.profile.edit.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.models.SocialMediaInfo
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.usecase.GetAllTagsUseCase
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase
import com.whysoezzy.network.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

sealed class ProfileEditNavEvent {
    object NavigateBack : ProfileEditNavEvent()
}

class ProfileEditViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val getAllTagsUseCase: GetAllTagsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<ProfileEditNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<ProfileEditNavEvent> = _navEvent.asSharedFlow()

    private var currentUser: User? = null

    init {
        loadProfile()
        loadAvailableTags()
    }

    fun onEvent(event: ProfileEditEvent) {
        when (event) {
            is ProfileEditEvent.UpdateNameSurname -> updateNameSurname(event.nameSurname)
            is ProfileEditEvent.UpdateName -> updateName(event.name)
            is ProfileEditEvent.UpdateSurname -> updateSurname(event.surname)
            is ProfileEditEvent.UpdateEmail -> updateEmail(event.email)
            is ProfileEditEvent.UpdateCity -> updateCity(event.city)
            is ProfileEditEvent.UpdateDescription -> updateDescription(event.description)
            is ProfileEditEvent.ChangeAvatar -> changeAvatar()
            is ProfileEditEvent.AddInterest -> { /* открывается через UI-флаг */ }
            is ProfileEditEvent.AddInterestWithText -> addInterestWithText(event.interest)
            is ProfileEditEvent.RemoveInterest -> removeInterest(event.interest)
            is ProfileEditEvent.ToggleTag -> toggleTag(event.tagId, event.tagName)
            is ProfileEditEvent.UpdateSocialMedia -> updateSocialMedia(event.type, event.username)
            is ProfileEditEvent.ToggleShowCommunities -> toggleShowCommunities()
            is ProfileEditEvent.ToggleShowMeetings -> toggleShowMeetings()
            is ProfileEditEvent.ToggleNotifications -> toggleNotifications()
            is ProfileEditEvent.Save -> saveProfile()
            is ProfileEditEvent.DeleteProfile -> showDeleteDialog()
            is ProfileEditEvent.ConfirmDeleteProfile -> confirmDeleteProfile()
            is ProfileEditEvent.DismissDeleteProfile -> dismissDeleteDialog()
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
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
                        avatarUrl = user.avatar.takeIf { it.isNotBlank() },
                        interests = user.interests.map { it.name },
                        socialMedias = extractSocialMedias(user.socialMedias),
                        showCommunities = user.showCommunities,
                        showMeetings = user.showMeetings,
                        notificationsEnabled = user.notificationsEnabled,
                        isLoading = false,
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toUserMessage(),
                    )
                }
        }
    }

    private fun loadAvailableTags() {
        viewModelScope.launch {
            getAllTagsUseCase()
                .onSuccess { tags ->
                    _uiState.value = _uiState.value.copy(
                        availableTags = tags.associate { it.id to it.name },
                    )
                }
        }
    }

    /**
     * Единое поле "Имя Фамилия" — первое слово = имя, остальное = фамилия.
     */
    private fun updateNameSurname(nameSurname: String) {
        val parts = nameSurname.trim().split(" ", limit = 2)
        val name = parts.getOrElse(0) { "" }
        val surname = parts.getOrElse(1) { "" }
        _uiState.value = _uiState.value.copy(
            name = name,
            surname = surname,
            nameError = validateName(name),
            surnameError = validateSurname(surname),
            isSaved = false,
        )
    }

    private fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = validateName(name),
            isSaved = false,
        )
    }

    private fun updateSurname(surname: String) {
        _uiState.value = _uiState.value.copy(
            surname = surname,
            surnameError = validateSurname(surname),
            isSaved = false,
        )
    }

    private fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = validateEmail(email),
            isSaved = false,
        )
    }

    private fun updateCity(city: String) {
        _uiState.value = _uiState.value.copy(city = city, isSaved = false)
    }

    private fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(
            description = description,
            descriptionError = validateDescription(description),
            isSaved = false,
        )
    }

    private fun changeAvatar() {
        // Обрабатывается в UI через ActivityResultLauncher
    }

    private fun addInterestWithText(interest: String) {
        if (interest.isBlank()) return
        val list = _uiState.value.interests.toMutableList()
        if (!list.contains(interest)) {
            list.add(interest)
            _uiState.value = _uiState.value.copy(interests = list, isSaved = false)
        }
    }

    private fun removeInterest(interest: String) {
        val list = _uiState.value.interests.toMutableList()
        list.remove(interest)
        _uiState.value = _uiState.value.copy(interests = list, isSaved = false)
    }

    private fun toggleTag(tagId: Long, tagName: String) {
        val list = _uiState.value.interests.toMutableList()
        if (list.contains(tagName)) list.remove(tagName) else list.add(tagName)
        _uiState.value = _uiState.value.copy(interests = list, isSaved = false)
    }

    private fun updateSocialMedia(type: String, username: String) {
        val map = _uiState.value.socialMedias.toMutableMap()
        if (username.isBlank()) map.remove(type) else map[type] = username
        _uiState.value = _uiState.value.copy(socialMedias = map, isSaved = false)
    }

    private fun extractSocialMedias(list: List<SocialMediaInfo>): Map<String, String> =
        list.associate { it.type.name.lowercase() to it.username }

    private fun toggleShowCommunities() {
        _uiState.value = _uiState.value.copy(
            showCommunities = !_uiState.value.showCommunities,
            isSaved = false,
        )
    }

    private fun toggleShowMeetings() {
        _uiState.value = _uiState.value.copy(
            showMeetings = !_uiState.value.showMeetings,
            isSaved = false,
        )
    }

    private fun toggleNotifications() {
        _uiState.value = _uiState.value.copy(
            notificationsEnabled = !_uiState.value.notificationsEnabled,
            isSaved = false,
        )
    }

    private fun saveProfile() {
        val state = _uiState.value
        val nameError = validateName(state.name)
        val surnameError = validateSurname(state.surname)
        val emailError = validateEmail(state.email)
        val descriptionError = validateDescription(state.description)

        _uiState.value = state.copy(
            nameError = nameError,
            surnameError = surnameError,
            emailError = emailError,
            descriptionError = descriptionError,
        )
        if (nameError != null ||
            surnameError != null ||
            emailError != null ||
            descriptionError != null
        ) {
            return
        }

        val user = currentUser ?: run {
            _uiState.value = _uiState.value.copy(error = "Профиль ещё не загружен, попробуйте позже")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            val socialMediasList: List<SocialMediaInfo>
            val updatedInterests: List<Tag>

            try {
                socialMediasList = state.socialMedias.mapNotNull { (type, username) ->
                    if (username.isBlank()) return@mapNotNull null
                    try {
                        val smType = SocialMediaType.valueOf(type.uppercase())
                        SocialMediaInfo(
                            type = smType,
                            url = generateSocialMediaUrl(smType, username),
                            username = username,
                        )
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }

                updatedInterests = state.interests.mapNotNull { name ->
                    val id = state.availableTags.entries
                        .firstOrNull { it.value == name }
                        ?.key
                    if (id != null) {
                        Tag(id = id, name = name)
                    } else {
                        user.interests.firstOrNull { it.name == name }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare profile data for save")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Ошибка при подготовке данных профиля",
                )
                return@launch
            }

            updateUserProfileUseCase(
                user.copy(
                    name = state.name,
                    surname = state.surname,
                    phone = state.phone,
                    email = state.email,
                    city = state.city,
                    bio = state.description,
                    avatar = state.avatarUrl ?: "",
                    socialMedias = socialMediasList,
                    interests = updatedInterests,
                    showCommunities = state.showCommunities,
                    showMeetings = state.showMeetings,
                    notificationsEnabled = state.notificationsEnabled,
                ),
            ).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                _navEvent.tryEmit(ProfileEditNavEvent.NavigateBack)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toUserMessage(),
                )
            }
        }
    }

    private fun showDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmDialog = true)
    }

    private fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmDialog = false)
    }

    private fun confirmDeleteProfile() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmDialog = false)
        // TODO: вызов deleteAccountUseCase когда будет добавлен
    }

    private fun validateName(name: String): String? = when {
        name.isBlank() -> "Имя не может быть пустым"
        name.length < 2 -> "Минимум 2 символа"
        else -> null
    }

    private fun validateSurname(surname: String): String? = when {
        surname.isBlank() -> null
        surname.length < 2 -> "Минимум 2 символа"
        else -> null
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> null // email опционален
        !email.contains("@") -> "Введите корректный email"
        else -> null
    }

    private fun validateDescription(description: String): String? = when {
        description.length > 500 -> "Максимум 500 символов"
        else -> null
    }

    private fun generateSocialMediaUrl(type: SocialMediaType, username: String): String =
        when (type) {
            SocialMediaType.TELEGRAM -> "https://t.me/$username"
            SocialMediaType.HABR -> "https://habr.com/users/$username"
            SocialMediaType.LINKEDIN -> "https://linkedin.com/in/$username"
            SocialMediaType.GITHUB -> "https://github.com/$username"
        }
}
