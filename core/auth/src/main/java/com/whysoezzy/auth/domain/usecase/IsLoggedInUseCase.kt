package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class IsLoggedInUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<Boolean> = authRepository.isLoggedInFlow
}