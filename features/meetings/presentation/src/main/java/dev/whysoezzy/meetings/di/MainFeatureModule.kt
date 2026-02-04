package dev.whysoezzy.meetings.di

import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.domain.usecase.SearchUseCase
import dev.whysoezzy.meetings.details.presentation.MeetingDetailsViewModel
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsViewModel
import dev.whysoezzy.meetings.presentation.MainScreenViewModel

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mainFeatureModule = module {

    // Use Cases для MainScreen
    factory { GetMainScreenDataUseCase(get(), get(), get()) }
    factory { SearchUseCase(get()) }
    factory { ManageCommunitySubscriptionUseCase() }

    // ViewModels
    viewModel { MainScreenViewModel(get(), get(), get()) }
    viewModel { MeetingDetailsViewModel() }
    viewModel { MeetingParticipantsViewModel() }
}