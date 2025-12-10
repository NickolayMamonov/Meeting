package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.AuthResult

interface AuthRepository {
    suspend fun sendSmsCode(phoneNumber: String): Result<String>
    suspend fun verifySmsCode(phoneNumber: String, code: String): Result<String>
    suspend fun register(phoneNumber: String, name: String): Result<AuthResult>
    suspend fun refreshToken(): Result<AuthResult>
    suspend fun logout()
    fun isLoggedIn(): Boolean
}