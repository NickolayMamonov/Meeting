package dev.whysoezzy.domain.usecase

import dev.whysoezzy.domain.models.SearchResults
import dev.whysoezzy.domain.repository.CommunitiesRepository
import dev.whysoezzy.domain.repository.MeetingsRepository

class SearchUseCase(
    private val meetingsRepository: MeetingsRepository,
    private val communitiesRepository: CommunitiesRepository,
) {
    suspend operator fun invoke(query: String): Result<SearchResults> {
        return try {
            val eventsResult = meetingsRepository.searchEvents(query)
            val communitiesResult = communitiesRepository.searchCommunities(query)
            val searchResults = SearchResults(
                events = eventsResult.getOrDefault(emptyList()),
                communities = communitiesResult.getOrDefault(emptyList())
            )
            Result.success(searchResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}