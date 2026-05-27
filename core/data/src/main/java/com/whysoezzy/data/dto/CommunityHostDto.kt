package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityHostDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("meetingsInfo") val meetingsInfo: List<MeetingInfoDto>,
)
