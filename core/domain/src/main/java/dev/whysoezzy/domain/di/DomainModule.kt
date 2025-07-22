package dev.whysoezzy.domain.di

import dev.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import dev.whysoezzy.domain.usecase.JoinMeetingUseCase
import dev.whysoezzy.domain.usecase.LeaveMeetingUseCase
import dev.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import dev.whysoezzy.domain.usecase.SearchUseCase
import org.koin.dsl.module

val domainModule = module {

    // Use Cases
    factory { GetMainScreenDataUseCase(get(), get(), get()) }
    factory { SearchUseCase(get(), get()) }
    factory { ManageCommunitySubscriptionUseCase(get()) }
    factory { JoinMeetingUseCase(get()) }
    factory { LeaveMeetingUseCase(get()) }
}