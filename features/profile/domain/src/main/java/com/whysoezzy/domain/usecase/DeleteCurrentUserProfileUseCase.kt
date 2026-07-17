package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.repository.UserRepository

class DeleteCurrentUserProfileUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.deleteCurrentUserProfile()
}
