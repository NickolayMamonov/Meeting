package dev.whysoezzy.meetings.participants.presentation

import androidx.compose.runtime.Immutable
import com.whysoezzy.common.error.ErrorType
import dev.whysoezzy.uikit.components.layouts.PersonItem

@Immutable
sealed interface MeetingParticipantsUiState {
    data object Loading : MeetingParticipantsUiState

    data class Success(
        val participants: List<PersonItem>,
    ) : MeetingParticipantsUiState

    data class Error(
        val errorType: ErrorType,
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
