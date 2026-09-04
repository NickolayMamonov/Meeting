package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.models.RefreshOutcome
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun requestEmailOtp(email: String): AuthOutcome<Unit>

    suspend fun verifyEmailOtp(
        email: String,
        code: String,
        name: String? = null,
        surname: String? = null,
    ): AuthOutcome<AuthResult>

    suspend fun refreshToken(operationPermit: AuthOperationPermit): RefreshOutcome

    suspend fun logout()

    suspend fun requestServerLogout(): Result<Unit>

    val isLoggedInFlow: Flow<Boolean>
}
