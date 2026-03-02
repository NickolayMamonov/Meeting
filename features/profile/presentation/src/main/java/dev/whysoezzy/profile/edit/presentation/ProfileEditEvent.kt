package dev.whysoezzy.profile.edit.presentation

sealed class ProfileEditEvent {
    // Основная информация
    data class UpdateName(val name: String) : ProfileEditEvent()
    data class UpdateSurname(val surname: String) : ProfileEditEvent()
    data class UpdatePhone(val phone: String) : ProfileEditEvent()
    data class UpdateEmail(val email: String) : ProfileEditEvent()
    data class UpdateCity(val city: String) : ProfileEditEvent()
    data class UpdateDescription(val description: String) : ProfileEditEvent()

    // Аватар
    data object ChangeAvatar : ProfileEditEvent()

    // Интересы
    data object AddInterest : ProfileEditEvent()  // Открыть диалог
    data class AddInterestWithText(val interest: String) : ProfileEditEvent()  // для обратной совместимости
    data class RemoveInterest(val interest: String) : ProfileEditEvent()
    data class ToggleTag(val tagId: Long, val tagName: String) : ProfileEditEvent()

    // Социальные сети
    data class UpdateSocialMedia(val type: String, val username: String) : ProfileEditEvent()

    // Настройки приватности
    data object ToggleShowCommunities : ProfileEditEvent()
    data object ToggleShowMeetings : ProfileEditEvent()
    data object ToggleNotifications : ProfileEditEvent()

    // Действия
    data object Save : ProfileEditEvent()
    data object DeleteProfile : ProfileEditEvent()
}