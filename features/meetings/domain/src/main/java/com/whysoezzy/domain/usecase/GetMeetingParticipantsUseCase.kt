package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Person
import com.whysoezzy.domain.repository.MeetingsRepository

class GetMeetingParticipantsUseCase(
    private val meetingsRepository: MeetingsRepository,
) {
    suspend operator fun invoke(meetingId: Long): Result<List<Person>> =
        meetingsRepository.getMeetingParticipants(meetingId)
}
