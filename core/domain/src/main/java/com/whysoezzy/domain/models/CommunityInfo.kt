package com.whysoezzy.domain.models

data class CommunityInfo(
    val id: Long,
    val title: String,
    val description: String? = null,
    val imageUrl: String,
    val membersCount : Int? = null
)
