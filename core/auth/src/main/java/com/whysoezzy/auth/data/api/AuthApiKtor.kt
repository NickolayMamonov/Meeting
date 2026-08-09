package com.whysoezzy.auth.data.api

import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.RefreshTokenRequest
import com.whysoezzy.auth.data.dto.RefreshTokenResponse
import com.whysoezzy.auth.data.dto.SendOtpRequest
import com.whysoezzy.auth.data.dto.SendOtpResponse
import com.whysoezzy.auth.data.dto.VerifyOtpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.retry
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class AuthApiKtor(
    private val client: HttpClient,
) : AuthApi {
    override suspend fun requestEmailOtp(email: String): SendOtpResponse =
        client
            .post("auth/email/send-otp") {
                contentType(ContentType.Application.Json)
                retry { noRetry() }
                setBody(SendOtpRequest(email))
            }.body()

    override suspend fun verifyEmailOtp(
        email: String,
        code: String,
        name: String?,
        surname: String?,
    ): AuthResponse =
        client
            .post("auth/email/verify-otp") {
                contentType(ContentType.Application.Json)
                retry { noRetry() }
                setBody(VerifyOtpRequest(email, code, name, surname))
            }.body()

    override suspend fun refreshToken(refreshToken: String): RefreshTokenResponse =
        client
            .post("auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequest(refreshToken))
            }.body()

    override suspend fun logout(): Map<String, String> = client.post("auth/logout").body()
}
