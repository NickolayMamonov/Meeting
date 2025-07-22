package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PersonHostDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("surname") val surname: String,
    @SerializedName("description") val description: String,
    @SerializedName("image_url") val imageUrl: String
)