package com.whysoezzy.domain.models

data class CommunityInfo(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val subscribersCount: Int
)
