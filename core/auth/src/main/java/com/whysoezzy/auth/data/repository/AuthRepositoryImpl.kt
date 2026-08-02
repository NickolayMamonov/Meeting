package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.network.error.ApiException
import com.whysoezzy.network.safeApiCall
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

internal class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
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
        emailApiCall(AuthEndpoint.Verify) {
            val response = authApi.verifyEmailOtp(email, code, name, surname)

            try {
                tokenManager.saveTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    userId = response.user.id,
                )
            } catch (_: Exception) {
                throw SessionPersistenceException()
            }

            AuthResult(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = response.user.id,
                isNewUser = response.isNewUser,
            )
        }

    override suspend fun refreshToken(): Result<String> {
        val currentTokens = tokenManager.loadTokens()
            ?: return Result.failure(ApiException.UnauthorizedError())
        return safeApiCall {
            val response = authApi.refreshToken(currentTokens.refreshToken)
            tokenManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: currentTokens.refreshToken,
                userId = tokenManager.getUserId(),
            )
            response.accessToken
        }
    }

    override suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Server logout failed, clearing local tokens anyway")
        } finally {
            withContext(NonCancellable) {
                tokenManager.clearTokens()
            }
        }
    }

    override val isLoggedInFlow: Flow<Boolean> = tokenManager.isLoggedInFlow

    private suspend fun <T> emailApiCall(
        endpoint: AuthEndpoint,
        block: suspend () -> T,
    ): AuthOutcome<T> =
        try {
            AuthOutcome.Success(block())
        } catch (e: ResponseException) {
            val metadata = parseMetadata(e)
            AuthOutcome.Failure(mapFailure(endpoint, metadata))
        } catch (_: IOException) {
            AuthOutcome.Failure(AuthFailure.NoConnection)
        } catch (_: SessionPersistenceException) {
            AuthOutcome.Failure(AuthFailure.SessionPersistenceFailure)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AuthOutcome.Failure(AuthFailure.Unknown)
        }

    private suspend fun parseMetadata(exception: ResponseException): ApiErrorMetadata {
        val status = exception.response.status.value
        val code =
            runCatching {
                errorJson.decodeFromString<AuthErrorEnvelope>(exception.response.bodyAsText()).code
            }.getOrNull()
        return ApiErrorMetadata(status, code)
    }

    private fun mapFailure(
        endpoint: AuthEndpoint,
        metadata: ApiErrorMetadata,
    ): AuthFailure =
        when {
            endpoint == AuthEndpoint.Verify &&
                metadata.status == 401 &&
                metadata.code == "OTP_INVALID_OR_EXPIRED" -> AuthFailure.InvalidOrExpiredCode
            metadata.status == 400 || metadata.code == "BAD_REQUEST" ->
                if (endpoint == AuthEndpoint.Send) AuthFailure.InvalidEmail else AuthFailure.InvalidCode
            metadata.status == 429 || metadata.code == "OTP_RATE_LIMITED" -> AuthFailure.RateLimited
            metadata.code == "OTP_DELIVERY_UNAVAILABLE" -> AuthFailure.DeliveryUnavailable
            metadata.code == "OTP_ACTIVATION_UNAVAILABLE" -> AuthFailure.ActivationUnavailable
            metadata.status == 401 || metadata.status == 403 -> AuthFailure.Unauthorized
            metadata.status >= 500 -> AuthFailure.Server
            else -> AuthFailure.Unknown
        }

    private enum class AuthEndpoint {
        Send,
        Verify,
    }

    private class SessionPersistenceException : Exception()

    private data class ApiErrorMetadata(
        val status: Int,
        val code: String?,
    )

    @Serializable
    private data class AuthErrorEnvelope(
        val code: String? = null,
    )

    private companion object {
        val errorJson = Json { ignoreUnknownKeys = true }
    }
}
