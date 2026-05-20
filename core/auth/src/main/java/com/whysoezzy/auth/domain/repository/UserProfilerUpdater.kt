package com.whysoezzy.auth.domain.repository

interface UserProfilerUpdater {
    suspend fun updateName(name: String,surname: String): Result<Unit>
}