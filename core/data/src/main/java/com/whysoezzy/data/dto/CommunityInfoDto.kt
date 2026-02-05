package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityInfoDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val imageUrl: String,
    val subscribersCount: Int? = null
)