package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.domain.models.AuthCredentialRead
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOperationPermit
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthRefreshSaveResult
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.models.AuthSaveResult
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.RefreshOutcome
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.common.error.AppException
import com.whysoezzy.network.error.ApiException
import com.whysoezzy.network.safeApiCall
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

internal enum class AuthEndpoint {
    Send,
    Verify,
}

internal data class ApiErrorMetadata(
    val status: Int,
    val code: String?,
)

internal fun mapAuthFailure(
    endpoint: AuthEndpoint,
    metadata: ApiErrorMetadata,
): AuthFailure =
    when (metadata.code?.uppercase()?.replace('-', '_')) {
        "OTP_DELIVERY_UNAVAILABLE", "OTP_PROVIDER_UNAVAILABLE", "PROVIDER_UNAVAILABLE",
        "X013_OTP_PROVIDER_UNAVAILABLE", "X_013_OTP_PROVIDER_UNAVAILABLE",
        "B056_OTP_PROVIDER_UNAVAILABLE", "B_056_OTP_PROVIDER_UNAVAILABLE",
        ->
            AuthFailure.DeliveryUnavailable
        "OTP_ACTIVATION_UNAVAILABLE", "OTP_PROVIDER_ACTIVATION_UNAVAILABLE",
        "ACTIVATION_UNAVAILABLE", "X013_OTP_ACTIVATION_UNAVAILABLE",
        "X_013_OTP_ACTIVATION_UNAVAILABLE", "B056_OTP_ACTIVATION_UNAVAILABLE",
        "B_056_OTP_ACTIVATION_UNAVAILABLE",
        ->
            AuthFailure.ActivationUnavailable
        "OTP_RATE_LIMITED", "OTP_SEND_RATE_LIMITED", "RATE_LIMITED",
        "X013_OTP_RATE_LIMITED", "X_013_OTP_RATE_LIMITED",
        "B056_OTP_RATE_LIMITED", "B_056_OTP_RATE_LIMITED",
        -> AuthFailure.RateLimited
        "OTP_INVALID", "OTP_INVALID_CODE", "INVALID_OTP", "X013_OTP_INVALID",
        "X_013_OTP_INVALID", "B056_OTP_INVALID", "B_056_OTP_INVALID",
        ->
            AuthFailure.InvalidCode
        "OTP_EXPIRED", "OTP_INVALID_OR_EXPIRED", "X013_OTP_EXPIRED",
        "B056_OTP_EXPIRED", "B_056_OTP_EXPIRED", "EXPIRED_OTP",
        "X013_OTP_EXPIRED", "X_013_OTP_EXPIRED", "OTP_INVALID_OR_EXPIRED_CODE",
        ->
            AuthFailure.InvalidOrExpiredCode
        "BAD_REQUEST", "INVALID_EMAIL", "X013_INVALID_EMAIL", "B056_INVALID_EMAIL" ->
            if (endpoint == AuthEndpoint.Send) AuthFailure.InvalidEmail else AuthFailure.InvalidCode
        else -> when {
            metadata.status == 400 ->
                if (endpoint == AuthEndpoint.Send) AuthFailure.InvalidEmail else AuthFailure.InvalidCode
            metadata.status == 429 -> AuthFailure.RateLimited
            metadata.status == 401 || metadata.status == 403 -> AuthFailure.Unauthorized
            metadata.status >= 500 -> AuthFailure.Server
            else -> AuthFailure.Unknown
        }
    }

internal class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val sessionRepository: AuthSessionRepository? = null,
) : AuthRepository {
    override suspend fun requestEmailOtp(email: String): AuthOutcome<Unit> =
        emailApiCall(AuthEndpoint.Send) {
            authApi.requestEmailOtp(email)
            Unit
        }

    override suspend fun verifyEmailOtp(
        email: String,
        code: String,
        name: String?,
        surname: String?,
    ): AuthOutcome<AuthResult> =
        when (
            val responseOutcome = emailApiCall(AuthEndpoint.Verify) {
                authApi.verifyEmailOtp(email, code, name, surname)
            }
        ) {
            is AuthOutcome.Failure -> responseOutcome
            is AuthOutcome.Success -> {
                val response = responseOutcome.value
                val reservation = tokenManager.reserveOwnerSave()
                try {
                    when (
                        tokenManager.saveAuthenticated(
                            reservation = reservation,
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken,
                            userId = response.user.id,
                            stage = if (response.isNewUser) {
                                AuthSession.Stage.NeedsName
                            } else {
                                AuthSession.Stage.Ready
                            },
                        )
                    ) {
                        AuthSaveResult.Persisted -> Unit
                        AuthSaveResult.StaleSkipped ->
                            return AuthOutcome.Failure(AuthFailure.SessionPersistenceFailure)
                    }
                    AuthOutcome.Success(
                        AuthResult(
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken,
                            userId = response.user.id,
                            isNewUser = response.isNewUser,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    withContext(NonCancellable) {
                        runCatching {
                            val clearReservation =
                                tokenManager.reserveClear(reservation.clearPermit)
                            if (clearReservation != null) {
                                tokenManager.clearReserved(clearReservation)
                            }
                        }
                    }
                    AuthOutcome.Failure(AuthFailure.SessionPersistenceFailure)
                }
            }
        }

    override suspend fun refreshToken(operationPermit: AuthOperationPermit): RefreshOutcome {
        val read = try {
            tokenManager.readCredentialSnapshot(operationPermit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RefreshOutcome.TransientFailure(e)
        }
        val boundPermit = when (read) {
            is AuthCredentialRead.Present -> read.permit
            is AuthCredentialRead.Missing -> return RefreshOutcome.Missing(read.permit)
            AuthCredentialRead.Stale -> return RefreshOutcome.StaleSkipped
        }
        val snapshot = read.snapshot

        return try {
            safeApiCall {
                authApi.refreshToken(snapshot.refreshToken)
            }.fold(
                onSuccess = { response ->
                    when (
                        val saved = tokenManager.saveRefreshedTokens(
                            permit = boundPermit,
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken ?: snapshot.refreshToken,
                        )
                    ) {
                        is AuthRefreshSaveResult.Persisted ->
                            RefreshOutcome.Refreshed(saved.pair)
                        AuthRefreshSaveResult.StaleSkipped -> RefreshOutcome.StaleSkipped
                        is AuthRefreshSaveResult.Failed ->
                            RefreshOutcome.TransientFailure(saved.error)
                    }
                },
                onFailure = { error ->
                    if (error.isUnauthorized()) {
                        RefreshOutcome.Unauthorized(boundPermit)
                    } else {
                        RefreshOutcome.TransientFailure(error)
                    }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isUnauthorized()) {
                RefreshOutcome.Unauthorized(boundPermit)
            } else {
                RefreshOutcome.TransientFailure(e)
            }
        }
    }

    override suspend fun logout() {
        val operationPermit = tokenManager.captureAuthOperationPermit()
        val clearPermit = when (val read = tokenManager.readCredentialSnapshot(operationPermit)) {
            is AuthCredentialRead.Present -> read.permit
            is AuthCredentialRead.Missing -> read.permit
            AuthCredentialRead.Stale -> null
        }
        try {
            authApi.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Server logout failed, clearing local tokens anyway")
        } finally {
            withContext(NonCancellable) {
                val clearReservation =
                    clearPermit?.let { tokenManager.reserveClear(it) }
                if (clearReservation != null) {
                    tokenManager.clearReserved(clearReservation)
                }
            }
        }
    }

    override suspend fun requestServerLogout(): Result<Unit> =
        try {
            authApi.logout()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    override val isLoggedInFlow: Flow<Boolean> = tokenManager.isLoggedInFlow

    private fun Throwable.isUnauthorized(): Boolean =
        this is AppException.UnauthorizedError || this is ApiException.UnauthorizedError

    private suspend fun <T> emailApiCall(
        endpoint: AuthEndpoint,
        block: suspend () -> T,
    ): AuthOutcome<T> =
        try {
            safeApiCall { block() }.fold(
                onSuccess = { AuthOutcome.Success(it) },
                onFailure = { error ->
                    val failure: AuthFailure = when (error) {
                        is ApiException.ServerError ->
                            mapAuthFailure(
                                endpoint,
                                ApiErrorMetadata(
                                    status = error.metadata.status,
                                    code = error.metadata.code,
                                ),
                            )
                        is ApiException.UnauthorizedError ->
                            error.metadata?.let {
                                mapAuthFailure(
                                    endpoint,
                                    ApiErrorMetadata(it.status, it.code),
                                )
                            } ?: AuthFailure.Unauthorized
                        is ApiException.NetworkError -> AuthFailure.NoConnection
                        else -> AuthFailure.Unknown
                    }
                    AuthOutcome.Failure(failure)
                },
            )
        } catch (_: IOException) {
            AuthOutcome.Failure(AuthFailure.NoConnection)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AuthOutcome.Failure(AuthFailure.Unknown)
        }
}
