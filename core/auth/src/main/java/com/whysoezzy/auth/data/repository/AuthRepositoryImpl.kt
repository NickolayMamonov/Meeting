package com.whysoezzy.auth.data.repository

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.network.safeApiCall
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

internal class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
) : AuthRepository {
    override suspend fun sendOtp(phone: String): Result<Unit> {
        return safeApiCall {
            authApi.sendOtp(phone)
        }
        // Unit
    }

    override suspend fun verifyOtp(
        phone: String,
        code: String,
        name: String?,
        surname: String?,
    ): Result<AuthResult> =
        safeApiCall {
            val response = authApi.verifyOtp(phone, code, name, surname)

            tokenManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = response.user.id,
            )

            AuthResult(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = response.user.id,
                isNewUser = response.isNewUser,
            )
        }

    override suspend fun refreshToken(): Result<String> =
        safeApiCall {
            val currentRefreshToken =
                tokenManager.getRefreshToken()
                    ?: throw Exception("No refresh token available")
            val response = authApi.refreshToken(currentRefreshToken)
            tokenManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: currentRefreshToken,
                userId = tokenManager.getUserId(),
            )

            response.accessToken
        }

    override suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Server logout failed, clearing local tokens anyway")
        } finally {
            tokenManager.clearTokens()
        }
    }

    override val isLoggedInFlow: Flow<Boolean> = tokenManager.isLoggedInFlow
}
