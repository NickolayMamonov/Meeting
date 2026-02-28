package com.whysoezzy.data.di

import com.whysoezzy.data.api.UserApiImpl
import com.whysoezzy.data.mapper.UserMapper
import com.whysoezzy.data.repository.UserRepositoryImpl
import com.whysoezzy.domain.repository.UserRepository
import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.GetUserByIdUseCase
import com.whysoezzy.domain.usecase.GetUserCommunitiesUseCase
import com.whysoezzy.domain.usecase.GetUserMeetingsUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase
import org.koin.core.qualifier.named
import org.koin.dsl.module

val profileDataModule = module {
    single { UserMapper() }

    single { UserApiImpl(get(named("authorizedClient"))) }

    single<UserRepository> { UserRepositoryImpl(get(), get()) }

    factory { GetCurrentUserUseCase(get()) }
    factory { GetUserByIdUseCase(get()) }
    factory { UpdateUserProfileUseCase(get()) }
    factory { GetUserMeetingsUseCase(get()) }
    factory { GetUserCommunitiesUseCase(get()) }
}