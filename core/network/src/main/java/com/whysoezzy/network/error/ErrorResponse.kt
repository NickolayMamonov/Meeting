package com.whysoezzy.network.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ErrorResponse(
    @SerialName("status") val status: Int,
    @SerialName("message") val message: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("path") val path: String,
    @SerialName("code") val code: String? = null,
)

data class ApiErrorMetadata(
    val status: Int,
    val code: String?,
)
