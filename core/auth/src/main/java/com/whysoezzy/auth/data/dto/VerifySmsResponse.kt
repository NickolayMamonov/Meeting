package com.whysoezzy.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifySmsResponse(
    @SerialName("message") val message: String
)
