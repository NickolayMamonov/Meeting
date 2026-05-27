package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.repository.CommunitiesRepository

class GetCommunitySubscribersUseCase(
    private val repository: CommunitiesRepository,
) {
    suspend operator fun invoke(communityId: Long): Result<List<Person>> {
        return repository.getCommunitySubscribers(communityId)
    }
}
