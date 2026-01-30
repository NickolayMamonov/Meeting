package dev.whysoezzy.profile.di

import com.whysoezzy.domain.usecase.GetCurrentUserUseCase
import com.whysoezzy.domain.usecase.UpdateUserProfileUseCase
import dev.whysoezzy.profile.details.presentation.ProfileDetailsViewModel

import dev.whysoezzy.profile.edit.presentation.ProfileEditViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {

    // Use cases
    factory { GetCurrentUserUseCase(get()) }
    factory { UpdateUserProfileUseCase(get()) }

    // ViewModels
    viewModel { (userId: Long?) ->
        ProfileDetailsViewModel(
            getUserProfileUseCase = get()
        )
    }

    viewModel {
        ProfileEditViewModel(
            get(), get()
        )
    }
}
