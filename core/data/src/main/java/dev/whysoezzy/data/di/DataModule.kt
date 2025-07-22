package dev.whysoezzy.data.di

import dev.whysoezzy.data.mapper.MeetingMapper
import dev.whysoezzy.data.network.NetworkModule
import dev.whysoezzy.data.remote.api.CommunitiesApi
import dev.whysoezzy.data.remote.api.MeetingsApi
import dev.whysoezzy.data.remote.api.UserApi
import dev.whysoezzy.data.repository.AdBlockRepositoryImpl
import dev.whysoezzy.data.repository.CommunitiesRepositoryImpl
import dev.whysoezzy.data.repository.MeetingsRepositoryImpl
import dev.whysoezzy.domain.repository.AdBlockRepository
import dev.whysoezzy.domain.repository.CommunitiesRepository
import dev.whysoezzy.domain.repository.MeetingsRepository
import org.koin.dsl.module

val dataModule = module {

    // Network APIs
    single<MeetingsApi> { NetworkModule.meetingsApi }
    single<CommunitiesApi> { NetworkModule.communitiesApi }
    single<UserApi> { NetworkModule.userApi }

    // Mappers
    single { MeetingMapper() }
//    single { UserMapper() }

    // Repositories
    single<MeetingsRepository> { MeetingsRepositoryImpl(get(), get()) }
    single<CommunitiesRepository> { CommunitiesRepositoryImpl() }
    single<AdBlockRepository> { AdBlockRepositoryImpl() }
}