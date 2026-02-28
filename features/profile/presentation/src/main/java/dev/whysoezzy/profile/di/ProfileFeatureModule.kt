package dev.whysoezzy.profile.di

import dev.whysoezzy.profile.details.presentation.ProfileDetailsViewModel
import dev.whysoezzy.profile.edit.presentation.ProfileEditViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val profileFeatureModule = module {
    viewModel {
        ProfileDetailsViewModel(
            getCurrentUserUseCase = get(),
            getUserByIdUseCase = get(),
            getUserMeetingsUseCase = get(),
            getUserCommunitiesUseCase = get()
        )
    }

    viewModel {
        ProfileEditViewModel(
            getCurrentUserUseCase = get(),
            updateUserProfileUseCase = get()
        )
    }
}