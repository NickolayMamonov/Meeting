package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MeetingTagDto(
    @SerializedName("id") val id: Long,
    @SerializedName("text") val text: String,
    @SerializedName("state") val state: String
)