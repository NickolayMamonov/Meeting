package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PersonDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("avatar") val avatar: String,
    @SerialName("bio") val bio: String?
)