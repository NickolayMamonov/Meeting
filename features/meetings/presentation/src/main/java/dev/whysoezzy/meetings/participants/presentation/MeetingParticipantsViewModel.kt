package dev.whysoezzy.meetings.participants.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.usecase.GetMeetingByIdUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeetingParticipantsViewModel(
    private val getMeetingByIdUseCase: GetMeetingByIdUseCase
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<MeetingParticipantsUiState>(MeetingParticipantsUiState.Loading)
    val uiState: StateFlow<MeetingParticipantsUiState> = _uiState.asStateFlow()

    fun onEvent(event: MeetingParticipantsEvent) {
        when (event) {
            is MeetingParticipantsEvent.LoadParticipants -> loadParticipants(event.meetingId)
            is MeetingParticipantsEvent.NavigateToProfile -> {
                // Навигация к профилю
            }
        }
    }

    fun loadParticipants(meetingId: Long) {
        viewModelScope.launch {
            _uiState.value = MeetingParticipantsUiState.Loading

            getMeetingByIdUseCase(meetingId)
                .onSuccess { meeting ->
                    _uiState.value = MeetingParticipantsUiState.Success(
                        meetingTitle = meeting.title,
                        participants = meeting.participants
                    )
                }
                .onFailure { exception ->
                    _uiState.value = MeetingParticipantsUiState.Error(
                        message = exception.message ?: "Не удалось загрузить участников встречи"
                    )
                }
        }
    }
}