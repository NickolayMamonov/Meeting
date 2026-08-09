package com.whysoezzy.data.repository

import com.whysoezzy.auth.domain.models.AuthFailure
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.data.api.UserApi
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.network.error.ApiException
import com.whysoezzy.network.safeApiCall

internal class UserProfileUpdaterImpl(
    private val userApi: UserApi,
) : UserProfileUpdater {
    override suspend fun updateName(name: String, surname: String): AuthOutcome<Unit> =
        safeApiCall {
            userApi.updateUserProfile(
                UpdateUserDto(name = name, surname = surname),
            )
        }.fold(
            onSuccess = { AuthOutcome.Success(Unit) },
            onFailure = { AuthOutcome.Failure(it.toAuthFailure()) },
        )

    private fun Throwable.toAuthFailure(): AuthFailure =
        when (this) {
            is ApiException.UnauthorizedError -> AuthFailure.Unauthorized
            is ApiException.ServerError -> AuthFailure.Server
            is ApiException.NetworkError -> AuthFailure.NoConnection
            else -> AuthFailure.Unknown
        }
}
