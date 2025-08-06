package dev.whysoezzy.communities.details.di

import dev.whysoezzy.communities.details.presentation.CommunityDetailsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val communityDetailsModule = module {
    viewModel { CommunityDetailsViewModel() }
}