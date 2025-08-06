package dev.whysoezzy.communities.details.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CommunityDetailsViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow<CommunityDetailsUiState>(CommunityDetailsUiState.Loading)
    val uiState: StateFlow<CommunityDetailsUiState> = _uiState.asStateFlow()

    init {
        loadCommunityDetails()
    }

    fun onEvent(event: CommunityDetailsEvent) {
        when (event) {
            CommunityDetailsEvent.LoadCommunity -> loadCommunityDetails()
            CommunityDetailsEvent.ToggleSubscription -> toggleSubscription()
        }
    }

    private fun loadCommunityDetails() {
        _uiState.value = CommunityDetailsUiState.Loading

        // Mock data for now
        _uiState.value = CommunityDetailsUiState.Success(
            communityName = "Android Developers",
            description = "Сообщество разработчиков Android",
            subscribersCount = 1250,
            isSubscribed = false
        )
    }

    private fun toggleSubscription() {
        val currentState = _uiState.value
        if (currentState is CommunityDetailsUiState.Success) {
            _uiState.value = currentState.copy(
                isSubscribed = !currentState.isSubscribed,
                subscribersCount = if (currentState.isSubscribed) {
                    currentState.subscribersCount - 1
                } else {
                    currentState.subscribersCount + 1
                }
            )
        }
    }
}