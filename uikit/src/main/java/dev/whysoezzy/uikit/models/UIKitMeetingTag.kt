package dev.whysoezzy.uikit.models

import androidx.compose.runtime.Immutable

@Immutable
data class UIKitMeetingTag(
    val id: Long,
    val text: String,
    val state: UIKitTagState
)

enum class UIKitTagState {
    ACTIVE,
    INACTIVE,
    SELECTED,
    DISABLED
}