package dev.whysoezzy.meetings.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.auth.domain.usecase.IsLoggedInUseCase
import com.whysoezzy.common.dispatcher.DispatcherProvider
import com.whysoezzy.common.utils.AddressUtils.extractMetroFromAddress
import com.whysoezzy.domain.usecase.GetMeetingByIdUseCase
import com.whysoezzy.domain.usecase.JoinMeetingUseCase
import com.whysoezzy.domain.usecase.LeaveMeetingUseCase
import com.whysoezzy.network.toErrorType
import dev.whysoezzy.meetings.mappers.toUIKit
import dev.whysoezzy.meetings.mappers.toUIKitCommunityHost
import dev.whysoezzy.meetings.mappers.toUIKitMeetingInfoList
import dev.whysoezzy.meetings.mappers.toUIKitMeetingTags
import dev.whysoezzy.meetings.mappers.toUIKitPersonHost
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MeetingDetailsNavEvent {
    data class NavigateToProfile(
        val userId: Long,
    ) : MeetingDetailsNavEvent

    data class NavigateToMeeting(
        val meetingId: Long,
    ) : MeetingDetailsNavEvent

    data class NavigateToCommunity(
        val communityId: Long,
    ) : MeetingDetailsNavEvent

    data class OpenMap(
        val latitude: Double,
        val longitude: Double,
        val address: String,
    ) : MeetingDetailsNavEvent

    data class ShareMeeting(
        val title: String,
        val shareText: String,
    ) : MeetingDetailsNavEvent

    data object NavigateToAuth : MeetingDetailsNavEvent
}

class MeetingDetailsViewModel(
    private val getMeetingByIdUseCase: GetMeetingByIdUseCase,
    private val joinMeetingUseCase: JoinMeetingUseCase,
    private val leaveMeetingUseCase: LeaveMeetingUseCase,
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MeetingDetailsUiState>(MeetingDetailsUiState.Loading)
    val uiState: StateFlow<MeetingDetailsUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<MeetingDetailsNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<MeetingDetailsNavEvent> = _navEvent.asSharedFlow()

    private var currentMeetingId: Long? = null

    private val isLoggedIn: StateFlow<Boolean> = isLoggedInUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    fun onEvent(event: MeetingDetailsEvent) {
        when (event) {
            is MeetingDetailsEvent.LoadMeeting -> loadMeeting(event.meetingId)
            MeetingDetailsEvent.JoinMeeting -> joinMeeting()
            MeetingDetailsEvent.LeaveMeeting -> leaveMeeting()
            is MeetingDetailsEvent.NavigateToProfile -> viewModelScope.launch {
                _navEvent.emit(MeetingDetailsNavEvent.NavigateToProfile(event.userId))
            }
            is MeetingDetailsEvent.NavigateToCommunity -> viewModelScope.launch {
                _navEvent.emit(MeetingDetailsNavEvent.NavigateToCommunity(event.communityId))
            }
            is MeetingDetailsEvent.NavigateToMeeting -> viewModelScope.launch {
                _navEvent.emit(MeetingDetailsNavEvent.NavigateToMeeting(event.meetingId))
            }
            MeetingDetailsEvent.OpenMap -> openMap()
            MeetingDetailsEvent.ShareMeeting -> shareMeeting()
        }
    }

    private fun loadMeeting(meetingId: Long) {
        viewModelScope.launch {
            _uiState.value = MeetingDetailsUiState.Loading
            currentMeetingId = meetingId

            getMeetingByIdUseCase(meetingId)
                .onSuccess { meeting ->
                    val success = withContext(dispatchers.default) {
                        MeetingDetailsUiState.Success(
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
                            otherMeetings = meeting.communityHost?.meetingsInfo?.toUIKitMeetingInfoList() ?: emptyList(),
                        )
                    }
                    _uiState.value = success
                }.onFailure { exception ->
                    _uiState.value = MeetingDetailsUiState.Error(
                        errorType = exception.toErrorType(),
                    )
                }
        }
    }

    private fun joinMeeting() {
        if (!isLoggedIn.value) {
            viewModelScope.launch { _navEvent.emit(MeetingDetailsNavEvent.NavigateToAuth) }
            return
        }
        val meetingId = currentMeetingId ?: return
        val currentState = _uiState.value as? MeetingDetailsUiState.Success ?: return
        if (currentState.isUserJoined) return
        viewModelScope.launch {
            _uiState.value = currentState.copy(isUserJoined = true)
            joinMeetingUseCase(meetingId)
                .onFailure {
                    _uiState.value = currentState.copy(isUserJoined = false)
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
                .onFailure {
                    _uiState.value = currentState.copy(isUserJoined = true)
                }
        }
    }

    private fun openMap() {
        val state = _uiState.value as? MeetingDetailsUiState.Success ?: return
        viewModelScope.launch {
            if (state.address.latitude != 0.0 && state.address.longitude != 0.0) {
                _navEvent.emit(
                    MeetingDetailsNavEvent.OpenMap(
                        latitude = state.address.latitude,
                        longitude = state.address.longitude,
                        address = state.address.address,
                    ),
                )
            }
        }
    }

    private fun shareMeeting() {
        val state = _uiState.value as? MeetingDetailsUiState.Success ?: return
        viewModelScope.launch {
            _navEvent.emit(
                MeetingDetailsNavEvent.ShareMeeting(
                    title = state.title,
                    shareText = "Приходи на встречу «${state.title}»! ${state.dateTime}, ${state.address.address}",
                ),
            )
        }
    }
}
