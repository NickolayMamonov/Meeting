package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.utils.ValidationUtils

class RegisterUserUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phoneNumber: String, name: String): Result<AuthResult> {
        if (!ValidationUtils.isValidName(name)) {
            return Result.failure(Exception("Имя должно содержать минимум 2 символа"))
        }

        return authRepository.register(phoneNumber, name)
    }
}