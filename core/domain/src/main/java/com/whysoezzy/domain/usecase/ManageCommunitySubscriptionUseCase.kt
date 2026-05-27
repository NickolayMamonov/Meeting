package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.repository.CommunitiesRepository

class ManageCommunitySubscriptionUseCase(
    private val communitiesRepository: CommunitiesRepository,
) {
    suspend operator fun invoke(
        communityId: Long,
        isSubscribed: Boolean,
    ): Result<Unit> =
        if (isSubscribed) {
            communitiesRepository.subscribeToCommunity(communityId)
        } else {
            communitiesRepository.unsubscribeFromCommunity(communityId)
        }
}
