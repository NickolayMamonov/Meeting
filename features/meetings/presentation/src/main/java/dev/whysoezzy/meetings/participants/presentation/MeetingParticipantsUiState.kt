package dev.whysoezzy.meetings.participants.presentation

import com.whysoezzy.domain.models.Person

sealed class MeetingParticipantsUiState {
    data object Loading : MeetingParticipantsUiState()
    data class Success(
        val meetingTitle: String,
        val participants: List<Person>
    ) : MeetingParticipantsUiState()

    data class Error(val message: String) : MeetingParticipantsUiState()
}

sealed class MeetingParticipantsEvent {
    data class LoadParticipants(val meetingId: Long) : MeetingParticipantsEvent()
    data class NavigateToProfile(val userId: Long) : MeetingParticipantsEvent()
}