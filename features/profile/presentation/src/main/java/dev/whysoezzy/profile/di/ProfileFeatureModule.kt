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
            getUserCommunitiesUseCase = get(),
            manageCommunitySubscriptionUseCase = get(),
            logoutUseCase = get(),
            dispatchers = get(),
            accountExitCoordinator = get(),
        )
    }

    viewModel {
        ProfileEditViewModel(
            getCurrentUserUseCase = get(),
            updateUserProfileUseCase = get(),
            getAllTagsUseCase = get(),
            deleteCurrentUserProfileUseCase = get(),
            uploadAvatarUseCase = get(),
            logoutUseCase = get(),
            accountExitCoordinator = get(),
        )
    }
}
