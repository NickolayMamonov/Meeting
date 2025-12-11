package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.repository.CommunitiesRepository

class SearchCommunitiesUseCase(
    private val repository: CommunitiesRepository
) {
    suspend operator fun invoke(query: String): Result<List<Community>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        return repository.searchCommunities(query)
    }
}