package dev.whysoezzy.communities.mappers

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.models.UIKitTagState

fun Meeting.toUIKitMeetingInfo() = UIKitMeetingInfo(
    id = id,
    title = title,
    imageUrl = imageUrl,
    date = date,
    address = address.address,
    latitude = address.latitude,
    longitude = address.longitude,
    tags = tags.map { it.toUIKitMeetingTag() },
    meetingStatus = meetingStatus.toUIKitMeetingStatus()
)

fun Person.toUIKitPerson() = UIKitPerson(
    id = id,
    name = name,
    surname = surname,
    avatar = avatarUrl,
    description = bio
)

private fun TagState.toUIKitTagState(): UIKitTagState = when (this) {
    TagState.ACTIVE -> UIKitTagState.ACTIVE
    TagState.INACTIVE -> UIKitTagState.INACTIVE
    TagState.SELECTED -> UIKitTagState.SELECTED
    TagState.DISABLED -> UIKitTagState.DISABLED
}

private fun MeetingStatus.toUIKitMeetingStatus(): UIKitMeetingStatus = when (this) {
    MeetingStatus.ACTIVE -> UIKitMeetingStatus.ACTIVE
    MeetingStatus.COMPLETED -> UIKitMeetingStatus.COMPLETED
    MeetingStatus.CANCELLED -> UIKitMeetingStatus.CANCELLED
    MeetingStatus.FULL -> UIKitMeetingStatus.FULL
    MeetingStatus.DRAFT -> UIKitMeetingStatus.DRAFT
}

private fun MeetingTag.toUIKitMeetingTag(): UIKitMeetingTag = UIKitMeetingTag(
    id = id,
    text = text,
    state = state.toUIKitTagState()
)

internal fun List<UIKitMeetingTag>.toEventCardTags(): List<UIKitEventCardTag> =
    map { tag ->
        UIKitEventCardTag(
            text = tag.text,
            isSelected = tag.state == UIKitTagState.SELECTED,
            isEnabled = tag.state != UIKitTagState.DISABLED
        )
    }

internal fun List<UIKitMeetingTag>.toEventCardTagsAllSelected(): List<UIKitEventCardTag> =
    map { tag -> UIKitEventCardTag(text = tag.text, isSelected = true, isEnabled = false) }

internal fun List<UIKitMeetingTag>.toEventCardTagsAllDisabled(): List<UIKitEventCardTag> =
    map { tag -> UIKitEventCardTag(text = tag.text, isSelected = false, isEnabled = false) }