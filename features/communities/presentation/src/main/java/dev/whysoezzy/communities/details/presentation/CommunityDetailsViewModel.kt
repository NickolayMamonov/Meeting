package dev.whysoezzy.communities.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunityMeetingsUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import com.whysoezzy.domain.usecase.SubscribeToCommunityUseCase
import com.whysoezzy.domain.usecase.UnsubscribeFromCommunityUseCase
import com.whysoezzy.network.toUserMessage
import dev.whysoezzy.communities.mappers.toUIKitMeetingInfo
import dev.whysoezzy.communities.mappers.toUIKitPerson
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitTagState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CommunityDetailsNavEvent {
    data class NavigateToMeeting(val meetingId: Long) : CommunityDetailsNavEvent()
    data class NavigateToProfile(val userId: Long) : CommunityDetailsNavEvent()
    object NavigateToSubscribers : CommunityDetailsNavEvent()
    data class ShareCommunity(val title: String, val shareText: String) : CommunityDetailsNavEvent()
}

class CommunityDetailsViewModel(
    private val getCommunityByIdUseCase: GetCommunityByIdUseCase,
    private val getCommunityMeetingsUseCase: GetCommunityMeetingsUseCase,
    private val getCommunitySubscribersUseCase: GetCommunitySubscribersUseCase,
    private val subscribeToCommunityUseCase: SubscribeToCommunityUseCase,
    private val unsubscribeFromCommunityUseCase: UnsubscribeFromCommunityUseCase
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<CommunityDetailsUiState>(CommunityDetailsUiState.Loading)
    val uiState: StateFlow<CommunityDetailsUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<CommunityDetailsNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<CommunityDetailsNavEvent> = _navEvent.asSharedFlow()

    fun onEvent(event: CommunityDetailsEvent) {
        when (event) {
            is CommunityDetailsEvent.LoadCommunity -> loadCommunityDetails(event.communityId)
            CommunityDetailsEvent.ToggleSubscription -> toggleSubscription()
            is CommunityDetailsEvent.NavigateToMeeting -> viewModelScope.launch {
                _navEvent.emit(CommunityDetailsNavEvent.NavigateToMeeting(event.meetingId))
            }
            is CommunityDetailsEvent.NavigateToProfile -> viewModelScope.launch {
                _navEvent.emit(CommunityDetailsNavEvent.NavigateToProfile(event.userId))
            }
            CommunityDetailsEvent.NavigateToSubscribers -> viewModelScope.launch {
                _navEvent.emit(CommunityDetailsNavEvent.NavigateToSubscribers)
            }
            CommunityDetailsEvent.ShareCommunity -> shareCommunity()
        }
    }

    private fun loadCommunityDetails(communityId: Long) {
        viewModelScope.launch {
            _uiState.value = CommunityDetailsUiState.Loading

            getCommunityByIdUseCase(communityId)
                .onFailure { e ->
                    _uiState.value = CommunityDetailsUiState.Error(
                        message = e.toUserMessage()
                    )
                }
                .onSuccess { community ->
                    val (meetings, subscribers) = coroutineScope {
                        val meetingsDeferred = async { getCommunityMeetingsUseCase(communityId) }
                        val subscribersDeferred = async { getCommunitySubscribersUseCase(communityId) }
                        (meetingsDeferred.await().getOrNull() ?: emptyList()) to
                                (subscribersDeferred.await().getOrNull() ?: emptyList())
                    }

                    val currentTime = System.currentTimeMillis()
                    val activeMeetings = meetings.filter { it.time >= currentTime }.sortedBy { it.time }
                    val pastMeetings = meetings.filter { it.time < currentTime }.sortedByDescending { it.time }

                    _uiState.value = CommunityDetailsUiState.Success(
                        communityId = community.id,
                        imageUrl = community.imageUrl,
                        title = community.name,
                        tags = community.tags.map { tag ->
                            UIKitMeetingTag(id = tag.id, text = tag.name, state = UIKitTagState.ACTIVE)
                        },
                        description = community.description,
                        isSubscribed = community.isSubscribed,
                        subscribersCount = community.subscribersCount,
                        subscribers = subscribers.map { it.toUIKitPerson() },
                        activeMeetings = activeMeetings.map { it.toUIKitMeetingInfo() },
                        pastMeetings = pastMeetings.map { it.toUIKitMeetingInfo() }

                    )
                }
        }
    }

    private fun toggleSubscription() {
        val currentState = _uiState.value as? CommunityDetailsUiState.Success ?: return
        val newIsSubscribed = !currentState.isSubscribed
        val newCount = if (newIsSubscribed)
            currentState.subscribersCount + 1
        else
            (currentState.subscribersCount - 1).coerceAtLeast(0)

        _uiState.value = currentState.copy(
            isSubscribed = newIsSubscribed,
            subscribersCount = newCount
        )

        viewModelScope.launch {
            val result = if (newIsSubscribed)
                subscribeToCommunityUseCase(currentState.communityId)
            else
                unsubscribeFromCommunityUseCase(currentState.communityId)

            result.onFailure {
                _uiState.value = currentState.copy(
                    isSubscribed = currentState.isSubscribed,
                    subscribersCount = currentState.subscribersCount
                )
            }
        }
    }

    private fun shareCommunity() {
        val state = _uiState.value as? CommunityDetailsUiState.Success ?: return
        viewModelScope.launch {
            _navEvent.emit(
                CommunityDetailsNavEvent.ShareCommunity(
                    title = state.title,
                    shareText = "Присоединяйся к сообществу «${state.title}» в приложении Meeting!"
                )
            )
        }
    }
}
