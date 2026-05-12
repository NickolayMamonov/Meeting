package dev.whysoezzy.communities.mappers

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.Person
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.models.UIKitTagState

fun Meeting.toUIKitMeetingInfo() = UIKitMeetingInfo(
    id = id,
    title = title,
    imageUrl = imageUrl,
    date = date,
    address = address.address,
    tags = tags.map { tag ->
        UIKitMeetingTag(
            id = tag.id,
            text = tag.text,
            state = UIKitTagState.ACTIVE
        )
    },
    meetingStatus = meetingStatus.toUIKitMeetingStatus()
)

fun Person.toUIKitPerson() = UIKitPerson(
    id = id,
    name = name,
    surname = surname,
    avatar = avatarUrl,
    description = bio
)

private fun MeetingStatus.toUIKitMeetingStatus(): UIKitMeetingStatus = when (this) {
    MeetingStatus.ACTIVE -> UIKitMeetingStatus.ACTIVE
    MeetingStatus.COMPLETED -> UIKitMeetingStatus.COMPLETED
    MeetingStatus.CANCELLED -> UIKitMeetingStatus.CANCELLED
    MeetingStatus.FULL -> UIKitMeetingStatus.FULL
    MeetingStatus.DRAFT -> UIKitMeetingStatus.DRAFT
}