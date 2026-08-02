package com.whysoezzy.auth.data.api

import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.RefreshTokenResponse
import com.whysoezzy.auth.data.dto.SendOtpResponse

internal interface AuthApi {
    suspend fun requestEmailOtp(email: String): SendOtpResponse

    suspend fun verifyEmailOtp(
        email: String,
        code: String,
        name: String? = null,
        surname: String? = null,
    ): AuthResponse

    suspend fun refreshToken(refreshToken: String): RefreshTokenResponse

    suspend fun logout(): Map<String, String>
}
