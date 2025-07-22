package dev.whysoezzy.domain.usecase

import dev.whysoezzy.domain.repository.CommunitiesRepository

class ManageCommunitySubscriptionUseCase(
    private val communitiesRepository: CommunitiesRepository
) {
    suspend operator fun invoke(communityId: Long, subscribe: Boolean): Result<Unit> {
        return try {
            if (subscribe) {
                communitiesRepository.subscribeToCommunity(communityId)
            } else {
                communitiesRepository.unsubscribeFromCommunity(communityId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}