package com.whysoezzy.data.di

import com.whysoezzy.auth.domain.repository.UserProfilerUpdater
import com.whysoezzy.data.api.TagsApiImpl
import com.whysoezzy.data.api.UserApiImpl
import com.whysoezzy.data.mapper.UserMapper
import com.whysoezzy.data.repository.TagRepositoryImpl
import com.whysoezzy.data.repository.UserProfileUpdaterImpl
import com.whysoezzy.data.repository.UserRepositoryImpl
import com.whysoezzy.domain.repository.TagRepository
import com.whysoezzy.domain.repository.UserRepository
import com.whysoezzy.domain.usecase.GetAllTagsUseCase
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.GetUserByIdUseCase
import com.whysoezzy.domain.usecase.GetUserCommunitiesUseCase
import com.whysoezzy.domain.usecase.GetUserMeetingsUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase
import org.koin.core.qualifier.named
import org.koin.dsl.module

val profileDataModule = module {
    // Mappers
    single { UserMapper() }

    // APIs
    single { UserApiImpl(get(named("authorizedClient"))) }
    single { TagsApiImpl(get(named("authorizedClient"))) }
    single<UserProfilerUpdater> { UserProfileUpdaterImpl(get()) }
    // Repositories
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<TagRepository> { TagRepositoryImpl(get()) }

    // Use Cases
    factory { GetCurrentUserUseCase(get()) }
    factory { GetUserByIdUseCase(get()) }
    factory { UpdateUserProfileUseCase(get()) }
    factory { GetUserMeetingsUseCase(get()) }
    factory { GetUserCommunitiesUseCase(get()) }
    factory { GetAllTagsUseCase(get()) }
}
