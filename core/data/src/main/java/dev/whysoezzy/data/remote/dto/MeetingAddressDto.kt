package dev.whysoezzy.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MeetingAddressDto(
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)