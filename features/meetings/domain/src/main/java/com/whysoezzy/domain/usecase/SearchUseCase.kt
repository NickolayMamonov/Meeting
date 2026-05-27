package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.SearchData
import com.whysoezzy.domain.repository.MeetingsRepository

class SearchUseCase(
    private val meetingsRepository: MeetingsRepository,
    private val searchCommunities: suspend (String) -> Result<List<Community>> = { Result.success(emptyList()) },
) {
    suspend operator fun invoke(query: String): Result<SearchData> {
        return try {
            val meetings = meetingsRepository.searchEvents(query).getOrThrow()
            val communities = searchCommunities(query).getOrNull() ?: emptyList()

            Result.success(
                SearchData(
                    meetings = meetings,
                    communities = communities,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
