package com.whysoezzy.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("name") val name: String
)
