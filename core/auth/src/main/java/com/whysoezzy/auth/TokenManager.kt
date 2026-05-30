package com.whysoezzy.auth

import com.whysoezzy.network.TokenProvider
import kotlinx.coroutines.flow.Flow

/**
 * Контракт хранилища токенов. Реализация — DataStoreTokenManager (DataStore + Tink AEAD).
 * Извлечён в интерфейс под R-044: тестируется через обычный mock, без mockk-inline на final class.
 */
internal interface TokenManager : TokenProvider {
    val isLoggedInFlow: Flow<Boolean>

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: Long? = null,
    )

    suspend fun getUserId(): Long?

    suspend fun clearTokens()
}