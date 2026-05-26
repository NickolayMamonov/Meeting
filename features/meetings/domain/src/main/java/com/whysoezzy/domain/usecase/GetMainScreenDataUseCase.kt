package com.whysoezzy.domain.usecase

import com.whysoezzy.domain.models.Community
import com.whysoezzy.domain.models.MainScreenData
import com.whysoezzy.domain.models.MeetingTag
import com.whysoezzy.domain.models.TagState
import com.whysoezzy.domain.repository.MeetingsRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.cancellation.CancellationException

/**
 * Use case для загрузки данных главного экрана.
 * Принимает лямбду для загрузки сообществ (чтобы избежать зависимости meetings -> communities).
 * Принимает лямбду для загрузки тегов (чтобы избежать зависимости meetings -> profile).
 */

typealias GetCommunitiesAction = suspend () -> Result<List<Community>>

class GetMainScreenDataUseCase(
    private val meetingsRepository: MeetingsRepository,
    private val getHeroMeetingsUseCase: GetHeroMeetingsUseCase,
    private val getPopularMeetingsUseCase: GetPopularMeetingsUseCase,
    private val getCommunities: GetCommunitiesAction
) {
    suspend operator fun invoke(): Result<MainScreenData> {
        return try {
            supervisorScope {
                val heroDeferred = async { getHeroMeetingsUseCase() }
                val popularDeferred = async { getPopularMeetingsUseCase() }
                val allDeferred = async { meetingsRepository.getAllEvents() }
                val adBlocksDeferred = async { meetingsRepository.getAdBlocks() }
                val communitiesDeferred = async { getCommunities() }

                val heroMeetings = heroDeferred.await().getOrThrow()
                val popularMeetings = popularDeferred.await().getOrThrow()
                val allMeetings = allDeferred.await().getOrThrow()

                val adBlocks = adBlocksDeferred.awaitOrEmpty()
                val communities = communitiesDeferred.awaitOrEmpty()

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
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private suspend fun <T> Deferred<Result<List<T>>>.awaitOrEmpty(): List<T> =
    try {
        await().getOrNull() ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        emptyList()
    }