package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.utils.ValidationUtils

class SendOtpUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String): Result<Unit> {
        if (!ValidationUtils.isValidPhoneNumber(phone)) {
            return Result.failure(Exception("Некорректный номер телефона"))
        }
        return authRepository.sendOtp(phone)
    }
}
