package com.whysoezzy.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendSmsRequest(
    @SerialName("phone_number") val phoneNumber: String
)
