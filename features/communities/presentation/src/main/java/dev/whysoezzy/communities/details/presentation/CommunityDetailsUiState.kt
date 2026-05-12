package dev.whysoezzy.communities.details.presentation

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPerson


sealed class CommunityDetailsUiState {
    object Loading : CommunityDetailsUiState()
    data class Success(
        val communityId: Long,
        val imageUrl: String,
        val title: String,
        val tags: List<UIKitMeetingTag>,
        val description: String,
        val isSubscribed: Boolean,
        val subscribersCount: Int,
        val subscribers: List<UIKitPerson>,
        val activeMeetings: List<UIKitMeetingInfo>,
        val pastMeetings: List<UIKitMeetingInfo>
    ) : CommunityDetailsUiState()

    data class Error(val message: String) : CommunityDetailsUiState()
}

sealed class CommunityDetailsEvent {
    data class LoadCommunity(val communityId: Long) : CommunityDetailsEvent()
    object ToggleSubscription : CommunityDetailsEvent()
    data class NavigateToMeeting(val meetingId: Long) : CommunityDetailsEvent()
    data class NavigateToProfile(val userId: Long) : CommunityDetailsEvent()
    object NavigateToSubscribers : CommunityDetailsEvent()
    object ShareCommunity : CommunityDetailsEvent()
}