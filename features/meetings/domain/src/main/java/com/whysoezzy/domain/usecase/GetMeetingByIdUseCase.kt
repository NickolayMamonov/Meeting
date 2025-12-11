package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.repository.MeetingsRepository

class GetMeetingByIdUseCase(
    private val repository: MeetingsRepository
) {
    suspend operator fun invoke(id: Long): Result<Meeting> {
        return repository.getMeetingById(id)
    }
}