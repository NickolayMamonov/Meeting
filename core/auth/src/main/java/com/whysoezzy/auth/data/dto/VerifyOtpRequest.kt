package com.whysoezzy.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyOtpRequest(
    @SerialName("email") val email: String,
    @SerialName("code") val code: String,
    @SerialName("name") val name: String? = null,
    @SerialName("surname") val surname: String? = null,
)
