package dev.whysoezzy.communities.subscribers

import androidx.compose.runtime.Immutable
import com.whysoezzy.common.error.ErrorType
import dev.whysoezzy.uikit.components.layouts.PersonItem

@Immutable
sealed interface CommunitySubscribersUiState {
    data object Loading : CommunitySubscribersUiState

    data class Success(
        val communityName: String,
        val subscribers: List<PersonItem>,
    ) : CommunitySubscribersUiState

    data class Error(
        val errorType: ErrorType,
    ) : CommunitySubscribersUiState
}

sealed interface CommunitySubscribersEvent {
    data class LoadSubscribers(
        val communityId: Long,
    ) : CommunitySubscribersEvent

    data class NavigateToProfile(
        val userId: Long,
    ) : CommunitySubscribersEvent
}
