package com.whysoezzy.auth.data.dto

import              kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenRequest(
    @SerialName("refreshToken") val refreshToken: String
)

@Serializable
data class RefreshTokenResponse(
    @SerialName("accessToken") val accessToken: String
)
