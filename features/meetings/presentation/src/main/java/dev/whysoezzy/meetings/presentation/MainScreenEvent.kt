package dev.whysoezzy.meetings.presentation

sealed interface MainScreenEvent {
    data object LoadData : MainScreenEvent

    data class Search(
        val query: String,
    ) : MainScreenEvent

    data class FilterByTag(
        val tagId: Long?,
    ) : MainScreenEvent

    data class CommunitySubscriptionChanged(
        val communityId: Long,
        val isSubscribed: Boolean,
    ) : MainScreenEvent

    data object Retry : MainScreenEvent

    data class NavigateToCommunity(
        val communityId: Long,
    ) : MainScreenEvent

    data class NavigateToMeeting(
        val meetingId: Long,
    ) : MainScreenEvent
}
