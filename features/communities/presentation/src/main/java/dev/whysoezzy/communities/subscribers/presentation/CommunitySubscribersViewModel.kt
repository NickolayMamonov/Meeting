package dev.whysoezzy.communities.subscribers.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CommunitySubscribersViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<CommunitySubscribersUiState>(CommunitySubscribersUiState.Loading)
    val uiState: StateFlow<CommunitySubscribersUiState> = _uiState.asStateFlow()

    init {
        loadSubscribers()
    }

    fun onEvent(event: CommunitySubscribersEvent) {
        when (event) {
            CommunitySubscribersEvent.LoadSubscribers -> loadSubscribers()
        }
    }

    private fun loadSubscribers() {
        _uiState.value = CommunitySubscribersUiState.Loading

        // Mock data for now
        _uiState.value = CommunitySubscribersUiState.Success(
            subscribers = listOf(
                "Иван Петров",
                "Мария Сидорова",
                "Алексей Кузнецов",
                "Елена Васильева",
                "Дмитрий Николаев"
            )
        )
    }
}