package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TagDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val text: String,
    @SerializedName("state") val state: String
)