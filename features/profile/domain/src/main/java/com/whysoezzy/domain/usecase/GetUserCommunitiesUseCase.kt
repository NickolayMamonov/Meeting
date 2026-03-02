package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.CommunityInfo
import com.whysoezzy.domain.repository.UserRepository

class GetUserCommunitiesUseCase(
    private val repository: UserRepository
) {
    suspend operator fun invoke(userId: Long): Result<List<CommunityInfo>> {
        return repository.getUserCommunities(userId)
    }
}