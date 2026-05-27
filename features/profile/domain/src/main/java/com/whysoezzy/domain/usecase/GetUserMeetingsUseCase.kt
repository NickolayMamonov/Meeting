package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.MeetingInfo
import com.whysoezzy.domain.repository.UserRepository

class GetUserMeetingsUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(userId: Long): Result<List<MeetingInfo>> {
        return repository.getUserMeetings(userId)
    }
}
