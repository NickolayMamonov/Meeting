package com.whysoezzy.data.di

import com.whysoezzy.data.api.CommunitiesApiImpl
import com.whysoezzy.data.mapper.CommunityMapper
import com.whysoezzy.data.repository.CommunitiesRepositoryImpl
import com.whysoezzy.domain.repository.CommunitiesRepository
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunityMeetingsUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import com.whysoezzy.domain.usecase.GetRecommendedCommunitiesUseCase
import com.whysoezzy.domain.usecase.SearchCommunitiesUseCase
import com.whysoezzy.domain.usecase.SubscribeToCommunityUseCase
import com.whysoezzy.domain.usecase.UnsubscribeFromCommunityUseCase
import org.koin.core.qualifier.named
import org.koin.dsl.module

val communitiesModule = module {
    single { CommunityMapper() }

    single { CommunitiesApiImpl(get(named("authorizedClient"))) }

    single<CommunitiesRepository> {
        CommunitiesRepositoryImpl(
            communitiesApi = get(),
            communityMapper = get()
        )
    }

    factory { GetRecommendedCommunitiesUseCase(get()) }
    factory { GetCommunityByIdUseCase(get()) }
    factory { SubscribeToCommunityUseCase(get()) }
    factory { UnsubscribeFromCommunityUseCase(get()) }
    factory { SearchCommunitiesUseCase(get()) }
    factory { GetCommunityMeetingsUseCase(get()) }
    factory { GetCommunitySubscribersUseCase(get()) }
}