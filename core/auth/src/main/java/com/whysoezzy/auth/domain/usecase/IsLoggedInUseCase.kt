package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.repository.AuthRepository

class IsLoggedInUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Boolean {
        return authRepository.isLoggedIn()
    }
}