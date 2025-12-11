package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingInfoDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("time") val time: Long,
    @SerialName("tags") val tags: List<MeetingTagDto>,
    @SerialName("address") val address: String,
    @SerialName("status") val status: String
)