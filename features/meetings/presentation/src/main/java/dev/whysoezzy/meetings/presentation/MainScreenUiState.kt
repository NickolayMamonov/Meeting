package dev.whysoezzy.meetings.presentation

import androidx.compose.runtime.Immutable
import com.whysoezzy.common.error.ErrorType
import dev.whysoezzy.uikit.models.UIKitAdBlock
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag

@Immutable
sealed interface MainScreenUiState {
    data object Loading : MainScreenUiState

    data class Success(
        val heroMeetings: List<UIKitMeetingInfo>,
        val popularMeetings: List<UIKitMeetingInfo>,
        val allMeetings: List<UIKitMeetingInfo>,
        val categories: List<UIKitMeetingTag>,
        val communities: List<UIKitCommunityInfo>,
        val adBlocks: List<UIKitAdBlock> = emptyList(),
        val searchQuery: String = "",
    ) : MainScreenUiState

    data class Error(
        val errorType: ErrorType,
    ) : MainScreenUiState
}
