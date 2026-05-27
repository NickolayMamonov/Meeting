package com.whysoezzy.auth.data.api

import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.RefreshTokenRequest
import com.whysoezzy.auth.data.dto.RefreshTokenResponse
import com.whysoezzy.auth.data.dto.SendOtpRequest
import com.whysoezzy.auth.data.dto.VerifyOtpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class AuthApiKtor(
    private val client: HttpClient,
) : AuthApi {
    override suspend fun sendOtp(phone: String): Map<String, String> =
        client
            .post("auth/send-otp") {
                contentType(ContentType.Application.Json)
                setBody(SendOtpRequest(phone))
            }.body()

    override suspend fun verifyOtp(
        phone: String,
        code: String,
        name: String?,
        surname: String?,
    ): AuthResponse =
        client
            .post("auth/verify-otp") {
                contentType(ContentType.Application.Json)
                setBody(VerifyOtpRequest(phone, code, name, surname))
            }.body()

    override suspend fun refreshToken(refreshToken: String): RefreshTokenResponse =
        client
            .post("auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequest(refreshToken))
            }.body()

    override suspend fun logout(): Map<String, String> = client.post("auth/logout").body()
}
