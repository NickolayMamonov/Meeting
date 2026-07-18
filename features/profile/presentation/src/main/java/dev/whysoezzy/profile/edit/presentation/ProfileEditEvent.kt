package dev.whysoezzy.profile.edit.presentation

import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.AvatarUpload

sealed interface ProfileEditEvent {
    // Основная информация — единое поле "Имя Фамилия"
    data class UpdateNameSurname(
        val nameSurname: String,
    ) : ProfileEditEvent

    // Раздельные — для совместимости
    data class UpdateName(
        val name: String,
    ) : ProfileEditEvent

    data class UpdateSurname(
        val surname: String,
    ) : ProfileEditEvent

    data class UpdateEmail(
        val email: String,
    ) : ProfileEditEvent

    data class UpdateCity(
        val city: String,
    ) : ProfileEditEvent

    data class UpdateDescription(
        val description: String,
    ) : ProfileEditEvent

    // Аватар
    data object ChangeAvatar : ProfileEditEvent

    data class UploadAvatar(
        val upload: AvatarUpload,
    ) : ProfileEditEvent

    data object UploadAvatarFailed : ProfileEditEvent

    // Интересы
    data object AddInterest : ProfileEditEvent

    data class AddInterestWithText(
        val interest: String,
    ) : ProfileEditEvent

    data class RemoveInterest(
        val interest: String,
    ) : ProfileEditEvent

    data class ToggleTag(
        val tagId: Long,
        val tagName: String,
    ) : ProfileEditEvent

    // Социальные сети
    data class UpdateSocialMedia(
        val type: SocialMediaType,
        val username: String,
    ) : ProfileEditEvent

    // Настройки приватности
    data object ToggleShowCommunities : ProfileEditEvent

    data object ToggleShowMeetings : ProfileEditEvent

    data object ToggleNotifications : ProfileEditEvent

    // Действия
    data object Save : ProfileEditEvent

    data object DeleteProfile : ProfileEditEvent

    data object ConfirmDeleteProfile : ProfileEditEvent

    data object DismissDeleteProfile : ProfileEditEvent
}
