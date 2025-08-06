package dev.whysoezzy.meetings.details.presentation

sealed class MeetingDetailsUiState {
    object Loading : MeetingDetailsUiState()
    data class Success(
        val meetingTitle: String,
        val meetingDescription: String,
        val meetingDate: String,
        val meetingAddress: String,
        val participantsCount: Int,
        val isUserJoined: Boolean
    ) : MeetingDetailsUiState()

    data class Error(val message: String) : MeetingDetailsUiState()
}

sealed class MeetingDetailsEvent {
    data class LoadMeeting(val meetingId: Long) : MeetingDetailsEvent()
    object JoinMeeting : MeetingDetailsEvent()
    object LeaveMeeting : MeetingDetailsEvent()
}