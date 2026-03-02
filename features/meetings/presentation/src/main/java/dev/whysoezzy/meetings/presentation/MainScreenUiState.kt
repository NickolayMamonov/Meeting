package dev.whysoezzy.meetings.presentation

import com.whysoezzy.domain.models.AdBlock
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
        val communities: List<UIKitCommunityInfo>,
        val adBlocks: List<AdBlock> = emptyList()
    ) : MainScreenUiState()

    data class Error(val message: String) : MainScreenUiState()
}