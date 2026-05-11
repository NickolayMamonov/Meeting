package dev.whysoezzy.communities.subscribers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import com.whysoezzy.network.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CommunitySubscribersNavEvent {
    data class NavigateToProfile(val userId: Long) : CommunitySubscribersNavEvent()
}

class CommunitySubscribersViewModel(
    private val getCommunityByIdUseCase: GetCommunityByIdUseCase,
    private val getCommunitySubscribersUseCase: GetCommunitySubscribersUseCase
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<CommunitySubscribersUiState>(CommunitySubscribersUiState.Loading)
    val uiState: StateFlow<CommunitySubscribersUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<CommunitySubscribersNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<CommunitySubscribersNavEvent> = _navEvent.asSharedFlow()

    fun onEvent(event: CommunitySubscribersEvent) {
        when (event) {
            is CommunitySubscribersEvent.LoadSubscribers -> loadSubscribers(event.communityId)
            is CommunitySubscribersEvent.NavigateToProfile -> viewModelScope.launch {
                _navEvent.emit(CommunitySubscribersNavEvent.NavigateToProfile(event.userId))
            }
        }
    }

    private fun loadSubscribers(communityId: Long) {
        viewModelScope.launch {
            _uiState.value = CommunitySubscribersUiState.Loading

            try {
                val communityResult = getCommunityByIdUseCase(communityId)
                if (communityResult.isFailure) {
                    _uiState.value = CommunitySubscribersUiState.Error(
                        message = "Не удалось загрузить информацию о сообществе"
                    )
                    return@launch
                }

                val community = communityResult.getOrThrow()
                val subscribersResult = getCommunitySubscribersUseCase(communityId)
                if (subscribersResult.isFailure) {
                    _uiState.value = CommunitySubscribersUiState.Error(
                        message = "Не удалось загрузить подписчиков"
                    )
                    return@launch
                }

                _uiState.value = CommunitySubscribersUiState.Success(
                    communityName = community.name,
                    subscribers = subscribersResult.getOrThrow()
                )
            } catch (e: Exception) {
                _uiState.value = CommunitySubscribersUiState.Error(
                    message = e.toUserMessage()
                )
            }
        }
    }
}
