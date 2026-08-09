package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.domain.models.AuthSession
import kotlinx.coroutines.flow.Flow

/**
 * Session state is stored in the same encrypted DataStore transaction as tokens.
 * This adapter intentionally contains no second source of authenticated truth.
 */
internal class DataStoreAuthSessionRepository(
    private val tokenManager: TokenManager,
) : AuthSessionRepository {
    override val session: Flow<AuthSession> = tokenManager.session

    override suspend fun read(): AuthSession = tokenManager.readSession()

    override suspend fun saveAuthenticated(
        userId: Long,
        stage: AuthSession.Stage,
    ) {
        tokenManager.saveStage(userId, stage)
    }

    override suspend fun compareAndSetStage(
        expected: AuthSession.Stage,
        next: AuthSession.Stage,
    ): Boolean = tokenManager.compareAndSetStage(expected, next)

    override suspend fun clear() {
        tokenManager.clearTokens()
    }
}
