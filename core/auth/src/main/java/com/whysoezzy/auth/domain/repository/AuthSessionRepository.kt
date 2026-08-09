package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.AuthSession
import kotlinx.coroutines.flow.Flow

interface AuthSessionRepository {
    val session: Flow<AuthSession>

    suspend fun read(): AuthSession

    suspend fun saveAuthenticated(
        userId: Long,
        stage: AuthSession.Stage,
    )

    suspend fun compareAndSetStage(
        expected: AuthSession.Stage,
        next: AuthSession.Stage,
    ): Boolean

    suspend fun clear()
}
