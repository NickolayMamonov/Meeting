package com.whysoezzy.auth.data.api

import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.RefreshTokenResponse

internal interface AuthApi {
    suspend fun sendOtp(phone: String): Map<String, String>

    suspend fun verifyOtp(
        phone: String,
        code: String,
        name: String? = null,
        surname: String? = null
    ): AuthResponse

    suspend fun refreshToken(refreshToken: String): RefreshTokenResponse

    suspend fun logout(): Map<String, String>
}