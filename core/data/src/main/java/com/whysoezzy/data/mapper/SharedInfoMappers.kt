package com.whysoezzy.data.mapper

import com.whysoezzy.data.dto.CommunityInfoDto
import com.whysoezzy.data.dto.UserInfoDto
import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.models.Person

fun CommunityInfoDto.toDomain(): CommunityInfo = CommunityInfo(
    id = id,
    name = name,
    description = description ?: "",
    imageUrl = imageUrl,
    subscribersCount = subscribersCount ?: 0,
    isSubscribed = isSubscribed,
)

fun UserInfoDto.toDomain(): Person = Person(
    id = id,
    name = name,
    surname = surname,
    avatarUrl = avatarUrl,
    bio = bio,
    role = role,
)
