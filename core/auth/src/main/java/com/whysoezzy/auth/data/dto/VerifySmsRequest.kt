package com.whysoezzy.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifySmsRequest(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("code") val code: String
)
