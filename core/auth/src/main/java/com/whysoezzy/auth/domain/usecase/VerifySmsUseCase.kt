package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.utils.ValidationUtils

class VerifySmsUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phoneNumber: String, code: String): Result<String> {
        if (!ValidationUtils.isValidSmsCode(code)) {
            return Result.failure(Exception("Некорректный код"))
        }

        return authRepository.verifySmsCode(phoneNumber, code)
    }
}