package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityDto
import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.UserInfoDto
import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.models.Tag

internal fun CommunityDto.toDomain(): Community = Community(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    subscribersCount = subscribersCount,
    isSubscribed = isSubscribed,
    tags = tags.map { tagDto ->
        Tag(
            id = tagDto.id,
            name = tagDto.name,
        )
    },
)

/**
 * Локальное имя `toCommunityInfo` — в :features:meetings:data
 * есть своя CommunityInfoDto.toDomain() (R-035 / R-037).
 */
internal fun CommunityInfoDto.toCommunityInfo(): CommunityInfo = CommunityInfo(
    id = id,
    name = name,
    description = description ?: "",
    imageUrl = imageUrl,
    subscribersCount = subscribersCount ?: 0,
    isSubscribed = isSubscribed,
)

/**
 * Локальное имя `toPerson` — в :features:meetings:data
 * есть UserInfoDto.toDomain() для тех же DTO.
 */
internal fun UserInfoDto.toPerson(): Person = Person(
    id = id,
    name = name,
    surname = surname,
    avatarUrl = avatarUrl,
    bio = bio,
    role = role,
)
