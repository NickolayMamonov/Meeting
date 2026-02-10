package dev.whysoezzy.communities.subscribers

import com.whysoezzy.domain.models.Person

sealed class CommunitySubscribersUiState {
    object Loading : CommunitySubscribersUiState()

    data class Success(
        val communityName: String,
        val subscribers: List<Person>
    ) : CommunitySubscribersUiState()

    data class Error(val message: String) : CommunitySubscribersUiState()
}

sealed class CommunitySubscribersEvent {
    data class LoadSubscribers(val communityId: Long) : CommunitySubscribersEvent()
    data class NavigateToProfile(val userId: Long) : CommunitySubscribersEvent()
}