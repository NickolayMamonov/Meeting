package com.whysoezzy.auth.di

import com.whysoezzy.auth.DataStoreTokenManager
import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApi
import com.whysoezzy.auth.data.api.AuthApiKtor
import com.whysoezzy.auth.data.repository.AuthRepositoryImpl
import com.whysoezzy.auth.domain.AttemptIdGenerator
import com.whysoezzy.auth.domain.AuthClock
import com.whysoezzy.auth.domain.EmailOtpCoordinator
import com.whysoezzy.auth.domain.models.EmailAddressParser
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import com.whysoezzy.auth.domain.repository.DataStoreAuthSessionRepository
import com.whysoezzy.auth.domain.repository.DataStorePendingEmailOtpStore
import com.whysoezzy.auth.domain.repository.PendingEmailOtpStore
import com.whysoezzy.auth.domain.usecase.ClearEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.ClearPendingEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.IsLoggedInUseCase
import com.whysoezzy.auth.domain.usecase.LoadActiveEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.LoadEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.auth.domain.usecase.RecoverEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.RequestEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.ResendEmailOtpUseCase
import com.whysoezzy.auth.domain.usecase.VerifyEmailOtpUseCase
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

        single<AuthRepository> {
            AuthRepositoryImpl(
                authApi = get(),
                tokenManager = get(),
                sessionRepository = get(),
            )
        }
        single<AuthSessionRepository> { DataStoreAuthSessionRepository(get()) }
        single<PendingEmailOtpStore> { DataStorePendingEmailOtpStore(androidContext()) }
        single { EmailAddressParser() }
        single<AuthClock> { AuthClock { System.currentTimeMillis() } }
        single<AttemptIdGenerator> { EmailOtpCoordinator.defaultIdGenerator() }
        single {
            EmailOtpCoordinator(
                repository = get(),
                store = get(),
                parser = get(),
                clock = get(),
                idGenerator = get(),
            )
        }

        single(qualifier = named("authorizedClient")) {
            val tokenManager: TokenManager = get()
            KtorNetworkModule.provideHttpClient(
                tokenProvider = tokenManager,
                onRefreshToken = {
                    refreshAuthorizedTokens(
                        authRepository = get(),
                        tokenManager = tokenManager,
                        sessionRepository = get(),
                    )
                },
            )
        }

        factory { RequestEmailOtpUseCase(get()) }
        factory { RecoverEmailOtpAttemptUseCase(get()) }
        factory { LoadEmailOtpAttemptUseCase(get()) }
        factory { ResendEmailOtpUseCase(get()) }
        factory { VerifyEmailOtpUseCase(get()) }
        factory { ClearEmailOtpAttemptUseCase(get()) }
        factory { ClearPendingEmailOtpUseCase(get()) }
        factory { LoadActiveEmailOtpAttemptUseCase(get()) }
        factory { LogoutUseCase(get()) }
        factory { IsLoggedInUseCase(get()) }
    }
