package com.whysoezzy.auth.di

import com.whysoezzy.auth.DataStoreTokenManager
import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.data.api.AuthApiKtor
import com.whysoezzy.auth.data.repository.AuthRepositoryImpl
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.usecase.IsLoggedInUseCase
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.auth.domain.usecase.SendOtpUseCase
import com.whysoezzy.auth.domain.usecase.VerifyOtpUseCase
import com.whysoezzy.network.KtorNetworkModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val authModule =
    module {

        single<TokenManager> { DataStoreTokenManager(androidContext()) }

        single(qualifier = named("publicClient")) {
            KtorNetworkModule.provideHttpClient()
        }

        single<AuthApi> { AuthApiKtor(get(named("publicClient"))) }

        single<AuthRepository> { AuthRepositoryImpl(authApi = get(), tokenManager = get()) }

        single(qualifier = named("authorizedClient")) {
            val tokenManager: TokenManager = get()

            KtorNetworkModule.provideHttpClient(
                tokenProvider = tokenManager,
                onRefreshToken = {
                    refreshAuthorizedTokens(
                        authRepository = get(),
                        tokenManager = tokenManager,
                    )
                },
            )
        }

        factory { SendOtpUseCase(get()) }
        factory { VerifyOtpUseCase(get()) }
        factory { LogoutUseCase(get()) }
        factory { IsLoggedInUseCase(get()) }
    }
