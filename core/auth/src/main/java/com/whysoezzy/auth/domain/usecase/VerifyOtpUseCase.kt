package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.models.AuthResult
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.utils.ValidationUtils

class VerifyOtpUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        phone: String,
        code: String,
        name: String? = null,
        surname: String? = null
    ): Result<AuthResult> {
        if (!ValidationUtils.isValidOtpCode(code)) {
            return Result.failure(Exception("Код должен состоять из 4 цифр"))
        }
        return authRepository.verifyOtp(phone, code, name, surname)
    }
}
