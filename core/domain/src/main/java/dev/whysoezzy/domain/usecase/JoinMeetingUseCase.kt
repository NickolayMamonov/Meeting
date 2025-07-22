package dev.whysoezzy.domain.usecase

import dev.whysoezzy.domain.repository.MeetingsRepository

class JoinMeetingUseCase(
    private val meetingsRepository: MeetingsRepository
) {
    suspend operator fun invoke(meetingId: Long): Result<Unit> {
        return try {
            meetingsRepository.joinMeeting(meetingId)
        } catch (e: Exception) {
            Result.failure(e)
        }

    }
}