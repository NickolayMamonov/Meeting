package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.repository.MeetingsRepository

class GetAllMeetingsUseCase(
    private val repository: MeetingsRepository,
) {
    suspend operator fun invoke(
        page: Int = 0,
        limit: Int = 20,
        tagId: Long? = null,
    ): Result<List<Meeting>> {
        return repository.getAllEvents(page, limit, tagId)
    }
}
