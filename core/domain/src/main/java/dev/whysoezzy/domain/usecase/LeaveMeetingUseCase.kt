package dev.whysoezzy.domain.usecase

import dev.whysoezzy.domain.repository.MeetingsRepository

class LeaveMeetingUseCase(
    private val meetingRepository: MeetingsRepository,
) {
    suspend operator fun invoke(meetingId: Long): Result<Unit> {
        return try {
            meetingRepository.leaveMeeting(meetingId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}