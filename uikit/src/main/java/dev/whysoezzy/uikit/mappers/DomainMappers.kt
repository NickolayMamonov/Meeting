package dev.whysoezzy.uikit.mappers

import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitTagState

fun TagState.toUIKitTagState(): UIKitTagState = when (this) {
    TagState.ACTIVE -> UIKitTagState.ACTIVE
    TagState.INACTIVE -> UIKitTagState.INACTIVE
    TagState.SELECTED -> UIKitTagState.SELECTED
    TagState.DISABLED -> UIKitTagState.DISABLED
}

fun MeetingStatus.toUIKitMeetingStatus(): UIKitMeetingStatus = when (this) {
    MeetingStatus.ACTIVE -> UIKitMeetingStatus.ACTIVE
    MeetingStatus.COMPLETED -> UIKitMeetingStatus.COMPLETED
    MeetingStatus.CANCELLED -> UIKitMeetingStatus.CANCELLED
    MeetingStatus.FULL -> UIKitMeetingStatus.FULL
    MeetingStatus.DRAFT -> UIKitMeetingStatus.DRAFT
}

fun MeetingTag.toUIKitMeetingTag(): UIKitMeetingTag = UIKitMeetingTag(
    id = id,
    text = text,
    state = state.toUIKitTagState()
)