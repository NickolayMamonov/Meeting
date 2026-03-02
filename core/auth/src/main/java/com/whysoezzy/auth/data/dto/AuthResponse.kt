package com.whysoezzy.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("isNewUser") val isNewUser: Boolean,
    @SerialName("user") val user: AuthUserDto
)

@Serializable
data class AuthUserDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("phone") val phone: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null
)
