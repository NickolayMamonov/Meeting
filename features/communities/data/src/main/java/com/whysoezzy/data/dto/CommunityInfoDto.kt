package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityInfoDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("image_url") val imageUrl: String
)
