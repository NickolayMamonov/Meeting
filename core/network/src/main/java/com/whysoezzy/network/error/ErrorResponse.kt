package com.whysoezzy.network.error

import kotlinx.serialization.SerialName

data class ErrorResponse(
    @SerialName("status") val status: Int,
    @SerialName("message") val message: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("path") val path: String,
)
