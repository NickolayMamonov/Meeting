package dev.whysoezzy.meetings.participants.presentation

sealed class MeetingParticipantsUiState {
    object Loading : MeetingParticipantsUiState()
    data class Success(
        val participants: List<Participant>
    ) : MeetingParticipantsUiState()

    data class Error(val message: String) : MeetingParticipantsUiState()
}

data class Participant(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
    val isHost: Boolean = false
)

sealed class MeetingParticipantsEvent {
    data class LoadParticipants(val meetingId: Long) : MeetingParticipantsEvent()
}