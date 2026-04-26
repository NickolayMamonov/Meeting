package com.whysoezzy.auth.di

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApiImpl
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

val authModule = module {

    single { TokenManager(androidContext()) }

    single(qualifier = named("publicClient")) {
        KtorNetworkModule.provideHttpClient()
    }

    single { AuthApiImpl(get(named("publicClient"))) }

    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }

    single(qualifier = named("authorizedClient")) {
        val tokenManager: TokenManager = get()
        val authRepo = lazy { get<AuthRepository>() }

        KtorNetworkModule.provideHttpClient(
            tokenProvider = tokenManager,
            onRefreshToken = {
                try {
                    val newAccessToken = authRepo.value.refreshToken().getOrNull()
                    if (newAccessToken != null) {
                        val refresh = tokenManager.getRefreshToken() ?: ""
                        Pair(newAccessToken, refresh)
                    } else {
                        // refresh token также протух — очищаем сессию
                        tokenManager.clearTokens()
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuthModule", "Refresh token failed, clearing session", e)
                    tokenManager.clearTokens()
                    null
                }
            }
        )
    }

    factory { SendOtpUseCase(get()) }
    factory { VerifyOtpUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { IsLoggedInUseCase(get()) }
}
