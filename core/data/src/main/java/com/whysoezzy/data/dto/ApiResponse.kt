package com.whysoezzy.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: T? = null,
    @SerialName("error") val error: ErrorResponse? = null,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class ErrorResponse(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("details") val details: Map<String, String>? = null,
)
