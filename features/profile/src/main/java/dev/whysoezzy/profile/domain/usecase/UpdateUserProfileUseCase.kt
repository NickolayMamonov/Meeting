package dev.whysoezzy.profile.domain.usecase

import dev.whysoezzy.domain.models.User
import dev.whysoezzy.domain.repository.UserRepository

class UpdateUserProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(user: User): Result<User> {
        return userRepository.updateUserProfile(user)
    }
}