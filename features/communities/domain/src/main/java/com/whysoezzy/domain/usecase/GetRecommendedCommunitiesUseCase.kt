package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.repository.CommunitiesRepository

class GetRecommendedCommunitiesUseCase(
    private val repository: CommunitiesRepository
) {
    suspend operator fun invoke(): Result<List<Community>> {
        return repository.getRecommendedCommunities()
    }
}