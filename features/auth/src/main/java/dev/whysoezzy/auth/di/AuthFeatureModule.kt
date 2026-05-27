package dev.whysoezzy.auth.di

import com.whysoezzy.auth.domain.repository.UserProfilerUpdater
import dev.whysoezzy.auth.presentation.code.CodeVerificationViewModel
import dev.whysoezzy.auth.presentation.name.NameInputViewModel
import dev.whysoezzy.auth.presentation.phone.PhoneInputViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val authFeatureModule =
    module {

        viewModel {
            PhoneInputViewModel(
                sendOtpUseCase = get(),
            )
        }

        viewModel { (phoneNumber: String) ->
            CodeVerificationViewModel(
                phoneNumber = phoneNumber,
                verifyOtpUseCase = get(),
                sendOtpUseCase = get(),
            )
        }

        viewModel { NameInputViewModel(get<UserProfilerUpdater>()) }
    }
