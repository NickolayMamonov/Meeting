package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PersonDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("surname") val surname: String,
    @SerializedName("avatar") val avatar: String,
    @SerializedName("bio") val bio: String?
)