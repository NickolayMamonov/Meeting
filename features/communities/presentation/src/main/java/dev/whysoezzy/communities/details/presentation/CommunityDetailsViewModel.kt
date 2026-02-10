package dev.whysoezzy.communities.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunityMeetingsUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import com.whysoezzy.domain.usecase.SubscribeToCommunityUseCase
import com.whysoezzy.domain.usecase.UnsubscribeFromCommunityUseCase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun onEvent(event: CommunityDetailsEvent) {
        when (event) {
            is CommunityDetailsEvent.LoadCommunity -> {
                loadCommunityDetails(event.communityId)
            }
            CommunityDetailsEvent.ToggleSubscription -> {
                toggleSubscription()
            }
            is CommunityDetailsEvent.NavigateToMeeting -> {
                // Навигация к встрече - обрабатывается в Screen
            }
            is CommunityDetailsEvent.NavigateToProfile -> {
                // Навигация к профилю - обрабатывается в Screen
            }
            CommunityDetailsEvent.NavigateToSubscribers -> {
                // Навигация к списку подписчиков - обрабатывается в Screen
            }
        }
    }

    private fun loadCommunityDetails(communityId: Long) {
        viewModelScope.launch {
            _uiState.value = CommunityDetailsUiState.Loading

            try {
                // Загружаем базовую информацию о сообществе
                val communityResult = getCommunityByIdUseCase(communityId)
                if (communityResult.isFailure) {
                    _uiState.value = CommunityDetailsUiState.Error(
                        message = communityResult.exceptionOrNull()?.message
                            ?: "Не удалось загрузить информацию о сообществе"
                    )
                    return@launch
                }

                val community = communityResult.getOrThrow()

                // Загружаем встречи
                val meetingsResult = getCommunityMeetingsUseCase(communityId)
                val meetings = meetingsResult.getOrNull() ?: emptyList()

                // Разделяем встречи на активные и прошедшие
                val currentTime = System.currentTimeMillis()
                val activeMeetings = meetings.filter { it.time >= currentTime }
                    .sortedBy { it.time }
                val pastMeetings = meetings.filter { it.time < currentTime }
                    .sortedByDescending { it.time }

                // Загружаем подписчиков
                val subscribersResult = getCommunitySubscribersUseCase(communityId)
                val subscribers = subscribersResult.getOrNull() ?: emptyList()

                _uiState.value = CommunityDetailsUiState.Success(
                    communityId = community.id,
                    imageUrl = community.imageUrl,
                    title = community.name,
                    tags = community.tags.map { tag ->
                        MeetingTag(
                            id = tag.id,
                            text = tag.name,
                            state = TagState.ACTIVE
                        )
                    },
                    description = community.description,
                    isSubscribed = community.isSubscribed,
                    subscribersCount = community.subscribersCount,
                    subscribers = subscribers,
                    activeMeetings = activeMeetings,
                    pastMeetings = pastMeetings
                )
            } catch (e: Exception) {
                _uiState.value = CommunityDetailsUiState.Error(
                    message = e.message ?: "Не удалось загрузить информацию о сообществе"
                )
            }
        }
    }

    private fun toggleSubscription() {
        val currentState = _uiState.value
        if (currentState !is CommunityDetailsUiState.Success) return

        viewModelScope.launch {
            try {
                if (currentState.isSubscribed) {
                    // Отписываемся
                    val result = unsubscribeFromCommunityUseCase(currentState.communityId)
                    if (result.isSuccess) {
                        _uiState.value = currentState.copy(
                            isSubscribed = false,
                            subscribersCount = (currentState.subscribersCount - 1).coerceAtLeast(0)
                        )
                    }
                } else {
                    // Подписываемся
                    val result = subscribeToCommunityUseCase(currentState.communityId)
                    if (result.isSuccess) {
                        _uiState.value = currentState.copy(
                            isSubscribed = true,
                            subscribersCount = currentState.subscribersCount + 1
                        )
                    }
                }
            } catch (e: Exception) {
                // Можно показать Toast или Snackbar с ошибкой
                // Пока просто игнорируем
            }
        }
    }
}