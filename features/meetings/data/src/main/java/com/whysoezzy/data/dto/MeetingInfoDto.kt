package com.whysoezzy.data.dto

import com.whysoezzy.network.serialization.LocalDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class MeetingInfoDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("date_time") @Serializable(with = LocalDateTimeSerializer::class) val dateTime: LocalDateTime,
    @SerialName("tags") val tags: List<MeetingTagDto>,
    @SerialName("address") val address: String,
    @SerialName("status") val status: String
)