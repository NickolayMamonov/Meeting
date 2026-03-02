package dev.whysoezzy.meetings.presentation

sealed class MainScreenEvent {
    object LoadData : MainScreenEvent()
    data class Search(val query: String) : MainScreenEvent()
    data class FilterByTag(val tagId: Long?) : MainScreenEvent()  // null = сбросить фильтр
    data class CommunitySubscriptionChanged(
        val communityId: Long,
        val isSubscribed: Boolean
    ) : MainScreenEvent()
    object Retry : MainScreenEvent()
    data class NavigateToCommunity(val communityId: Long) : MainScreenEvent()
    data class NavigateToMeeting(val meetingId: Long) : MainScreenEvent()
}
