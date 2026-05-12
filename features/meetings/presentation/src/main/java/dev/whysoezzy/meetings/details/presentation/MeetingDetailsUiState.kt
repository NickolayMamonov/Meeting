package dev.whysoezzy.meetings.details.presentation

import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitCommunityHost
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.models.UIKitPersonHost

sealed class MeetingDetailsUiState {
    data object Loading : MeetingDetailsUiState()

    data class Success(
        val meetingId: Long,
        val imageUrl: String,
        val title: String,
        val dateTime: String,
        val address: UIKitAddress,
        val tags: List<UIKitMeetingTag>,
        val description: String,
        val host: UIKitPersonHost?,
        val nearestMetro: String,
        val participants: List<UIKitPerson>,
        val isUserJoined: Boolean,
        val totalPlaces: Int,
        val community: UIKitCommunityHost?,
        val otherMeetings: List<UIKitMeetingInfo>
    ) : MeetingDetailsUiState()

    data class Error(val message: String) : MeetingDetailsUiState()
}

sealed class MeetingDetailsEvent {
    data class LoadMeeting(val meetingId: Long) : MeetingDetailsEvent()
    data object JoinMeeting : MeetingDetailsEvent()
    data object LeaveMeeting : MeetingDetailsEvent()
    data class NavigateToProfile(val userId: Long) : MeetingDetailsEvent()
    data class NavigateToCommunity(val communityId: Long) : MeetingDetailsEvent()
    data class NavigateToMeeting(val meetingId: Long) : MeetingDetailsEvent()
    data object OpenMap : MeetingDetailsEvent()
    data object ShareMeeting : MeetingDetailsEvent()
}