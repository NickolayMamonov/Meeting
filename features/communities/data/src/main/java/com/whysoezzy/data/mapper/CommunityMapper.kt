package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityDto
import com.whysoezzy.domain.models.Community
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
