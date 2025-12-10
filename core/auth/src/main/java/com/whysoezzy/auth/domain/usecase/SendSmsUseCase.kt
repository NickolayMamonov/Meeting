package com.whysoezzy.auth.domain.usecase

import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.common.utils.ValidationUtils

class SendSmsUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phoneNumber: String): Result<String> {
        if (!ValidationUtils.isValidPhoneNumber(phoneNumber)) {
            return Result.failure(Exception("Некорректный номер телефона"))
        }

        return authRepository.sendSmsCode(phoneNumber)
    }

}