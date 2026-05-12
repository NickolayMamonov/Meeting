package dev.whysoezzy.profile.details.presentation

import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitSocialMediaInfo

sealed class ProfileDetailsUiState {
    data object Loading : ProfileDetailsUiState()
    data class Success(
        val userId: Long,
        val name: String,
        val surname: String,
        val email: String,
        val city: String = "",
        val description: String,
        val avatarUrl: String?,
        val interests: List<String> = emptyList(),
        val isOwnProfile: Boolean,
        val socialMedias: List<UIKitSocialMediaInfo>,
        val userMeetings: List<UIKitMeetingInfo>,
        val userCommunities: List<UIKitCommunityInfo>,
    ) : ProfileDetailsUiState()

    data class Error(val message: String) : ProfileDetailsUiState()
}

sealed class ProfileDetailsEvent {
    data class LoadProfile(val userId: Long?) : ProfileDetailsEvent()
    data object EditProfile : ProfileDetailsEvent()
    data object ShareProfile : ProfileDetailsEvent()
    data object Logout : ProfileDetailsEvent()
    data class NavigateToMeeting(val meetingId: Long) : ProfileDetailsEvent()
    data class NavigateToCommunity(val communityId: Long) : ProfileDetailsEvent()
    data class OpenSocialMedia(val url: String) : ProfileDetailsEvent()
    data class ToggleCommunitySubscription(val communityId: Long, val isSubscribed: Boolean) :
        ProfileDetailsEvent()
}

sealed class ProfileDetailsNavEvent {
    data object NavigateToAuth : ProfileDetailsNavEvent()
    /** Профиль загружен, но имя пустое — отправляем заполнить */
    data object NavigateToNameInput : ProfileDetailsNavEvent()
    data class NavigateToMeeting(val meetingId: Long) : ProfileDetailsNavEvent()
    data class NavigateToCommunity(val communityId: Long) : ProfileDetailsNavEvent()
    data object NavigateToEdit : ProfileDetailsNavEvent()
    data class OpenSocialMedia(val url: String) : ProfileDetailsNavEvent()
    data class ShareProfile(val name: String, val shareText: String) : ProfileDetailsNavEvent()
}
