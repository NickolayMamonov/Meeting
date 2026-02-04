package dev.whysoezzy.communities.mappers

import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.Tag
import dev.whysoezzy.uikit.models.UIKitCommunity
import dev.whysoezzy.uikit.models.UIKitTag

fun CommunityInfo.toUIKit() = UIKitCommunity(
    id = id,
    name = title,
    description = description ?: "",
    imageUrl = imageUrl
)

fun MeetingTag.toUIKit(
    isSelected: Boolean = false,
    isEnabled: Boolean = true
) = UIKitTag(
    text = text,
    isSelected = isSelected,
    isEnabled = isEnabled
)