package dev.whysoezzy.communities.details.presentation

sealed class CommunityDetailsUiState {
    object Loading : CommunityDetailsUiState()
    data class Success(
        val communityName: String,
        val description: String,
        val subscribersCount: Int,
        val isSubscribed: Boolean
    ) : CommunityDetailsUiState()

    data class Error(val message: String) : CommunityDetailsUiState()
}

sealed class CommunityDetailsEvent {
    object LoadCommunity : CommunityDetailsEvent()
    object ToggleSubscription : CommunityDetailsEvent()
}