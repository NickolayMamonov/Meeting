package com.whysoezzy.domain.usecase

interface AccountExitCoordinator {
    suspend fun logout()

    suspend fun deleteCurrentAccount(): Result<Unit>
}
