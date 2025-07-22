package dev.whysoezzy.domain.usecase

import dev.whysoezzy.domain.models.MainScreenData
import dev.whysoezzy.domain.repository.AdBlockRepository
import dev.whysoezzy.domain.repository.CommunitiesRepository
import dev.whysoezzy.domain.repository.MeetingsRepository

class GetMainScreenDataUseCase(
    private val meetingsRepository: MeetingsRepository,
    private val communitiesRepository: CommunitiesRepository,
    private val adBlockRepository: AdBlockRepository
) {
    suspend operator fun invoke(): Result<MainScreenData> {
        return try {
            val heroEventsResult = meetingsRepository.getHeroEvents()
            val popularEventsResult = meetingsRepository.getPopularEvents()
            val communitiesResult = communitiesRepository.getRecommendedCommunities()
            val allEventsResult = meetingsRepository.getAllEvents()
            val adBlocksResult = adBlockRepository.getAdBlocks()

            if (heroEventsResult.isSuccess && popularEventsResult.isSuccess && communitiesResult.isSuccess && allEventsResult.isSuccess && adBlocksResult.isSuccess) {
                val mainScreenData = MainScreenData(
                    heroEvents = heroEventsResult.getOrThrow(),
                    popularEvents = popularEventsResult.getOrThrow(),
                    recommendedCommunities = communitiesResult.getOrThrow(),
                    allEvents = allEventsResult.getOrThrow(),
                    adBlocks = adBlocksResult.getOrThrow()
                )
                Result.success(mainScreenData)
            } else {
                heroEventsResult.exceptionOrNull()?.let { return Result.failure(it) }
                popularEventsResult.exceptionOrNull()?.let { return Result.failure(it) }
                communitiesResult.exceptionOrNull()?.let { return Result.failure(it) }
                allEventsResult.exceptionOrNull()?.let { return Result.failure(it) }
                adBlocksResult.exceptionOrNull()?.let { return Result.failure(it) }

                Result.failure(Exception("Unknown error occurred"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}