package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingInfoDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("date") val date: String,
)
