package com.whysoezzy.data.repository

import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.data.api.UserApi
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.network.safeApiCall

internal class UserProfileUpdaterImpl(
    private val userApi: UserApi,
) : UserProfileUpdater {
    override suspend fun updateName(name: String, surname: String): Result<Unit> =
        safeApiCall {
            userApi.updateUserProfile(
                UpdateUserDto(name = name, surname = surname),
            )
        }
}
