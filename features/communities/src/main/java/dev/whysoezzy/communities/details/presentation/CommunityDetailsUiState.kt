package dev.whysoezzy.communities.details.presentation

import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.Person

sealed class CommunityDetailsUiState {
    object Loading : CommunityDetailsUiState()
    data class Success(
        // Основная информация о сообществе
        val communityId: Long,
        val imageUrl: String,
        val title: String,
        val tags: List<MeetingTag>,
        val description: String,
        val isSubscribed: Boolean,

        // Участники сообщества
        val subscribers: List<Person>,

        // Активные встречи
        val activeMeetings: List<MeetingInfo>,

        // Прошедшие встречи
        val pastMeetings: List<MeetingInfo>
    ) : CommunityDetailsUiState()

    data class Error(val message: String) : CommunityDetailsUiState()
}

sealed class CommunityDetailsEvent {
    data class LoadCommunity(val communityId: Long) : CommunityDetailsEvent()
    object ToggleSubscription : CommunityDetailsEvent()
    data class NavigateToMeeting(val meetingId: Long) : CommunityDetailsEvent()
    data class NavigateToProfile(val userId: Long) : CommunityDetailsEvent()
    object NavigateToSubscribers : CommunityDetailsEvent()
}