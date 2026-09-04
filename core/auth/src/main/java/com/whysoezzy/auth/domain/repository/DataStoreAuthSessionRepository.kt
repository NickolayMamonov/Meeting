package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.domain.models.AuthCredentialState
import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.ClearReservation
import kotlinx.coroutines.flow.Flow

/**
 * Session state is stored in the same encrypted DataStore transaction as tokens.
 * This adapter intentionally contains no second source of authenticated truth.
 */
internal class DataStoreAuthSessionRepository(
    private val tokenManager: TokenManager,
) : AuthSessionRepository {
    override val session: Flow<AuthSession> = tokenManager.session
    override val credentialState: Flow<AuthCredentialState> = tokenManager.credentialState

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

    override fun captureAuthOperationPermit(): AuthOperationPermit =
        tokenManager.captureAuthOperationPermit()

    override fun reserveClear(permit: AuthOperationPermit): ClearReservation? =
        tokenManager.reserveClear(permit)

    override suspend fun clearReserved(reservation: ClearReservation): AuthClearResult =
        tokenManager.clearReserved(reservation)
}
