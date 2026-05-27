package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.SearchData
import com.whysoezzy.domain.repository.MeetingsRepository

/**
 * Поиск встреч. Для поиска сообществ используется SearchCommunitiesUseCase из communities:domain.
 * Объединение результатов происходит в SearchUseCase из meetings:presentation через MainFeatureModule.
 */
class SearchMeetingsUseCase(
    private val meetingsRepository: MeetingsRepository,
) {
    suspend operator fun invoke(query: String): Result<SearchData> {
        return try {
            val meetings = meetingsRepository.searchEvents(query).getOrThrow()
            Result.success(SearchData(meetings = meetings, communities = emptyList()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
