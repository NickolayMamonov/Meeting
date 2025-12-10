package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApiImpl
import com.whysoezzy.auth.data.dto.RefreshTokenRequest
import com.whysoezzy.auth.data.dto.RegisterRequest
import com.whysoezzy.auth.data.dto.SendSmsRequest
import com.whysoezzy.auth.data.dto.VerifySmsRequest
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.network.safeApiCall

class AuthRepositoryImpl(
    private val authApi: AuthApiImpl,
    private val tokenManager: TokenManager
) : AuthRepository {
    override suspend fun sendSmsCode(phoneNumber: String): Result<String> {
        return safeApiCall {
            val response = authApi.sendSms(SendSmsRequest(phoneNumber))
            response.message
        }
    }

    override suspend fun verifySmsCode(
        phoneNumber: String,
        code: String
    ): Result<String> {
        return safeApiCall {
            val response = authApi.verifySms(VerifySmsRequest(phoneNumber, code))
            response.message
        }
    }

    override suspend fun register(
        phoneNumber: String,
        name: String
    ): Result<AuthResult> {
        return safeApiCall {
            val response = authApi.register(RegisterRequest(phoneNumber, name))

            tokenManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = response.userId
            )

            AuthResult(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = response.userId
            )
        }
    }

    override suspend fun refreshToken(): Result<AuthResult> {
        return safeApiCall {
            val currentRefreshToken =
                tokenManager.getRefreshToken() ?: throw Exception("No refresh token available")

            val response = authApi.refreshToken(RefreshTokenRequest(currentRefreshToken))

            tokenManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = response.userId
            )

            AuthResult(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = response.userId
            )
        }
    }

    override suspend fun logout() {
        tokenManager.clearTokens()
    }

    override fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }
}