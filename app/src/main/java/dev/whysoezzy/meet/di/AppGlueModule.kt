package dev.whysoezzy.meet.di

import com.whysoezzy.domain.usecase.GetHeroMeetingsUseCase
import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.GetPopularMeetingsUseCase
import com.whysoezzy.domain.usecase.GetRecommendedCommunitiesUseCase
import com.whysoezzy.domain.usecase.SearchCommunitiesUseCase
import com.whysoezzy.domain.usecase.SearchUseCase
import org.koin.dsl.module

/**
 * Связочный модуль: собирает use case'ы, которые объединяют данные
 * из нескольких feature-модулей (meetings + communities).
 *
 * Живёт в :app, потому что только :app подключает все feature-модули.
 * Это решение для R-040: UseCase'ы оркестрации не место в
 * presentation-DI одной из участвующих фич.
 */
val appGlueModule =
    module {

        factory {
            GetMainScreenDataUseCase(
                meetingsRepository = get(),
                getHeroMeetingsUseCase = get<GetHeroMeetingsUseCase>(),
                getPopularMeetingsUseCase = get<GetPopularMeetingsUseCase>(),
                getCommunities = { get<GetRecommendedCommunitiesUseCase>().invoke() },
            )
        }

        factory {
            SearchUseCase(
                meetingsRepository = get(),
                searchCommunities = { query ->
                    get<SearchCommunitiesUseCase>().invoke(query)
                },
            )
        }
    }
