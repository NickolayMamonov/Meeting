package dev.whysoezzy.meetings.participants.presentation

import androidx.compose.runtime.Immutable
import com.whysoezzy.domain.models.Person

@Immutable
sealed interface MeetingParticipantsUiState {
    data object Loading : MeetingParticipantsUiState

    data class Success(
        val meetingTitle: String,
        val participants: List<Person>,
    ) : MeetingParticipantsUiState

    data class Error(
        val message: String,
    ) : MeetingParticipantsUiState
}

sealed interface MeetingParticipantsEvent {
    data class LoadParticipants(
        val meetingId: Long,
    ) : MeetingParticipantsEvent

    data class NavigateToProfile(
        val userId: Long,
    ) : MeetingParticipantsEvent
}
