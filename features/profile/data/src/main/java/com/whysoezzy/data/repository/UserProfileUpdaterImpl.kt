package com.whysoezzy.data.repository

import com.whysoezzy.auth.domain.repository.UserProfilerUpdater
import com.whysoezzy.data.api.UserApi
import com.whysoezzy.data.dto.UpdateUserDto
import com.whysoezzy.network.safeApiCall

internal class UserProfileUpdaterImpl(
    private val userApi: UserApi,
) : UserProfilerUpdater {
    override suspend fun updateName(name: String, surname: String): Result<Unit> =
        safeApiCall {
            userApi.updateUserProfile(
                UpdateUserDto(name = name, surname = surname),
            )
        }
}
