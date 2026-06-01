package com.whysoezzy.domain.usecase

import androidx.paging.PagingData
import com.whysoezzy.domain.models.Meeting
import com.whysoezzy.domain.repository.MeetingsRepository
import kotlinx.coroutines.flow.Flow

class GetPagedMeetingsUseCase(
    private val meetingsRepository: MeetingsRepository,
) {
    operator fun invoke(tagId: Long? = null): Flow<PagingData<Meeting>> =
        meetingsRepository.getAllEventsPaged(tagId)
}
