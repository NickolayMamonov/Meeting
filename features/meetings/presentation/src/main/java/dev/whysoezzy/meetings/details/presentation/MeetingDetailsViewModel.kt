package dev.whysoezzy.meetings.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.usecase.GetMeetingByIdUseCase
import com.whysoezzy.domain.usecase.JoinMeetingUseCase
import com.whysoezzy.domain.usecase.LeaveMeetingUseCase
import dev.whysoezzy.meetings.mappers.toUIKit
import dev.whysoezzy.meetings.mappers.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeetingDetailsViewModel(
    private val getMeetingByIdUseCase: GetMeetingByIdUseCase,
    private val joinMeetingUseCase: JoinMeetingUseCase,
    private val leaveMeetingUseCase: LeaveMeetingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<MeetingDetailsUiState>(MeetingDetailsUiState.Loading)
    val uiState: StateFlow<MeetingDetailsUiState> = _uiState.asStateFlow()

    private var currentMeetingId: Long? = null
    fun onEvent(event: MeetingDetailsEvent) {
        when (event) {
            is MeetingDetailsEvent.LoadMeeting -> loadMeeting(event.meetingId)
            MeetingDetailsEvent.JoinMeeting -> joinMeeting()
            MeetingDetailsEvent.LeaveMeeting -> leaveMeeting()
            is MeetingDetailsEvent.NavigateToProfile -> {
                // Навигация к профилю пользователя
            }

            is MeetingDetailsEvent.NavigateToCommunity -> {
                // Навигация к сообществу
            }

            is MeetingDetailsEvent.NavigateToMeeting -> {
                // Навигация к другой встрече
            }

            MeetingDetailsEvent.OpenMap -> {
                // Открытие карты
            }

            MeetingDetailsEvent.ShareMeeting -> {
                // Поделиться встречей
            }
        }
    }

    private fun loadMeeting(meetingId: Long) {
        viewModelScope.launch {
            _uiState.value = MeetingDetailsUiState.Loading
            currentMeetingId = meetingId

            getMeetingByIdUseCase(meetingId)
                .onSuccess { meeting ->
                    _uiState.value = MeetingDetailsUiState.Success(
                        meetingId = meeting.id,
                        imageUrl = meeting.imageUrl,
                        title = meeting.title,
                        dateTime = meeting.date,
                        address = meeting.address.toUIKit(),
                        tags = meeting.tags.toUIKitMeetingTags(),
                        description = meeting.description,
                        host = meeting.personHost?.toUIKitPersonHost(),
                        nearestMetro = extractMetroFromAddress(meeting.address.address),
                        participants = meeting.participants.map { it.toUIKit() },
                        isUserJoined = meeting.isUserInParticipants,
                        totalPlaces = meeting.capacity,
                        community = meeting.communityHost?.toUIKitCommunityHost(),
                        otherMeetings = meeting.communityHost?.meetingsInfo?.toUIKitMeetingInfoList() ?: emptyList()
                    )
                }
                .onFailure { exception ->
                    _uiState.value = MeetingDetailsUiState.Error(
                        message = exception.message ?: "Не удалось загрузить информацию о встрече"
                    )
                }
        }
    }

    private fun joinMeeting() {
        val meetingId = currentMeetingId ?: return
        val currentState = _uiState.value as? MeetingDetailsUiState.Success ?: return
        if (currentState.isUserJoined) return
        viewModelScope.launch {
            _uiState.value = currentState.copy(isUserJoined = true)

            joinMeetingUseCase(meetingId)
                .onSuccess {

                }
                .onFailure { exception ->
                    _uiState.value = currentState.copy(isUserJoined = false)
                    println("Failed to join meeting: ${exception.message}")

                }
        }
    }

    private fun leaveMeeting() {
        val meetingId = currentMeetingId ?: return
        val currentState = _uiState.value as? MeetingDetailsUiState.Success ?: return
        if (!currentState.isUserJoined) return
        viewModelScope.launch {
            _uiState.value = currentState.copy(isUserJoined = false)
            leaveMeetingUseCase(meetingId)
                .onSuccess {

                }
                .onFailure { exception ->
                    _uiState.value = currentState.copy(isUserJoined = true)
                    println("Failed to leave meeting: ${exception.message}")
                }
        }
    }
    private fun extractMetroFromAddress(address: String): String {
        return if (address.contains("М.")) {
            address.substringAfter("М.").substringBefore(",").trim()
        } else {
            "Не указано"
        }
    }
}