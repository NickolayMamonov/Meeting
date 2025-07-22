package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CommunityInfoDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("tags") val tags: List<MeetingTagDto>,
)