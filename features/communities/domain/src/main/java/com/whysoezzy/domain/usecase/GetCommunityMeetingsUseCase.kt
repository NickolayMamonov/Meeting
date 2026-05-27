package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.repository.CommunitiesRepository

class GetCommunityMeetingsUseCase(
    private val repository: CommunitiesRepository,
) {
    suspend operator fun invoke(communityId: Long): Result<List<Meeting>> {
        return repository.getCommunityMeetings(communityId)
    }
}
