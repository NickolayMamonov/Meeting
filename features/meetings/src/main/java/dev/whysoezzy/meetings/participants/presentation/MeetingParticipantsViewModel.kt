package dev.whysoezzy.meetings.participants.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeetingParticipantsViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<MeetingParticipantsUiState>(MeetingParticipantsUiState.Loading)
    val uiState: StateFlow<MeetingParticipantsUiState> = _uiState.asStateFlow()

    fun onEvent(event: MeetingParticipantsEvent) {
        when (event) {
            is MeetingParticipantsEvent.LoadParticipants -> loadParticipants(event.meetingId)
        }
    }

    fun loadParticipants(meetingId: Long) {
        viewModelScope.launch {
            _uiState.value = MeetingParticipantsUiState.Loading

            try {
                // Mock data for now
                val mockParticipants = listOf(
                    Participant(1, "Иван Петров", isHost = true),
                    Participant(2, "Мария Сидорова"),
                    Participant(3, "Алексей Кузнецов"),
                    Participant(4, "Елена Васильева"),
                    Participant(5, "Дмитрий Николаев"),
                    Participant(6, "Анна Соколова"),
                    Participant(7, "Максим Орлов")
                )

                _uiState.value = MeetingParticipantsUiState.Success(
                    participants = mockParticipants
                )
            } catch (e: Exception) {
                _uiState.value = MeetingParticipantsUiState.Error(
                    message = e.message ?: "Не удалось загрузить участников"
                )
            }
        }
    }
}