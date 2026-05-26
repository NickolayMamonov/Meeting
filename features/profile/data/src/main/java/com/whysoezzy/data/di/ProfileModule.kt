package com.whysoezzy.data.di

import com.whysoezzy.auth.domain.repository.UserProfilerUpdater
import com.whysoezzy.data.api.TagsApi
import com.whysoezzy.data.api.TagsApiKtor
import com.whysoezzy.data.api.UserApi
import com.whysoezzy.data.api.UserApiKtor
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
    // APIs
    single<UserApi> { UserApiKtor(get(named("authorizedClient"))) }
    single<TagsApi> { TagsApiKtor(get(named("authorizedClient"))) }
    // Repositories
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<TagRepository> { TagRepositoryImpl(get()) }

    single<UserProfilerUpdater> { UserProfileUpdaterImpl(get<UserApi>()) }
    // Use Cases
    factory { GetCurrentUserUseCase(get()) }
    factory { GetUserByIdUseCase(get()) }
    factory { UpdateUserProfileUseCase(get()) }
    factory { GetUserMeetingsUseCase(get()) }
    factory { GetUserCommunitiesUseCase(get()) }
    factory { GetAllTagsUseCase(get()) }
}
