package com.whysoezzy.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendOtpRequest(
    @SerialName("email") val email: String,
)

@Serializable
data class SendOtpResponse(
    @SerialName("message") val message: String,
)
