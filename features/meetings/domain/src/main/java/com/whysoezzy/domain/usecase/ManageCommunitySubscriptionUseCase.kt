package com.whysoezzy.domain.usecase

class ManageCommunitySubscriptionUseCase {
    suspend operator fun invoke(communityId: Long, isSubscribed: Boolean): Result<Unit> {
        return try {
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}