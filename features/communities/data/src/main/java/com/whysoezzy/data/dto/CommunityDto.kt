package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("subscribers_count") val subscribersCount: Int,
    @SerialName("is_subscribed") val isSubscribed: Boolean,
    @SerialName("tags") val tags: List<TagDto>
)
