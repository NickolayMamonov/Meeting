package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CommunityDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("subscribersCount") val subscribersCount: Int,
    @SerialName("isSubscribed") val isSubscribed: Boolean,
    @SerialName("tags") val tags: List<TagDto>
)
