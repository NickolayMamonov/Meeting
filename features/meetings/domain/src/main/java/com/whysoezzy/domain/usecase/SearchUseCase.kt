package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.SearchData
import com.whysoezzy.domain.repository.MeetingsRepository

class SearchUseCase(
    private val meetingsRepository: MeetingsRepository
) {
    suspend operator fun invoke(query: String): Result<SearchData> {
        return try {
            val meetings = meetingsRepository.searchEvents(query).getOrThrow()
            // TODO: поиск по сообществам

            Result.success(
                SearchData(
                    meetings = meetings,
                    communities = emptyList()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}