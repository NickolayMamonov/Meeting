package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.MainScreenData
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.repository.MeetingsRepository

/**
 * Use case для загрузки данных главного экрана.
 * Принимает лямбду для загрузки сообществ (чтобы избежать зависимости meetings -> communities).
 * Принимает лямбду для загрузки тегов (чтобы избежать зависимости meetings -> profile).
 */
class GetMainScreenDataUseCase(
    private val meetingsRepository: MeetingsRepository,
    private val getHeroMeetingsUseCase: GetHeroMeetingsUseCase,
    private val getPopularMeetingsUseCase: GetPopularMeetingsUseCase,
    private val getCommunities: suspend () -> Result<List<Community>> = { Result.success(emptyList()) },
    private val getTags: suspend () -> Result<List<MeetingTag>> = { Result.success(emptyList()) }
) {
    suspend operator fun invoke(): Result<MainScreenData> {
        return try {
            val heroMeetings = getHeroMeetingsUseCase().getOrThrow()
            val popularMeetings = getPopularMeetingsUseCase().getOrThrow()
            val allMeetings = meetingsRepository.getAllEvents().getOrThrow()
            val adBlocks = meetingsRepository.getAdBlocks().getOrNull() ?: emptyList()
            val communities = getCommunities().getOrNull() ?: emptyList()

            val categories = allMeetings
                .flatMap { it.tags }
                .distinctBy { it.id }
                .map { MeetingTag(id = it.id, text = it.text, state = TagState.ACTIVE) }
                .take(10)

            Result.success(
                MainScreenData(
                    heroMeetings = heroMeetings,
                    popularMeetings = popularMeetings,
                    allMeetings = allMeetings,
                    categories = categories,
                    communities = communities,
                    adBlocks = adBlocks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
