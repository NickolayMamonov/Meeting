package dev.whysoezzy.communities.subscribers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunitySubscribersViewModel(
    private val getCommunityByIdUseCase: GetCommunityByIdUseCase,
    private val getCommunitySubscribersUseCase: GetCommunitySubscribersUseCase
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<CommunitySubscribersUiState>(CommunitySubscribersUiState.Loading)
    val uiState: StateFlow<CommunitySubscribersUiState> = _uiState.asStateFlow()

    fun onEvent(event: CommunitySubscribersEvent) {
        when (event) {
            is CommunitySubscribersEvent.LoadSubscribers -> loadSubscribers(event.communityId)
            is CommunitySubscribersEvent.NavigateToProfile -> {
                // Навигация к профилю - обрабатывается в Screen
            }
        }
    }

    private fun loadSubscribers(communityId: Long) {
        viewModelScope.launch {
            _uiState.value = CommunitySubscribersUiState.Loading

            try {
                // Загружаем базовую информацию о сообществе для получения названия
                val communityResult = getCommunityByIdUseCase(communityId)
                if (communityResult.isFailure) {
                    _uiState.value = CommunitySubscribersUiState.Error(
                        message = "Не удалось загрузить информацию о сообществе"
                    )
                    return@launch
                }

                val community = communityResult.getOrThrow()

                // Загружаем подписчиков
                val subscribersResult = getCommunitySubscribersUseCase(communityId)
                if (subscribersResult.isFailure) {
                    _uiState.value = CommunitySubscribersUiState.Error(
                        message = "Не удалось загрузить подписчиков"
                    )
                    return@launch
                }

                val subscribers = subscribersResult.getOrThrow()

                _uiState.value = CommunitySubscribersUiState.Success(
                    communityName = community.name,
                    subscribers = subscribers
                )
            } catch (e: Exception) {
                _uiState.value = CommunitySubscribersUiState.Error(
                    message = e.message ?: "Не удалось загрузить подписчиков"
                )
            }
        }
    }
}


