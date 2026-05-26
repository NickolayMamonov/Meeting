package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.AdBlockResponseDto
import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.UserInfoDto
import com.whysoezzy.domain.models.AdBlock
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.Person

internal fun AdBlockResponseDto.toDomain(): AdBlock {
    return when (type) {
        "COMMUNITIES" -> AdBlock.CommunitiesAd(
            id = id,
            title = title,
            description = description,
            communities = communities?.map { it.toDomain() } ?: emptyList(),
            isActive = isActive
        )
        "TEXT" -> AdBlock.TextAd(
            id = id,
            title = title,
            description = description,
            actionText = actionText,
            actionUrl = actionUrl,
            isActive = isActive
        )
        "PEOPLE" -> AdBlock.PeopleAd(
            id = id,
            title = title,
            description = description,
            users = users?.map { it.toDomain() } ?: emptyList(),
            isActive = isActive
        )
        else -> throw IllegalArgumentException("Unknown AdBlock type: $type")
    }
}

internal fun CommunityInfoDto.toDomain(): CommunityInfo {
    return CommunityInfo(
        id = id,
        name = name,
        description = description ?: "",
        imageUrl = imageUrl,
        subscribersCount = subscribersCount ?: 0,
        isSubscribed = isSubscribed
    )
}

internal fun UserInfoDto.toDomain(): Person {
    return Person(
        id = id,
        name = name,
        surname = surname,
        avatarUrl = avatarUrl,
        bio = bio,
        role = role
    )
}

internal fun List<AdBlockResponseDto>.toDomain(): List<AdBlock> = map { it.toDomain() }