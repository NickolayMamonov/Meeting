package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.AuthResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>

    suspend fun verifyOtp(
        phone: String,
        code: String,
        name: String? = null,
        surname: String? = null,
    ): Result<AuthResult>

    suspend fun refreshToken(): Result<String>

    suspend fun logout()

    val isLoggedInFlow: Flow<Boolean>
}
