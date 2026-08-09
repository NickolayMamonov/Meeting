package com.whysoezzy.auth.di

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.common.error.AppException
import com.whysoezzy.network.error.ApiException
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Converts a bearer-token refresh into Ktor tokens without treating transient failures as logout.
 */
internal suspend fun refreshAuthorizedTokens(
    authRepository: AuthRepository,
    tokenManager: TokenManager,
    sessionRepository: AuthSessionRepository? = null,
): Pair<String, String>? {
    if (tokenManager.getRefreshToken().isNullOrBlank()) {
        clearSession(tokenManager, sessionRepository)
        return null
    }

    try {
        val result = authRepository.refreshToken()
        val accessToken = result.getOrElse { error ->
            if (error is AppException.UnauthorizedError || error is ApiException.UnauthorizedError) {
                clearSession(tokenManager, sessionRepository)
            } else {
                Timber.w(error, "Token refresh failed; preserving local session for retry")
            }
            return null
        }
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            clearSession(tokenManager, sessionRepository)
            return null
        }
        return accessToken to refreshToken
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Token refresh failed; preserving local session for retry")
        return null
    }
}

private suspend fun clearSession(
    tokenManager: TokenManager,
    sessionRepository: AuthSessionRepository?,
) {
    try {
        sessionRepository?.clear()
    } finally {
        tokenManager.clearTokens()
    }
}
