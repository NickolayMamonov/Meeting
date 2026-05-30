package dev.whysoezzy.profile.mappers

import com.whysoezzy.common.utils.DateUtils.formatDateTime
import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.CommunityHost
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.models.MeetingStatus
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.PersonHost
import com.whysoezzy.domain.models.SocialMediaInfo
import com.whysoezzy.domain.models.SocialMediaType
import com.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.models.UIKitAddress
import dev.whysoezzy.uikit.models.UIKitCommunity
import dev.whysoezzy.uikit.models.UIKitCommunityHost
import dev.whysoezzy.uikit.models.UIKitCommunityInfo
import dev.whysoezzy.uikit.models.UIKitMeetingInfo
import dev.whysoezzy.uikit.models.UIKitMeetingStatus
import dev.whysoezzy.uikit.models.UIKitMeetingTag
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.models.UIKitPersonHost
import dev.whysoezzy.uikit.models.UIKitSocialMedia
import dev.whysoezzy.uikit.models.UIKitSocialMediaInfo
import dev.whysoezzy.uikit.models.UIKitTagState

fun List<MeetingTag?>.toUIKitMeetingTags(): List<UIKitMeetingTag> {
    return filterNotNull().map { it.toUIKitMeetingTag() }
}

fun MeetingTag.toUIKitEventCardTag(
    isSelected: Boolean = state == TagState.SELECTED,
    isEnabled: Boolean = state != TagState.DISABLED,
): UIKitEventCardTag {
    return UIKitEventCardTag(
        text = text,
        isSelected = isSelected,
        isEnabled = isEnabled,
    )
}

fun MeetingInfo.toUIKitAddress(): UIKitAddress {
    return UIKitAddress(
        address = address,
        latitude = 0.0,
        longitude = 0.0,
    )
}

fun Person.toUIKitPerson(): UIKitPerson {
    return UIKitPerson(
        id = id,
        name = name,
        surname = surname,
        avatar = avatarUrl,
        description = bio,
    )
}

fun CommunityInfo.toUIKitCommunity(): UIKitCommunity {
    return UIKitCommunity(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
    )
}

fun SocialMediaType.toUIKitSocialMedia(): UIKitSocialMedia = when (this) {
    SocialMediaType.TELEGRAM -> UIKitSocialMedia.TELEGRAM
    SocialMediaType.HABR -> UIKitSocialMedia.HABR
    SocialMediaType.LINKEDIN -> UIKitSocialMedia.LINKEDIN
    SocialMediaType.GITHUB -> UIKitSocialMedia.GITHUB
}

fun SocialMediaInfo.toUIKitSocialMediaInfo(): UIKitSocialMediaInfo {
    return UIKitSocialMediaInfo(
        type = type.toUIKitSocialMedia(),
        url = url,
        username = username,
    )
}

fun Map<SocialMediaType, String>.toUIKitSocialMediaInfoList(): List<UIKitSocialMediaInfo> {
    return map { (type, url) ->
        UIKitSocialMediaInfo(
            type = type.toUIKitSocialMedia(),
            url = url,
            username = extractUsername(url),
        )
    }
}

fun CommunityInfo.toUIKitCommunityInfo(
    isSubscribed: Boolean = false,
): UIKitCommunityInfo {
    return UIKitCommunityInfo(
        id = id,
        title = name,
        imageUrl = imageUrl,
        isSubscribed = isSubscribed,
    )
}

fun List<CommunityInfo>.toUIKitCommunityInfoList(): List<UIKitCommunityInfo> =
    map { it.toUIKitCommunityInfo(isSubscribed = it.isSubscribed) }

fun List<Community>.toUIKitCommunityInfoList(): List<UIKitCommunityInfo> = map { community ->
    UIKitCommunityInfo(
        id = community.id,
        title = community.name,
        imageUrl = community.imageUrl,
        isSubscribed = community.isSubscribed,
    )
}

private fun extractUsername(url: String): String {
    return url.substringAfterLast('/').substringBefore('?')
}

fun List<MeetingTag?>.toUIKitEventCardTags(
    isSelected: Boolean = false,
    isEnabled: Boolean = true,
): List<UIKitEventCardTag> {
    return filterNotNull().map { tag ->
        tag.toUIKitEventCardTag(
            isSelected = isSelected || tag.state == TagState.SELECTED,
            isEnabled = isEnabled && tag.state != TagState.DISABLED,
        )
    }
}

fun List<Person>.toUIKitPersons(): List<UIKitPerson> {
    return map { it.toUIKitPerson() }
}

fun List<CommunityInfo>.toUIKitCommunities(): List<UIKitCommunity> {
    return map { it.toUIKitCommunity() }
}

fun PersonHost.toUIKitPersonHost(): UIKitPersonHost {
    return UIKitPersonHost(
        id = id,
        name = name,
        surname = surname,
        description = description,
        imageUrl = imageUrl,
    )
}

fun CommunityHost.toUIKitCommunityHost(): UIKitCommunityHost {
    return UIKitCommunityHost(
        id = id,
        title = title,
        description = description,
        imageUrl = imageUrl,
        meetingsInfo = meetingsInfo.map { it.toUIKitMeetingInfo() },
    )
}

fun MeetingInfo.toUIKitMeetingInfo(): UIKitMeetingInfo {
    return UIKitMeetingInfo(
        id = id,
        title = title,
        imageUrl = imageUrl,
        date = formatDateTime(time),
        address = address,
        latitude = 0.0,
        longitude = 0.0,
        tags = tags.toUIKitMeetingTags(),
        meetingStatus = meetingStatus.toUIKitMeetingStatus(),
    )
}

// List маппер для встреч
fun List<MeetingInfo>.toUIKitMeetingInfoList(): List<UIKitMeetingInfo> {
    return map { it.toUIKitMeetingInfo() }
}

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
    state = state.toUIKitTagState(),
)

internal fun List<UIKitMeetingTag>.toEventCardTags(): List<UIKitEventCardTag> =
    map { tag ->
        UIKitEventCardTag(
            text = tag.text,
            isSelected = tag.state == UIKitTagState.SELECTED,
            isEnabled = tag.state != UIKitTagState.DISABLED,
        )
    }
