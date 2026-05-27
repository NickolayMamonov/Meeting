package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.User
import com.whysoezzy.domain.repository.UserRepository

class GetCurrentUserUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(): Result<User> {
        return repository.getCurrentUser()
    }
}
