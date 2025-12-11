package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.repository.MeetingsRepository

class GetHeroMeetingsUseCase(
    private val repository: MeetingsRepository
) {
    suspend operator fun invoke(): Result<List<Meeting>> {
        return repository.getHeroEvents()
    }
}