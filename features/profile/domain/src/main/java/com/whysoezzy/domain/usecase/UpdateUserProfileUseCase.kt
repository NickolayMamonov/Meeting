package com.whysoezzy.domain.usecase

import com.whysoezzy.common.utils.ValidationUtils
import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository

class UpdateUserProfileUseCase(
    private val repository: UserRepository
) {
    suspend operator fun invoke(user: User): Result<User> {
        if (!ValidationUtils.isValidName(user.name)) {
            return Result.failure(Exception("Некорректное имя"))
        }

        if (!ValidationUtils.isValidName(user.surname)) {
            return Result.failure(Exception("Некорректная фамилия"))
        }

        return repository.updateUserProfile(user)
    }
}