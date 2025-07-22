package dev.whysoezzy.meetings.presentation

sealed class MainScreenEvent {
    object LoadData : MainScreenEvent()
    data class Search(val query: String) : MainScreenEvent()
    data class CommunitySubscriptionChanged(
        val communityId: Long,
        val isSubscribed: Boolean
    ) : MainScreenEvent()
    object Retry : MainScreenEvent()
}