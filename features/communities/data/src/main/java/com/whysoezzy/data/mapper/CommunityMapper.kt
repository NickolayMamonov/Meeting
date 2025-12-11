package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityDto
import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.Tag

class CommunityMapper {

    fun toDomain(dto: CommunityDto): Community {
        return Community(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            imageUrl = dto.imageUrl,
            subscribersCount = dto.subscribersCount,
            isSubscribed = dto.isSubscribed,
            tags = dto.tags.map { tagDto ->
                Tag(
                    id = tagDto.id,
                    name = tagDto.name
                )
            }
        )
    }

    fun communityInfoToDomain(dto: CommunityInfoDto): CommunityInfo {
        return CommunityInfo(
            id = dto.id,
            name = dto.name,
            imageUrl = dto.imageUrl
        )
    }
}