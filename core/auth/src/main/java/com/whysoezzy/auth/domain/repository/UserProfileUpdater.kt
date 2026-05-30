package com.whysoezzy.auth.domain.repository

interface UserProfileUpdater {
    suspend fun updateName(
        name: String,
        surname: String,
    ): Result<Unit>
}
