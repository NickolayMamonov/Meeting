package dev.whysoezzy.meetings.participants.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.common.dispatcher.DispatcherProvider
import com.whysoezzy.domain.usecase.GetMeetingParticipantsUseCase
import com.whysoezzy.network.toUserMessage
import dev.whysoezzy.meetings.mappers.toPersonItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MeetingParticipantsNavEvent {
    data class NavigateToProfile(
        val userId: Long,
    ) : MeetingParticipantsNavEvent
}

class MeetingParticipantsViewModel(
    private val getMeetingParticipantsUseCase: GetMeetingParticipantsUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<MeetingParticipantsUiState>(MeetingParticipantsUiState.Loading)
    val uiState: StateFlow<MeetingParticipantsUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<MeetingParticipantsNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<MeetingParticipantsNavEvent> = _navEvent.asSharedFlow()

    private var currentMeetingId: Long? = null

    fun onEvent(event: MeetingParticipantsEvent) {
        when (event) {
            is MeetingParticipantsEvent.LoadParticipants -> loadParticipants(event.meetingId)
            is MeetingParticipantsEvent.NavigateToProfile -> viewModelScope.launch {
                _navEvent.emit(MeetingParticipantsNavEvent.NavigateToProfile(event.userId))
            }
        }
    }

    private fun loadParticipants(meetingId: Long) {
        currentMeetingId = meetingId
        viewModelScope.launch {
            _uiState.value = MeetingParticipantsUiState.Loading

            getMeetingParticipantsUseCase(meetingId)
                .onSuccess { participants ->
                    val items = withContext(dispatchers.default) {
                        participants.map { it.toPersonItem() }
                    }
                    _uiState.value = MeetingParticipantsUiState.Success(participants = items)
                }.onFailure { exception ->
                    _uiState.value = MeetingParticipantsUiState.Error(
                        message = exception.toUserMessage(),
                    )
                }
        }
    }
}
