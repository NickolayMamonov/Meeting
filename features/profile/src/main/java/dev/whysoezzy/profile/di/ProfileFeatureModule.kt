package dev.whysoezzy.profile.di

import dev.whysoezzy.profile.details.presentation.ProfileDetailsViewModel
import dev.whysoezzy.profile.edit.presentation.ProfileEditViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val profileFeatureModule = module {
    includes(profileModule)

    viewModel { ProfileDetailsViewModel(get()) }
    viewModel { ProfileEditViewModel(get(), get()) }
}