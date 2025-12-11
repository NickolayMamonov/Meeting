package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingInfoDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val name: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("date_time") val dateTime: Long
)