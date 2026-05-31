package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.AdBlockResponseDto
import com.whysoezzy.domain.models.AdBlock

internal fun AdBlockResponseDto.toDomain(): AdBlock? {
    return when (type) {
        "COMMUNITIES" -> AdBlock.CommunitiesAd(
            id = id,
            title = title,
            description = description,
            communities = communities?.map { it.toDomain() } ?: emptyList(),
            isActive = isActive,
        )
        "TEXT" -> AdBlock.TextAd(
            id = id,
            title = title,
            description = description,
            actionText = actionText,
            actionUrl = actionUrl,
            isActive = isActive,
        )
        "PEOPLE" -> AdBlock.PeopleAd(
            id = id,
            title = title,
            description = description,
            users = users?.map { it.toDomain() } ?: emptyList(),
            isActive = isActive,
        )
        else -> null
    }
}

internal fun List<AdBlockResponseDto>.toDomain(): List<AdBlock> = mapNotNull { it.toDomain() }
