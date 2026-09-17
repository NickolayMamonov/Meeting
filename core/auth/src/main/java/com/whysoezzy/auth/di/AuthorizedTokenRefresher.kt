package com.whysoezzy.auth.di

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.domain.models.AuthClearResult
import com.whysoezzy.auth.domain.models.RefreshOutcome
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.error.AppException
import com.whysoezzy.network.error.ApiException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Converts an explicit refresh outcome into the token pair required by Ktor.
 *
 * The operation permit is captured before the first suspension. The repository owns the one
 * coherent credential read and returns the exact pair it persisted.
 */
internal suspend fun refreshAuthorizedTokens(
    authRepository: AuthRepository,
    tokenManager: TokenManager,
    sessionRepository: com.whysoezzy.auth.domain.repository.AuthSessionRepository? = null,
): Pair<String, String>? {
    val operationPermit = tokenManager.captureAuthOperationPermit()
    return try {
        when (val outcome = authRepository.refreshToken(operationPermit)) {
            is RefreshOutcome.Refreshed ->
                outcome.pair.accessToken to outcome.pair.refreshToken
            is RefreshOutcome.Missing ->
                clearReserved(tokenManager, outcome.clearPermit)
            is RefreshOutcome.Unauthorized ->
                clearReserved(tokenManager, outcome.clearPermit)
            is RefreshOutcome.TransientFailure -> {
                Timber.w(outcome.error, "Token refresh failed; preserving local session for retry")
                null
            }
            RefreshOutcome.StaleSkipped -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e is AppException.UnauthorizedError || e is ApiException.UnauthorizedError) {
            Timber.w(e, "Token refresh authorization outcome was not explicit")
        } else {
            Timber.w(e, "Token refresh failed; preserving local session for retry")
        }
        null
    }
}

private suspend fun clearReserved(
    tokenManager: TokenManager,
    permit: com.whysoezzy.auth.domain.models.AuthOperationPermit,
): Pair<String, String>? =
    withContext(NonCancellable) {
        val reservation = tokenManager.reserveClear(permit) ?: return@withContext null
        when (tokenManager.clearReserved(reservation)) {
            AuthClearResult.Cleared,
            AuthClearResult.StaleSkipped,
            -> null
        }
    }
