package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository

class GetUserByIdUseCase(
    private val repository: UserRepository
) {
    suspend operator fun invoke(id: Long): Result<User> {
        return repository.getUserById(id)
    }
}