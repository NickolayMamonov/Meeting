package dev.whysoezzy.profile.domain.usecase

import dev.whysoezzy.domain.models.User
import dev.whysoezzy.domain.repository.UserRepository

class GetUserProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: Long? = null): Result<User> {
        return if (userId == null) {
            userRepository.getCurrentUser()
        } else {
            userRepository.getUserById(userId)
        }
    }
}