package dev.whysoezzy.meetings.presentation

import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag


sealed class MainScreenUiState {
    object Loading : MainScreenUiState()

    data class Success(
        val heroMeetings: List<UIKitMeetingInfo>,
        val popularMeetings: List<UIKitMeetingInfo>,
        val allMeetings: List<UIKitMeetingInfo>,
        val categories: List<UIKitMeetingTag>,
        val communities: List<UIKitCommunityInfo>
    ) : MainScreenUiState()

    data class SearchResults(
        val meetings: List<UIKitMeetingInfo>,
        val communities: List<UIKitCommunityInfo>
    ) : MainScreenUiState()

    data class Error(val message: String) : MainScreenUiState()
}