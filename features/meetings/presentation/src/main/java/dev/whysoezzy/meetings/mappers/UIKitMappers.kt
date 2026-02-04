package dev.whysoezzy.meetings.mappers

import com.whysoezzy.domain.models.CommunityHost
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.models.MeetingAddress
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.PersonHost
import com.whysoezzy.domain.models.Tag
import com.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitCommunity
import dev.whysoezzy.uikit.models.UIKitCommunityHost
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitHost
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.models.UIKitPersonHost
import dev.whysoezzy.uikit.models.UIKitTag
import dev.whysoezzy.uikit.models.UIKitTagState

fun MeetingAddress.toUIKit() = UIKitAddress(
    address = address,
    latitude = latitude,
    longitude = longitude
)

fun Person.toUIKit() = UIKitPerson(
    id = id,
    name = name,
    surname = surname,
    avatar = avatar,
    description = bio
)

fun Tag.toUIKit(
    isSelected: Boolean = false,
    isEnabled: Boolean = true
) = UIKitTag(
    text = name,
    isSelected = isSelected,
    isEnabled = isEnabled
)

fun PersonHost.toUIKit() = UIKitHost(
    id = id,
    name = name,
    surname = surname,
    description = description,
    imageUrl = imageUrl
)

fun CommunityHost.toUIKit() = UIKitCommunity(
    id = id,
    name = title,
    description = description,
    imageUrl = imageUrl
)

fun MeetingInfo.toUIKitAddress() = UIKitAddress(
    address = address ?: "",
    latitude = 0.0,
    longitude = 0.0
)

fun formatDateTime(timestamp: Long): String {
    val formatter = java.text.SimpleDateFormat("dd MMMM yyyy, HH:mm", java.util.Locale("ru"))
    return formatter.format(java.util.Date(timestamp))
}

fun List<Tag>.toUIKit(
    isSelected: Boolean = false,
    isEnabled: Boolean = true
) = map { it.toUIKit(isSelected, isEnabled) }

fun List<Person>.toUIKitPersons() = map { it.toUIKit() }

fun List<Person>.toAvatarUrls() = map { it.avatar }

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

fun MeetingTag.toUIKitMeetingTag(): UIKitMeetingTag {
    return UIKitMeetingTag(
        id = id,
        text = text,
        state = state.toUIKitTagState()
    )
}

fun List<MeetingTag?>.toUIKitMeetingTags(): List<UIKitMeetingTag> {
    return filterNotNull().map { it.toUIKitMeetingTag() }
}

fun PersonHost.toUIKitPersonHost(): UIKitPersonHost {
    return UIKitPersonHost(
        id = id,
        name = name,
        surname = surname,
        description = description,
        imageUrl = imageUrl
    )
}

fun CommunityHost.toUIKitCommunityHost(): UIKitCommunityHost {
    return UIKitCommunityHost(
        id = id,
        title = title,
        description = description,
        imageUrl = imageUrl,
        meetingsInfo = meetingsInfo.map { it.toUIKitMeetingInfo() }
    )
}

fun List<MeetingInfo>.toUIKitMeetingInfoList(): List<UIKitMeetingInfo> {
    return map { it.toUIKitMeetingInfo() }
}

fun Meeting.toUIKitMeetingInfo(): UIKitMeetingInfo {
    return UIKitMeetingInfo(
        id = id,
        title = title,
        imageUrl = imageUrl,
        date = formatDateTime(time),
        address = address.address,
        tags = tags.toUIKitMeetingTags(),
        meetingStatus = meetingStatus.toUIKitMeetingStatus()
    )
}

fun List<Meeting>.toUIKitMeetingInfos(): List<UIKitMeetingInfo> {
    return map { it.toUIKitMeetingInfo() }
}

fun MeetingInfo.toUIKitMeetingInfo(): UIKitMeetingInfo {
    return UIKitMeetingInfo(
        id = id,
        title = title,
        imageUrl = imageUrl,
        date = formatDateTime(time),
        address = address ?: "",
        tags = tags.toUIKitMeetingTags(),
        meetingStatus = meetingStatus?.toUIKitMeetingStatus()
    )
}

fun CommunityInfo.toUIKitCommunityInfo(
    isSubscribed: Boolean = false,
    onSubscribeClick: (Boolean) -> Unit = {},
    onCardClick: (() -> Unit)? = null
): UIKitCommunityInfo {
    return UIKitCommunityInfo(
        id = id,
        title = title,
        imageUrl = imageUrl,
        isSubscribed = isSubscribed,
        onSubscribeClick = onSubscribeClick,
        onCardClick = onCardClick
    )
}

// List маппер для CommunityInfo
fun List<CommunityInfo>.toUIKitCommunityInfoList(
    subscribedIds: Set<Long> = emptySet(),
    onSubscribeClick: (Long, Boolean) -> Unit = { _, _ -> },
    onCardClick: ((Long) -> Unit)? = null
): List<UIKitCommunityInfo> {
    return map { community ->
        community.toUIKitCommunityInfo(
            isSubscribed = subscribedIds.contains(community.id),
            onSubscribeClick = { isSubscribed ->
                onSubscribeClick(community.id, isSubscribed)
            },
            onCardClick = onCardClick?.let { { it(community.id) } }
        )
    }
}