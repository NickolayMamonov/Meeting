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

class AuthApiImpl(private val client: HttpClient) {

    suspend fun sendOtp(phone: String): Map<String, String> {
        return client.post("auth/send-otp") {
            contentType(ContentType.Application.Json)
            setBody(SendOtpRequest(phone))
        }.body()
    }

    suspend fun verifyOtp(
        phone: String,
        code: String,
        name: String? = null,
        surname: String? = null
    ): AuthResponse {
        return client.post("auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(VerifyOtpRequest(phone, code, name, surname))
        }.body()
    }

    suspend fun refreshToken(refreshToken: String): RefreshTokenResponse {
        return client.post("auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken))
        }.body()
    }

    suspend fun logout(): Map<String, String> {
        return client.post("auth/logout").body()
    }
}
