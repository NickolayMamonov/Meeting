package com.whysoezzy.data.di

import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.data.api.PushInstallationApi
import com.whysoezzy.data.api.PushInstallationApiKtor
import com.whysoezzy.data.api.TagsApi
import com.whysoezzy.data.api.TagsApiKtor
import com.whysoezzy.data.api.UserApi
import com.whysoezzy.data.api.UserApiKtor
import com.whysoezzy.data.repository.PushInstallationRepositoryImpl
import com.whysoezzy.data.repository.TagRepositoryImpl
import com.whysoezzy.data.repository.UserProfileUpdaterImpl
import com.whysoezzy.data.repository.UserRepositoryImpl
import com.whysoezzy.domain.repository.PushInstallationRepository
import com.whysoezzy.domain.repository.TagRepository
import com.whysoezzy.domain.repository.UserRepository
import com.whysoezzy.domain.usecase.DeleteCurrentUserProfileUseCase
import com.whysoezzy.domain.usecase.GetAllTagsUseCase
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.GetUserByIdUseCase
import com.whysoezzy.domain.usecase.GetUserCommunitiesUseCase
import com.whysoezzy.domain.usecase.GetUserMeetingsUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase
import com.whysoezzy.domain.usecase.UploadAvatarUseCase
import org.koin.core.qualifier.named
import org.koin.dsl.module

val profileDataModule = module {
    // APIs
    single<UserApi> { UserApiKtor(get(named("authorizedClient"))) }
    single<TagsApi> { TagsApiKtor(get(named("authorizedClient"))) }
    single<PushInstallationApi> {
        PushInstallationApiKtor(get(named("authorizedClient")))
    }
    // Repositories
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<TagRepository> { TagRepositoryImpl(get()) }
    single<PushInstallationRepository> { PushInstallationRepositoryImpl(get()) }

    single<UserProfileUpdater> { UserProfileUpdaterImpl(get<UserApi>()) }
    // Use Cases
    factory { GetCurrentUserUseCase(get()) }
    factory { DeleteCurrentUserProfileUseCase(get()) }
    factory { GetUserByIdUseCase(get()) }
    factory { UpdateUserProfileUseCase(get()) }
    factory { UploadAvatarUseCase(get()) }
    factory { GetUserMeetingsUseCase(get()) }
    factory { GetUserCommunitiesUseCase(get()) }
    factory { GetAllTagsUseCase(get()) }
}
