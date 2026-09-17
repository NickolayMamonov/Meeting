package com.whysoezzy.auth

import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.AuthCredentialRead
import com.whysoezzy.auth.domain.models.AuthCredentialState
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthRefreshSaveResult
import com.whysoezzy.auth.domain.models.AuthSaveResult
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.ClearReservation
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.auth.domain.models.OwnerSaveReservation
import com.whysoezzy.network.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Контракт хранилища токенов. Реализация — DataStoreTokenManager (DataStore + Tink AEAD).
 * Извлечён в интерфейс под R-044: тестируется через обычный mock, без mockk-inline на final class.
 */
internal interface TokenManager : TokenProvider {
    val isLoggedInFlow: Flow<Boolean>
    val session: Flow<AuthSession>
    val credentialState: Flow<AuthCredentialState>
        get() = session.map { AuthCredentialState(it, CredentialVersion("legacy", 0L)) }
    val credentialVersion: Flow<CredentialVersion>
        get() = flowOf(CredentialVersion("legacy", 0L))

    fun captureAuthOperationPermit(): AuthOperationPermit

    suspend fun readCredentialSnapshot(permit: AuthOperationPermit): AuthCredentialRead

    fun reserveOwnerSave(): OwnerSaveReservation

    suspend fun saveAuthenticated(
        reservation: OwnerSaveReservation,
        accessToken: String,
        refreshToken: String,
        userId: Long,
        stage: AuthSession.Stage,
    ): AuthSaveResult

    suspend fun saveRefreshedTokens(
        permit: AuthOperationPermit,
        accessToken: String,
        refreshToken: String,
    ): AuthRefreshSaveResult

    fun reserveClear(permit: AuthOperationPermit): ClearReservation?

    suspend fun clearReserved(reservation: ClearReservation): AuthClearResult

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: Long? = null,
    )

    suspend fun saveAuthenticated(
        accessToken: String,
        refreshToken: String,
        userId: Long,
        stage: AuthSession.Stage,
    )

    suspend fun readSession(): AuthSession

    suspend fun compareAndSetStage(
        expected: AuthSession.Stage,
        next: AuthSession.Stage,
    ): Boolean

    suspend fun saveStage(
        userId: Long,
        stage: AuthSession.Stage,
    ): Boolean

    suspend fun getUserId(): Long?

    suspend fun clearTokens()
}
