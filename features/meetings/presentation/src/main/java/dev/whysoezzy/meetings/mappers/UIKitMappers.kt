package dev.whysoezzy.meetings.mappers

import com.whysoezzy.domain.models.Community
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
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Address ─────────────────────────────────────────────────────────────────

fun MeetingAddress.toUIKit() = UIKitAddress(
    address = address,
    latitude = latitude,
    longitude = longitude
)

// ─── Person ───────────────────────────────────────────────────────────────────

fun Person.toUIKit() = UIKitPerson(
    id = id,
    name = name,
    surname = surname,
    avatar = avatarUrl,
    description = bio
)

fun List<Person>.toUIKitPersons() = map { it.toUIKit() }

fun List<Person>.toAvatarUrls() = map { it.avatarUrl }

// ─── Tag ──────────────────────────────────────────────────────────────────────

fun Tag.toUIKit(
    isSelected: Boolean = false,
    isEnabled: Boolean = true
) = UIKitTag(
    text = name,
    isSelected = isSelected,
    isEnabled = isEnabled
)

fun List<Tag>.toUIKit(
    isSelected: Boolean = false,
    isEnabled: Boolean = true
) = map { it.toUIKit(isSelected, isEnabled) }




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

fun List<MeetingTag?>.toUIKitMeetingTags(): List<UIKitMeetingTag> =
    filterNotNull().map { it.toUIKitMeetingTag() }


// ─── Hosts ────────────────────────────────────────────────────────────────────

fun PersonHost.toUIKit() = UIKitHost(
    id = id,
    name = name,
    surname = surname,
    description = description,
    imageUrl = imageUrl
)

fun PersonHost.toUIKitPersonHost() = UIKitPersonHost(
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

fun CommunityHost.toUIKitCommunityHost() = UIKitCommunityHost(
    id = id,
    title = title,
    description = description,
    imageUrl = imageUrl,
    meetingsInfo = meetingsInfo.map { it.toUIKitMeetingInfo() }
)

// ─── Meeting ──────────────────────────────────────────────────────────────────

private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru"))
    return formatter.format(Date(timestamp))
}

/** Полная доменная Meeting → UIKitMeetingInfo */
fun Meeting.toUIKitMeetingInfo() = UIKitMeetingInfo(
    id = id,
    title = title,
    imageUrl = imageUrl,
    date = if (date.isNotBlank()) date else formatDateTime(time),
    address = address.address,
    tags = tags.toUIKitMeetingTags(),
    meetingStatus = meetingStatus.toUIKitMeetingStatus(),
    latitude = address.latitude,
    longitude = address.longitude
)

fun List<Meeting>.toUIKitMeetingInfos(): List<UIKitMeetingInfo> =
    map { it.toUIKitMeetingInfo() }

/** Краткая MeetingInfo (из CommunityHost.meetingsInfo) → UIKitMeetingInfo */
fun MeetingInfo.toUIKitMeetingInfo() = UIKitMeetingInfo(
    id = id,
    title = title,
    imageUrl = imageUrl,
    date = if (time > 0) formatDateTime(time) else "",
    address = address.orEmpty(),
    tags = tags.toUIKitMeetingTags(),
    meetingStatus = meetingStatus.toUIKitMeetingStatus(),
    latitude = 0.0,
    longitude = 0.0
)

fun List<MeetingInfo>.toUIKitMeetingInfoList(): List<UIKitMeetingInfo> =
    map { it.toUIKitMeetingInfo() }

fun MeetingInfo.toUIKitAddress() = UIKitAddress(
    address = address.orEmpty(),
    latitude = 0.0,
    longitude = 0.0
)

// ─── Community ────────────────────────────────────────────────────────────────

fun CommunityInfo.toUIKitCommunityInfo(
    isSubscribed: Boolean = false,
) = UIKitCommunityInfo(
    id = id,
    title = name,
    imageUrl = imageUrl,
    isSubscribed = isSubscribed,

)

/**
 * Community (полная модель с isSubscribed от бэкенда) → UIKitCommunityInfo.
 * isSubscribed берётся из самой модели, не из внешнего Set.
 */
fun Community.toUIKitCommunityInfo() = UIKitCommunityInfo(
    id = id,
    title = name,
    imageUrl = imageUrl,
    isSubscribed = isSubscribed
)

/** Маппер для List<Community> — isSubscribed из модели, callbacks через DI */
fun List<Community>.toUIKitCommunityInfoList(): List<UIKitCommunityInfo> = map { community ->
    UIKitCommunityInfo(
        id = community.id,
        title = community.name,
        imageUrl = community.imageUrl,
        isSubscribed = community.isSubscribed
    )
}


/** Маппер для List<CommunityInfo> — isSubscribed из внешнего Set */
fun List<CommunityInfo>.toUIKitCommunityInfoList(
    subscribedIds: Set<Long> = emptySet()
): List<UIKitCommunityInfo> {
    return map { community ->
        community.toUIKitCommunityInfo(
            isSubscribed = subscribedIds.contains(community.id)
        )
    }
}

internal fun List<UIKitMeetingTag>.toEventCardTags(): List<UIKitEventCardTag> =
    map { tag ->
        UIKitEventCardTag(
            text = tag.text,
            isSelected = tag.state == UIKitTagState.SELECTED,
            isEnabled = tag.state != UIKitTagState.DISABLED
        )
    }