package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.repository.CommunitiesRepository

class UnsubscribeFromCommunityUseCase(
    private val repository: CommunitiesRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> {
        return repository.unsubscribeFromCommunity(id)
    }
}
