package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingTagDto(
    @SerialName("id") val id: Long,
    @SerialName("text") val text: String,
)
