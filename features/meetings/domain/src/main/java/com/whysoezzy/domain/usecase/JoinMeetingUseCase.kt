package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.repository.MeetingsRepository

class JoinMeetingUseCase(
    private val repository: MeetingsRepository,
) {
    suspend operator fun invoke(meetingId: Long): Result<Unit> {
        return repository.joinMeeting(meetingId)
    }
}
