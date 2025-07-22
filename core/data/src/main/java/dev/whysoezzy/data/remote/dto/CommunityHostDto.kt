package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CommunityHostDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("meetings_info") val meetingsInfo: List<MeetingInfoDto>
)