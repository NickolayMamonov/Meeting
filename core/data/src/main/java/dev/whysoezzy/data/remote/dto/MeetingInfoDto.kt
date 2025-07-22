package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MeetingInfoDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("date_time") val dateTime: Long,
    @SerializedName("tags") val tags: List<MeetingTagDto>,
    @SerializedName("address") val address: String,
    @SerializedName("status") val status: String
)