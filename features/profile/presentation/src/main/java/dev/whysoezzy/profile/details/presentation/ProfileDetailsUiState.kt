package dev.whysoezzy.profile.details.presentation

import dev.whysoezzy.domain.models.CommunityInfo
import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.SocialMediaInfo

sealed class ProfileDetailsUiState {
    object Loading : ProfileDetailsUiState()
    data class Success(
        val userId: Long,
        val name: String,
        val surname: String,
        val email: String,
        val description: String,
        val avatarUrl: String?,
        val isOwnProfile: Boolean,
        val socialMedias: List<SocialMediaInfo>,
        val userMeetings: List<MeetingInfo>,
        val userCommunities: List<CommunityInfo>,
        val subscribedCommunityIds: Set<Long> = emptySet()
    ) : ProfileDetailsUiState()

    data class Error(val message: String) : ProfileDetailsUiState()
}

sealed class ProfileDetailsEvent {
    data class LoadProfile(val userId: Long?) : ProfileDetailsEvent()
    object EditProfile : ProfileDetailsEvent()
    object ShareProfile : ProfileDetailsEvent()
    data class NavigateToMeeting(val meetingId: Long) : ProfileDetailsEvent()
    data class NavigateToCommunity(val communityId: Long) : ProfileDetailsEvent()
    data class OpenSocialMedia(val url: String) : ProfileDetailsEvent()
    data class ToggleCommunitySubscription(val communityId: Long, val isSubscribed: Boolean) :
        ProfileDetailsEvent()
}
