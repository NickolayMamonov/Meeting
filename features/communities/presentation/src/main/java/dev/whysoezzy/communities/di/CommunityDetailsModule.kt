package dev.whysoezzy.communities.di

import dev.whysoezzy.communities.details.presentation.CommunityDetailsViewModel
import dev.whysoezzy.communities.subscribers.CommunitySubscribersViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val communityModule = module {
    viewModel {
        CommunityDetailsViewModel(
            getCommunityByIdUseCase = get(),
            getCommunityMeetingsUseCase = get(),
            getCommunitySubscribersUseCase = get(),
            subscribeToCommunityUseCase = get(),
            unsubscribeFromCommunityUseCase = get()
        )


    }
    viewModel {
        CommunitySubscribersViewModel(
            getCommunityByIdUseCase = get(),
            getCommunitySubscribersUseCase = get()
        )
    }
}