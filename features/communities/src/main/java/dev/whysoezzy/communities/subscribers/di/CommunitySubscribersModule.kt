package dev.whysoezzy.communities.subscribers.di

import dev.whysoezzy.communities.subscribers.presentation.CommunitySubscribersViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val communitySubscribersModule = module {
    viewModel { CommunitySubscribersViewModel() }
}