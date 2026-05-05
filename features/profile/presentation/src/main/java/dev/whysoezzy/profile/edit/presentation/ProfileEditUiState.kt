package dev.whysoezzy.profile.edit.presentation

data class ProfileEditUiState(
    // Основная информация
    val name: String = "",
    val surname: String = "",
    val phone: String = "",
    val email: String = "",
    val city: String = "",
    val description: String = "",
    val avatarUrl: String? = null,

    // Интересы — названия выбранных тегов
    val interests: List<String> = emptyList(),
    // Все доступные теги (id -> name)
    val availableTags: Map<Long, String> = emptyMap(),

    // Социальные сети (Map: тип -> username)
    val socialMedias: Map<String, String> = emptyMap(),

    // Настройки приватности
    val showCommunities: Boolean = true,
    val showMeetings: Boolean = true,
    val notificationsEnabled: Boolean = true,

    // Ошибки валидации
    val nameError: String? = null,
    val surnameError: String? = null,
    val phoneError: String? = null,
    val emailError: String? = null,
    val descriptionError: String? = null,

    // Состояние загрузки
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,

    // Диалог подтверждения удаления
    val showDeleteConfirmDialog: Boolean = false
) {
    // Единое поле "Имя Фамилия" для отображения в форме
    val nameSurname: String
        get() = buildString {
            append(name)
            if (surname.isNotBlank()) append(" $surname")
        }.trim()

    // Валидация формы
    val isValid: Boolean
        get() = name.isNotBlank() &&
                nameError == null &&
                surnameError == null &&
                emailError == null &&
                descriptionError == null
}
