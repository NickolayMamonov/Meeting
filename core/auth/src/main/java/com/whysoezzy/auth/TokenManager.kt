package com.whysoezzy.auth

import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.CredentialVersion
import com.whysoezzy.network.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Контракт хранилища токенов. Реализация — DataStoreTokenManager (DataStore + Tink AEAD).
 * Извлечён в интерфейс под R-044: тестируется через обычный mock, без mockk-inline на final class.
 */
internal interface TokenManager : TokenProvider {
    val isLoggedInFlow: Flow<Boolean>
    val session: Flow<AuthSession>
    val credentialVersion: Flow<CredentialVersion>
        get() = flowOf(CredentialVersion("legacy", 0L))

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
