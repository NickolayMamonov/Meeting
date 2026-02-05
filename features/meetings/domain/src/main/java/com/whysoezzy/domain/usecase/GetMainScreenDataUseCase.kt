package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.MainScreenData
import com.whysoezzy.domain.repository.MeetingsRepository

class GetMainScreenDataUseCase(
    private val meetingsRepository: MeetingsRepository,
    private val getHeroMeetingsUseCase: GetHeroMeetingsUseCase,
    private val getPopularMeetingsUseCase: GetPopularMeetingsUseCase
) {
    suspend operator fun invoke(): Result<MainScreenData> {
        return try {
            val heroMeetings = getHeroMeetingsUseCase().getOrThrow()
            val popularMeetings = getPopularMeetingsUseCase().getOrThrow()
            val allMeetings = meetingsRepository.getAllEvents().getOrThrow()
            val adBlocks = meetingsRepository.getAdBlocks().getOrNull() ?: emptyList()
            // TODO: получить категории и сообщества

            Result.success(
                MainScreenData(
                    heroMeetings = heroMeetings,
                    popularMeetings = popularMeetings,
                    allMeetings = allMeetings,
                    categories = emptyList(),
                    communities = emptyList(),
                    adBlocks = adBlocks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}