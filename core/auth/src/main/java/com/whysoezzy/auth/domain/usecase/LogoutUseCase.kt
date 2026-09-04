package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.repository.AuthRepository

class LogoutUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() {
        authRepository.logout()
    }

    suspend fun requestServerLogout(): Result<Unit> =
        authRepository.requestServerLogout()
}
