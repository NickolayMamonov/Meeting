package dev.whysoezzy.meetings.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeetingDetailsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<MeetingDetailsUiState>(MeetingDetailsUiState.Loading)
    val uiState: StateFlow<MeetingDetailsUiState> = _uiState.asStateFlow()

    fun onEvent(event: MeetingDetailsEvent) {
        when (event) {
            is MeetingDetailsEvent.LoadMeeting -> loadMeeting(event.meetingId)
            MeetingDetailsEvent.JoinMeeting -> joinMeeting()
            MeetingDetailsEvent.LeaveMeeting -> leaveMeeting()
        }
    }

    fun loadMeeting(meetingId: Long) {
        viewModelScope.launch {
            _uiState.value = MeetingDetailsUiState.Loading

            try {
                // Mock data for now
                _uiState.value = MeetingDetailsUiState.Success(
                    meetingTitle = "Встреча разработчиков #$meetingId",
                    meetingDescription = "Обсуждение новых технологий и подходов в разработке мобильных приложений",
                    meetingDate = "15 декабря 2024, 19:00",
                    meetingAddress = "ул. Тверская, 15, офис 301",
                    participantsCount = 25,
                    isUserJoined = false
                )
            } catch (e: Exception) {
                _uiState.value = MeetingDetailsUiState.Error(
                    message = e.message ?: "Не удалось загрузить информацию о встрече"
                )
            }
        }
    }

    private fun joinMeeting() {
        val currentState = _uiState.value
        if (currentState is MeetingDetailsUiState.Success && !currentState.isUserJoined) {
            _uiState.value = currentState.copy(
                isUserJoined = true,
                participantsCount = currentState.participantsCount + 1
            )
        }
    }

    private fun leaveMeeting() {
        val currentState = _uiState.value
        if (currentState is MeetingDetailsUiState.Success && currentState.isUserJoined) {
            _uiState.value = currentState.copy(
                isUserJoined = false,
                participantsCount = currentState.participantsCount - 1
            )
        }
    }
}