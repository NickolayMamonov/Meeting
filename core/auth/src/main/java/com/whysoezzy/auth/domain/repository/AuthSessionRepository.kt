package com.whysoezzy.auth.domain.repository

import com.whysoezzy.auth.domain.models.AuthCredentialState
import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.ClearReservation
import com.whysoezzy.auth.domain.models.CredentialVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AuthSessionRepository {
    val session: Flow<AuthSession>

    val credentialState: Flow<AuthCredentialState>
        get() = session.map {
            AuthCredentialState(it, CredentialVersion("legacy", 0L))
        }

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

    /**
     * Exit coordinators use the same generation-fenced clear authority as refresh/logout.
     * These calls are non-suspending until the reserved write is submitted.
     */
    fun captureAuthOperationPermit(): AuthOperationPermit =
        AuthOperationPermit(0L, null)

    fun reserveClear(permit: AuthOperationPermit): ClearReservation? = null

    suspend fun clearReserved(reservation: ClearReservation): AuthClearResult =
        AuthClearResult.StaleSkipped
}
