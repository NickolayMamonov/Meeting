package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.repository.MeetingsRepository

class SearchMeetingsUseCase(
    private val repository: MeetingsRepository
) {
    suspend operator fun invoke(query: String): Result<List<Meeting>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        return repository.searchEvents(query)
    }
}