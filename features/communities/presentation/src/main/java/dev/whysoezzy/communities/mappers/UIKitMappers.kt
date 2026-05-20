package dev.whysoezzy.communities.mappers

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.Person
import dev.whysoezzy.uikit.mappers.toUIKitMeetingStatus
import dev.whysoezzy.uikit.mappers.toUIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitPerson

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
