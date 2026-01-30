package dev.whysoezzy.uikit.models

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