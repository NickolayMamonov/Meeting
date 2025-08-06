package dev.whysoezzy.auth.di

import dev.whysoezzy.auth.presentation.code.CodeVerificationViewModel
import dev.whysoezzy.auth.presentation.name.NameInputViewModel
import dev.whysoezzy.auth.presentation.phone.PhoneInputViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val authFeatureModule = module {

    // ViewModels
    viewModel { NameInputViewModel() }

    viewModel { PhoneInputViewModel() }

    viewModel { (phoneNumber: String) ->
        CodeVerificationViewModel(
            phoneNumber = phoneNumber
        )
    }
}
