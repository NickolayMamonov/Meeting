package dev.whysoezzy.auth.di

import com.whysoezzy.auth.TokenManager
import com.whysoezzy.auth.data.api.AuthApiImpl
import com.whysoezzy.auth.data.repository.AuthRepositoryImpl
import com.whysoezzy.auth.domain.repository.AuthRepository
import com.whysoezzy.auth.domain.usecase.IsLoggedInUseCase
import com.whysoezzy.auth.domain.usecase.LogoutUseCase
import com.whysoezzy.auth.domain.usecase.RegisterUserUseCase
import com.whysoezzy.auth.domain.usecase.SendSmsUseCase
import com.whysoezzy.auth.domain.usecase.VerifySmsUseCase
import com.whysoezzy.network.KtorNetworkModule
import dev.whysoezzy.auth.presentation.code.CodeVerificationViewModel
import dev.whysoezzy.auth.presentation.name.NameInputViewModel
import dev.whysoezzy.auth.presentation.phone.PhoneInputViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
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
        val authRepositoryLazy = lazy { get<AuthRepository>() }

        KtorNetworkModule.provideHttpClient(
            tokenProvider = tokenManager,
            onRefreshToken = {
                try {
                    val result = authRepositoryLazy.value.refreshToken().getOrNull()
                    result?.let { Pair(it.accessToken, it.refreshToken) }
                } catch (e: Exception) {
                    null
                }
            }
        )
    }

    // 6. Use Cases
    factory { SendSmsUseCase(get()) }
    factory { VerifySmsUseCase(get()) }
    factory { RegisterUserUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { IsLoggedInUseCase(get()) }
}