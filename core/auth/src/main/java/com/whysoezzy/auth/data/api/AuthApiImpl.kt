package com.whysoezzy.auth.data.api

import com.whysoezzy.auth.data.dto.AuthResponse
import com.whysoezzy.auth.data.dto.RefreshTokenRequest
import com.whysoezzy.auth.data.dto.RegisterRequest
import com.whysoezzy.auth.data.dto.SendSmsRequest
import com.whysoezzy.auth.data.dto.SendSmsResponse
import com.whysoezzy.auth.data.dto.VerifySmsRequest
import com.whysoezzy.auth.data.dto.VerifySmsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthApiImpl(private val client: HttpClient) {
    suspend fun sendSms(request: SendSmsRequest): SendSmsResponse {
        return client.post("auth/send-sms") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun verifySms(request: VerifySmsRequest): VerifySmsResponse {
        return client.post("auth/verify-sms") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        return client.post("auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        return client.post("auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}