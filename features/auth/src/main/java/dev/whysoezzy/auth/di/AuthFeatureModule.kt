package dev.whysoezzy.auth.di

import com.whysoezzy.auth.domain.repository.UserProfileUpdater
import com.whysoezzy.auth.domain.usecase.RecoverEmailOtpAttemptUseCase
import com.whysoezzy.auth.domain.usecase.RequestEmailOtpUseCase
import dev.whysoezzy.auth.presentation.code.CodeVerificationViewModel
import dev.whysoezzy.auth.presentation.email.EmailInputViewModel
import dev.whysoezzy.auth.presentation.name.NameInputViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val authFeatureModule =
    module {

        viewModel {
            EmailInputViewModel(
                requestEmailOtp = get<RequestEmailOtpUseCase>(),
                recoverEmailOtp = get<RecoverEmailOtpAttemptUseCase>(),
            )
        }

        viewModel { (attemptId: String) ->
            CodeVerificationViewModel(
                attemptId = attemptId,
                loadAttempt = get(),
                resendOtp = get(),
                verifyOtpUseCase = get(),
                clearAttempt = get(),
            )
        }

        viewModel { NameInputViewModel(get<UserProfileUpdater>()) }
    }
