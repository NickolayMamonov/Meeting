package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TagDto(
    @SerialName("id") val id: Long,
    @SerialName("text") val name: String,
    @SerialName("state") val state: String? = null,
)
