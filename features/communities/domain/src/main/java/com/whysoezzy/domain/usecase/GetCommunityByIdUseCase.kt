package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.repository.CommunitiesRepository

class GetCommunityByIdUseCase(
    private val repository: CommunitiesRepository
) {
    suspend operator fun invoke(id: Long): Result<Community> {
        return repository.getCommunityById(id)
    }
}