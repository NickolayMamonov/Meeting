package dev.whysoezzy.meetings.details.presentation

import dev.whysoezzy.domain.models.CommunityHost
import dev.whysoezzy.domain.models.MeetingAddress
import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.Person
import dev.whysoezzy.domain.models.PersonHost

sealed class MeetingDetailsUiState {
    object Loading : MeetingDetailsUiState()

    data class Success(
        // Основная информация о встрече
        val meetingId: Long,
        val imageUrl: String,
        val title: String,
        val dateTime: String,
        val address: MeetingAddress,
        val tags: List<MeetingTag>,
        val description: String,

        // Ведущий мероприятия
        val host: PersonHost,

        // Карта с ближайшим метро
        val nearestMetro: String,

        // Участники
        val participants: List<Person>,
        val isUserJoined: Boolean,
        val totalPlaces: Int,

        // Организатор-сообщество
        val community: CommunityHost,

        // Другие встречи сообщества
        val otherMeetings: List<MeetingInfo>
    ) : MeetingDetailsUiState()

    data class Error(val message: String) : MeetingDetailsUiState()
}

sealed class MeetingDetailsEvent {
    data class LoadMeeting(val meetingId: Long) : MeetingDetailsEvent()
    object JoinMeeting : MeetingDetailsEvent()
    object LeaveMeeting : MeetingDetailsEvent()
    data class NavigateToProfile(val userId: Long) : MeetingDetailsEvent()
    data class NavigateToCommunity(val communityId: Long) : MeetingDetailsEvent()
    data class NavigateToMeeting(val meetingId: Long) : MeetingDetailsEvent()
    object OpenMap : MeetingDetailsEvent()
    object ShareMeeting : MeetingDetailsEvent()
}