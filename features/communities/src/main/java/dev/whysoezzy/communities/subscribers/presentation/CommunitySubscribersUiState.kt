package dev.whysoezzy.communities.subscribers.presentation

sealed class CommunitySubscribersUiState {
    object Loading : CommunitySubscribersUiState()
    data class Success(
        val subscribers: List<String>
    ) : CommunitySubscribersUiState()

    data class Error(val message: String) : CommunitySubscribersUiState()
}

sealed class CommunitySubscribersEvent {
    object LoadSubscribers : CommunitySubscribersEvent()
}