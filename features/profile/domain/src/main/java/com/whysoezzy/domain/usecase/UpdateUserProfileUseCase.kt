package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository

class UpdateUserProfileUseCase(
    private val repository: UserRepository
) {
    suspend operator fun invoke(user: User): Result<User> {
        // Минимальная проверка: имя не должно быть пустым
        // Детальная валидация (символы, длина и т.д.) выполняется на стороне сервера
        if (user.name.isBlank()) {
            return Result.failure(Exception("Имя не может быть пустым"))
        }

        return repository.updateUserProfile(user)
    }
}
